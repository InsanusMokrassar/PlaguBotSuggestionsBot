package dev.inmo.plagubot.suggestionsbot.reviews.repo

import dev.inmo.micro_utils.repos.exposed.eqOrIsNull
import dev.inmo.micro_utils.repos.exposed.onetomany.AbstractExposedKeyValuesRepo
import dev.inmo.plagubot.suggestionsbot.reviews.models.ReviewContentInfo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionId
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.MessageThreadId
import dev.inmo.tgbotapi.types.RawChatId
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database

class ExposedReviewMessagesInfo(
    database: Database
) : ReviewMessagesInfo,
    AbstractExposedKeyValuesRepo<SuggestionId, ReviewContentInfo>(
        database,
        "reviews_messages_info"
    ) {
    override val keyColumn: Column<String> = text("suggestion_id")
    val chatIdColumn = long("chat_id")
    val threadIdColumn = long("thread_id").nullable().default(null)
    val messageIdColumn = long("message_id")
    val suggestionChatIdColumn = long("suggestion_chat_id")
    val suggestionThreadIdColumn = long("suggestion_thread_id").nullable().default(null)
    val suggestionMessageIdColumn = long("suggestion_message_id")


    override val selectById: (SuggestionId) -> Op<Boolean> = { keyColumn.eq(it.string) }
    override val selectByValue: (ReviewContentInfo) -> Op<Boolean> = {
        chatIdColumn.eq(it.chatId.chatId.long).and(
            threadIdColumn.eqOrIsNull(it.chatId.threadId ?.long)
        ).and(
            messageIdColumn.eq(it.messageId.long)
        ).and(
            suggestionChatIdColumn.eq(it.suggestionChatId.chatId.long)
        ).and(
            suggestionThreadIdColumn.eqOrIsNull(it.suggestionChatId.threadId ?.long)
        ).and(
            suggestionMessageIdColumn.eq(it.suggestionMessageId.long)
        )
    }
    override val ResultRow.asKey: SuggestionId
        get() = SuggestionId(get(keyColumn))
    override val ResultRow.asObject: ReviewContentInfo
        get() = ReviewContentInfo(
            IdChatIdentifier(RawChatId(get(chatIdColumn)), get(threadIdColumn) ?.let(::MessageThreadId)),
            MessageId(get(messageIdColumn)),
            IdChatIdentifier(RawChatId(get(suggestionChatIdColumn)), get(suggestionThreadIdColumn) ?.let(::MessageThreadId)),
            MessageId(get(suggestionMessageIdColumn))
        )

    override fun insert(k: SuggestionId, v: ReviewContentInfo, it: UpdateBuilder<Int>) {
        it[keyColumn] = k.string
        it[chatIdColumn] = v.chatId.chatId.long
        it[threadIdColumn] = v.chatId.threadId ?.long
        it[messageIdColumn] = v.messageId.long
        it[suggestionChatIdColumn] = v.suggestionChatId.chatId.long
        it[suggestionThreadIdColumn] = v.suggestionChatId.threadId ?.long
        it[suggestionMessageIdColumn] = v.suggestionMessageId.long
    }
}
