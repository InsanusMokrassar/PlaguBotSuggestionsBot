package dev.inmo.plagubot.suggestionsbot.suggestons.models

import dev.inmo.tgbotapi.extensions.utils.possiblyMediaGroupMessageOrNull
import dev.inmo.tgbotapi.types.FullChatIdentifierSerializer
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageIdentifier
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.content.MediaGroupContent
import kotlinx.serialization.Serializable

@Serializable
data class SuggestionContentInfo(
    @Serializable(FullChatIdentifierSerializer::class)
    val chatId: IdChatIdentifier,
    val messageId: MessageIdentifier,
    val group: String?,
    val order: Int
) {
    companion object {
        private fun fromMessage(message: ContentMessage<*>, order: Int) = SuggestionContentInfo(
            message.chat.id,
            message.messageId,
            message.possiblyMediaGroupMessageOrNull() ?.mediaGroupId,
            order
        )
        fun fromMessage(message: ContentMessage<*>): List<SuggestionContentInfo> {
            val content = message.content

            return if (content is MediaGroupContent<*>) {
                content.group.mapIndexed { i, it ->
                    fromMessage(it.sourceMessage, i)
                }
            } else {
                listOf(fromMessage(message, 0))
            }
        }
    }
}
