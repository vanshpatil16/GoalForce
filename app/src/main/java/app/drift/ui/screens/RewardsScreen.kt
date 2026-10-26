package app.drift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.data.db.RedemptionOutcome
import app.drift.domain.RewardCatalog
import app.drift.domain.model.Achievement
import app.drift.domain.model.RewardItem
import app.drift.ui.DriftViewModel

@Composable
fun RewardsScreen(vm: DriftViewModel, modifier: Modifier = Modifier) {
    val progress = vm.progress.collectAsStateWithLifecycle().value
    val redemptions = vm.redemptions.collectAsStateWithLifecycle().value
    val achievements = vm.achievements.collectAsStateWithLifecycle().value
    val outcome = vm.redemptionOutcome.collectAsStateWithLifecycle().value
    val owned = redemptions.map { it.rewardId }.toSet()

    LaunchedEffect(outcome) {
        if (outcome != null) {
            kotlinx.coroutines.delay(2500)
            vm.clearRedemptionOutcome()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(app.drift.ui.theme.PastelBackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Rewards & Shop",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = app.drift.ui.theme.TextPrimary
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, androidx.compose.foundation.shape.RoundedCornerShape(22.dp)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Spendable coins", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = app.drift.ui.theme.TextSecondary)
                        Text("${progress?.spendableBalance ?: 0}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = app.drift.ui.theme.TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Lifetime XP", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = app.drift.ui.theme.TextSecondary)
                        Text("${progress?.lifetimeXp ?: 0}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = app.drift.ui.theme.AccentPurple)
                    }
                }
            }

            outcome?.let {
                Text(outcomeMessage(it), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = app.drift.ui.theme.AccentPurple)
            }

            Text("Marketplace", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = app.drift.ui.theme.TextPrimary)
            RewardCatalog.all.forEach { item ->
                RewardRow(
                    item = item,
                    owned = item.id in owned,
                    affordable = (progress?.spendableBalance ?: 0) >= item.xpCost,
                    onRedeem = { vm.redeemReward(item) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Badges & Unlocks", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = app.drift.ui.theme.TextPrimary)
            achievements.forEach { (achievement, unlocked) ->
                BadgeRow(achievement, unlocked)
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun RewardRow(item: RewardItem, owned: Boolean, affordable: Boolean, onRedeem: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = app.drift.ui.theme.TextPrimary)
                Text(item.description, fontSize = 12.sp, color = app.drift.ui.theme.TextSecondary)
                Text("${item.xpCost} coins", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = app.drift.ui.theme.AccentPurple)
            }
            Box(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (owned) Color(0xFFF3F1F8) else if (affordable) app.drift.ui.theme.AccentPurple else Color(0xFFE5E2F0))
                    .clickable(enabled = !owned && affordable) { onRedeem() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (owned) "Owned" else "Redeem",
                    color = if (owned) app.drift.ui.theme.TextSecondary else if (affordable) Color.White else app.drift.ui.theme.TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun BadgeRow(achievement: Achievement, unlocked: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dot = if (unlocked) app.drift.ui.theme.PriorityLowBg else Color(0xFFF3F1F8)
            Column(
                Modifier
                    .size(42.dp)
                    .background(dot, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (unlocked) "✓" else "🔒", fontSize = 16.sp)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (achievement.isSecret && !unlocked) "???" else achievement.title,
                    fontSize = 14.sp,
                    fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Medium,
                    color = app.drift.ui.theme.TextPrimary
                )
                Text(
                    if (achievement.isSecret && !unlocked) "Keep going to reveal this."
                    else achievement.description,
                    fontSize = 12.sp,
                    color = app.drift.ui.theme.TextSecondary,
                )
                Text("+${achievement.xpReward} XP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = app.drift.ui.theme.AccentPurple)
            }
        }
    }
}

private fun outcomeMessage(o: RedemptionOutcome): String = when (o) {
    RedemptionOutcome.Success -> "Redeemed! Enjoy your reward."
    RedemptionOutcome.InsufficientBalance -> "Not enough coins yet."
    RedemptionOutcome.AlreadyRedeemed -> "You already own this reward."
}
