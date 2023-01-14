package dev.inmo.plagubot.suggestionsbot.reviews.models

import dev.inmo.plagubot.suggestionsbot.suggestons.models.SuggestionContentInfo
import dev.inmo.tgbotapi.types.FullChatIdentifierSerializer
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import kotlinx.serialization.Serializable

@Serializable
data class ReviewContentInfo(
    @Serializable(FullChatIdentifierSerializer::class)
    val chatId: IdChatIdentifier,
    val messageId: MessageId,
    @Serializable(FullChatIdentifierSerializer::class)
    val suggestionChatId: IdChatIdentifier,
    val suggestionMessageId: MessageId,
)
