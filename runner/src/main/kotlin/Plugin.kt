package dev.inmo.plagubot.suggestionsbot.runner

import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.plagubot.Plugin
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import kotlinx.serialization.json.JsonObject
import org.koin.core.Koin
import org.koin.core.module.Module

object Plugin : Plugin {
    override fun Module.setupDI(params: JsonObject) {}

    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {}
}
