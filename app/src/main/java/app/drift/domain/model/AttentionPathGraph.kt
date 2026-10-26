package app.drift.domain.model

/** A fixed, privacy-safe description of a UI state exposed by an app. */
data class AttentionPathNode(
    val id: String,
    val label: String,
    val appId: String?,
    val relationBucket: String,
    val supportedForBlocking: Boolean,
)

/** A transition between two observed states, aggregated over local sessions. */
data class AttentionPathEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val relationBucket: String,
    val transitions: Int,
    val sessions: Int,
)

data class AttentionPathNodeStats(
    val node: AttentionPathNode,
    val observedSessions: Int,
    val completedSessions: Int,
    val overrunSessions: Int,
    val overrunRate: Float,
    val evidenceLabel: String,
)

data class AttentionPathGraph(
    val nodes: List<AttentionPathNodeStats> = emptyList(),
    val edges: List<AttentionPathEdge> = emptyList(),
    val protectedAppIds: Set<String> = emptySet(),
)

/** Bounded, aggregateable per-session evidence; it contains no accessible text. */
data class AttentionPathSessionEvidence(
    val sessionId: Long,
    val observedAtMs: Long,
    val nodeIds: Set<String>,
    val edgeKeys: Set<String>,
    val outcome: Outcome? = null,
)

data class AttentionPathEdgeKey(
    val fromNodeId: String,
    val toNodeId: String,
    val relationBucket: String,
) {
    fun encoded(): String = "$fromNodeId>$toNodeId#$relationBucket"
}
