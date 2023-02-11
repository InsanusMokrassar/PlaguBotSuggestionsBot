package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.fsm.common.StatesManager
import dev.inmo.micro_utils.fsm.common.managers.DefaultStatesManager
import dev.inmo.micro_utils.fsm.common.managers.DefaultStatesManagerRepo
import dev.inmo.micro_utils.fsm.common.managers.InMemoryDefaultStatesManagerRepo
import dev.inmo.plagubot.Plugin
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module

object Plugin : Plugin {
    override fun Module.setupDI(database: Database, params: JsonObject) {
        params["chats"] ?.let {
            single { _ ->
                get<Json>().decodeFromJsonElement(ChatsConfig.serializer(), it)
            }
        }

        single { CoroutineScope(Dispatchers.Default) }
        single<StatesManager<State>> {
            DefaultStatesManager(
                getOrNull<DefaultStatesManagerRepo<State>>() ?: InMemoryDefaultStatesManagerRepo<State>()
            )
        }
    }

    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {

    }
}
