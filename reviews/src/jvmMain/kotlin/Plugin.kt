package dev.inmo.plagubot.suggestionsbot.reviews

import com.soywiz.klock.DateTime
import dev.inmo.micro_utils.coroutines.launchSafelyWithoutExceptions
import dev.inmo.micro_utils.coroutines.runCatchingSafely
import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.koin.singleWithBinds
import dev.inmo.micro_utils.pagination.utils.doForAllWithNextPaging
import dev.inmo.micro_utils.repos.set
import dev.inmo.micro_utils.repos.unset
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.suggestionsbot.common.ChatsConfig
import dev.inmo.plagubot.suggestionsbot.common.locale
import dev.inmo.plagubot.suggestionsbot.reviews.ReviewsResources
import dev.inmo.plagubot.suggestionsbot.reviews.repo.ExposedReviewMessagesInfo
import dev.inmo.plagubot.suggestionsbot.suggestions.exposed.SuggestionsMessageMetaInfosExposedRepo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.RegisteredSuggestion
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionStatus
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.SuggestionsRepo
import dev.inmo.tgbotapi.extensions.api.chat.get.getChat
import dev.inmo.tgbotapi.extensions.api.delete
import dev.inmo.tgbotapi.extensions.api.edit.edit
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.utils.privateChatOrNull
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.flatInlineKeyboard
import dev.inmo.tgbotapi.extensions.utils.types.buttons.inlineKeyboard
import dev.inmo.tgbotapi.extensions.utils.userOrNull
import dev.inmo.tgbotapi.libraries.resender.MessageMetaInfo
import dev.inmo.tgbotapi.libraries.resender.MessagesResender
import dev.inmo.tgbotapi.libraries.resender.invoke
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.message.textsources.link
import dev.inmo.tgbotapi.types.message.textsources.mention
import dev.inmo.tgbotapi.utils.buildEntities
import dev.inmo.tgbotapi.types.queries.callback.MessageCallbackQuery
import dev.inmo.tgbotapi.types.userLink
import dev.inmo.tgbotapi.utils.bold
import dev.inmo.tgbotapi.utils.underline
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.qualifier.named

object Plugin : Plugin {
    private val ReviewsSuggestionsMessageMetaInfosExposedRepoQualifier = named("ReviewsSuggestionsMessageMetaInfosExposedRepoQualifier")
    @Serializable
    private data class Config(
        val acceptRequireApprove: Boolean = false,
        val banRequireApprove: Boolean = true,
        val rejectRequireApprove: Boolean = true,
    )
    private val PrivateChat.name
        get() = "${lastName.takeIf { it.isNotEmpty() } ?.let { "$it " } ?: ""}${firstName}"
    override fun Module.setupDI(database: Database, params: JsonObject) {
        singleWithBinds {
            ExposedReviewMessagesInfo(get())
        }
        single(ReviewsSuggestionsMessageMetaInfosExposedRepoQualifier) {
            SuggestionsMessageMetaInfosExposedRepo(get(), "reviews_suggestions_messages")
        }
        single {
            params["reviews"] ?.let {
                get<Json>().decodeFromJsonElement(Config.serializer(), it)
            } ?: Config()
        }
    }

    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val suggestionsRepo = koin.get<SuggestionsRepo>()
        val publisher = koin.get<MessagesResender>()
        val chatsConfig = koin.get<ChatsConfig>()
        val suggestionsMessagesRepo = koin.get<SuggestionsMessageMetaInfosExposedRepo>(ReviewsSuggestionsMessageMetaInfosExposedRepoQualifier)
        val config = koin.get<Config>()

        val acceptButtonData = "review_accept"
        val rejectButtonData = "review_reject"
        val banButtonData = "review_ban"
        val cancelButtonData = "cancel"

        suspend fun BehaviourContext.updateSuggestionPanel(
            suggestion: RegisteredSuggestion,
            firstMetaInfo: MessageMetaInfo?
        ): MessageMetaInfo? {
            val managementMessage: MessageMetaInfo? = suggestionsMessagesRepo.get(suggestion.id)
            val user = runCatchingSafely {
                getChat(suggestion.user).privateChatOrNull()
            }.getOrNull()
            val statusString = suggestion.status.titleResource.localized(chatsConfig.locale)

            val entities = buildEntities {
                underline(statusString) + "\n\n"

                when {
                    user == null -> {
                        +link(ReviewsResources.strings.defaultUserName.localized(chatsConfig.locale), suggestion.user.userLink)
                    }
                    user.username == null -> {
                        +link(user.name, suggestion.user.chatId.userLink)
                    }
                    else -> {
                        user.username ?.let {
                            +mention(it)
                        } ?: +link(user.name, suggestion.user.chatId.userLink)
                    }
                }

                +ReviewsResources.strings.statusMessageSuggestedPart.localized(chatsConfig.locale)
                if (suggestion.isAnonymous) {
                    underline(ReviewsResources.strings.statusMessageAnonymouslyPart.localized(chatsConfig.locale))
                } else {
                    underline(ReviewsResources.strings.statusMessageNotAnonymouslyPart.localized(chatsConfig.locale))
                }
            }

            val replyMarkup = when (suggestion.status) {
                is SuggestionStatus.Created,
                is SuggestionStatus.OnReview -> flatInlineKeyboard {
                    dataButton(ReviewsResources.strings.buttonTextAccept.localized(chatsConfig.locale), acceptButtonData)
                    dataButton(ReviewsResources.strings.buttonTextBan.localized(chatsConfig.locale), banButtonData)
                    dataButton(ReviewsResources.strings.buttonTextReject.localized(chatsConfig.locale), rejectButtonData)
                }
                is SuggestionStatus.Done -> null
            }

            return when {
                managementMessage != null -> {
                    edit(
                        managementMessage.chatId,
                        managementMessage.messageId,
                        entities = entities,
                        replyMarkup = replyMarkup
                    )
                    managementMessage
                }
                firstMetaInfo != null -> {
                    reply(
                        firstMetaInfo.chatId,
                        firstMetaInfo.messageId,
                        entities = entities,
                        replyMarkup = replyMarkup
                    ).let {
                        MessageMetaInfo(it).also {
                            suggestionsMessagesRepo.set(suggestion.id, it)
                            suggestionsRepo.updateStatus(suggestion.id, SuggestionStatus.OnReview(DateTime.now()))
                        }
                    }
                }
                else -> null
            }
        }

        suspend fun initSuggestionMessage(suggestion: RegisteredSuggestion) {
            val sent = publisher.resend(
                chatsConfig.suggestionsChat,
                suggestion.content.map { it.messageMetaInfo }
            )
            updateSuggestionPanel(
                suggestion = suggestion,
                firstMetaInfo = sent.first().second
            )
        }

        suggestionsRepo.newObjectsFlow.subscribeSafelyWithoutExceptions(this) { suggestion ->
            initSuggestionMessage(suggestion)
        }

        suggestionsRepo.updatedObjectsFlow.subscribeSafelyWithoutExceptions(this) {
            updateSuggestionPanel(it, null)
        }

        launchSafelyWithoutExceptions {
            doForAllWithNextPaging {
                suggestionsRepo.getByPagination(it).also {
                    it.results.forEach {
                        if (suggestionsMessagesRepo.contains(it.id)) {
                            return@forEach
                        }

                        launchSafelyWithoutExceptions {
                            initSuggestionMessage(it)
                        }
                    }
                }
            }
        }

        suspend fun BehaviourContext.approveSuggestionPanel(
            approveText: String,
            approveButtonText: String,
            approveButtonData: String,
            managementMessageMetaInfo: MessageMetaInfo
        ) {
            edit(
                managementMessageMetaInfo.chatId,
                managementMessageMetaInfo.messageId,
                approveText,
                replyMarkup = flatInlineKeyboard {
                    dataButton(approveButtonText, approveButtonData)
                    dataButton(ReviewsResources.strings.buttonTextCancel.localized(chatsConfig.locale), cancelButtonData)
                }
            )
        }

        suspend fun registerCompleteMessageDataCallbackQueryTrigger(
            data: String,
            approveTextAndButton: Pair<String, String>?,
            block: suspend MessageCallbackQuery.(RegisteredSuggestion) -> Unit
        ) {
            val approveData = "${data}_approve"
            onMessageDataCallbackQuery(
                approveTextAndButton ?.let {
                    Regex(approveData)
                } ?: Regex("($data)|($approveData)"),
                initialFilter = { it.message.chat.id == chatsConfig.suggestionsChat }
            ) {
                val suggestionId = suggestionsMessagesRepo.getSuggestionId(
                    it.message.chat.id,
                    it.message.messageId
                ) ?: return@onMessageDataCallbackQuery

                val suggestion = suggestionsRepo.getById(suggestionId)

                if (suggestion == null) {
                    runCatchingSafely {
                        delete(it.message)
                    }
                    suggestionsMessagesRepo.unset(suggestionId)
                } else {
                    it.block(suggestion)
                }
            }

            approveTextAndButton ?.let {
                onMessageDataCallbackQuery(
                    data,
                    initialFilter = { it.message.chat.id == chatsConfig.suggestionsChat }
                ) {
                    approveSuggestionPanel(
                        approveTextAndButton.first,
                        approveTextAndButton.second,
                        approveData,
                        MessageMetaInfo(it.message)
                    )
                }
            }
        }
        onMessageDataCallbackQuery(
            cancelButtonData,
            initialFilter = { it.message.chat.id == chatsConfig.suggestionsChat }
        ) {
            val suggestionId = suggestionsMessagesRepo.getSuggestionId(
                it.message.chat.id,
                it.message.messageId
            ) ?: return@onMessageDataCallbackQuery

            updateSuggestionPanel(
                suggestionsRepo.getById(suggestionId) ?: return@onMessageDataCallbackQuery,
                null
            )
        }

        registerCompleteMessageDataCallbackQueryTrigger(
            acceptButtonData,
            Pair(
                ReviewsResources.strings.approveTextAccept.localized(chatsConfig.locale),
                ReviewsResources.strings.buttonTextAccept.localized(chatsConfig.locale)
            ).takeIf { config.acceptRequireApprove }
        ) {
            suggestionsRepo.updateStatus(
                it.id,
                SuggestionStatus.Accepted(
                    user.id,
                    DateTime.now()
                )
            )
        }

        registerCompleteMessageDataCallbackQueryTrigger(
            banButtonData,
            Pair(
                ReviewsResources.strings.approveTextBan.localized(chatsConfig.locale),
                ReviewsResources.strings.buttonTextBan.localized(chatsConfig.locale)
            ).takeIf { config.banRequireApprove }
        ) {
            suggestionsRepo.updateStatus(
                it.id,
                SuggestionStatus.Banned(
                    user.id,
                    DateTime.now()
                )
            )
        }

        registerCompleteMessageDataCallbackQueryTrigger(
            rejectButtonData,
            Pair(
                ReviewsResources.strings.approveTextReject.localized(chatsConfig.locale),
                ReviewsResources.strings.buttonTextReject.localized(chatsConfig.locale)
            ).takeIf { config.rejectRequireApprove }
        ) {
            suggestionsRepo.updateStatus(
                it.id,
                SuggestionStatus.Rejected(
                    user.id,
                    DateTime.now()
                )
            )
        }
    }
}
