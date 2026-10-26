package app.drift.domain

/**
 * Estimates the expected time cost of an interruption from *valid historical
 * observations only*. When insufficient history exists, it returns an
 * [InsufficientData] state rather than fabricating a number.
 */
data class InterruptionObservation(
    val ledToDrift: Boolean,
    val timeAwayMinutes: Double,
)

sealed interface CostEstimate {
    data class Available(
        val expectedCostMinutes: Double,
        val driftProbability: Double,
        val sampleCount: Int,
    ) : CostEstimate

    data object InsufficientData : CostEstimate
}

class NotificationCostService(private val minSamples: Int = 3) {

    fun estimate(observations: List<InterruptionObservation>): CostEstimate {
        if (observations.size < minSamples) return CostEstimate.InsufficientData
        val driftCount = observations.count { it.ledToDrift }
        val driftProbability = driftCount.toDouble() / observations.size
        val driftEpisodes = observations.filter { it.ledToDrift }
        val avgAway = if (driftEpisodes.isEmpty()) 0.0
        else driftEpisodes.map { it.timeAwayMinutes }.average()
        return CostEstimate.Available(
            expectedCostMinutes = avgAway * driftProbability,
            driftProbability = driftProbability,
            sampleCount = observations.size,
        )
    }
}
