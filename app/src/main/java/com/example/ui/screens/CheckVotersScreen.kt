package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AttendanceStatus
import com.example.data.entity.Voter
import com.example.ui.CheckVotersUiState
import com.example.ui.components.PinGateDialog
import com.example.ui.components.WardFilterRow
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.VoteGreen
import com.example.ui.theme.VoteRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckVotersScreen(
    state: CheckVotersUiState,
    onBack: () -> Unit,
    onSelectWard: (Int?) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleAttendance: (voterId: Long, currentStatus: AttendanceStatus, targetStatus: AttendanceStatus) -> Unit,
    onResetAttendance: () -> Unit,
    onNavigateToResults: () -> Unit,
    onDeleteVoter: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Search text lives here so typing updates the field instantly (no cursor jumping);
    // the filtering in the ViewModel catches up in the background.
    var searchText by remember { mutableStateOf(state.searchQuery) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showResetPinGate by remember { mutableStateOf(false) }
    var voterToRecycle by remember { mutableStateOf<Voter?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.height(48.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Check Voters | मतदान चेकिंग",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
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
                    IconButton(onClick = onNavigateToResults) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "परिणाम / Results",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "रीसेट / Reset",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("check_voters_screen")
        ) {
            // Common Ward Navigation Row (ALL, Ward 1, Ward 2...)
            WardFilterRow(
                availableWards = state.availableWards,
                selectedWard = state.selectedWard,
                onSelectWard = onSelectWard
            )

            // Check Voters Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(9.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "कुल मतदाता: ${state.summary.totalVoters}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        val checkedTotal = state.summary.greenCount + state.summary.yellowCount + state.summary.redCount
                        Text(
                            text = "जाँच पूर्ण: $checkedTotal (${if (state.summary.totalVoters > 0) (checkedTotal * 100 / state.summary.totalVoters) else 0}%)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // 4 Counts Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Green Count Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = VoteGreen, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("${state.summary.greenCount}", fontWeight = FontWeight.Bold, color = VoteGreen)
                                }
                                Text("पक्ष में", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Yellow Count Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.QuestionMark, contentDescription = null, tint = AmberTertiary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("${state.summary.yellowCount}", fontWeight = FontWeight.Bold, color = AmberTertiary)
                                }
                                Text("संशय", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Red Count Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = VoteRed, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("${state.summary.redCount}", fontWeight = FontWeight.Bold, color = VoteRed)
                                }
                                Text("विपक्ष", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Not Checked Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${state.summary.notCheckedCount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("बाकी", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    onSearchChange(it)
                },
                placeholder = { Text("मतदाता का नाम, क्रमांक या EPIC खोजें...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = ""
                            onSearchChange("")
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "सर्च साफ करें")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 12.dp, vertical = 1.dp)
                    .testTag("search_voter_field"),
                shape = RoundedCornerShape(12.dp)
            )

            // Voters List
            if (state.voters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.searchQuery.isNotBlank()) "कोई मतदाता नहीं मिला" else "इस वार्ड में कोई मतदाता नहीं है",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("check_voters_list"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(
                        items = state.voters,
                        key = { it.id },
                        contentType = { "voter" }
                    ) { voter ->
                        VoterAttendanceCard(
                            voter = voter,
                            onToggleAttendance = { target ->
                                onToggleAttendance(voter.id, voter.attendanceStatus, target)
                            },
                            onDelete = { voterToRecycle = voter }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    // Reset Attendance Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("हाजिरी / रुझान रीसेट करें?") },
            text = { Text("क्या आप सभी मतदाताओं की उपस्थिति स्थिति (Green/Yellow/Red) को हटाना चाहते हैं?") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        showResetPinGate = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("हाँ, रीसेट करें", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("रद्द करें")
                }
            }
        )
    }

    // PIN check before the attendance reset actually runs
    if (showResetPinGate) {
        PinGateDialog(
            actionLabel = "पुष्टि करें और रीसेट करें",
            onConfirmed = {
                onResetAttendance()
                showResetPinGate = false
            },
            onDismiss = { showResetPinGate = false }
        )
    }

    // Send to Recycle Bin Dialog
    if (voterToRecycle != null) {
        val target = voterToRecycle!!
        AlertDialog(
            onDismissRequest = { voterToRecycle = null },
            title = { Text("रीसायकल बिन में भेजें?") },
            text = { Text("क्या आप \"${target.name}\" (क्र. ${target.serialNumber}) को रीसायकल बिन में भेजना चाहते हैं? आप इसे होम स्क्रीन के रीसायकल बिन से कभी भी वापस ला सकते हैं।") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVoter(target.id)
                        voterToRecycle = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("रीसायकल बिन में भेजें", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { voterToRecycle = null }) {
                    Text("रद्द करें")
                }
            }
        )
    }
}

@Composable
fun VoterAttendanceCard(
    voter: Voter,
    onToggleAttendance: (AttendanceStatus) -> Unit,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voter_card_${voter.id}"),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Voter Information Left Side
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "क्र. ${voter.serialNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(5.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Ward ${voter.wardNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "रीसायकल बिन में भेजें",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = if (voter.epicNumber.isNotBlank()) "EPIC: ${voter.epicNumber}" else "EPIC: -",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Visible
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = voter.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (voter.relativeName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${voter.relativeName}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Attendance Action Buttons: Green (✔), Yellow (~), Red (✖)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Green Button (उम्मीदवार का वोट)
                val isGreen = voter.attendanceStatus == AttendanceStatus.GREEN
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isGreen) VoteGreen else VoteGreen.copy(alpha = 0.12f))
                        .border(
                            width = if (isGreen) 2.dp else 1.dp,
                            color = if (isGreen) VoteGreen else VoteGreen.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onToggleAttendance(AttendanceStatus.GREEN) }
                        .testTag("btn_green_${voter.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Green Tick (पक्ष में)",
                        tint = if (isGreen) Color.White else VoteGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Yellow Button (संशय / अनिश्चित)
                val isYellow = voter.attendanceStatus == AttendanceStatus.YELLOW
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isYellow) AmberTertiary else AmberTertiary.copy(alpha = 0.12f))
                        .border(
                            width = if (isYellow) 2.dp else 1.dp,
                            color = if (isYellow) AmberTertiary else AmberTertiary.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onToggleAttendance(AttendanceStatus.YELLOW) }
                        .testTag("btn_yellow_${voter.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QuestionMark,
                        contentDescription = "Yellow Tick (संशय)",
                        tint = if (isYellow) Color.White else AmberTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Red Button (विपक्षी वोट)
                val isRed = voter.attendanceStatus == AttendanceStatus.RED
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isRed) VoteRed else VoteRed.copy(alpha = 0.12f))
                        .border(
                            width = if (isRed) 2.dp else 1.dp,
                            color = if (isRed) VoteRed else VoteRed.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onToggleAttendance(AttendanceStatus.RED) }
                        .testTag("btn_red_${voter.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Red Cross (विपक्ष में)",
                        tint = if (isRed) Color.White else VoteRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
