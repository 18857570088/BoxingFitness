package com.zclei.boxingfitness

enum class TrainingPlayMode {
    Classic30,
    Classic60,
    Burst10,
    Burst15,
    LevelChallenge,
    DailyChallenge,
}

data class TrainingCoachOutcome(
    val playMode: TrainingPlayMode,
    val goalMet: Boolean,
    val levelBefore: Int,
    val levelAfter: Int,
    val targetHits: Int?,
    val streak: Int,
    val xpGain: Int,
)

data class DailyAggregateReport(
    val totalHits: Int,
    val stressReduction: Float,
    val calmIncrease: Float,
    val totalCalories: Float,
    val totalFatBurnGrams: Float,
    val totalDurationMinutes: Float,
    val activeModules: Int,
) {
    fun hasData(): Boolean =
        totalHits > 0 ||
            stressReduction > 0f ||
            calmIncrease > 0f ||
            totalCalories > 0f ||
            totalFatBurnGrams > 0f ||
            totalDurationMinutes > 0f ||
            activeModules > 0
}

data class LocalLeaderboardEntry(
    val rank: Int,
    val title: String,
    val badge: String,
    val tertiaryBadge: String = "",
    val primaryValue: String,
    val secondaryValue: String,
)

data class TrainingGoalPresentation(
    val title: String,
    val body: String,
    val accentColor: String,
    val targetHits: Int? = null,
)

data class TrainingLevelDefinition(
    val level: Int,
    val targetHits: Int,
)

data class LocalSessionSummary(
    val dateKey: String,
    val endedAtMs: Long,
    val durationSeconds: Int,
    val hits: Int,
    val playMode: String,
)

data class ModuleTodayTotals(
    val hits: Int = 0,
    val stressReduction: Float = 0f,
    val calmIncrease: Float = 0f,
    val calories: Float = 0f,
    val fatBurnGrams: Float = 0f,
    val durationSeconds: Int = 0,
) {
    fun hasData(): Boolean =
        hits > 0 || stressReduction > 0f || calmIncrease > 0f || calories > 0f || fatBurnGrams > 0f || durationSeconds > 0
}

data class WorkoutMetricTotals(
    val hits: Int = 0,
    val durationSeconds: Int = 0,
    val calories: Float = 0f,
    val fatBurnGrams: Float = 0f,
) {
    operator fun plus(other: WorkoutMetricTotals): WorkoutMetricTotals =
        WorkoutMetricTotals(
            hits = hits + other.hits,
            durationSeconds = durationSeconds + other.durationSeconds,
            calories = calories + other.calories,
            fatBurnGrams = fatBurnGrams + other.fatBurnGrams,
        )

    fun hasData(): Boolean = hits > 0 || durationSeconds > 0 || calories > 0f || fatBurnGrams > 0f
}

data class WorkoutResultsDashboard(
    val today: WorkoutMetricTotals,
    val week: WorkoutMetricTotals,
    val month: WorkoutMetricTotals,
    val year: WorkoutMetricTotals,
    val sinceRegistered: WorkoutMetricTotals,
    val registeredAtEpochMs: Long,
) {
    fun hasData(): Boolean =
        today.hasData() || week.hasData() || month.hasData() || year.hasData() || sinceRegistered.hasData()
}

data class ModuleAggregateSnapshot(
    val title: String,
    val category: String,
    val totals: WorkoutMetricTotals,
)

class ReflexBallTrainingEngine {
    fun cancel() = Unit
}
