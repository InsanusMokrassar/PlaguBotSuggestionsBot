package dev.inmo.plagubot.suggestionsbot.suggestions.exposed

import dev.inmo.micro_utils.repos.exposed.eqOrIsNull
import dev.inmo.micro_utils.repos.exposed.initTable
import dev.inmo.micro_utils.repos.exposed.keyvalue.AbstractExposedKeyValueRepo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionId
import dev.inmo.tgbotapi.libraries.resender.MessageMetaInfo
import dev.inmo.tgbotapi.types.*
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
        chatIdColumn.eq(it.chatId.chatId.long).and(
            threadIdColumn.eqOrIsNull(it.chatId.threadId ?.long)
        ).and(
            messageIdColumn.eq(it.messageId.long)
        ).and(
            groupColumn.eqOrIsNull(it.group ?.string)
        )
    }
    override val ResultRow.asKey: SuggestionId
        get() = SuggestionId(get(keyColumn))
    override val ResultRow.asObject: MessageMetaInfo
        get() = MessageMetaInfo(
            IdChatIdentifier(RawChatId(get(chatIdColumn)), get(threadIdColumn) ?.let(::MessageThreadId)),
            MessageId(get(messageIdColumn)),
            get(groupColumn) ?.let(::MediaGroupId)
        )

    init {
        initTable()
    }

    override fun update(k: SuggestionId, v: MessageMetaInfo, it: UpdateBuilder<Int>) {
        it[chatIdColumn] = v.chatId.chatId.long
        it[threadIdColumn] = v.chatId.threadId ?.long
        it[messageIdColumn] = v.messageId.long
        it[groupColumn] = v.group ?.string
    }

    override fun insertKey(k: SuggestionId, v: MessageMetaInfo, it: InsertStatement<Number>) {
        it[keyColumn] = k.string
    }

    suspend fun getSuggestionId(
        chatId: IdChatIdentifier,
        messageId: MessageId
    ) = transaction(database) {
        selectAll().where {
            chatIdColumn.eq(chatId.chatId.long).and(
                threadIdColumn.eqOrIsNull(chatId.threadId ?.long)
            ).and(
                messageIdColumn.eq(messageId.long)
            )
        }.limit(1).firstOrNull() ?.get(keyColumn) ?.let(::SuggestionId)
    }
}
