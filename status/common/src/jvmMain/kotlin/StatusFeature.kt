package dev.inmo.plagubot.suggestionsbot.status.common

import dev.inmo.plagubot.suggestionsbot.common.MessageInfo
import dev.inmo.plagubot.suggestionsbot.suggestons.models.SuggestionId
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.InlineKeyboardButton

interface StatusFeature {
    interface ButtonsBuilder {
        suspend fun BehaviourContext.createButtons(suggestionId: SuggestionId): List<InlineKeyboardButton>
    }

    suspend fun BehaviourContext.refreshStatus(suggestionId: SuggestionId)
    suspend fun resolveSuggestionId(messageInfo: MessageInfo): SuggestionId?
    suspend fun resolveSuggestionId(chatId: IdChatIdentifier, messageId: MessageId): SuggestionId? {
        return resolveSuggestionId(
            MessageInfo(chatId, messageId)
        )
    }

    companion object {
        const val REFRESH_KEY_ADMINS = "inline_buttons_suggestion_status_update_admin"
        const val REFRESH_KEY_USERS = "inline_buttons_suggestion_status_update_user"
    }
}
