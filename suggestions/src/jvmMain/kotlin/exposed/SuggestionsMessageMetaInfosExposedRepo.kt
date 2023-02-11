package dev.inmo.plagubot.suggestionsbot.suggestions.exposed

import dev.inmo.micro_utils.repos.exposed.eqOrIsNull
import dev.inmo.micro_utils.repos.exposed.initTable
import dev.inmo.micro_utils.repos.exposed.keyvalue.AbstractExposedKeyValueRepo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionId
import dev.inmo.tgbotapi.libraries.resender.MessageMetaInfo
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ISqlExpressionBuilder
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction

class SuggestionsMessageMetaInfosExposedRepo(
    database: Database,
    tableName: String
) : AbstractExposedKeyValueRepo<SuggestionId, MessageMetaInfo>(database, tableName) {
    override val keyColumn = text("suggestion_id")
    private val chatIdColumn = long("chat_id")
    private val threadIdColumn = long("thread_id").nullable().default(null)
    private val messageIdColumn = long("message_id")
    private val groupColumn = text("group").nullable()
    override val selectById: ISqlExpressionBuilder.(SuggestionId) -> Op<Boolean> = { keyColumn.eq(it.string) }
    override val selectByValue: ISqlExpressionBuilder.(MessageMetaInfo) -> Op<Boolean> = {
        chatIdColumn.eq(it.chatId.chatId).and(
            threadIdColumn.eqOrIsNull(it.chatId.threadId)
        ).and(
            messageIdColumn.eq(it.messageId)
        ).and(
            groupColumn.eqOrIsNull(it.group)
        )
    }
    override val ResultRow.asKey: SuggestionId
        get() = SuggestionId(get(keyColumn))
    override val ResultRow.asObject: MessageMetaInfo
        get() = MessageMetaInfo(
            IdChatIdentifier(get(chatIdColumn), get(threadIdColumn)),
            get(messageIdColumn),
            get(groupColumn)
        )

    init {
        initTable()
    }

    override fun update(k: SuggestionId, v: MessageMetaInfo, it: UpdateBuilder<Int>) {
        it[chatIdColumn] = v.chatId.chatId
        it[threadIdColumn] = v.chatId.threadId
        it[messageIdColumn] = v.messageId
        it[groupColumn] = v.group
    }

    override fun insertKey(k: SuggestionId, v: MessageMetaInfo, it: InsertStatement<Number>) {
        it[keyColumn] = k.string
    }

    suspend fun getSuggestionId(
        chatId: IdChatIdentifier,
        messageId: MessageId
    ) = transaction(database) {
        select {
            chatIdColumn.eq(chatId.chatId).and(
                threadIdColumn.eqOrIsNull(chatId.threadId)
            ).and(
                messageIdColumn.eq(messageId)
            )
        }.limit(1).firstOrNull() ?.get(keyColumn) ?.let(::SuggestionId)
    }
}
