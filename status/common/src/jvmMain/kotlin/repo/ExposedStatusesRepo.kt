package dev.inmo.plagubot.suggestionsbot.status.common.repo

import dev.inmo.micro_utils.repos.exposed.keyvalue.AbstractExposedKeyValueRepo
import dev.inmo.plagubot.suggestionsbot.common.MessageInfo
import dev.inmo.plagubot.suggestionsbot.suggestons.models.SuggestionId
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ISqlExpressionBuilder
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedStatusesRepo(
    database: Database,
    tableName: String
) : StatusesRepo,
    AbstractExposedKeyValueRepo<SuggestionId, MessageInfo>(
        database,
        tableName
) {
    override val keyColumn: Column<String> = text("suggestion_id")

    private val chatIdColumn = long("chat_id")
    private val threadIdColumn = long("thread_id").nullable()
    private val messageIdColumn = long("chat_id")

    private fun Column<Long?>.eqOrNull(long: Long?) = long ?.let { eq(long) } ?: isNull()

    override val selectById: ISqlExpressionBuilder.(SuggestionId) -> Op<Boolean> = { keyColumn.eq(it.string) }
    override val selectByValue: ISqlExpressionBuilder.(MessageInfo) -> Op<Boolean> = {
        chatIdColumn.eq(it.chatId.chatId).and(
            threadIdColumn.eqOrNull(it.chatId.threadId)
        ).and(
            messageIdColumn.eq(it.messageId)
        )
    }
    override val ResultRow.asKey: SuggestionId
        get() = SuggestionId(get(keyColumn))
    override val ResultRow.asObject: MessageInfo
        get() = MessageInfo(
            chatId = IdChatIdentifier(get(chatIdColumn), get(threadIdColumn)),
            messageId = get(messageIdColumn)
        )

    override fun update(k: SuggestionId, v: MessageInfo, it: UpdateBuilder<Int>) {
        it[chatIdColumn] = v.chatId.chatId
        it[threadIdColumn] = v.chatId.threadId
        it[messageIdColumn] = v.messageId
    }

    override fun insertKey(k: SuggestionId, v: MessageInfo, it: InsertStatement<Number>) {
        it[keyColumn] = k.string
    }

    override suspend fun getSuggestionIdWithMessage(chatId: IdChatIdentifier, messageId: MessageId): SuggestionId? {
        return transaction(database) {
            select {
                chatIdColumn.eq(chatId.chatId).and(
                    threadIdColumn.eqOrNull(chatId.threadId)
                ).and(
                    this@ExposedStatusesRepo.messageIdColumn.eq(messageId)
                )
            }.limit(1).firstOrNull() ?.get(keyColumn) ?.let(::SuggestionId)
        }
    }
}
