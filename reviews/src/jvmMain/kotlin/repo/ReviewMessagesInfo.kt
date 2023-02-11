package dev.inmo.plagubot.suggestionsbot.reviews.repo

import dev.inmo.micro_utils.repos.KeyValuesRepo
import dev.inmo.plagubot.suggestionsbot.reviews.models.ReviewContentInfo
import dev.inmo.plagubot.suggestionsbot.suggestions.models.SuggestionId

interface ReviewMessagesInfo : KeyValuesRepo<SuggestionId, ReviewContentInfo>
