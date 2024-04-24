package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.tgbotapi.extensions.utils.possiblyMediaGroupMessageOrNull
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MediaGroupId
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.message.abstracts.Message
import kotlinx.serialization.Serializable

@Serializable
data class MessageInfo(
    val chatId: IdChatIdentifier,
    val messageId: MessageId,
    val group: MediaGroupId? = null
)

operator fun MessageInfo.Companion.invoke(
    message: Message
) = MessageInfo(message.chat.id, message.messageId, message.possiblyMediaGroupMessageOrNull() ?.mediaGroupId)
