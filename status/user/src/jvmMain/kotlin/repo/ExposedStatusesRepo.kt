package dev.inmo.plagubot.suggestionsbot.status.user.repo

import dev.inmo.micro_utils.repos.exposed.keyvalue.AbstractExposedKeyValueRepo
import dev.inmo.plagubot.suggestionsbot.common.MessageInfo
import dev.inmo.plagubot.suggestionsbot.status.user.models.StatusMessagesInfo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionId
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
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedStatusesRepo(
    database: Database
) : StatusesRepo,
    AbstractExposedKeyValueRepo<SuggestionId, StatusMessagesInfo>(
        database,
        "status_messages"
) {
    override val keyColumn: Column<String> = text("suggestion_id")

    private val adminChatId = long("admin_chat_id")
    private val adminThreadId = long("admin_thread_id").nullable()
    private val adminMessageId = long("admin_chat_id")

    private val userChatId = long("user_chat_id")
    private val userThreadId = long("user_thread_id").nullable()
    private val userMessageId = long("user_chat_id")

    private fun Column<Long?>.eqOrNull(long: Long?) = long ?.let { eq(long) } ?: isNull()

    override val selectById: ISqlExpressionBuilder.(SuggestionId) -> Op<Boolean> = { keyColumn.eq(it.string) }
    override val selectByValue: ISqlExpressionBuilder.(StatusMessagesInfo) -> Op<Boolean> = {
        adminChatId.eq(it.admin.chatId.chatId).and(
            adminThreadId.eqOrNull(it.admin.chatId.threadId)
        ).and(
            adminMessageId.eq(it.admin.messageId)
        ).and(
            userChatId.eq(it.user.chatId.chatId).and(
                userThreadId.eqOrNull(it.user.chatId.threadId)
            ).and(
                userMessageId.eq(it.user.messageId)
            )
        )
    }
    override val ResultRow.asKey: SuggestionId
        get() = SuggestionId(get(keyColumn))
    override val ResultRow.asObject: StatusMessagesInfo
        get() = StatusMessagesInfo(
            admin = MessageInfo(
                chatId = IdChatIdentifier(get(adminChatId), get(adminThreadId)),
                messageId = get(adminMessageId)
            ),
            user = MessageInfo(
                chatId = IdChatIdentifier(get(userChatId), get(userThreadId)),
                messageId = get(userMessageId)
            )
        )

    override fun update(k: SuggestionId, v: StatusMessagesInfo, it: UpdateBuilder<Int>) {
        it[adminChatId] = v.admin.chatId.chatId
        it[adminThreadId] = v.admin.chatId.threadId
        it[adminMessageId] = v.admin.messageId

        it[userChatId] = v.user.chatId.chatId
        it[userThreadId] = v.user.chatId.threadId
        it[userMessageId] = v.user.messageId
    }

    override fun insertKey(k: SuggestionId, v: StatusMessagesInfo, it: InsertStatement<Number>) {
        it[keyColumn] = k.string
    }

    override suspend fun getSuggestionIdWithMessage(chatId: IdChatIdentifier, messageId: MessageId): SuggestionId? {
        return transaction(database) {
            select {
                adminChatId.eq(chatId.chatId).and(
                    adminThreadId.eqOrNull(chatId.threadId)
                ).and(
                    adminMessageId.eq(messageId)
                ).or(
                    userChatId.eq(chatId.chatId).and(
                        userThreadId.eqOrNull(chatId.threadId)
                    ).and(
                        userMessageId.eq(messageId)
                    )
                )
            }.limit(1).firstOrNull() ?.get(keyColumn) ?.let(::SuggestionId)
        }
    }
}
