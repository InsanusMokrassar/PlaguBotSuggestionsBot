package dev.inmo.plagubot.suggestionsbot.suggestions.models

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class SuggestionId(
    val string: String
) {
    override fun toString(): String = string
}
