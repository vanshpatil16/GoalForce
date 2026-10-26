package app.drift.domain

import app.drift.domain.model.RewardCategory
import app.drift.domain.model.RewardItem

/** The initial rewards catalogue. Costs are spendable reward currency, not lifetime XP. */
object RewardCatalog {
    val all: List<RewardItem> = listOf(
        RewardItem("theme_ocean", "Ocean Theme", "A calm blue dashboard theme.", RewardCategory.THEME, 150),
        RewardItem("theme_sunset", "Sunset Theme", "A warm sunset palette.", RewardCategory.THEME, 200),
        RewardItem("avatar_fox", "Fox Avatar", "A friendly fox avatar.", RewardCategory.AVATAR, 100),
        RewardItem("frame_gold", "Gold Frame", "A golden achievement frame.", RewardCategory.FRAME, 300),
        RewardItem("sound_rain", "Rain Focus Sound", "Gentle rain for focus.", RewardCategory.SOUND, 120),
        RewardItem("report_advanced", "Advanced Reports", "Deeper insight reports.", RewardCategory.REPORT, 250),
        RewardItem("challenge_entry", "Challenge Entry", "One group-challenge entry.", RewardCategory.CHALLENGE_ENTRY, 80),
    )

    fun byId(id: String): RewardItem? = all.firstOrNull { it.id == id }
}
