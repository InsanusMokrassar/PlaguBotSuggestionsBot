package dev.inmo.plagubot.suggestionsbot.suggestons.models

import com.soywiz.klock.DateTime
import dev.inmo.tgbotapi.types.UserId
import kotlinx.serialization.Serializable

@Serializable
sealed interface Suggestion {
    val user: UserId
    val isAnonymous: Boolean
    val content: List<SuggestionContentInfo>
}

@Serializable
data class NewSuggestion(
    override val user: UserId,
    override val isAnonymous: Boolean,
    override val content: List<SuggestionContentInfo>
) : Suggestion

@Serializable
data class RegisteredSuggestion(
    val id: SuggestionId,
    val status: SuggestionStatus,
    override val user: UserId,
    override val isAnonymous: Boolean,
    override val content: List<SuggestionContentInfo>
) : Suggestion
