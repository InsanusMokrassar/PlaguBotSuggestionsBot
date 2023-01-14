package dev.inmo.plagubot.suggestionsbot.status.common

import dev.inmo.plagubot.suggestionsbot.status.common.repo.StatusesRepo
import dev.inmo.plagubot.suggestionsbot.suggestons.models.SuggestionId
import dev.inmo.plagubot.suggestionsbot.suggestons.repo.SuggestionsRepo
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.IdChatIdentifier

abstract class BaseStatusFeature(
    private val statusesRepo: StatusesRepo,
    private val suggestionsRepo: SuggestionsRepo,
) : StatusFeature {
    protected abstract suspend fun resolveStatusChatId(suggestionId: SuggestionId): IdChatIdentifier

    override suspend fun BehaviourContext.refreshStatus(suggestionId: SuggestionId) {
        val messageInfo = statusesRepo.get(suggestionId)

        if (messageInfo == null) {
            val suggestion = suggestionsRepo.getById(suggestionId) ?: return


        }
    }
}
