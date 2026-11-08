package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.VoteViewModel
import com.example.ui.screens.CheckVotersResultsScreen
import com.example.ui.screens.CheckVotersScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RecycleBinScreen
import com.example.ui.screens.VoteCountingResultsScreen
import com.example.ui.screens.VoteCountingScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VoteAppMain()
            }
        }
    }
}

@Composable
fun VoteAppMain(
    viewModel: VoteViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val allVoters by viewModel.allVoters.collectAsStateWithLifecycle()
    val distinctWards by viewModel.distinctWardNumbers.collectAsStateWithLifecycle()
    val checkState by viewModel.checkVotersState.collectAsStateWithLifecycle()
    val voteState by viewModel.voteCountingState.collectAsStateWithLifecycle()
    val resultsState by viewModel.resultsState.collectAsStateWithLifecycle()
    val deletedVoters by viewModel.deletedVoters.collectAsStateWithLifecycle()
    val deletedCount by viewModel.deletedCount.collectAsStateWithLifecycle()

    // BackHandler to return to HOME screen from any sub-screen
    if (currentScreen != AppScreen.HOME) {
        BackHandler {
            viewModel.currentScreen.value = AppScreen.HOME
        }
    }

    val isCheckVotersFlow = currentScreen == AppScreen.CHECK_VOTERS || currentScreen == AppScreen.CHECK_VOTERS_RESULTS
    val isVoteCountingFlow = currentScreen == AppScreen.VOTE_COUNTING || currentScreen == AppScreen.VOTE_COUNTING_RESULTS

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            when {
                // Check Voters Subsystem Bottom Bar
                isCheckVotersFlow -> {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.height(64.dp).testTag("check_voters_bottom_nav")
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.CHECK_VOTERS,
                            onClick = { viewModel.currentScreen.value = AppScreen.CHECK_VOTERS },
                            icon = {
                                Icon(imageVector = Icons.Default.People, contentDescription = "जाँच सूची", modifier = Modifier.size(18.dp))
                            },
                            label = {
                                Text(
                                    text = "मतदाता सूची (Check)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (currentScreen == AppScreen.CHECK_VOTERS) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_check_list")
                        )

                        NavigationBarItem(
                            selected = currentScreen == AppScreen.CHECK_VOTERS_RESULTS,
                            onClick = { viewModel.currentScreen.value = AppScreen.CHECK_VOTERS_RESULTS },
                            icon = {
                                Icon(imageVector = Icons.Default.Assessment, contentDescription = "जाँच परिणाम", modifier = Modifier.size(18.dp))
                            },
                            label = {
                                Text(
                                    text = "जाँच परिणाम (Results)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (currentScreen == AppScreen.CHECK_VOTERS_RESULTS) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_check_results")
                        )
                    }
                }

                // Vote Counting Subsystem Bottom Bar
                isVoteCountingFlow -> {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.height(64.dp).testTag("vote_counting_bottom_nav")
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.VOTE_COUNTING,
                            onClick = { viewModel.currentScreen.value = AppScreen.VOTE_COUNTING },
                            icon = {
                                Icon(imageVector = Icons.Default.HowToVote, contentDescription = "मतगणना", modifier = Modifier.size(18.dp))
                            },
                            label = {
                                Text(
                                    text = "मतगणना (Counting)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (currentScreen == AppScreen.VOTE_COUNTING) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_counting_list")
                        )

                        NavigationBarItem(
                            selected = currentScreen == AppScreen.VOTE_COUNTING_RESULTS,
                            onClick = { viewModel.currentScreen.value = AppScreen.VOTE_COUNTING_RESULTS },
                            icon = {
                                Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = "गणना परिणाम", modifier = Modifier.size(18.dp))
                            },
                            label = {
                                Text(
                                    text = "गणना परिणाम (Results)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (currentScreen == AppScreen.VOTE_COUNTING_RESULTS) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_counting_results")
                        )
                    }
                }

                // On Home screen: No bottom nav bar
                else -> {}
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.HOME -> {
                    HomeScreen(
                        voters = allVoters,
                        wardsCount = distinctWards.size,
                        deletedCount = deletedCount,
                        onNavigateToCheckVoters = {
                            if (allVoters.isNotEmpty()) {
                                viewModel.currentScreen.value = AppScreen.CHECK_VOTERS
                            } else {
                                Toast.makeText(context, "कृपया पहले Excel फाइल अपलोड करें", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onNavigateToVoteCounting = {
                            if (allVoters.isNotEmpty()) {
                                viewModel.currentScreen.value = AppScreen.VOTE_COUNTING
                            } else {
                                Toast.makeText(context, "कृपया पहले Excel अपलोड करें या नमूना डेटा लोड करें", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onNavigateToRecycleBin = {
                            viewModel.currentScreen.value = AppScreen.RECYCLE_BIN
                        },
                        onFileSelected = { uri ->
                            viewModel.loadExcelFromUri(context, uri) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        onMoveAllToRecycleBin = { viewModel.moveAllToRecycleBin() },
                        onClearAllData = { viewModel.clearAllVoters() }
                    )
                }

                // Check Voters: List Screen
                AppScreen.CHECK_VOTERS -> {
                    CheckVotersScreen(
                        state = checkState,
                        onBack = { viewModel.currentScreen.value = AppScreen.HOME },
                        onSelectWard = { wardNum -> viewModel.selectWardFilter(wardNum) },
                        onSearchChange = { q -> viewModel.setSearch(q) },
                        onToggleAttendance = { voterId, currentStatus, targetStatus ->
                            viewModel.toggleVoterAttendance(voterId, currentStatus, targetStatus)
                        },
                        onResetAttendance = { viewModel.resetAttendance() },
                        onNavigateToResults = { viewModel.currentScreen.value = AppScreen.CHECK_VOTERS_RESULTS },
                        onDeleteVoter = { voterId -> viewModel.softDeleteVoter(voterId) }
                    )
                }

                // Check Voters: Results Screen (Isolated attendance metrics only)
                AppScreen.CHECK_VOTERS_RESULTS -> {
                    CheckVotersResultsScreen(
                        state = resultsState,
                        allVoters = allVoters,
                        onBack = { viewModel.currentScreen.value = AppScreen.CHECK_VOTERS }
                    )
                }

                // Vote Counting: Counting Screen
                AppScreen.VOTE_COUNTING -> {
                    VoteCountingScreen(
                        state = voteState,
                        onBack = { viewModel.currentScreen.value = AppScreen.HOME },
                        onSelectWard = { wardNum -> viewModel.selectWardFilter(wardNum) },
                        onSearchChange = { q -> viewModel.setSearch(q) },
                        onSelectCandidateForVoter = { voterId, curCandId, targetCandId ->
                            viewModel.selectCandidateForVoter(voterId, curCandId, targetCandId)
                        },
                        onSetupCandidates = { names -> viewModel.setCandidateList(names) },
                        onResetVotes = { viewModel.resetVotes() },
                        onNavigateToResults = { viewModel.currentScreen.value = AppScreen.VOTE_COUNTING_RESULTS },
                        onDeleteVoter = { voterId -> viewModel.softDeleteVoter(voterId) }
                    )
                }

                // Vote Counting: Results Screen (Isolated candidate votes and winner only)
                AppScreen.VOTE_COUNTING_RESULTS -> {
                    VoteCountingResultsScreen(
                        state = resultsState,
                        allVoters = allVoters,
                        onBack = { viewModel.currentScreen.value = AppScreen.VOTE_COUNTING }
                    )
                }

                // Recycle Bin Screen
                AppScreen.RECYCLE_BIN -> {
                    RecycleBinScreen(
                        deletedVoters = deletedVoters,
                        onBack = { viewModel.currentScreen.value = AppScreen.HOME },
                        onRestoreVoter = { voterId -> viewModel.restoreVoter(voterId) },
                        onRestoreAll = { viewModel.restoreAllFromRecycleBin() },
                        onPermanentlyDeleteVoter = { voterId -> viewModel.permanentlyDeleteVoter(voterId) },
                        onEmptyRecycleBin = { viewModel.emptyRecycleBin() }
                    )
                }
            }
        }
    }
}
