@file:GenerateKoinDefinition("languagesRepo", KeyValueRepo::class, IdChatIdentifier::class, IetfLanguageCode::class, nullable = false)
package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.fsm.common.StatesManager
import dev.inmo.micro_utils.fsm.common.managers.DefaultStatesManager
import dev.inmo.micro_utils.fsm.common.managers.DefaultStatesManagerRepo
import dev.inmo.micro_utils.fsm.common.managers.InMemoryDefaultStatesManagerRepo
import dev.inmo.micro_utils.koin.annotations.GenerateKoinDefinition
import dev.inmo.micro_utils.koin.getAllDistinct
import dev.inmo.micro_utils.language_codes.IetfLanguageCode
import dev.inmo.micro_utils.repos.KeyValueRepo
import dev.inmo.micro_utils.repos.MapKeyValueRepo
import dev.inmo.micro_utils.repos.cache.cache.FullKVCache
import dev.inmo.micro_utils.repos.cache.cached
import dev.inmo.micro_utils.repos.cache.full.fullyCached
import dev.inmo.micro_utils.repos.exposed.keyvalue.ExposedKeyValueRepo
import dev.inmo.micro_utils.repos.mappers.withMapper
import dev.inmo.plagubot.Plugin
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.ChatIdentifier
import dev.inmo.tgbotapi.types.FullChatIdentifierSerializer
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.message.MarkdownV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module

object Plugin : Plugin {
    @Serializable
    private data class HelloConfig(
        val m2: String
    )
    override fun Module.setupDI(database: Database, params: JsonObject) {
        params["chats"] ?.let {
            single { _ ->
                get<Json>().decodeFromJsonElement(ChatsConfig.serializer(), it)
            }
        }
        params["hello"] ?.let {
            single { _ ->
                get<Json>().decodeFromJsonElement(HelloConfig.serializer(), it)
            }
        }

        single { CoroutineScope(Dispatchers.Default) }
        single<StatesManager<State>> {
            val startChainConflictSolvers = getAllDistinct<StartChainConflictSolver>()
            val updateChainConflictSolver = getAllDistinct<UpdateChainConflictSolver>()
            DefaultStatesManager(
                getOrNull<DefaultStatesManagerRepo<State>>() ?: InMemoryDefaultStatesManagerRepo<State>(),
                { exists, new -> startChainConflictSolvers.firstNotNullOfOrNull { it(exists, new) } ?: true },
                { exists, new, stateOnContext -> updateChainConflictSolver.firstNotNullOfOrNull { it(exists, new, stateOnContext) } ?: true }
            )
        }
        languagesRepoSingle {
            val json = get<Json>()
            ExposedKeyValueRepo(
                database,
                { text("chatId") },
                { text("language") },
                "chats_languages"
            ).withMapper(
                { json.encodeToString(FullChatIdentifierSerializer, this) },
                { code },
                { json.decodeFromString(FullChatIdentifierSerializer, this) as IdChatIdentifier },
                { IetfLanguageCode(this) }
            ).fullyCached(MapKeyValueRepo(), get())
        }
    }

    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val helloConfig = koin.getOrNull<HelloConfig>()
        val chatsConfig = koin.get<ChatsConfig>()
        helloConfig ?.let {
            onCommand("start", initialFilter = { !chatsConfig.checkIsOfWorkChat(it.chat.id) }) {
                reply(it, helloConfig.m2, MarkdownV2)
            }
        }
    }
}
