package dev.inmo.plagubot.suggestionsbot.status

import dev.inmo.micro_utils.coroutines.runCatchingSafely
import dev.inmo.plagubot.suggestionsbot.common.MessageInfo
import dev.inmo.plagubot.suggestionsbot.status.user.repo.StatusesRepo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionId
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.ReadSuggestionsRepo
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.SuggestionsRepo
import dev.inmo.tgbotapi.extensions.api.edit.edit
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.utils.types.buttons.inlineKeyboard
import dev.inmo.tgbotapi.types.IdChatIdentifier

internal class StatusFeatureImpl(
    private val suggestionsRepo: ReadSuggestionsRepo,
    private val statusesRepo: StatusesRepo,
    private val keyboardBuilders: List<StatusFeature.ButtonsBuilder>,
    private val suggestionsChat: IdChatIdentifier
) : StatusFeature {
    private suspend fun BehaviourContext.buildKeyboardForAdmins(suggestionId: SuggestionId) = inlineKeyboard {
        keyboardBuilders.forEach {
            add(
                with (it) {
                    createButtonsForAdmin(suggestionId).ifEmpty { return@forEach }
                }
            )
        }
    }
    private suspend fun BehaviourContext.buildKeyboardForUsers(suggestionId: SuggestionId) = inlineKeyboard {
        keyboardBuilders.forEach {
            add(
                with (it) {
                    createButtonsForUser(suggestionId).ifEmpty { return@forEach }
                }
            )
        }
    }

    override suspend fun BehaviourContext.refreshStatus(suggestionId: SuggestionId) {
        val messagesInfo = statusesRepo.get(suggestionId)

        if (messagesInfo == null) {
            runCatchingSafely {
                send(
                    suggestionsChat,

                )
            }

            return
        }

        messagesInfo.admin.let {
            runCatchingSafely {
                edit(
                    it.chatId,
                    it.messageId,
                    buildKeyboardForAdmins(suggestionId).also {
                        if (it.keyboard.isEmpty()) {
                            return@runCatchingSafely
                        }
                    }
                )
            }
        }

        messagesInfo.user.let {
            runCatchingSafely {
                edit(
                    it.chatId,
                    it.messageId,
                    buildKeyboardForUsers(suggestionId).also {
                        if (it.keyboard.isEmpty()) {
                            return@runCatchingSafely
                        }
                    }
                )
            }
        }
    }

    override suspend fun resolveSuggestionId(messageInfo: MessageInfo): SuggestionId? {
        return statusesRepo.getSuggestionIdWithMessage(
            messageInfo.chatId,
            messageInfo.messageId
        )
    }
}
