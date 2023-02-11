package dev.inmo.plagubot.suggestionsbot.registrar

import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionContentInfo
import dev.inmo.tgbotapi.types.FullChatIdentifierSerializer
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.UserId
import kotlinx.serialization.Serializable

interface RegistrationState : State {
    override val context: IdChatIdentifier
    val isAnonymous: Boolean

    @Serializable
    data class InProcess(
        override val context: UserId,
        val messages: List<SuggestionContentInfo>,
        override val isAnonymous: Boolean = false
    ) : RegistrationState

    @Serializable
    data class Finish(
        override val context: UserId,
        val messages: List<SuggestionContentInfo>,
        override val isAnonymous: Boolean
    ) : RegistrationState
}
