package app.drift.vision

/** Lightweight expression estimates from MediaPipe face blendshapes, not an emotion diagnosis. */
object FaceExpression {
    fun estimate(scores: Map<String, Float>): String {
        fun score(name: String) = scores[name] ?: 0f
        val smile = maxOf(score("mouthSmileLeft"), score("mouthSmileRight"))
        val frown = maxOf(score("mouthFrownLeft"), score("mouthFrownRight"))
        val browDown = maxOf(score("browDownLeft"), score("browDownRight"))
        val eyesWide = maxOf(score("eyeWideLeft"), score("eyeWideRight"))
        val jawOpen = score("jawOpen")
        val innerBrowUp = score("browInnerUp")
        return when {
            smile >= 0.45f -> "Happy"
            eyesWide >= 0.48f && jawOpen >= 0.35f -> "Surprised"
            browDown >= 0.45f && frown >= 0.25f -> "Angry"
            frown >= 0.35f && innerBrowUp >= 0.30f -> "Sad"
            else -> "Neutral"
        }
    }
}
