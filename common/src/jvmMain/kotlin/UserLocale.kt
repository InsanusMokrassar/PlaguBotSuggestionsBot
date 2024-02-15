package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.micro_utils.language_codes.IetfLang
import dev.inmo.tgbotapi.extensions.utils.commonUserOrNull
import dev.inmo.tgbotapi.extensions.utils.userOrNull
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.chat.User
import dev.inmo.tgbotapi.types.javaLocale
import java.util.*

val User?.ietfLanguageCodeOrDefault: IetfLang
    get() = this?.commonUserOrNull()?.ietfLanguageCode ?: IetfLang(Locale.getDefault().toLanguageTag())

val PrivateChat?.ietfLanguageCodeOrDefault: IetfLang
    get() = this?.userOrNull().ietfLanguageCodeOrDefault

val User?.locale: Locale
    get() = ietfLanguageCodeOrDefault.javaLocale() ?: Locale.getDefault()

val PrivateChat?.locale: Locale
    get() = this?.userOrNull().locale

val IetfLang.locale: Locale
    get() = Locale.forLanguageTag(code)

val Locale.ietfLanguageCode: IetfLang
    get() = IetfLang(toLanguageTag())
