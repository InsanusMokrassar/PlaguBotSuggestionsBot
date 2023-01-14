package dev.inmo.plagubot.suggestionsbot.status.user.models

import dev.inmo.plagubot.suggestionsbot.common.MessageInfo
import kotlinx.serialization.Serializable

@Serializable
data class StatusMessagesInfo(
    val admin: MessageInfo,
    val user: MessageInfo
)
