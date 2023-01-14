package dev.inmo.plagubot.suggestionsbot.suggestons.sending

import dev.inmo.kslog.common.logger
import dev.inmo.kslog.common.w
import dev.inmo.plagubot.suggestionsbot.common.MessageInfo
import dev.inmo.plagubot.suggestionsbot.suggestons.repo.SuggestionsRepo
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.forwardMessage
import dev.inmo.tgbotapi.extensions.api.send.copyMessage
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.utils.*
import dev.inmo.tgbotapi.types.*
import dev.inmo.tgbotapi.types.message.content.MediaGroupPartContent

class MessagesPublisher(
    private val bot: TelegramBot,
    private val suggestionsRepo: SuggestionsRepo,
    private val cachingChatId: IdChatIdentifier
) {
    suspend fun publish(
        messagesInfo: List<MessageInfo>,
        targetChatId: IdChatIdentifier
    ): Pair<MessageInfo, MessageInfo> {
        val orders = messagesInfo.mapIndexed { i, messageInfo -> messageInfo to i }.toMap()
        val sortedMessagesContents = messagesInfo.groupBy { it.group }.flatMap { (group, list) ->
            if (group == null) {
                list.map {
                    orders.getValue(it) to listOf(it)
                }
            } else {
                listOf(orders.getValue(list.first()) to list)
            }
        }.sortedBy { it.first }

        sortedMessagesContents.flatMap { (_, contents) ->
            val result = mutableListOf<Pair<MessageInfo, MessageInfo>>()

            when {
                contents.size == 1 -> {
                    val messageInfo = contents.first()
                    runCatching {
                        MessageInfo(
                            targetChatId,
                            bot.copyMessage(targetChatId, messageInfo.chatId, messageInfo.messageId)
                        )
                    }.onFailure { _ ->
                        runCatching {
                            bot.forwardMessage(
                                messageInfo.chatId,
                                targetChatId,
                                messageInfo.messageId
                            )
                        }.onSuccess {
                            MessageInfo(
                                targetChatId,
                                bot.copyMessage(targetChatId, it)
                            )
                        }
                    }.getOrNull() ?.let {
                        messageInfo to it
                    }
                }
                else -> {
                    val resultContents = contents.mapNotNull {
                        it to (bot.forwardMessage(toChatId = cachingChatId, fromChatId = it.chatId, messageId = it.messageId).contentMessageOrNull() ?: return@mapNotNull null)
                    }.mapNotNull { (src, forwardedMessage) ->
                        src to (forwardedMessage.withContentOrNull<MediaGroupPartContent>() ?: null.also { _ ->
                            result.add(
                                src to MessageInfo(
                                    targetChatId,
                                    bot.copyMessage(targetChatId, forwardedMessage)
                                )
                            )
                        } ?: return@mapNotNull null)
                    }

                    resultContents.singleOrNull() ?.also { (src, it) ->
                        result.add(
                            src to MessageInfo(
                                targetChatId,
                                bot.copyMessage(targetChatId, it)
                            )
                        )
                    } ?: resultContents.chunked(mediaCountInMediaGroup.last).forEach {
                        bot.send(
                            targetChatId,
                            it.map { it.second.content.toMediaGroupMemberTelegramMedia() }
                        ).content.group
                    }
                }
            }



            result.toList()
        }

    }
}
