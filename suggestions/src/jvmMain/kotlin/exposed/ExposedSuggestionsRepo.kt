package dev.inmo.plagubot.suggestionsbot.suggestions.exposed

import com.benasher44.uuid.uuid4
import korlibs.time.DateTime
import dev.inmo.micro_utils.repos.UpdatedValuePair
import dev.inmo.micro_utils.repos.exposed.AbstractExposedCRUDRepo
import dev.inmo.micro_utils.repos.exposed.initTable
import dev.inmo.plagubot.suggestionsbot.suggestions.exposed.ExposedStatusesRepo.Companion.statusType
import dev.inmo.plagubot.suggestionsbot.suggestions.models.*
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.SuggestionsRepo
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageIdentifier
import dev.inmo.tgbotapi.types.UserId
import dev.inmo.tgbotapi.types.messageThreadIdField
import kotlinx.coroutines.flow.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedSuggestionsRepo(
    override val database: Database
) : SuggestionsRepo, AbstractExposedCRUDRepo<RegisteredSuggestion, SuggestionId, NewSuggestion>(
    tableName = "suggestions"
) {
    val idColumn = text("id")
    val userIdColumn = long("user_id")
    val isAnonymousColumn = bool("anonymous")

    private val contentRepo by lazy {
        ExposedContentInfoRepo(
            database,
            idColumn
        )
    }

    private val statusesRepo by lazy {
        ExposedStatusesRepo(
            database,
            idColumn
        )
    }

    override val primaryKey: PrimaryKey = PrimaryKey(idColumn)

    override val selectById: ISqlExpressionBuilder.(SuggestionId) -> Op<Boolean> = { idColumn.eq(it.string) }
    override val selectByIds: ISqlExpressionBuilder.(List<SuggestionId>) -> Op<Boolean> = { idColumn.inList(it.map { it.string }) }
    override val ResultRow.asId: SuggestionId
        get() = SuggestionId(get(idColumn))
    override val ResultRow.asObject: RegisteredSuggestion
        get() {
            val id = asId
            return RegisteredSuggestion(
                id,
                with(statusesRepo) {
                    select {
                        suggestionIdColumn.eq(id.string)
                    }.orderBy(
                        dateTimeColumn,
                        SortOrder.DESC
                    ).limit(1).firstOrNull() ?.asObject
                } ?: error("Unable to take any status for the suggestion $id"),
                UserId(get(userIdColumn)),
                get(isAnonymousColumn),
                with(contentRepo) {
                    select { suggestionIdColumn.eq(id.string) }.map {
                        it.asObject
                    }
                }
            )
        }

    private val _removedPostsFlow = MutableSharedFlow<RegisteredSuggestion>()
    override val removedPostsFlow: Flow<RegisteredSuggestion> = _removedPostsFlow.asSharedFlow()

    init {
        initTable()
    }

    override fun InsertStatement<Number>.asObject(value: NewSuggestion): RegisteredSuggestion {
        val id = SuggestionId(get(idColumn))

        return RegisteredSuggestion(
            id,
            with(statusesRepo) {
                select {
                    suggestionIdColumn.eq(id.string)
                }.orderBy(
                    dateTimeColumn,
                    SortOrder.DESC
                ).limit(1).firstOrNull() ?.asObject
            } ?: value.status,
            UserId(get(userIdColumn)),
            get(isAnonymousColumn),
            with(contentRepo) {
                select { suggestionIdColumn.eq(id.string) }.map {
                    it.asObject
                }
            }
        )
    }

    override fun createAndInsertId(value: NewSuggestion, it: InsertStatement<Number>): SuggestionId {
        val id = SuggestionId(uuid4().toString())
        it[idColumn] = id.string
        return id
    }

    override fun update(id: SuggestionId?, value: NewSuggestion, it: UpdateBuilder<Int>) {
        it[userIdColumn] = value.user.chatId
        it[isAnonymousColumn] = value.isAnonymous
    }

    private fun updateStatusWithoutTransaction(suggestionId: SuggestionId, status: SuggestionStatus) {
        with(statusesRepo) {
            val latestStatus = select {
                suggestionIdColumn.eq(suggestionId.string)
            }.orderBy(dateTimeColumn, SortOrder.DESC).limit(1).firstOrNull() ?.asObject
            if (latestStatus ?.statusType() != status.statusType()) {
                insert {
                    it[suggestionIdColumn] = suggestionId.string
                    it[dateTimeColumn] = status.dateTime.unixMillis
                    it[reviewerIdColumn] = (status as? SuggestionStatus.Reviewed) ?.reviewerId ?.chatId
                    it[statusTypeColumn] = status.statusType()
                }
            }
        }
    }

    private fun updateContentWithoutTransaction(post: RegisteredSuggestion) {
        with(contentRepo) {
            deleteWhere { suggestionIdColumn.eq(post.id.string) }
            post.content.forEach { contentInfo ->
                insert {
                    it[suggestionIdColumn] = post.id.string
                    it[chatIdColumn] = contentInfo.messageMetaInfo.chatId.chatId
                    it[threadIdColumn] = contentInfo.messageMetaInfo.chatId.threadId
                    it[messageIdColumn] = contentInfo.messageMetaInfo.messageId
                    it[groupColumn] = contentInfo.messageMetaInfo.group
                    it[orderColumn] = contentInfo.order
                }
            }
        }
    }

    override suspend fun onAfterCreate(values: List<Pair<NewSuggestion, RegisteredSuggestion>>): List<RegisteredSuggestion> {
        return values.map {
            val actual = it.second.copy(content = it.first.content)
            transaction(database) {
                updateStatusWithoutTransaction(actual.id, actual.status)
                updateContentWithoutTransaction(actual)
            }
            actual
        }
    }

    override suspend fun onAfterUpdate(value: List<UpdatedValuePair<NewSuggestion, RegisteredSuggestion>>): List<RegisteredSuggestion> {
        return value.map {
            val actual = it.second.copy(content = it.first.content)
            transaction(database) {
                updateStatusWithoutTransaction(actual.id, actual.status)
                updateContentWithoutTransaction(actual)
            }
            actual
        }
    }

    override suspend fun deleteById(ids: List<SuggestionId>) {
        onBeforeDelete(ids)
        val suggestions = ids.mapNotNull {
            getById(it)
        }.associateBy { it.id }
        val existsIds = suggestions.keys.toList()
        transaction(db = database) {
            val deleted = deleteWhere(null, null) {
                selectByIds(it, existsIds)
            }
            with(contentRepo) {
                deleteWhere {
                    suggestionIdColumn.inList(existsIds.map { it.string })
                }
            }
            if (deleted == existsIds.size) {
                existsIds
            } else {
                existsIds.filter {
                    select { selectById(it) }.limit(1).none()
                }
            }
        }.forEach {
            _deletedObjectsIdsFlow.emit(it)
            _removedPostsFlow.emit(suggestions[it] ?: return@forEach)
        }
    }

    override suspend fun updateStatus(
        suggestionId: SuggestionId,
        status: SuggestionStatus
    ): RegisteredSuggestion? = transaction(database) {
        updateStatusWithoutTransaction(suggestionId, status)
        select { selectById(suggestionId) }.limit(1).firstOrNull() ?.asObject
    } ?.also {
        _updatedObjectsFlow.emit(it)
    }

    override suspend fun getIdByChatAndMessage(chatId: IdChatIdentifier, messageId: MessageIdentifier): SuggestionId? {
        return transaction(database) {
            with(contentRepo) {
                select {
                    chatIdColumn.eq(chatId.chatId)
                        .and(chatId.threadId ?.let { threadIdColumn.eq(it) } ?: threadIdColumn.isNull())
                        .and(messageIdColumn.eq(messageId))
                }.limit(1).firstOrNull() ?.get(suggestionIdColumn)
            } ?.let(::SuggestionId)
        }
    }

    override suspend fun getFirstMessageInfo(suggestionId: SuggestionId): SuggestionContentInfo? = transaction(database) {
        with(contentRepo) {
            select { suggestionIdColumn.eq(suggestionId.string) }.limit(1).firstOrNull() ?.asObject
        }
    }

    override suspend fun getSuggestionStatusesHistory(
        suggestionId: SuggestionId
    ): List<SuggestionStatus> = transaction(database) {
        with (statusesRepo) {
            select { suggestionIdColumn.eq(suggestionId.string) }.map { it.asObject }
        }
    }.sortedBy {
        it.dateTime
    }

    override suspend fun isUserHaveBannedSuggestions(userid: UserId): Boolean = transaction(database) {
        select {
            userIdColumn.eq(userid.chatId).and(
                idColumn.inSubQuery(
                    with(statusesRepo) {
                        slice(suggestionIdColumn).select {
                            statusTypeColumn.eq(
                                SuggestionStatus.Banned::class.statusType()
                            )
                        }
                    }
                )
            )
        }.limit(1).count() > 0
    }
}
