package dev.inmo.plagubot.suggestionsbot.reviews

import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.koin.singleWithBinds
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.suggestionsbot.common.ChatsConfig
import dev.inmo.plagubot.suggestionsbot.reviews.repo.ExposedReviewMessagesInfo
import dev.inmo.plagubot.suggestionsbot.suggestons.repo.SuggestionsRepo
import dev.inmo.plagubot.suggestionsbot.suggestons.sending.MessagesPublisher
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module

object Plugin : Plugin {
    override fun Module.setupDI(database: Database, params: JsonObject) {
        singleWithBinds {
            ExposedReviewMessagesInfo(get())
        }
    }

    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val suggestionsRepo = koin.get<SuggestionsRepo>()
        val publisher = koin.get<MessagesPublisher>()
        val chatsConfig = koin.get<ChatsConfig>()

        suggestionsRepo.newObjectsFlow.subscribeSafelyWithoutExceptions(this) {

        }
    }
}
