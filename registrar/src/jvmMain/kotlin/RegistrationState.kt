package dev.inmo.plagubot.suggestionsbot.registrar

import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.language_codes.IetfLanguageCode
import dev.inmo.plagubot.suggestionsbot.common.ietfLanguageCode
import dev.inmo.plagubot.suggestionsbot.common.locale
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionContentInfo
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.UserId
import kotlinx.serialization.Serializable
import java.util.Locale

interface RegistrationState : State {
    override val context: IdChatIdentifier
    val isAnonymous: Boolean
    val languageCode: IetfLanguageCode
    val locale: Locale
        get() = languageCode.locale

    @Serializable
    data class InProcess(
        override val context: UserId,
        val messages: List<SuggestionContentInfo>,
        override val isAnonymous: Boolean = false,
        override val languageCode: IetfLanguageCode = Locale.getDefault().ietfLanguageCode
    ) : RegistrationState

    @Serializable
    data class Finish(
        override val context: UserId,
        val messages: List<SuggestionContentInfo>,
        override val isAnonymous: Boolean,
        override val languageCode: IetfLanguageCode = Locale.getDefault().ietfLanguageCode,
    ) : RegistrationState
}
