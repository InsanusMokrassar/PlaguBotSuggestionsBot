package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.tgbotapi.types.ChatIdentifier
import dev.inmo.tgbotapi.types.FullChatIdentifierSerializer
import dev.inmo.tgbotapi.types.IdChatIdentifier
import kotlinx.serialization.Serializable

@Serializable
data class ChatsConfig(
    @Serializable(FullChatIdentifierSerializer::class)
    val suggestionsChat: IdChatIdentifier,
    @Serializable(FullChatIdentifierSerializer::class)
    val targetChat: IdChatIdentifier,
    @Serializable(FullChatIdentifierSerializer::class)
    val cacheChat: IdChatIdentifier
) {
    fun checkIsOfWorkChat(chatId: ChatIdentifier): Boolean {
        return chatId == suggestionsChat || chatId == targetChat || chatId == cacheChat
    }
}
