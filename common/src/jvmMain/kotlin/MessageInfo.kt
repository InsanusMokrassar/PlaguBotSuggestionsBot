package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import kotlinx.serialization.Serializable

@Serializable
data class MessageInfo(
    val chatId: IdChatIdentifier,
    val messageId: MessageId
)
