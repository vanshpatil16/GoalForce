package app.drift.data

import android.content.Context
import app.drift.domain.AttentionPathAnalyzer
import app.drift.domain.model.AttentionPathEdgeKey
import app.drift.domain.model.AttentionPathGraph
import app.drift.domain.model.AttentionPathNode
import app.drift.domain.model.AttentionPathSessionEvidence
import app.drift.domain.model.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Stores a bounded local graph without retaining raw accessibility text. */
class AttentionPathStore(
    context: Context,
    private val analyzer: AttentionPathAnalyzer,
) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val state = readState()
    private val mutableGraph = MutableStateFlow(toGraph(state))

    val graph = mutableGraph.asStateFlow()

    suspend fun recordNodeObservation(node: AttentionPathNode, sessionId: Long?, atMs: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val graphState = readState()
            val safeNode = sanitizeNode(node) ?: return@withLock
            graphState.nodes[safeNode.id] = safeNode
            if (sessionId != null) {
                val record = graphState.sessions.getOrPut(sessionId) {
                    MutableSession(sessionId = sessionId, observedAtMs = atMs)
                }
                record.observedAtMs = atMs
                record.nodeIds += safeNode.id
            }
            trim(graphState, atMs)
            writeState(graphState)
            mutableGraph.value = toGraph(graphState)
        }
    }

    suspend fun recordTransition(
        from: AttentionPathNode,
        to: AttentionPathNode,
        sessionId: Long?,
        atMs: Long,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val graphState = readState()
            val safeFrom = sanitizeNode(from) ?: return@withLock
            val safeTo = sanitizeNode(to) ?: return@withLock
            graphState.nodes[safeFrom.id] = safeFrom
            graphState.nodes[safeTo.id] = safeTo
            val edgeKey = AttentionPathEdgeKey(safeFrom.id, safeTo.id, safeTo.relationBucket)
            graphState.edgeCounts[edgeKey] = (graphState.edgeCounts[edgeKey] ?: 0) + 1
            graphState.edgeTimes[edgeKey] = atMs

            if (sessionId != null) {
                val record = graphState.sessions.getOrPut(sessionId) {
                    MutableSession(sessionId = sessionId, observedAtMs = atMs)
                }
                record.observedAtMs = atMs
                record.nodeIds += safeFrom.id
                record.nodeIds += safeTo.id
                record.edgeKeys += edgeKey.encoded()
            }
            trim(graphState, atMs)
            writeState(graphState)
            mutableGraph.value = toGraph(graphState)
        }
    }

    suspend fun recordSessionOutcome(sessionId: Long, outcome: Outcome) = withContext(Dispatchers.IO) {
        if (outcome == Outcome.ABANDONED) return@withContext
        mutex.withLock {
            val graphState = readState()
            val now = System.currentTimeMillis()
            val record = graphState.sessions.getOrPut(sessionId) {
                MutableSession(sessionId = sessionId, observedAtMs = now)
            }
            record.outcome = outcome
            trim(graphState, now)
            writeState(graphState)
            mutableGraph.value = toGraph(graphState)
        }
    }

    private fun toGraph(graphState: StoredGraph): AttentionPathGraph = analyzer.build(
        nodes = graphState.nodes,
        edgeTransitionCounts = graphState.edgeCounts,
        sessions = graphState.sessions.values.map { record ->
            AttentionPathSessionEvidence(
                sessionId = record.sessionId,
                observedAtMs = record.observedAtMs,
                nodeIds = record.nodeIds,
                edgeKeys = record.edgeKeys,
                outcome = record.outcome,
            )
        },
    )

    private fun readState(): StoredGraph = runCatching {
        val payload = preferences.getString(KEY_GRAPH, null) ?: return StoredGraph()
        val json = JSONObject(payload)
        val nodes = linkedMapOf<String, AttentionPathNode>()
        val nodeRows = json.optJSONArray("nodes") ?: JSONArray()
        for (index in 0 until nodeRows.length()) {
            val row = nodeRows.optJSONObject(index) ?: continue
            val node = AttentionPathNode(
                id = row.optString("id"),
                label = row.optString("label"),
                appId = row.optString("appId").takeIf { it.isNotBlank() && it != JSONObject.NULL.toString() },
                relationBucket = row.optString("relation"),
                supportedForBlocking = row.optBoolean("supported"),
            )
            sanitizeNode(node)?.let { nodes[it.id] = it }
        }

        val edgeCounts = linkedMapOf<AttentionPathEdgeKey, Int>()
        val edgeTimes = linkedMapOf<AttentionPathEdgeKey, Long>()
        val edgeRows = json.optJSONArray("edges") ?: JSONArray()
        for (index in 0 until edgeRows.length()) {
            val row = edgeRows.optJSONObject(index) ?: continue
            val key = AttentionPathEdgeKey(
                fromNodeId = row.optString("from"),
                toNodeId = row.optString("to"),
                relationBucket = row.optString("relation"),
            )
            if (key.fromNodeId in nodes && key.toNodeId in nodes) {
                edgeCounts[key] = row.optInt("count").coerceAtLeast(0)
                edgeTimes[key] = row.optLong("at")
            }
        }

        val sessions = linkedMapOf<Long, MutableSession>()
        val sessionRows = json.optJSONArray("sessions") ?: JSONArray()
        for (index in 0 until sessionRows.length()) {
            val row = sessionRows.optJSONObject(index) ?: continue
            val id = row.optLong("id", Long.MIN_VALUE)
            if (id == Long.MIN_VALUE) continue
            val nodeIds = row.stringSet("nodes").filterTo(linkedSetOf()) { it in nodes }
            val edgeKeys = row.stringSet("edges").filterTo(linkedSetOf()) { encoded -> edgeCounts.keys.any { it.encoded() == encoded } }
            val outcomeName = row.optString("outcome").takeIf { it.isNotBlank() && it != "null" }
            val outcome = outcomeName?.let { runCatching { Outcome.valueOf(it) }.getOrNull() }
            sessions[id] = MutableSession(id, row.optLong("at"), nodeIds, edgeKeys, outcome)
        }
        StoredGraph(nodes, edgeCounts, edgeTimes, sessions)
    }.getOrElse { StoredGraph() }

    private fun writeState(graphState: StoredGraph) {
        val json = JSONObject()
        json.put("nodes", JSONArray().apply {
            graphState.nodes.values.forEach { node ->
                put(JSONObject().apply {
                    put("id", node.id)
                    put("label", node.label)
                    put("appId", node.appId ?: JSONObject.NULL)
                    put("relation", node.relationBucket)
                    put("supported", node.supportedForBlocking)
                })
            }
        })
        json.put("edges", JSONArray().apply {
            graphState.edgeCounts.forEach { (key, count) ->
                put(JSONObject().apply {
                    put("from", key.fromNodeId)
                    put("to", key.toNodeId)
                    put("relation", key.relationBucket)
                    put("count", count)
                    put("at", graphState.edgeTimes[key] ?: 0L)
                })
            }
        })
        json.put("sessions", JSONArray().apply {
            graphState.sessions.values.forEach { session ->
                put(JSONObject().apply {
                    put("id", session.sessionId)
                    put("at", session.observedAtMs)
                    put("nodes", JSONArray(session.nodeIds.toList()))
                    put("edges", JSONArray(session.edgeKeys.toList()))
                    put("outcome", session.outcome?.name ?: JSONObject.NULL)
                })
            }
        })
        preferences.edit().putString(KEY_GRAPH, json.toString()).apply()
    }

    private fun trim(graphState: StoredGraph, nowMs: Long) {
        val oldestAllowed = nowMs - RETENTION_MS
        graphState.sessions.entries.removeAll { it.value.observedAtMs < oldestAllowed }
        graphState.edgeTimes.entries.removeAll { it.value < oldestAllowed }
        graphState.edgeCounts.keys.retainAll(graphState.edgeTimes.keys)
        graphState.sessions.entries.sortedBy { it.value.observedAtMs }
            .take((graphState.sessions.size - MAX_SESSION_RECORDS).coerceAtLeast(0))
            .forEach { graphState.sessions.remove(it.key) }

        val usedNodeIds = graphState.sessions.values.flatMapTo(mutableSetOf()) { it.nodeIds }
        val orderedNodes = graphState.nodes.values.sortedByDescending { node ->
            graphState.sessions.count { node.id in it.value.nodeIds }
        }
        val retainedNodes = orderedNodes
            .sortedWith(compareByDescending<AttentionPathNode> { it.id in usedNodeIds }
                .thenByDescending { graphState.sessions.count { session -> it.id in session.value.nodeIds } })
            .take(MAX_NODES)
            .mapTo(mutableSetOf()) { it.id }
        graphState.nodes.keys.retainAll(retainedNodes)

        val orderedEdges = graphState.edgeCounts.keys.sortedWith(
            compareByDescending<AttentionPathEdgeKey> { graphState.edgeCounts[it] ?: 0 }
                .thenByDescending { graphState.edgeTimes[it] ?: 0L }
        )
        val retainedEdgeKeys = orderedEdges.take(MAX_EDGES).mapTo(mutableSetOf()) { it.encoded() }
        val keyByEncoded = graphState.edgeCounts.keys.associateBy { it.encoded() }
        val retained = retainedEdgeKeys.mapNotNullTo(mutableSetOf()) { keyByEncoded[it] }
            .filterTo(mutableSetOf()) { it.fromNodeId in graphState.nodes && it.toNodeId in graphState.nodes }
        graphState.edgeCounts.keys.retainAll(retained)
        graphState.edgeTimes.keys.retainAll(retained)
        graphState.sessions.values.forEach { it.edgeKeys.retainAll(retained.mapTo(mutableSetOf()) { key -> key.encoded() }) }
        graphState.sessions.values.forEach { it.nodeIds.retainAll(graphState.nodes.keys) }
    }

    private fun sanitizeNode(node: AttentionPathNode): AttentionPathNode? {
        if (node.id.length !in 1..96 || !SAFE_ID.matches(node.id)) return null
        if (node.label !in SAFE_LABELS) return null
        if (node.relationBucket !in RELATION_BUCKETS) return null
        if (node.appId != null && node.appId !in SUPPORTED_APP_IDS) return null
        if (node.supportedForBlocking && node.appId !in SUPPORTED_APP_IDS) return null
        return node
    }

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return buildSet { for (index in 0 until array.length()) add(array.optString(index)) }
    }

    private data class StoredGraph(
        val nodes: MutableMap<String, AttentionPathNode> = linkedMapOf(),
        val edgeCounts: MutableMap<AttentionPathEdgeKey, Int> = linkedMapOf(),
        val edgeTimes: MutableMap<AttentionPathEdgeKey, Long> = linkedMapOf(),
        val sessions: MutableMap<Long, MutableSession> = linkedMapOf(),
    )

    private data class MutableSession(
        val sessionId: Long,
        var observedAtMs: Long,
        val nodeIds: MutableSet<String> = linkedSetOf(),
        val edgeKeys: MutableSet<String> = linkedSetOf(),
        var outcome: Outcome? = null,
    )

    private companion object {
        const val FILE_NAME = "attention_path_graph"
        const val KEY_GRAPH = "graph_json"
        const val MAX_NODES = 32
        const val MAX_EDGES = 64
        const val MAX_SESSION_RECORDS = 500
        const val RETENTION_MS = 90L * 24L * 60L * 60L * 1000L
        val SAFE_ID = Regex("[A-Za-z0-9_.#/-]+")
        val SAFE_LABELS = setOf(
            "Instagram feed", "Instagram inbox", "Instagram search", "Instagram profile", "Instagram Reels",
            "YouTube feed", "YouTube Shorts", "TikTok", "TikTok feed", "TikTok video", "Unknown screen",
        )
        val RELATION_BUCKETS = setOf("goal_related", "no_goal_match", "unavailable")
        val SUPPORTED_APP_IDS = setOf("instagram_reels", "youtube_shorts", "tiktok")
    }
}
