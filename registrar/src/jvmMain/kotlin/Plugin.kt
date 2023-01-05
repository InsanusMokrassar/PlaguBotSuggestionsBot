package dev.inmo.plagubot.suggestionsbot.registrar

import dev.inmo.micro_utils.coroutines.firstOf
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.repos.create
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.plugins.inline.queries.models.Format
import dev.inmo.plagubot.plugins.inline.queries.models.OfferTemplate
import dev.inmo.plagubot.plugins.inline.queries.repos.InlineTemplatesRepo
import dev.inmo.plaguposter.common.ChatConfig
import dev.inmo.plaguposter.common.FirstSourceIsCommandsFilter
import dev.inmo.plaguposter.posts.models.NewPost
import dev.inmo.plaguposter.posts.models.PostContentInfo
import dev.inmo.plaguposter.posts.repo.PostsRepo
import dev.inmo.tgbotapi.extensions.api.delete
import dev.inmo.tgbotapi.extensions.api.edit.edit
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitContentMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.strictlyOn
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.extensions.utils.extensions.sameChat
import dev.inmo.tgbotapi.extensions.utils.extensions.sameMessage
import dev.inmo.tgbotapi.extensions.utils.textContentOrNull
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.flatInlineKeyboard
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.content.MessageContent
import dev.inmo.tgbotapi.utils.buildEntities
import dev.inmo.tgbotapi.utils.regular
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module

object Plugin : Plugin {
    override fun Module.setupDI(database: Database, params: JsonObject) {

    }

    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val config = koin.get<ChatConfig>()
        val postsRepo = koin.get<PostsRepo>()

        strictlyOn {state: RegistrationState.InProcess ->
            val buttonUuid = "finish"
            val cancelUuid = "cancel"

            val messageToDelete = send(
                state.context,
                buildEntities {
                    if (state.messages.isNotEmpty()) {
                        regular("Your message(s) has been registered. You may send new ones or push \"Finish\" to finalize your suggestion")
                    } else {
                        regular("Ok, send me your messages for new suggestion")
                    }
                },
                replyMarkup = if (state.messages.isNotEmpty()) {
                    flatInlineKeyboard {
                        dataButton(
                            "Finish",
                            buttonUuid
                        )
                        dataButton(
                            "Cancel",
                            cancelUuid
                        )
                    }
                } else {
                    null
                }
            )

            val newMessagesInfo = firstOf<List<ContentMessage<*>>?> {
                add {
                    listOf(
                        waitContentMessage().filter {
                            it.chat.id == state.context && it.content.textContentOrNull() ?.text != "/finish" && it.content.textContentOrNull() ?.text != "/cancel"
                        }.take(1).first()
                    )
                }
                add {
                    val cancelPressed = waitMessageDataCallbackQuery().filter {
                        it.message.sameMessage(messageToDelete) && it.data == cancelUuid
                    }.first()
                    null
                }
                add {
                    val cancelPressed = waitTextMessage ().filter {
                        it.sameChat(messageToDelete) && it.content.text == "/cancel"
                    }.first()
                    null
                }
                add {
                    val finishPressed = waitMessageDataCallbackQuery().filter {
                        it.message.sameMessage(messageToDelete) && it.data == buttonUuid
                    }.first()
                    emptyList<ContentMessage<MessageContent>>()
                }
                add {
                    val finishPressed = waitTextMessage ().filter {
                        it.sameChat(messageToDelete) && it.content.text == "/finish"
                    }.first()
                    emptyList<ContentMessage<MessageContent>>()
                }
            } ?.ifEmpty {
                edit(messageToDelete, "Ok, finishing your request")
                return@strictlyOn RegistrationState.Finish(
                    state.context,
                    state.messages
                )
            } ?.flatMap {
                PostContentInfo.fromMessage(it)
            } ?: return@strictlyOn RegistrationState.Finish(
                state.context,
                emptyList()
            )

            RegistrationState.InProcess(
                state.context,
                state.messages + newMessagesInfo
            ).also {
                delete(messageToDelete)
            }
        }

        strictlyOn { state: RegistrationState.Finish ->
            when {
                state.messages.isEmpty() -> send(state.context, "Suggestion has been cancelled")
                else -> postsRepo.create(
                    NewPost(state.messages)
                )
            }
            null
        }

        onCommand("start_suggestion", initialFilter = { it.chat.id != config.sourceChatId }) {
            startChain(RegistrationState.InProcess(it.chat.id, emptyList()))
        }

        onContentMessage (
            initialFilter = { it.chat.id != config.sourceChatId && !FirstSourceIsCommandsFilter(it) }
        ) {
            startChain(RegistrationState.InProcess(it.chat.id, PostContentInfo.fromMessage(it)))
        }
        koin.getOrNull<InlineTemplatesRepo>() ?.apply {
            addTemplate(
                OfferTemplate(
                    "Start suggestion creating",
                    listOf(Format("/start_suggestion")),
                    "Use this command to start creating of complex suggestion with several messages"
                )
            )
            addTemplate(
                OfferTemplate(
                    "Finish suggestion creating",
                    listOf(Format("/finish")),
                    "Finish creating of complex suggestion"
                )
            )
            addTemplate(
                OfferTemplate(
                    "Finish suggestion creating",
                    listOf(Format("/cancel")),
                    "Finish creating of complex suggestion"
                )
            )
        }
    }
}
