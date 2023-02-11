package dev.inmo.plagubot.suggestionsbot.publisher

import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.suggestionsbot.common.ChatsConfig
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionStatus
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.SuggestionsRepo
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.libraries.resender.MessagesResender
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module

object Plugin : Plugin {
    @Serializable
    private data class BoundsConfig(
        val beforeMessage: String? = null,
        val afterMessage: String? = null
    )
    override fun Module.setupDI(database: Database, params: JsonObject) {
        params["publisher"] ?.let { json ->
            single { get<Json>().decodeFromJsonElement(BoundsConfig.serializer(), json) }
        }
    }

    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val suggestionsRepo = koin.get<SuggestionsRepo>()
        val chatsConfig = koin.get<ChatsConfig>()
        val publisher = koin.get<MessagesResender>()
        val config = koin.getOrNull<BoundsConfig>()

        suggestionsRepo.updatedObjectsFlow.filter {
            it.status is SuggestionStatus.Accepted
        }.subscribeSafelyWithoutExceptions(koin.get()) {
            config ?.beforeMessage ?.let {
                send(
                    chatsConfig.targetChat,
                    it
                )
            }
            delay(500L)
            publisher.resend(
                chatsConfig.targetChat,
                it.content.sortedBy {
                    it.order
                }.map {
                    it.messageMetaInfo
                }
            )
            delay(500L)
            config ?.afterMessage ?.let {
                send(
                    chatsConfig.targetChat,
                    it
                )
            }
        }
    }
}
