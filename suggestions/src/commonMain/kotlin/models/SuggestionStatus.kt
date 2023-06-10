package dev.inmo.plagubot.suggestionsbot.suggestions.models

import korlibs.time.DateTime
import dev.icerock.moko.resources.StringResource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import dev.inmo.plagubot.suggestionsbot.common.DateTimeSerializer
import dev.inmo.plagubot.suggestionsbot.suggestions.SuggestionsResources
import dev.inmo.tgbotapi.types.UserId

@Serializable
sealed interface SuggestionStatus {
    val dateTime: DateTime
    val titleResource: StringResource

    @Serializable
    sealed interface Cancelable : SuggestionStatus {
        companion object {
            val titleResource: StringResource
                get() = SuggestionsResources.strings.statusCancelable
        }
    }
    @Serializable
    @SerialName("C")
    data class Created(
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Cancelable {
        override val titleResource: StringResource
            get() = SuggestionsResources.strings.statusCreated
    }
    @Serializable
    @SerialName("OR")
    data class OnReview(
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Cancelable {
        override val titleResource: StringResource
            get() = SuggestionsResources.strings.statusOnReview
    }

    @Serializable
    sealed interface Done : SuggestionStatus {
        companion object {
            val titleResource: StringResource
                get() = SuggestionsResources.strings.statusDone
        }
    }
    @Serializable
    @SerialName("Ca")
    data class Cancelled(
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Done {
        override val titleResource: StringResource
            get() = SuggestionsResources.strings.statusCancelled
    }

    @Serializable
    sealed interface Reviewed : Done {
        val reviewerId: UserId

        companion object {
            val titleResource: StringResource
                get() = SuggestionsResources.strings.statusReviewed
        }
    }

    @Serializable
    @SerialName("A")
    data class Accepted(
        override val reviewerId: UserId,
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Reviewed {
        override val titleResource: StringResource
            get() = SuggestionsResources.strings.statusAccepted
    }

    @Serializable
    @SerialName("R")
    data class Rejected(
        override val reviewerId: UserId,
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Reviewed {
        override val titleResource: StringResource
            get() = SuggestionsResources.strings.statusRejected
    }

    @Serializable
    @SerialName("B")
    data class Banned(
        override val reviewerId: UserId,
        @Serializable(DateTimeSerializer::class)
        override val dateTime: DateTime
    ) : Reviewed {
        override val titleResource: StringResource
            get() = SuggestionsResources.strings.statusBanned
    }
}
