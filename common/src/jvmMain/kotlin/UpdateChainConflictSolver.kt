package dev.inmo.plagubot.suggestionsbot.common

import dev.inmo.micro_utils.fsm.common.State

fun interface UpdateChainConflictSolver {
    suspend operator fun invoke(from: State, to: State, stateOnContext: State): Boolean?
}
