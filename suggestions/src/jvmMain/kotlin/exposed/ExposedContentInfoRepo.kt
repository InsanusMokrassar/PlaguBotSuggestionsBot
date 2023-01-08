package dev.inmo.plagubot.suggestionsbot.suggestons.exposed

import dev.inmo.micro_utils.repos.exposed.*
import dev.inmo.plagubot.suggestionsbot.suggestons.models.*
import dev.inmo.tgbotapi.types.IdChatIdentifier
import org.jetbrains.exposed.sql.*

internal class ExposedContentInfoRepo(
    override val database: Database,
    suggestionIdColumnReference: Column<String>
) : ExposedRepo, Table(name = "suggestions_content") {
    val suggestionIdColumn = text("suggestion_id").references(suggestionIdColumnReference, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    val chatIdColumn = long("chat_id")
    val threadIdColumn = long("thread_id").nullable().default(null)
    val messageIdColumn = long("message_id")
    val groupColumn = text("group").nullable()
    val orderColumn = integer("order")

    val ResultRow.asObject
        get() = SuggestionContentInfo(
            IdChatIdentifier(get(chatIdColumn), get(threadIdColumn)),
            get(messageIdColumn),
            get(groupColumn),
            get(orderColumn)
        )

    init {
        initTable()
    }
}
