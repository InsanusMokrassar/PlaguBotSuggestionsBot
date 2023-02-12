package dev.inmo.plagubot.suggestionsbot.suggestions.models

import com.soywiz.klock.DateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import dev.inmo.plagubot.suggestionsbot.common.DateTimeSerializer
import dev.inmo.tgbotapi.types.UserId

@Serializable
sealed interface SuggestionStatus {
    val dateTime: DateTime
    @Serializable
    sealed interface Cancelable : SuggestionStatus
    @Serializable
    @SerialName("C")
    data class Created(
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Cancelable
    @Serializable
    @SerialName("OR")
    data class OnReview(
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Cancelable

    @Serializable
    sealed interface Done : SuggestionStatus
    @Serializable
    @SerialName("Ca")
    data class Cancelled(
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Done

    @Serializable
    sealed interface Reviewed : Done {
        val reviewerId: UserId
    }

    @Serializable
    @SerialName("A")
    data class Accepted(
        override val reviewerId: UserId,
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Reviewed

    @Serializable
    @SerialName("R")
    data class Rejected(
        override val reviewerId: UserId,
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Reviewed

    @Serializable
    @SerialName("B")
    data class Banned(
        override val reviewerId: UserId,
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Reviewed
}
