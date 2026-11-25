package com.alananasss.kittytune.ui.pet

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Something the listener just did that the cat notices. */
sealed interface PetEvent {
    data object Like : PetEvent
    data object Unlike : PetEvent
    data object Dislike : PetEvent
    /** A manual skip; what was skipped, how long it played and whether it was a favourite, for the "go back?" hint. */
    data class Skip(val trackId: Long?, val playedMs: Long, val favourite: Boolean) : PetEvent
    data object Previous : PetEvent
    data class Repeat(val on: Boolean) : PetEvent
}

/** The player tells the cat; the cat on the bar listens. Fire and forget, never blocks the player. */
object PetEvents {
    private val flow = MutableSharedFlow<PetEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PetEvent> = flow

    fun emit(event: PetEvent) {
        flow.tryEmit(event)
    }
}
