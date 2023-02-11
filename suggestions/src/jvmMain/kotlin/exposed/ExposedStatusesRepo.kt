package dev.inmo.plagubot.suggestionsbot.suggestions.exposed

import com.soywiz.klock.DateTime
import dev.inmo.micro_utils.repos.exposed.*
import dev.inmo.plagubot.suggestionsbot.suggestions.exposed.ExposedStatusesRepo.Companion.statusType
import dev.inmo.plagubot.suggestionsbot.suggestions.models.*
import dev.inmo.tgbotapi.types.UserId
import org.jetbrains.exposed.sql.*
import kotlin.reflect.KClass

internal class ExposedStatusesRepo(
    override val database: Database,
    suggestionIdColumnReference: Column<String>
) : ExposedRepo, Table(name = "suggestions_statuses") {
    val suggestionIdColumn = text("suggestion_id").references(suggestionIdColumnReference, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    val statusTypeColumn = byte("type")
    val reviewerIdColumn = long("reviewer_id").nullable().default(null)
    val dateTimeColumn = double("date_time")

    val ResultRow.asObject
        get() = get(statusTypeColumn).status(
            DateTime(get(dateTimeColumn)),
            get(reviewerIdColumn) ?.let(::UserId)
        )

    init {
        initTable()
    }

    companion object {
        fun KClass<out SuggestionStatus>.statusType(): Byte = when (this) {
            SuggestionStatus.Created::class -> 0
            SuggestionStatus.OnReview::class -> 1
            SuggestionStatus.Accepted::class -> 2
            SuggestionStatus.Banned::class -> 3
            SuggestionStatus.Rejected::class -> 4
            else -> error("Unexpected type of suggestion status")
        }.toByte()
        fun SuggestionStatus.statusType(): Byte = (this::class).statusType()

        fun Byte.status(
            dateTime: DateTime,
            reviewerId: UserId?
        ) = when (this.toInt()) {
            0 -> SuggestionStatus.Created(dateTime)
            1 -> SuggestionStatus.OnReview(dateTime)
            2 -> SuggestionStatus.Accepted(reviewerId ?: error("Reviewer parameter is required for Accepted status"), dateTime)
            3 -> SuggestionStatus.Banned(reviewerId ?: error("Reviewer parameter is required for Banned status"), dateTime)
            4 -> SuggestionStatus.Rejected(reviewerId ?: error("Reviewer parameter is required for Rejected status"), dateTime)
            else -> SuggestionStatus.Rejected(reviewerId ?: error("Reviewer parameter is required for Rejected status"), dateTime)
        }
    }
}
