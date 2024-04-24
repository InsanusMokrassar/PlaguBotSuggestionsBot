package dev.inmo.plagubot.suggestionsbot.suggestions.repo

import dev.inmo.micro_utils.repos.ReadCRUDRepo
import dev.inmo.micro_utils.repos.WriteCRUDRepo
import dev.inmo.micro_utils.repos.CRUDRepo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.*
import korlibs.time.DateTime
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.UserId

import kotlinx.coroutines.flow.Flow


interface ReadSuggestionsRepo : ReadCRUDRepo<RegisteredSuggestion, SuggestionId> {
    suspend fun getIdByChatAndMessage(chatId: IdChatIdentifier, messageId: MessageId): SuggestionId?

    suspend fun getFirstMessageInfo(suggestionId: SuggestionId): SuggestionContentInfo?

    suspend fun getSuggestionStatusesHistory(suggestionId: SuggestionId): List<SuggestionStatus>

    suspend fun getSuggestionStatus(suggestionId: SuggestionId): SuggestionStatus? = getSuggestionStatusesHistory(suggestionId).maxByOrNull {
        it.dateTime
    }

    suspend fun getSuggestionCreationTime(suggestionId: SuggestionId): DateTime? = getSuggestionStatusesHistory(suggestionId).firstOrNull {
        it is SuggestionStatus.Created
    } ?.dateTime

    suspend fun isUserHaveBannedSuggestions(userid: UserId): Boolean
}

interface WriteSuggestionsRepo : WriteCRUDRepo<RegisteredSuggestion, SuggestionId, NewSuggestion> {
    val removedPostsFlow: Flow<RegisteredSuggestion>

    suspend fun updateStatus(suggestionId: SuggestionId, status: SuggestionStatus): RegisteredSuggestion?
}

interface SuggestionsRepo : CRUDRepo<RegisteredSuggestion, SuggestionId, NewSuggestion>, ReadSuggestionsRepo, WriteSuggestionsRepo {
}

