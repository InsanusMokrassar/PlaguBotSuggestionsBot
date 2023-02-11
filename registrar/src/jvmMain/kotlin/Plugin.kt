package dev.inmo.plagubot.suggestionsbot.registrar

import com.soywiz.klock.DateTime
import dev.inmo.micro_utils.coroutines.firstOf
import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.repos.create
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.plugins.inline.queries.models.Format
import dev.inmo.plagubot.plugins.inline.queries.models.OfferTemplate
import dev.inmo.plagubot.plugins.inline.queries.repos.InlineTemplatesRepo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.NewSuggestion
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionContentInfo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionStatus
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.SuggestionsRepo
import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.api.delete
import dev.inmo.tgbotapi.extensions.api.edit.edit
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitContentMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.strictlyOn
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.CommonMessageFilter
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.utils.not
import dev.inmo.tgbotapi.extensions.behaviour_builder.utils.times
import dev.inmo.tgbotapi.extensions.utils.extensions.sameChat
import dev.inmo.tgbotapi.extensions.utils.extensions.sameMessage
import dev.inmo.tgbotapi.extensions.utils.textContentOrNull
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.flatInlineKeyboard
import dev.inmo.tgbotapi.extensions.utils.withContentOrNull
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.content.MessageContent
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.message.textsources.BotCommandTextSource
import dev.inmo.tgbotapi.types.toChatId
import dev.inmo.tgbotapi.utils.buildEntities
import dev.inmo.tgbotapi.utils.regular
import dev.inmo.plagubot.suggestionsbot.common.ChatsConfig
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module

object Plugin : Plugin {
    override fun Module.setupDI(database: Database, params: JsonObject) {

    }

    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val config = koin.get<ChatsConfig>()
        val postsRepo = koin.get<SuggestionsRepo>()

        strictlyOn { state: RegistrationState.InProcess ->
            var state = state.copy()
            val buttonUuid = "finish"
            val cancelUuid = "cancel"
            val toggleAnonymousUuid = "toggleAnon"

            fun buildKeyboard() = flatInlineKeyboard {
                if (state.messages.isNotEmpty()) {
                    dataButton(
                        "Finish",
                        buttonUuid
                    )
                }
                dataButton(
                    "Cancel",
                    cancelUuid
                )
                val anonStatus = if (state.isAnonymous) {
                    "✅"
                } else {
                    "❌"
                }
                dataButton(
                    "$anonStatus Anonymous",
                    toggleAnonymousUuid
                )
            }

            val messageToDelete = send(
                state.context,
                buildEntities {
                    if (state.messages.isNotEmpty()) {
                        regular("Your message(s) has been registered. You may send new ones or push \"Finish\" to finalize your suggestion")
                    } else {
                        regular("Ok, send me your messages for new suggestion")
                    }
                },
                replyMarkup = buildKeyboard(),
                replyToMessageId = state.messages.lastOrNull() ?.messageMetaInfo ?.messageId
            )

            waitMessageDataCallbackQuery().filter {
                it.message.sameMessage(messageToDelete) && it.data == toggleAnonymousUuid
            }.subscribeSafelyWithoutExceptions(this) {
                state = state.copy(isAnonymous = !state.isAnonymous)
                answer(it)
                edit(messageToDelete, buildKeyboard())
            }

            val newMessagesInfo = firstOf<List<ContentMessage<*>>?> {
                add {
                    val result = mutableListOf<ContentMessage<MessageContent>>()
                    waitContentMessage().filter {
                        it.chat.id == state.context && it.content.textContentOrNull() ?.text != "/finish" && it.content.textContentOrNull() ?.text != "/cancel"
                    }.onEach { result.add(it) }.debounce(1000L).first()
                    result
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
                    state.messages,
                    state.isAnonymous
                )
            } ?.flatMapIndexed { i, it ->
                SuggestionContentInfo.fromMessage(it, state.messages.size + i)
            } ?: return@strictlyOn RegistrationState.Finish(
                state.context,
                emptyList(),
                state.isAnonymous
            )

            RegistrationState.InProcess(
                state.context,
                state.messages + newMessagesInfo,
                state.isAnonymous
            ).also {
                delete(messageToDelete)
            }
        }

        strictlyOn { state: RegistrationState.Finish ->
            when {
                state.messages.isEmpty() -> send(state.context, "Suggestion has been cancelled")
                else -> postsRepo.create(
                    NewSuggestion(SuggestionStatus.Created(DateTime.now()), state.context, state.isAnonymous, state.messages)
                )
            }
            null
        }

        val registrarCommandsFilter: CommonMessageFilter<*> = CommonMessageFilter {
            !config.checkIsOfWorkChat(it.chat.id) && it.chat is PrivateChat
        }
        val firstMessageNotCommandFilter: CommonMessageFilter<*> = CommonMessageFilter {
            it.withContentOrNull<TextContent>() ?.let { it.content.textSources.firstOrNull() is BotCommandTextSource } == true
        }

        onCommand("start_suggestion", initialFilter = registrarCommandsFilter) {
            startChain(RegistrationState.InProcess(it.chat.id.toChatId(), emptyList()))
        }

        onContentMessage (
            initialFilter = registrarCommandsFilter * !firstMessageNotCommandFilter
        ) {
            startChain(RegistrationState.InProcess(it.chat.id.toChatId(), SuggestionContentInfo.fromMessage(it, 0)))
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
                    "Cancel suggestion creating",
                    listOf(Format("/cancel")),
                    "Cancel creating of complex suggestion"
                )
            )
        }
    }
}
