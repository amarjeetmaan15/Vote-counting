package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.entity.Voter
import com.example.ui.ResultsUiState
import com.example.ui.WardCheckResult
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.VoteGreen
import com.example.ui.theme.VoteRed
import com.example.util.ExcelExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckVotersResultsScreen(
    state: ResultsUiState,
    allVoters: List<Voter>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val summary = state.overallCheckSummary

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = "Check Voters परिणाम",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "मतदाता उपस्थिति एवं वार्ड-वार स्थिति",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "वापस / Back"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            try {
                                val distinctWards = allVoters.map { it.wardNumber }.distinct().sorted()
                                val file = ExcelExporter.generateCheckVotersReport(
                                    context = context,
                                    voters = allVoters,
                                    wards = distinctWards
                                )
                                ExcelExporter.shareExcelFile(context, file, "Check Voters Attendance Report")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VoteGreen),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_export_check_voters_results")
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Excel एक्सपोर्ट", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("check_voters_results_screen"),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall Stats Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "समग्र उपस्थिति सारांश (Overall Attendance)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Row 1: Total & Green
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatBox(
                                label = "कुल मतदाता (Total)",
                                count = summary.totalVoters,
                                pct = 100f,
                                bgColor = MaterialTheme.colorScheme.surfaceVariant,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            val greenPct = if (summary.totalVoters > 0) (summary.greenCount.toFloat() / summary.totalVoters * 100) else 0f
                            StatBox(
                                label = "Green (उपस्थित/वोटर)",
                                count = summary.greenCount,
                                pct = greenPct,
                                bgColor = VoteGreen.copy(alpha = 0.15f),
                                textColor = VoteGreen,
                                icon = Icons.Default.Check,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Row 2: Yellow, Red, Pending
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val yellowPct = if (summary.totalVoters > 0) (summary.yellowCount.toFloat() / summary.totalVoters * 100) else 0f
                            StatBox(
                                label = "Yellow (संशय)",
                                count = summary.yellowCount,
                                pct = yellowPct,
                                bgColor = AmberContainer,
                                textColor = AmberTertiary,
                                icon = Icons.Default.QuestionMark,
                                modifier = Modifier.weight(1f)
                            )
                            val redPct = if (summary.totalVoters > 0) (summary.redCount.toFloat() / summary.totalVoters * 100) else 0f
                            StatBox(
                                label = "Red (विपक्ष/मृत)",
                                count = summary.redCount,
                                pct = redPct,
                                bgColor = VoteRed.copy(alpha = 0.15f),
                                textColor = VoteRed,
                                icon = Icons.Default.Close,
                                modifier = Modifier.weight(1f)
                            )
                            val pendingPct = if (summary.totalVoters > 0) (summary.notCheckedCount.toFloat() / summary.totalVoters * 100) else 0f
                            StatBox(
                                label = "जाँच शेष (Pending)",
                                count = summary.notCheckedCount,
                                pct = pendingPct,
                                bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section Header: Ward-wise Attendance Table
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "वार्ड-वार उपस्थिति विवरण (Ward-wise Attendance)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Ward-wise cards
            if (state.wardCheckResults.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "कोई वार्ड डेटा उपलब्ध नहीं है",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.wardCheckResults, key = { it.wardNumber }) { wardResult ->
                    WardCheckResultCard(wardResult)
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    count: Int,
    pct: Float,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold
                )
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = "${String.format("%.1f", pct)}%",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun WardCheckResultCard(result: WardCheckResult) {
    val s = result.summary
    val greenPct = if (s.totalVoters > 0) (s.greenCount.toFloat() / s.totalVoters * 100) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ward ${result.wardNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(VoteGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "उपस्थिति: ${String.format("%.1f", greenPct)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = VoteGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Grid of counts in this ward
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("कुल वोटर्स", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${s.totalVoters}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Green (✔)", style = MaterialTheme.typography.labelSmall, color = VoteGreen)
                    Text("${s.greenCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = VoteGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Yellow (~)", style = MaterialTheme.typography.labelSmall, color = AmberTertiary)
                    Text("${s.yellowCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AmberTertiary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Red (✖)", style = MaterialTheme.typography.labelSmall, color = VoteRed)
                    Text("${s.redCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = VoteRed)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("शेष (Pending)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${s.notCheckedCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
