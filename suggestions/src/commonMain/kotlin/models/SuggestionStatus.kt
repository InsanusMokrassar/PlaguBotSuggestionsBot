package dev.inmo.plagubot.suggestionsbot.suggestons.models

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
    @SerialName("C")
    data class OnReview(
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Cancelable

    @Serializable
    sealed interface Reviewed : SuggestionStatus {
        val reviewerId: UserId
    }

    @Serializable
    @SerialName("C")
    data class Accepted(
        override val reviewerId: UserId,
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Reviewed

    @Serializable
    @SerialName("C")
    data class Rejected(
        override val reviewerId: UserId,
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Reviewed

    @Serializable
    @SerialName("C")
    data class Banned(
        override val reviewerId: UserId,
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Reviewed
}
