package app.drift.domain.model

/** What kind of user behaviour an event records. */
enum class ForegroundEventKind { APP_OPEN, CONTENT_BLOCKED }

/**
 * How much we trust an event.
 *  - OBSERVED: directly seen by the accessibility service.
 *  - INFERRED: derived from a gap or reconstructed after an interruption.
 *  - UNKNOWN: presence is known but the precise outcome is not.
 */
enum class EventConfidence { OBSERVED, INFERRED, UNKNOWN }

/**
 * A point-in-time behavioural event. Kept deliberately fine-grained so the
 * assistant can surface "easily missed" signals like "Instagram opened 4 times
 * in a minute" — which aggregate sessions can't express.
 *
 * [sessionId] ties the event to the focus session it occurred during (null when
 * outside a session). [elapsedRealtimeMs] is the monotonic clock at capture
 * (for duration math that is robust to wall-clock changes); [at] is the
 * wall-clock timestamp for display and correlation.
 */
data class ForegroundEvent(
    val id: Long = 0,
    val sessionId: Long? = null,
    val packageName: String,
    val label: String,
    val kind: ForegroundEventKind,
    val at: Long,
    val elapsedRealtimeMs: Long? = null,
    val confidence: EventConfidence = EventConfidence.OBSERVED,
)
