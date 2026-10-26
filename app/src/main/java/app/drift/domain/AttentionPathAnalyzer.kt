package app.drift.domain

import app.drift.domain.model.AttentionPathEdge
import app.drift.domain.model.AttentionPathEdgeKey
import app.drift.domain.model.AttentionPathGraph
import app.drift.domain.model.AttentionPathNode
import app.drift.domain.model.AttentionPathNodeStats
import app.drift.domain.model.AttentionPathSessionEvidence
import app.drift.domain.model.Outcome

/** Computes cautious, session-level associations from the local path graph. */
class AttentionPathAnalyzer {

    fun build(
        nodes: Map<String, AttentionPathNode>,
        edgeTransitionCounts: Map<AttentionPathEdgeKey, Int>,
        sessions: List<AttentionPathSessionEvidence>,
        protectedAppIds: Set<String> = emptySet(),
    ): AttentionPathGraph {
        val completed = sessions.filter { it.outcome != null && it.outcome != Outcome.ABANDONED }
        val overallOverrunRate = if (completed.isEmpty()) 0.0 else {
            completed.count { it.outcome == Outcome.OVERRAN }.toDouble() / completed.size
        }

        val nodeStats = nodes.values.map { node ->
            val observed = sessions.filter { node.id in it.nodeIds }
            val completedForNode = observed.filter { it.outcome != null && it.outcome != Outcome.ABANDONED }
            val overruns = completedForNode.count { it.outcome == Outcome.OVERRAN }
            val rate = if (completedForNode.isEmpty()) 0.0 else overruns.toDouble() / completedForNode.size
            val label = when {
                completedForNode.size < MIN_COMPLETED_SESSIONS -> "Not enough sessions yet"
                rate >= MIN_OVERRUN_RATE && rate - overallOverrunRate + RATE_EPSILON >= MIN_LIFT -> "Associated with distraction"
                else -> "No elevated association"
            }
            AttentionPathNodeStats(
                node = node,
                observedSessions = observed.size,
                completedSessions = completedForNode.size,
                overrunSessions = overruns,
                overrunRate = rate.coerceIn(0.0, 1.0).toFloat(),
                evidenceLabel = label,
            )
        }

        val edgeStats = edgeTransitionCounts.map { (key, transitions) ->
            AttentionPathEdge(
                fromNodeId = key.fromNodeId,
                toNodeId = key.toNodeId,
                relationBucket = key.relationBucket,
                transitions = transitions.coerceAtLeast(0),
                sessions = sessions.count { key.encoded() in it.edgeKeys },
            )
        }

        return AttentionPathGraph(
            nodes = nodeStats.sortedWith(compareByDescending<AttentionPathNodeStats> { it.observedSessions }.thenBy { it.node.label }),
            edges = edgeStats.sortedWith(compareByDescending<AttentionPathEdge> { it.transitions }.thenBy { it.fromNodeId }),
            protectedAppIds = protectedAppIds,
        )
    }

    private companion object {
        const val MIN_COMPLETED_SESSIONS = 3
        const val MIN_OVERRUN_RATE = 0.5
        const val MIN_LIFT = 0.2
        const val RATE_EPSILON = 1e-9
    }
}
