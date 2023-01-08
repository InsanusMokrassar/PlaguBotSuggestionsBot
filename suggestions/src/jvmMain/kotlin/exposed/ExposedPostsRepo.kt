package dev.inmo.plagubot.suggestionsbot.suggestons.exposed

import com.benasher44.uuid.uuid4
import com.soywiz.klock.DateTime
import dev.inmo.micro_utils.repos.UpdatedValuePair
import dev.inmo.micro_utils.repos.exposed.AbstractExposedCRUDRepo
import dev.inmo.micro_utils.repos.exposed.initTable
import dev.inmo.plagubot.suggestionsbot.suggestons.models.*
import dev.inmo.plagubot.suggestionsbot.suggestons.repo.SuggestionsRepo
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageIdentifier
import kotlinx.coroutines.flow.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedPostsRepo(
    override val database: Database
) : SuggestionsRepo, AbstractExposedCRUDRepo<RegisteredSuggestion, SuggestionId, NewSuggestion>(
    tableName = "suggestions"
) {
    val idColumn = text("id")

    private val contentRepo by lazy {
        ExposedContentInfoRepo(
            database,
            idColumn
        )
    }

    private val statusesRepo by lazy {
        ExposedContentInfoRepo(
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
                DateTime(get(createdColumn)),
                with(contentRepo) {
                    select { postIdColumn.eq(id.string) }.map {
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
            DateTime(get(createdColumn)),
            with(contentRepo) {
                select { postIdColumn.eq(id.string) }.map {
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

    override fun update(id: SuggestionId?, value: NewSuggestion, it: UpdateBuilder<Int>) {}

    private fun updateContent(post: RegisteredSuggestion) {
        transaction(database) {
            with(contentRepo) {
                deleteWhere { postIdColumn.eq(post.id.string) }
                post.content.forEach { contentInfo ->
                    insert {
                        it[postIdColumn] = post.id.string
                        it[chatIdColumn] = contentInfo.chatId.chatId
                        it[threadIdColumn] = contentInfo.chatId.threadId
                        it[messageIdColumn] = contentInfo.messageId
                        it[groupColumn] = contentInfo.group
                        it[orderColumn] = contentInfo.order
                    }
                }
            }
        }
    }

    override fun insert(value: NewSuggestion, it: InsertStatement<Number>) {
        super.insert(value, it)
        it[createdColumn] = DateTime.now().unixMillis
    }

    override suspend fun onAfterCreate(values: List<Pair<NewSuggestion, RegisteredSuggestion>>): List<RegisteredSuggestion> {
        return values.map {
            val actual = it.second.copy(content = it.first.content)
            updateContent(actual)
            actual
        }
    }

    override suspend fun onAfterUpdate(value: List<UpdatedValuePair<NewSuggestion, RegisteredSuggestion>>): List<RegisteredSuggestion> {
        return value.map {
            val actual = it.second.copy(content = it.first.content)
            updateContent(actual)
            actual
        }
    }

    override suspend fun deleteById(ids: List<SuggestionId>) {
        onBeforeDelete(ids)
        val posts = ids.mapNotNull {
            getById(it)
        }.associateBy { it.id }
        val existsIds = posts.keys.toList()
        transaction(db = database) {
            val deleted = deleteWhere(null, null) {
                selectByIds(it, existsIds)
            }
            with(contentRepo) {
                deleteWhere {
                    postIdColumn.inList(existsIds.map { it.string })
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
            _removedPostsFlow.emit(posts[it] ?: return@forEach)
        }
    }

    override suspend fun getIdByChatAndMessage(chatId: IdChatIdentifier, messageId: MessageIdentifier): SuggestionId? {
        return transaction(database) {
            with(contentRepo) {
                select {
                    chatIdColumn.eq(chatId.chatId)
                        .and(chatId.threadId ?.let { threadIdColumn.eq(it) } ?: threadIdColumn.isNull())
                        .and(messageIdColumn.eq(messageId))
                }.limit(1).firstOrNull() ?.get(postIdColumn)
            } ?.let(::SuggestionId)
        }
    }

    override suspend fun getSuggestionCreationTime(suggestionId: SuggestionId): DateTime? = transaction(database) {
        select { selectById(suggestionId) }.limit(1).firstOrNull() ?.get(createdColumn) ?.let(::DateTime)
    }

    override suspend fun getFirstMessageInfo(suggestionId: SuggestionId): SuggestionContentInfo? = transaction(database) {
        with(contentRepo) {
            select { postIdColumn.eq(suggestionId.string) }.limit(1).firstOrNull() ?.asObject
        }
    }
}
