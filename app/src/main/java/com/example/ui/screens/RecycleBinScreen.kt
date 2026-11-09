package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.entity.Voter
import com.example.ui.components.VoterDetailsDialog
import com.example.ui.theme.VoteGreen
import com.example.ui.theme.VoteRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    deletedVoters: List<Voter>,
    onBack: () -> Unit,
    onRestoreVoter: (Long) -> Unit,
    onRestoreAll: () -> Unit,
    onPermanentlyDeleteVoter: (Long) -> Unit,
    onEmptyRecycleBin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEmptyBinDialog by remember { mutableStateOf(false) }
    var voterToDeletePermanently by remember { mutableStateOf<Voter?>(null) }
    var selectedVoter by remember { mutableStateOf<Voter?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = "रीसायकल बिन (Recycle Bin)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${deletedVoters.size} हटाए गए मतदाता",
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
                    if (deletedVoters.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onRestoreAll()
                                Toast.makeText(context, "सभी मतदाता सफलतापूर्वक पुनर्स्थापित कर दिए गए", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = "सभी वापस लाएं",
                                tint = VoteGreen
                            )
                        }

                        IconButton(
                            onClick = { showEmptyBinDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "बिन खाली करें",
                                tint = VoteRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (deletedVoters.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .testTag("recycle_bin_empty_state"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "रीसायकल बिन खाली है",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "यहाँ कोई हटाया गया मतदाता रिकॉर्ड नहीं है। यदि आप किसी मतदाता को हटाते हैं तो वह यहाँ सुरक्षित रहता है ताकि आप उसे वापस ला सकें।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("recycle_bin_list"),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info & Action Header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "हटाए गए मतदाताओं की सूची",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "आप किसी भी मतदाता को वापस सक्रिय सूची में रिस्टोर कर सकते हैं या स्थायी रूप से मिटा सकते हैं।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onRestoreAll()
                                        Toast.makeText(context, "सभी मतदाता सफलतापूर्वक पुनर्स्थापित कर दिए गए", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VoteGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("सब वापस लाएं", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showEmptyBinDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VoteRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("बिन खाली करें", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Deleted Voters List
                items(deletedVoters, key = { it.id }) { voter ->
                    DeletedVoterCard(
                        voter = voter,
                        onRestore = {
                            onRestoreVoter(voter.id)
                            Toast.makeText(context, "${voter.name} को वापस सक्रिय सूची में जोड़ दिया गया", Toast.LENGTH_SHORT).show()
                        },
                        onDeletePermanently = {
                            voterToDeletePermanently = voter
                        },
                        onNameClick = { selectedVoter = voter }
                    )
                }
            }
        }
    }

    selectedVoter?.let { voter ->
        VoterDetailsDialog(voter = voter, onDismiss = { selectedVoter = null })
    }

    // Confirmation dialog: Empty entire Recycle Bin
    if (showEmptyBinDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyBinDialog = false },
            title = { Text("रीसायकल बिन खाली करें?") },
            text = { Text("क्या आप रीसायकल बिन के सभी ${deletedVoters.size} मतदाताओं को हमेशा के लिए मिटाना चाहते हैं? यह क्रिया वापस नहीं ली जा सकेगी।") },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyRecycleBin()
                        showEmptyBinDialog = false
                        Toast.makeText(context, "रीसायकल बिन पूरी तरह खाली कर दिया गया", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoteRed)
                ) {
                    Text("हाँ, स्थायी हटाएं", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyBinDialog = false }) {
                    Text("रद्द करें")
                }
            }
        )
    }

    // Confirmation dialog: Permanently delete single voter
    if (voterToDeletePermanently != null) {
        val targetVoter = voterToDeletePermanently!!
        AlertDialog(
            onDismissRequest = { voterToDeletePermanently = null },
            title = { Text("स्थायी रूप से हटाएं?") },
            text = { Text("क्या आप मतदाता \"${targetVoter.name}\" (Sr. No. ${targetVoter.serialNumber}) को डेटाबेस से स्थायी रूप से मिटाना चाहते हैं?") },
            confirmButton = {
                Button(
                    onClick = {
                        onPermanentlyDeleteVoter(targetVoter.id)
                        voterToDeletePermanently = null
                        Toast.makeText(context, "${targetVoter.name} स्थायी रूप से हटा दिया गया", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoteRed)
                ) {
                    Text("हाँ, हटाएं", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { voterToDeletePermanently = null }) {
                    Text("रद्द करें")
                }
            }
        )
    }
}

@Composable
private fun DeletedVoterCard(
    voter: Voter,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    onNameClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = voter.serialNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = voter.name,
                            modifier = Modifier.clickable(onClick = onNameClick),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (voter.relativeName.isNotBlank()) {
                            Text(
                                text = "पिता/पति: ${voter.relativeName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Ward ${voter.wardNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (voter.epicNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "EPIC: ${voter.epicNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons: Restore and Delete Permanently
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDeletePermanently,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VoteRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("स्थायी हटाएं", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = VoteGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("वापस लाएं (Restore)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
