package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.micro_utils.fsm.common.State

fun interface StartChainConflictSolver {
    suspend operator fun invoke(exists: State, new: State): Boolean?
}
