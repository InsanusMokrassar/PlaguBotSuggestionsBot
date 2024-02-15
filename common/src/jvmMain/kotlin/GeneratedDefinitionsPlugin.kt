// THIS CODE HAVE BEEN GENERATED AUTOMATICALLY
// TO REGENERATE IT JUST DELETE FILE
// ORIGINAL FILE: Plugin.kt
package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.micro_utils.language_codes.IetfLang
import dev.inmo.micro_utils.repos.KeyValueRepo
import dev.inmo.tgbotapi.types.IdChatIdentifier
import kotlin.Boolean
import org.koin.core.Koin
import org.koin.core.definition.Definition
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope

public val Scope.languagesRepo: KeyValueRepo<IdChatIdentifier, IetfLang>
  get() = get(named("languagesRepo"))

public val Koin.languagesRepo: KeyValueRepo<IdChatIdentifier, IetfLang>
  get() = get(named("languagesRepo"))

public fun Module.languagesRepoSingle(createdAtStart: Boolean = false,
    definition: Definition<KeyValueRepo<IdChatIdentifier, IetfLang>>):
    KoinDefinition<KeyValueRepo<IdChatIdentifier, IetfLang>> = single(named("languagesRepo"),
    createdAtStart = createdAtStart, definition = definition)

public
    fun Module.languagesRepoFactory(definition: Definition<KeyValueRepo<IdChatIdentifier, IetfLang>>):
    KoinDefinition<KeyValueRepo<IdChatIdentifier, IetfLang>> = factory(named("languagesRepo"),
    definition = definition)
