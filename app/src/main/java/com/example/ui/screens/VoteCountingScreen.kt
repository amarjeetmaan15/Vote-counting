package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.entity.Candidate
import com.example.data.entity.Voter
import com.example.ui.VoteCountingUiState
import com.example.ui.components.WardFilterRow
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.VoteGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteCountingScreen(
    state: VoteCountingUiState,
    onBack: () -> Unit,
    onSelectWard: (Int?) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectCandidateForVoter: (voterId: Long, currentCandidateId: Long?, targetCandidateId: Long) -> Unit,
    onSetupCandidates: (List<String>) -> Unit,
    onResetVotes: () -> Unit,
    onNavigateToResults: () -> Unit,
    onDeleteVoter: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSetupCandidatesDialog by remember { mutableStateOf(false) }
    var candidateNamesInput by remember { mutableStateOf("") }
    var showResetVotesDialog by remember { mutableStateOf(false) }
    var voterToRecycle by remember { mutableStateOf<Voter?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.height(48.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Vote Counting (वोट काउंटिंग)",
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
                    IconButton(
                        onClick = {
                            val nonNotaNames = state.candidates.filter { !it.isNota }.joinToString("\n") { it.name }
                            candidateNamesInput = nonNotaNames
                            showSetupCandidatesDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "उम्मीदवार सेटअप",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onNavigateToResults) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "परिणाम / Results",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showResetVotesDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "वोट रीसेट",
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
                .testTag("vote_counting_screen")
        ) {
            // Common Ward Navigation Row (ALL, Ward 1, Ward 2...)
            WardFilterRow(
                availableWards = state.availableWards,
                selectedWard = state.selectedWard,
                onSelectWard = onSelectWard
            )

            // Vote Counting Summary Card
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
                            text = "कुल दर्ज वोट: ${state.totalPolled} / ${state.voters.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = "मतदान: ${if (state.voters.isNotEmpty()) (state.totalPolled * 100 / state.voters.size) else 0}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Candidates votes pill row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.candidates.forEach { candidate ->
                            val votes = state.candidateVoteCounts[candidate.id] ?: 0
                            val isNota = candidate.isNota
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isNota) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = null,
                                            tint = AmberTertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "${candidate.name}: ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isNota) AmberTertiary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$votes",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isNota) AmberTertiary else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("मतदाता का नाम, क्रमांक या EPIC खोजें...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "सर्च साफ करें")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 12.dp, vertical = 1.dp)
                    .testTag("search_counting_voter_field"),
                shape = RoundedCornerShape(12.dp)
            )

            // Voters List with Candidate Selection
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
                        .testTag("vote_counting_list"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(
                        items = state.voters,
                        key = { it.id }
                    ) { voter ->
                        VoterCountingCard(
                            voter = voter,
                            candidates = state.candidates,
                            onSelectCandidate = { candidateId ->
                                onSelectCandidateForVoter(voter.id, voter.votedCandidateId, candidateId)
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

    // Candidate Setup Dialog
    if (showSetupCandidatesDialog) {
        AlertDialog(
            onDismissRequest = { showSetupCandidatesDialog = false },
            title = { Text("उम्मीदवार सेटअप (Candidate Setup)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "सभी उम्मीदवारों के नाम एक बार दर्ज करें (प्रत्येक लाइन में एक नाम)। ये सभी वार्डों में उपलब्ध रहेंगे। NOTA स्वतः शामिल रहेगा:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = candidateNamesInput,
                        onValueChange = { candidateNamesInput = it },
                        label = { Text("उम्मीदवारों के नाम (प्रत्येक लाइन में एक)") },
                        placeholder = { Text("Candidate 1\nCandidate 2\nCandidate 3") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val names = candidateNamesInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                        if (names.isNotEmpty()) {
                            onSetupCandidates(names)
                            showSetupCandidatesDialog = false
                        }
                    }
                ) {
                    Text("सेव करें / OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupCandidatesDialog = false }) {
                    Text("रद्द करें")
                }
            }
        )
    }

    // Reset Votes Dialog
    if (showResetVotesDialog) {
        AlertDialog(
            onDismissRequest = { showResetVotesDialog = false },
            title = { Text("वोट काउंटिंग रीसेट करें?") },
            text = { Text("क्या आप सभी मतदाताओं के दर्ज वोट (उम्मीदवार व NOTA चयन) को हटाना चाहते हैं?") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetVotes()
                        showResetVotesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("हाँ, रीसेट करें", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetVotesDialog = false }) {
                    Text("रद्द करें")
                }
            }
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
fun VoterCountingCard(
    voter: Voter,
    candidates: List<Candidate>,
    onSelectCandidate: (Long) -> Unit,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("counting_voter_card_${voter.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(9.dp)
        ) {
            // Header: Serial, Ward, Name, Relation, EPIC
            // Compact metadata row: serial, EPIC, ward and delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (voter.epicNumber.isNotBlank()) "EPIC: ${voter.epicNumber}" else "EPIC: -",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "रीसायकल बिन में भेजें",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

            Spacer(modifier = Modifier.height(6.dp))

            // Candidate choices stay on one horizontally scrollable row
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                candidates.forEach { candidate ->
                    val isSelected = voter.votedCandidateId == candidate.id
                    val isNota = candidate.isNota

                    val bgColor = when {
                        isSelected -> VoteGreen
                        isNota -> AmberContainer.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }

                    val textColor = when {
                        isSelected -> Color.White
                        isNota -> AmberTertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) VoteGreen else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectCandidate(candidate.id) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("candidate_btn_${voter.id}_${candidate.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isNota) "NOTA" else candidate.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}
