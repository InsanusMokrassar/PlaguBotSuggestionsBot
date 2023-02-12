package dev.inmo.plagubot.suggestionsbot.publisher

import dev.inmo.micro_utils.coroutines.runCatchingSafely
import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.suggestionsbot.common.ChatsConfig
import dev.inmo.plagubot.suggestionsbot.suggestions.models.RegisteredSuggestion
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionStatus
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.SuggestionsRepo
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.chat.get.getChat
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.utils.extendedPrivateChatOrNull
import dev.inmo.tgbotapi.libraries.resender.MessagesResender
import dev.inmo.tgbotapi.types.chat.ExtendedBot
import dev.inmo.tgbotapi.types.chat.ExtendedPrivateChat
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.message.textsources.TextSourcesList
import dev.inmo.tgbotapi.types.message.textsources.hashtag
import dev.inmo.tgbotapi.types.message.textsources.mention
import dev.inmo.tgbotapi.utils.buildEntities
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module

object Plugin : Plugin {

    /**
     * * Use "$user" to mention user
     * * Use "$bot" to mention current bot as source of suggestion
     */
    @Serializable
    private data class BoundsConfig(
        val beforeMessage: String? = null,
        val afterMessage: String? = null,
        val template: String? = null,
        val anonText: String = "anonymous",
        val defaultUserText: String = "user"
    ) {
        private val PrivateChat.name
            get() = "${lastName.takeIf { it.isNotEmpty() } ?.let { "$it " } ?: ""}${firstName}"
        fun prependWithTemplate(suggestion: RegisteredSuggestion, suggester: ExtendedPrivateChat?, bot: ExtendedBot): TextSourcesList {
            template ?: return emptyList()

            val userMention by lazy {
                if (suggestion.isAnonymous) {
                    hashtag(anonText)
                } else {
                    suggester ?.let {
                        mention(it.name, it.id)
                    } ?: mention(defaultUserText, suggestion.user)
                }
            }
            val parts = template.split("$")
            return buildEntities {
                parts.forEachIndexed { i, s ->
                    when {
                        s.isEmpty() -> return@forEachIndexed
                        parts.getOrNull(i - 1) ?.lastOrNull() != '\\' -> {
                            val sWithoutCommand = s.dropWhile { it != ' ' && it != '\\' }
                            val firstPartOfS = s.removeSuffix(sWithoutCommand)
                            when {
                                firstPartOfS == "user" -> +userMention
                                firstPartOfS == "bot" -> +mention(bot.name, bot.id)
                                else -> +firstPartOfS
                            }
                            +(sWithoutCommand.removeSuffix("\\").takeIf {
                                it.isNotEmpty()
                            } ?: return@forEachIndexed)
                        }
                        else -> +"$$s"
                    }
                }
            }
        }
    }
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
        val bot = koin.getOrNull<ExtendedBot>() ?: getMe()

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
            config ?.prependWithTemplate(
                it,
                runCatchingSafely {
                    getChat(it.user).extendedPrivateChatOrNull()
                }.getOrNull(),
                bot
            ) ?.takeIf {
                it.isNotEmpty()
            } ?.let {
                send(chatsConfig.targetChat, it)
            }
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
