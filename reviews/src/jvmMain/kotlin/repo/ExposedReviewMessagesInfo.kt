package dev.inmo.plagubot.suggestionsbot.reviews.repo

import dev.inmo.micro_utils.repos.exposed.eqOrIsNull
import dev.inmo.micro_utils.repos.exposed.onetomany.AbstractExposedKeyValuesRepo
import dev.inmo.plagubot.suggestionsbot.reviews.models.ReviewContentInfo
import dev.inmo.plagubot.suggestionsbot.suggestons.models.SuggestionId
import dev.inmo.tgbotapi.types.IdChatIdentifier
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ISqlExpressionBuilder
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.statements.InsertStatement

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


    override val selectById: ISqlExpressionBuilder.(SuggestionId) -> Op<Boolean> = { keyColumn.eq(it.string) }
    override val selectByValue: ISqlExpressionBuilder.(ReviewContentInfo) -> Op<Boolean> = {
        chatIdColumn.eq(it.chatId.chatId).and(
            threadIdColumn.eqOrIsNull(it.chatId.threadId)
        ).and(
            messageIdColumn.eq(it.messageId)
        ).and(
            suggestionChatIdColumn.eq(it.suggestionChatId.chatId)
        ).and(
            suggestionThreadIdColumn.eqOrIsNull(it.suggestionChatId.threadId)
        ).and(
            suggestionMessageIdColumn.eq(it.suggestionMessageId)
        )
    }
    override val ResultRow.asKey: SuggestionId
        get() = SuggestionId(get(keyColumn))
    override val ResultRow.asObject: ReviewContentInfo
        get() = ReviewContentInfo(
            IdChatIdentifier(get(chatIdColumn), get(threadIdColumn)),
            get(messageIdColumn),
            IdChatIdentifier(get(suggestionChatIdColumn), get(suggestionThreadIdColumn)),
            get(suggestionMessageIdColumn)
        )

    override fun insert(k: SuggestionId, v: ReviewContentInfo, it: InsertStatement<Number>) {
        it[keyColumn] = k.string
        it[chatIdColumn] = v.chatId.chatId
        it[threadIdColumn] = v.chatId.threadId
        it[messageIdColumn] = v.messageId
        it[suggestionChatIdColumn] = v.suggestionChatId.chatId
        it[suggestionThreadIdColumn] = v.suggestionChatId.threadId
        it[suggestionMessageIdColumn] = v.suggestionMessageId
    }
}
