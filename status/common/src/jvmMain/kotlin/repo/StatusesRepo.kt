package dev.inmo.plagubot.suggestionsbot.status.common.repo

import dev.inmo.micro_utils.repos.KeyValueRepo
import dev.inmo.plagubot.suggestionsbot.common.MessageInfo
import dev.inmo.plagubot.suggestionsbot.status.common.models.StatusMessagesInfo
import dev.inmo.plagubot.suggestionsbot.suggestons.models.SuggestionId
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId

interface StatusesRepo : KeyValueRepo<SuggestionId, MessageInfo> {
    suspend fun getSuggestionIdWithMessage(chatId: IdChatIdentifier, messageId: MessageId): SuggestionId?
}
