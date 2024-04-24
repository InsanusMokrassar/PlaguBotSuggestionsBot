package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.micro_utils.language_codes.IetfLang
import dev.inmo.tgbotapi.types.ChatIdentifier
import dev.inmo.tgbotapi.types.FullChatIdentifierSerializer
import dev.inmo.tgbotapi.types.IdChatIdentifier
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class ChatsConfig(
    @Serializable(FullChatIdentifierSerializer::class)
    val suggestionsChat: IdChatIdentifier,
    @Serializable(FullChatIdentifierSerializer::class)
    val targetChat: IdChatIdentifier,
    @Serializable(FullChatIdentifierSerializer::class)
    val cacheChat: IdChatIdentifier,
    val language: IetfLang = IetfLang.English
) {
    val locale: Locale
        get() = language.locale
    fun checkIsOfWorkChat(chatId: ChatIdentifier): Boolean {
        return chatId == suggestionsChat || chatId == targetChat || chatId == cacheChat
    }
}
