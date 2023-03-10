package dev.inmo.plagubot.suggestionsbot.suggestions

import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.suggestionsbot.common.ChatsConfig
import dev.inmo.plagubot.suggestionsbot.suggestions.exposed.ExposedSuggestionsRepo
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.*
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.libraries.resender.MessagesResender
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.binds

object Plugin : Plugin {
    override fun Module.setupDI(database: Database, params: JsonObject) {
        single { ExposedSuggestionsRepo(database) } binds arrayOf(
            SuggestionsRepo::class,
            ReadSuggestionsRepo::class,
            WriteSuggestionsRepo::class,
        )
        single {
            val config = get<ChatsConfig>()
            MessagesResender(get(), config.cacheChat)
        }
    }

    override suspend fun BehaviourContext.setupBotPlugin(koin: Koin) {
        val postsRepo = koin.get<SuggestionsRepo>()
        val chatsConfig = koin.get<ChatsConfig>()



//        if (config.autoRemoveMessages) {
//            postsRepo.removedPostsFlow.subscribeSafelyWithoutExceptions(this) {
//                it.content.forEach {
//                    runCatchingSafely {
//                        delete(it.chatId, it.messageId)
//                    }
//                }
//            }
//        }
//
//        onCommand("delete_post", requireOnlyCommandInMessage = true) {
//            val messageInReply = it.replyTo ?: run {
//                reply(it, "Reply some message of post to delete it")
//                return@onCommand
//            }
//
//            val postId = postsRepo.getIdByChatAndMessage(messageInReply.chat.id, messageInReply.messageId) ?: run {
//                reply(it, "Unable to find post id by message")
//                return@onCommand
//            }
//
//            postsRepo.deleteById(postId)
//
//            if (postsRepo.contains(postId)) {
//                edit(it, it.content.textSources + regular(UnsuccessfulSymbol))
//            } else {
//                edit(it, it.content.textSources + regular(SuccessfulSymbol))
//            }
//        }
//
//        koin.getOrNull<InlineTemplatesRepo>() ?.addTemplate(
//            OfferTemplate(
//                "Delete post",
//                listOf(
//                    Format("/delete_post")
//                ),
//                "Should be used with a reply on any post message"
//            )
//        )
    }
}
