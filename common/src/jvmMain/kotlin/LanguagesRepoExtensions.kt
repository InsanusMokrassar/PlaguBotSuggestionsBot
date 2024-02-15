package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.micro_utils.language_codes.IetfLang
import dev.inmo.micro_utils.repos.KeyValueRepo
import dev.inmo.micro_utils.repos.set
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.chat.User
import java.util.*

typealias LanguagesRepo = KeyValueRepo<IdChatIdentifier, IetfLang>

suspend fun LanguagesRepo.update(user: User) {
    set(user.id, user.ietfLanguageCodeOrDefault)
}

suspend fun LanguagesRepo.getOrDefault(idChatIdentifier: IdChatIdentifier) = get(
    idChatIdentifier
) ?: Locale.getDefault().ietfLanguageCode
