package app.drift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.domain.model.AttentionPathNodeStats
import app.drift.ui.DriftViewModel
import app.drift.ui.theme.CardBackground
import app.drift.ui.theme.PastelBackgroundGradient
import app.drift.ui.theme.TextPrimary
import app.drift.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun AttentionPathsScreen(vm: DriftViewModel, modifier: Modifier = Modifier) {
    val graph = vm.attentionPathGraph.collectAsStateWithLifecycle().value
    val nodesById = graph.nodes.associateBy { it.node.id }
    val protectableNodes = graph.nodes
        .filter { it.node.supportedForBlocking && it.node.appId != null }
        .groupBy { it.node.appId!! }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PastelBackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Attention paths", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(
            "See which app screens tend to appear before a session overruns. These are patterns in your history, not proof of cause.",
            fontSize = 14.sp,
            color = TextSecondary,
        )

        if (graph.nodes.isEmpty()) {
            InfoCard("No paths observed yet", "Enable Drift's Accessibility service, then visit supported app screens. Drift records only coarse screen states and goal-match buckets.")
        } else {
            SectionTitle("Observed transitions")
            if (graph.edges.isEmpty()) {
                InfoCard("First screen recorded", "Drift has seen a supported app screen. Move between screens or scroll to add transitions.")
            } else {
                graph.edges.take(20).forEach { edge ->
                    val from = nodesById[edge.fromNodeId]?.node?.label ?: "Unknown screen"
                    val to = nodesById[edge.toNodeId]?.node?.label ?: "Unknown screen"
                    TransitionCard(
                        from = from,
                        to = to,
                        detail = "${edge.transitions} transition${if (edge.transitions == 1) "" else "s"} · ${edge.sessions} session${if (edge.sessions == 1) "" else "s"} · ${edge.relationBucket.displayName()}",
                    )
                }
            }

            SectionTitle("Observed short-form viewers")
            if (protectableNodes.isEmpty()) {
                InfoCard("No supported viewer observed yet", "Protection choices become available after Drift detects Reels, Shorts, or TikTok in the accessibility tree.")
            } else {
                protectableNodes.forEach { (appId, variants) ->
                    ProtectionCard(
                        variants = variants,
                        checked = appId in graph.protectedAppIds,
                        onCheckedChange = { vm.toggleProtectedPath(appId, it) },
                    )
                }
            }
            Text(
                "A node is marked as associated with distraction only after at least three completed sessions meet the evidence threshold. The global content-block switch and cover/full mode in Settings still apply.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(70.dp))
    }
}

@Composable
private fun ProtectionCard(variants: List<AttentionPathNodeStats>, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(variants.first().node.label, fontWeight = FontWeight.Bold, color = TextPrimary)
                variants.sortedBy { it.node.relationBucket }.forEach { stats ->
                    Text(
                        "${stats.node.relationBucket.displayName()}: ${stats.observedSessions} sessions observed; ${stats.overrunSessions}/${stats.completedSessions} completed overran; ${stats.evidenceLabel.lowercase(Locale.ROOT)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun TransitionCard(from: String, to: String, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    from,
                    modifier = Modifier.weight(1f).background(Color(0xFFF1ECFF), RoundedCornerShape(12.dp)).padding(10.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text("→", fontWeight = FontWeight.Bold, color = TextSecondary)
                Text(
                    to,
                    modifier = Modifier.weight(1f).background(Color(0xFFE9F7F2), RoundedCornerShape(12.dp)).padding(10.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
}

private fun String.displayName(): String = when (this) {
    "goal_related" -> "visible text matches your goal"
    "no_goal_match" -> "visible text did not match your goal"
    else -> "goal relation unavailable"
}
