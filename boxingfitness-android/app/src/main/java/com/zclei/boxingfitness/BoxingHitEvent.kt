package com.zclei.boxingfitness

data class BoxingHitEvent(
    val elapsedSeconds: Float = 0f,
    val intensity: Float = 1f,
    val similarity: Float = 1f,
)

sealed interface TrainingSessionUpdate {
    data class Countdown(val value: Int) : TrainingSessionUpdate
    data object StartCue : TrainingSessionUpdate
    data class Running(val count: Int, val remainingMillis: Long) : TrainingSessionUpdate
}
