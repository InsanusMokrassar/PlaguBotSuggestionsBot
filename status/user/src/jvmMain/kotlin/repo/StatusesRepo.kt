package dev.inmo.plagubot.suggestionsbot.status.user.repo

import dev.inmo.micro_utils.repos.KeyValueRepo
import dev.inmo.plagubot.suggestionsbot.status.user.models.StatusMessagesInfo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionId
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId

internal interface StatusesRepo : KeyValueRepo<SuggestionId, StatusMessagesInfo> {
    suspend fun getSuggestionIdWithMessage(chatId: IdChatIdentifier, messageId: MessageId): SuggestionId?
}
