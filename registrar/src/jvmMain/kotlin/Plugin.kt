package dev.inmo.plagubot.suggestionsbot.registrar

import com.soywiz.klock.DateTime
import dev.inmo.micro_utils.coroutines.firstOf
import dev.inmo.micro_utils.coroutines.runCatchingSafely
import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.koin.singleWithRandomQualifierAndBinds
import dev.inmo.micro_utils.repos.create
import dev.inmo.micro_utils.repos.set
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.plugins.inline.queries.models.Format
import dev.inmo.plagubot.plugins.inline.queries.models.OfferTemplate
import dev.inmo.plagubot.plugins.inline.queries.repos.InlineTemplatesRepo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.NewSuggestion
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionContentInfo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionStatus
import dev.inmo.plagubot.suggestionsbot.suggestions.repo.SuggestionsRepo
import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.api.delete
import dev.inmo.tgbotapi.extensions.api.edit.edit
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.strictlyOn
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.CommonMessageFilter
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.utils.not
import dev.inmo.tgbotapi.extensions.behaviour_builder.utils.times
import dev.inmo.tgbotapi.extensions.utils.extensions.sameChat
import dev.inmo.tgbotapi.extensions.utils.extensions.sameMessage
import dev.inmo.tgbotapi.extensions.utils.textContentOrNull
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.flatInlineKeyboard
import dev.inmo.tgbotapi.extensions.utils.withContentOrNull
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.content.MessageContent
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.message.textsources.BotCommandTextSource
import dev.inmo.tgbotapi.types.toChatId
import dev.inmo.tgbotapi.utils.buildEntities
import dev.inmo.tgbotapi.utils.regular
import dev.inmo.plagubot.suggestionsbot.common.ChatsConfig
import dev.inmo.plagubot.suggestionsbot.common.StartChainConflictSolver
import dev.inmo.plagubot.suggestionsbot.common.ietfLanguageCodeOrDefault
import dev.inmo.plagubot.suggestionsbot.common.locale
import dev.inmo.plagubot.suggestionsbot.suggestions.exposed.SuggestionsMessageMetaInfosExposedRepo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.RegisteredSuggestion
import dev.inmo.tgbotapi.extensions.api.chat.get.getChat
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitAnyContentMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.utils.privateChatOrNull
import dev.inmo.tgbotapi.extensions.utils.userOrNull
import dev.inmo.tgbotapi.libraries.resender.MessageMetaInfo
import dev.inmo.tgbotapi.libraries.resender.invoke
import dev.inmo.tgbotapi.utils.bold
import dev.inmo.tgbotapi.utils.underline
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Database
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.qualifier.named

object Plugin : Plugin {
    private val RegistrarSuggestionsMessageMetaInfosExposedRepoQualifier = named("RegistrarSuggestionsMessageMetaInfosExposedRepoQualifier")
    override fun Module.setupDI(database: Database, params: JsonObject) {
        single(RegistrarSuggestionsMessageMetaInfosExposedRepoQualifier) {
            SuggestionsMessageMetaInfosExposedRepo(get(), "registrar_suggestions_messages")
        }
        singleWithRandomQualifierAndBinds {
            StartChainConflictSolver { old, new ->
                if (old is RegistrationState.InProcess && new is RegistrationState.InProcess) {
                    false
                } else {
                    null
                }
            }
        }
    }

    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val chatsConfig = koin.get<ChatsConfig>()
        val suggestionsRepo = koin.get<SuggestionsRepo>()
        val suggestionsMessagesRepo = koin.get<SuggestionsMessageMetaInfosExposedRepo>(RegistrarSuggestionsMessageMetaInfosExposedRepoQualifier)

        val cancelButtonData = "cancel"

        suspend fun BehaviourContext.updateSuggestionPanel(
            suggestion: RegisteredSuggestion,
            firstMetaInfo: MessageMetaInfo?
        ): MessageMetaInfo? {
            val managementMessage: MessageMetaInfo? = suggestionsMessagesRepo.get(suggestion.id)
            val user = getChat(suggestion.user).privateChatOrNull()
            val statusString = when (suggestion.status) {
                is SuggestionStatus.Created,
                is SuggestionStatus.OnReview,
                is SuggestionStatus.Cancelled -> suggestion.status.titleResource.localized(user.locale)
                is SuggestionStatus.Reviewed -> SuggestionStatus.Reviewed.titleResource.localized(user.locale)
            }

            val entities = buildEntities {
                underline(statusString) + "\n\n"
                regular(RegistrarResources.strings.youSuggestedText.localized(user.locale))
                if (suggestion.isAnonymous) {
                    underline(RegistrarResources.strings.anonymouslyText.localized(user.locale))
                } else {
                    underline(RegistrarResources.strings.nonAnonymouslyText.localized(user.locale))
                }
            }

            val replyMarkup = when (suggestion.status) {
                is SuggestionStatus.Created,
                is SuggestionStatus.OnReview -> flatInlineKeyboard {
                    dataButton(RegistrarResources.strings.cancelButtonText.localized(user.locale), cancelButtonData)
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
            updateSuggestionPanel(
                suggestion = suggestion,
                firstMetaInfo = suggestion.content.minBy { it.order }.messageMetaInfo
            )
        }

        suggestionsRepo.newObjectsFlow.subscribeSafelyWithoutExceptions(koin.get()) {
            initSuggestionMessage(it)
        }
        suggestionsRepo.updatedObjectsFlow.subscribeSafelyWithoutExceptions(koin.get()) {
            updateSuggestionPanel(it, null)
        }
        suggestionsRepo.deletedObjectsIdsFlow.subscribeSafelyWithoutExceptions(koin.get()) {
            suggestionsMessagesRepo.get(it) ?.let {
                edit(it.chatId, it.messageId, RegistrarResources.strings.suggestionRemoved.localized())
            }
        }

        onMessageDataCallbackQuery(cancelButtonData) {
            val suggestionId = suggestionsMessagesRepo.getSuggestionId(it.message.chat.id, it.message.messageId) ?: let { _ ->
                return@onMessageDataCallbackQuery
            }

            suggestionsRepo.updateStatus(suggestionId, SuggestionStatus.Cancelled(DateTime.now()))
        }

        strictlyOn { state: RegistrationState.InProcess ->
            var state = state.copy()
            val buttonUuid = "finish"
            val cancelUuid = "cancel"
            val toggleAnonymousUuid = "toggleAnon"

            fun buildKeyboard() = flatInlineKeyboard {
                if (state.messages.isNotEmpty()) {
                    dataButton(
                        RegistrarResources.strings.finishPostCreatingButtonText.localized(state.locale),
                        buttonUuid
                    )
                }
                dataButton(
                    RegistrarResources.strings.cancelButtonText.localized(state.locale),
                    cancelUuid
                )
                dataButton(
                    if (state.isAnonymous) {
                        RegistrarResources.strings.anonymousPostCreatingButtonText.localized(state.locale)
                    } else {
                        RegistrarResources.strings.notAnonymousPostCreatingButtonText.localized(state.locale)
                    },
                    toggleAnonymousUuid
                )
            }

            val messageToDelete = send(
                state.context,
                buildEntities {
                    regular(
                        if (state.messages.isNotEmpty()) {
                            RegistrarResources.strings.partRegisteredText.localized(state.locale)
                        } else {
                            RegistrarResources.strings.startPostRegistrationText.localized(state.locale)
                        }
                    )
                },
                replyMarkup = buildKeyboard(),
                replyToMessageId = state.messages.lastOrNull() ?.messageMetaInfo ?.messageId
            )

            waitMessageDataCallbackQuery().filter {
                it.message.sameMessage(messageToDelete) && it.data == toggleAnonymousUuid
            }.subscribeSafelyWithoutExceptions(this) {
                state = state.copy(isAnonymous = !state.isAnonymous)
                answer(it)
                edit(messageToDelete, buildKeyboard())
            }

            val newMessagesInfo = firstOf<List<ContentMessage<*>>?> {
                add {
                    val result = mutableListOf<ContentMessage<MessageContent>>()
                    waitAnyContentMessage().filter {
                        it.chat.id == state.context && it.content.textContentOrNull() ?.text != "/finish" && it.content.textContentOrNull() ?.text != "/cancel"
                    }.onEach { result.add(it) }.debounce(1000L).first()
                    result
                }
                add {
                    waitMessageDataCallbackQuery().filter {
                        it.message.sameMessage(messageToDelete) && it.data == cancelUuid
                    }.first()
                    null
                }
                add {
                    waitTextMessage ().filter {
                        it.sameChat(messageToDelete) && it.content.text == "/cancel"
                    }.first()
                    null
                }
                add {
                    waitMessageDataCallbackQuery().filter {
                        it.message.sameMessage(messageToDelete) && it.data == buttonUuid
                    }.first()
                    emptyList<ContentMessage<MessageContent>>()
                }
                add {
                    waitTextMessage ().filter {
                        it.sameChat(messageToDelete) && it.content.text == "/finish"
                    }.first()
                    emptyList<ContentMessage<MessageContent>>()
                }
            } ?.ifEmpty {
                edit(messageToDelete, RegistrarResources.strings.finishPostRegistrationText.localized(state.locale))
                return@strictlyOn RegistrationState.Finish(
                    state.context,
                    state.messages,
                    state.isAnonymous,
                    state.languageCode
                )
            } ?.flatMapIndexed { i, it ->
                SuggestionContentInfo.fromMessage(it, state.messages.size + i)
            } ?: return@strictlyOn RegistrationState.Finish(
                state.context,
                emptyList(),
                state.isAnonymous,
                state.languageCode
            ).also {
                runCatchingSafely {
                    delete(messageToDelete)
                }
            }

            RegistrationState.InProcess(
                state.context,
                state.messages + newMessagesInfo,
                state.isAnonymous,
                state.languageCode
            ).also {
                delete(messageToDelete)
            }
        }

        strictlyOn { state: RegistrationState.Finish ->
            when {
                state.messages.isEmpty() -> {
                    send(
                        state.context,
                        RegistrarResources.strings.cancelPostRegistrationText.localized(state.locale)
                    )
                }
                else -> suggestionsRepo.create(
                    NewSuggestion(SuggestionStatus.Created(DateTime.now()), state.context, state.isAnonymous, state.messages)
                )
            }
            null
        }

        val registrarCommandsFilter: CommonMessageFilter<*> = CommonMessageFilter {
            !chatsConfig.checkIsOfWorkChat(it.chat.id) && it.chat is PrivateChat && !suggestionsRepo.isUserHaveBannedSuggestions(it.chat.id.toChatId())
        }
        val firstMessageNotCommandFilter: CommonMessageFilter<*> = CommonMessageFilter {
            it.withContentOrNull<TextContent>() ?.let { it.content.textSources.firstOrNull() is BotCommandTextSource } == true
        }

        onCommand("start_suggestion", initialFilter = registrarCommandsFilter) {
            startChain(RegistrationState.InProcess(
                it.chat.id.toChatId(),
                emptyList(),
                languageCode = it.chat.userOrNull().ietfLanguageCodeOrDefault
            ))
        }

        onContentMessage (
            initialFilter = registrarCommandsFilter * !firstMessageNotCommandFilter
        ) {
            startChain(RegistrationState.InProcess(
                it.chat.id.toChatId(),
                SuggestionContentInfo.fromMessage(it, 0),
                languageCode = it.chat.userOrNull().ietfLanguageCodeOrDefault
            ))
        }
        koin.getOrNull<InlineTemplatesRepo>() ?.apply {
            addTemplate(
                OfferTemplate(
                    "Start suggestion creating",
                    listOf(Format("/start_suggestion")),
                    "Use this command to start creating of complex suggestion with several messages"
                )
            )
            addTemplate(
                OfferTemplate(
                    "Finish suggestion creating",
                    listOf(Format("/finish")),
                    "Finish creating of complex suggestion"
                )
            )
            addTemplate(
                OfferTemplate(
                    "Cancel suggestion creating",
                    listOf(Format("/cancel")),
                    "Cancel creating of complex suggestion"
                )
            )
        }
    }
}
