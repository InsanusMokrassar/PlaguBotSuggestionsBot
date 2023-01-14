package dev.inmo.plagubot.suggestionsbot.suggestons.models

import dev.inmo.plagubot.suggestionsbot.common.MessageInfo

operator fun MessageInfo.Companion.invoke(
    suggestionContentInfo: SuggestionContentInfo
) = MessageInfo(suggestionContentInfo.chatId, suggestionContentInfo.messageId, suggestionContentInfo.group)
