package dev.inmo.plagubot.suggestionsbot.suggestions.models

import dev.inmo.plagubot.suggestionsbot.common.MessageInfo

operator fun MessageInfo.Companion.invoke(
    suggestionContentInfo: SuggestionContentInfo
) = MessageInfo(
    suggestionContentInfo.messageMetaInfo.chatId,
    suggestionContentInfo.messageMetaInfo.messageId,
    suggestionContentInfo.messageMetaInfo.group
)
