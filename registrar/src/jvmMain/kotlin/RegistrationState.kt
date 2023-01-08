package dev.inmo.plagubot.suggestionsbot.registrar

import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.plaguposter.posts.models.SuggestionContentInfo
import dev.inmo.tgbotapi.types.FullChatIdentifierSerializer
import dev.inmo.tgbotapi.types.IdChatIdentifier
import kotlinx.serialization.Serializable

interface RegistrationState : State {
    override val context: IdChatIdentifier

    @Serializable
    data class InProcess(
        @Serializable(FullChatIdentifierSerializer::class)
        override val context: IdChatIdentifier,
        val messages: List<SuggestionContentInfo>
    ) : RegistrationState

    @Serializable
    data class Finish(
        @Serializable(FullChatIdentifierSerializer::class)
        override val context: IdChatIdentifier,
        val messages: List<SuggestionContentInfo>
    ) : RegistrationState
}
