package dev.inmo.plagubot.suggestionsbot.reviews

import com.soywiz.klock.DateTime
import dev.inmo.micro_utils.coroutines.runCatchingSafely
import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.koin.singleWithBinds
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.suggestionsbot.common.ChatsConfig
import dev.inmo.plagubot.suggestionsbot.reviews.repo.ExposedReviewMessagesInfo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.RegisteredSuggestion
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionId
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionStatus
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.SuggestionsRepo
import dev.inmo.tgbotapi.extensions.api.chat.get.getChat
import dev.inmo.tgbotapi.extensions.api.delete
import dev.inmo.tgbotapi.extensions.api.edit.edit
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.oneOf
import dev.inmo.tgbotapi.extensions.behaviour_builder.strictlyOn
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.flatInlineKeyboard
import dev.inmo.tgbotapi.extensions.utils.userOrNull
import dev.inmo.tgbotapi.libraries.resender.MessageMetaInfo
import dev.inmo.tgbotapi.libraries.resender.MessagesResender
import dev.inmo.tgbotapi.libraries.resender.invoke
import dev.inmo.tgbotapi.utils.buildEntities
import dev.inmo.tgbotapi.types.message.textsources.mention
import dev.inmo.tgbotapi.utils.bold
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module

object Plugin : Plugin {
    override fun Module.setupDI(database: Database, params: JsonObject) {
        singleWithBinds {
            ExposedReviewMessagesInfo(get())
        }
    }

    @Serializable
    private sealed interface ReviewState : State {
        override val context: SuggestionId
        @Serializable
        data class Initialization(
            override val context: SuggestionId
        ) : ReviewState

        @Serializable
        data class WaitReview(
            override val context: SuggestionId,
            val messages: List<MessageMetaInfo>,
            val manageMessage: MessageMetaInfo
        ) : ReviewState
    }

    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val suggestionsRepo = koin.get<SuggestionsRepo>()
        val publisher = koin.get<MessagesResender>()
        val chatsConfig = koin.get<ChatsConfig>()

        val acceptButtonData = "accept"
        val rejectButtonData = "reject"
        val banButtonData = "ban"

        suspend fun BehaviourContext.updateSuggestionPanel(
            suggestion: RegisteredSuggestion,
            firstMetaInfo: MessageMetaInfo,
            managementMessage: MessageMetaInfo?
        ): MessageMetaInfo {
            val user = getChat(suggestion.user).userOrNull()
            val statusString = when (suggestion.status) {
                is SuggestionStatus.Created -> "Created"
                is SuggestionStatus.OnReview -> "In review"
                is SuggestionStatus.Accepted -> "Accepted"
                is SuggestionStatus.Banned -> "User banned"
                is SuggestionStatus.Rejected -> "Rejected"
            }

            val entities = buildEntities {
                +"User: " + (user ?.let { mention(it) } ?: mention("link", suggestion.user)) + "\n"
                +"Anonymous: " + (if (suggestion.isAnonymous) "✅" else "❌") + "\n"
                +"Status: " + bold(statusString)
            }

            val replyMarkup = when (suggestion.status) {
                is SuggestionStatus.Created,
                is SuggestionStatus.OnReview -> flatInlineKeyboard {
                    dataButton("Accept", acceptButtonData)
                    dataButton("Ban", banButtonData)
                    dataButton("Reject", rejectButtonData)
                }
                is SuggestionStatus.Accepted,
                is SuggestionStatus.Banned,
                is SuggestionStatus.Rejected -> null
            }

            return managementMessage ?.also {
                edit(it.chatId, it.messageId, entities = entities, replyMarkup = replyMarkup)
            } ?: reply(
                firstMetaInfo.chatId,
                firstMetaInfo.messageId,
                entities = entities,
                replyMarkup = replyMarkup
            ).let {
                MessageMetaInfo(it)
            }
        }

        strictlyOn { state: ReviewState.Initialization ->
            val suggestion = suggestionsRepo.getById(state.context) ?: return@strictlyOn null
            val sent = publisher.resend(
                chatsConfig.suggestionsChat,
                suggestion.content.map { it.messageMetaInfo }
            )

            ReviewState.WaitReview(
                state.context,
                sent.map { it.second },
                updateSuggestionPanel(
                    suggestion = suggestion,
                    firstMetaInfo = sent.first().second,
                    managementMessage = null
                )
            )
        }

        strictlyOn { state: ReviewState.WaitReview ->
            val suggestion = suggestionsRepo.getById(state.context) ?: return@strictlyOn null

            val callbackQuery = oneOf(
                async {
                    waitMessageDataCallbackQuery().filter {
                        it.data == acceptButtonData
                            && it.message.messageId == state.manageMessage.messageId
                            && it.message.chat.id == state.manageMessage.chatId
                    }.first()
                },
                async {
                    waitMessageDataCallbackQuery().filter {
                        it.data == banButtonData
                            && it.message.messageId == state.manageMessage.messageId
                            && it.message.chat.id == state.manageMessage.chatId
                    }.first()
                },
                async {
                    waitMessageDataCallbackQuery().filter {
                        it.data == rejectButtonData
                            && it.message.messageId == state.manageMessage.messageId
                            && it.message.chat.id == state.manageMessage.chatId
                    }.first()
                },
                async {
                    merge(
                        suggestionsRepo.updatedObjectsFlow.filter { it.id == state.context },
                        suggestionsRepo.deletedObjectsIdsFlow.filter { it == state.context }
                    ).first()
                    null
                }
            )

            when (callbackQuery ?.data) {
                acceptButtonData -> {
                    suggestionsRepo.updateStatus(
                        suggestion.id,
                        SuggestionStatus.Accepted(
                            callbackQuery.user.id,
                            DateTime.now()
                        )
                    )
                    null
                }
                banButtonData -> {
                    suggestionsRepo.updateStatus(
                        suggestion.id,
                        SuggestionStatus.Banned(
                            callbackQuery.user.id,
                            DateTime.now()
                        )
                    )
                    null
                }
                rejectButtonData -> {
                    suggestionsRepo.updateStatus(
                        suggestion.id,
                        SuggestionStatus.Rejected(
                            callbackQuery.user.id,
                            DateTime.now()
                        )
                    )
                    null
                }
                else -> {
                    val existsSuggestion = suggestionsRepo.getById(state.context)
                    if (existsSuggestion == null) {
                        edit(
                            state.manageMessage.chatId,
                            state.manageMessage.messageId,
                            "Suggestion has been removed"
                        )
                        null
                    } else {
                        state
                    }
                }
            }.also {
                runCatchingSafely {
                    suggestionsRepo.getById(state.context) ?.also {
                        updateSuggestionPanel(it, state.messages.first(), state.manageMessage)
                    } ?: also {
                        edit(
                            state.manageMessage.chatId,
                            state.manageMessage.messageId,
                            "Suggestion has been removed"
                        )
                    }
                }
            }
        }

        suggestionsRepo.newObjectsFlow.subscribeSafelyWithoutExceptions(this) {
            startChain(ReviewState.Initialization(it.id))
        }
    }
}
