package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.micro_utils.language_codes.IetfLanguageCode
import dev.inmo.tgbotapi.extensions.utils.commonUserOrNull
import dev.inmo.tgbotapi.types.chat.User
import dev.inmo.tgbotapi.types.javaLocale
import java.util.*

val User?.ietfLanguageCodeOrDefault: IetfLanguageCode
    get() = this ?.commonUserOrNull() ?.ietfLanguageCode ?: IetfLanguageCode(Locale.getDefault().toLanguageTag())

val User?.locale: Locale
    get() = ietfLanguageCodeOrDefault.javaLocale() ?: Locale.getDefault()

val IetfLanguageCode.locale: Locale
    get() = Locale.forLanguageTag(code)

val Locale.ietfLanguageCode: IetfLanguageCode
    get() = IetfLanguageCode(toLanguageTag())
