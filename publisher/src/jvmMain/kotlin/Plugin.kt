package dev.inmo.plagubot.suggestionsbot.publisher

import dev.inmo.micro_utils.coroutines.runCatchingSafely
import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.suggestionsbot.common.ChatsConfig
import dev.inmo.plagubot.suggestionsbot.suggestions.models.RegisteredSuggestion
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionStatus
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.SuggestionsRepo
import dev.inmo.tgbotapi.abstracts.Texted
import dev.inmo.tgbotapi.abstracts.TextedWithTextSources
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.chat.get.getChat
import dev.inmo.tgbotapi.extensions.api.edit.caption.editMessageCaption
import dev.inmo.tgbotapi.extensions.api.edit.edit
import dev.inmo.tgbotapi.extensions.api.forwardMessage
import dev.inmo.tgbotapi.extensions.api.send.copyMessage
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.utils.contentMessageOrNull
import dev.inmo.tgbotapi.extensions.utils.extendedPrivateChatOrNull
import dev.inmo.tgbotapi.extensions.utils.formatting.makeChatLink
import dev.inmo.tgbotapi.extensions.utils.withContentOrNull
import dev.inmo.tgbotapi.libraries.resender.MessageMetaInfo
import dev.inmo.tgbotapi.libraries.resender.MessagesResender
import dev.inmo.tgbotapi.libraries.resender.invoke
import dev.inmo.tgbotapi.requests.send.SendTextMessage
import dev.inmo.tgbotapi.types.chat.ExtendedBot
import dev.inmo.tgbotapi.types.chat.ExtendedPrivateChat
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.content.MediaContent
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.message.content.TextedMediaContent
import dev.inmo.tgbotapi.types.message.textsources.RegularTextSource
import dev.inmo.tgbotapi.types.message.textsources.TextSourcesList
import dev.inmo.tgbotapi.types.message.textsources.hashtag
import dev.inmo.tgbotapi.types.message.textsources.link
import dev.inmo.tgbotapi.types.message.textsources.mention
import dev.inmo.tgbotapi.types.message.textsources.regular
import dev.inmo.tgbotapi.types.userLink
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
        val defaultUserText: String = "user",

    ) {
        private val PrivateChat.name
            get() = "${lastName.takeIf { it.isNotEmpty() } ?.let { "$it " } ?: ""}${firstName}"
        fun suggestionNoteTextSources(suggestion: RegisteredSuggestion, suggester: ExtendedPrivateChat?, bot: ExtendedBot): TextSourcesList {
            template ?: return emptyList()

            val userMention by lazy {
                when {
                    suggestion.isAnonymous -> hashtag(anonText)
                    suggester == null -> link(defaultUserText, suggestion.user.chatId.userLink)
                    suggester.username == null && suggester.allowCreateUserIdLink -> link(suggester.name, suggester.id.userLink)
                    else -> suggester.username ?.let {
                        mention(it)
                    } ?: regular(suggester.name)
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
            val sourceSortedContent = it.content.sortedBy { it.order }
            val firstAvailableMessageInfoToMessage = sourceSortedContent.firstNotNullOfOrNull {
                runCatchingSafely {
                    forwardMessage(it.messageMetaInfo.chatId, toChatId = chatsConfig.cacheChat, it.messageMetaInfo.messageId)
                }.getOrNull() ?.let { message ->
                    it to message
                }
            } ?: return@subscribeSafelyWithoutExceptions
            val (firstAvailableMessageInfo, message) = firstAvailableMessageInfoToMessage
            config ?.beforeMessage ?.let {
                send(
                    chatsConfig.targetChat,
                    it
                )
            }
            delay(200L)
            val suggestionTextSources = config ?.suggestionNoteTextSources(
                it,
                runCatchingSafely {
                    getChat(it.user).extendedPrivateChatOrNull()
                }.getOrNull(),
                bot
            ) ?.takeIf {
                it.isNotEmpty()
            }
            var resultMessageChanged = false
            val resultMessage = suggestionTextSources ?.let { textSources ->
                runCatchingSafely {
                    message.contentMessageOrNull() ?.let {
                        val resend = it.content.createResend(
                            chatsConfig.cacheChat,
                        )
                        execute(resend).contentMessageOrNull() ?.also {
                            it.withContentOrNull<TextedMediaContent>() ?.let {
                                editMessageCaption(it, (it.content as? TextedMediaContent) ?.textSources ?.let { it + RegularTextSource("\n\n") + textSources } ?: return@let )
                            } ?: it.withContentOrNull<TextContent>() ?.let {
                                edit(it, it.content.textSources + RegularTextSource("\n\n") + textSources)
                            } ?.also {
                                resultMessageChanged = true
                            }
                        }
                    }
                }
            } ?.getOrNull() ?: message
            val sentInfos = publisher.resend(
                chatsConfig.targetChat,
                listOf(MessageMetaInfo(resultMessage).copy(group = firstAvailableMessageInfo.messageMetaInfo.group)) + ((sourceSortedContent.dropWhile { it != firstAvailableMessageInfo } - firstAvailableMessageInfo)).map {
                    it.messageMetaInfo
                }
            )
            sentInfos.firstOrNull() ?.second ?.also {
                suggestionTextSources ?.also { textSources ->
                    if (!resultMessageChanged) {
                        runCatchingSafely {
                            reply(
                                it.chatId,
                                it.messageId,
                                textSources
                            )
                        }
                    }
                }
            }
            delay(2000L)
            config ?.afterMessage ?.let {
                send(
                    chatsConfig.targetChat,
                    it
                )
            }
        }
    }
}
