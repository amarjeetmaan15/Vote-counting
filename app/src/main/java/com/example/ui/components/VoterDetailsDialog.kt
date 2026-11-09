package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.entity.AttendanceStatus
import com.example.data.entity.Voter

@Composable
fun VoterDetailsDialog(
    voter: Voter,
    onDismiss: () -> Unit
) {
    val attendance = when (voter.attendanceStatus) {
        AttendanceStatus.GREEN -> "पक्ष में"
        AttendanceStatus.YELLOW -> "संशय"
        AttendanceStatus.RED -> "विपक्ष"
        AttendanceStatus.NONE -> "जाँच बाकी"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("वोटर विवरण / Voter Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VoterDetailRow("वोटर का नाम", voter.name)
                VoterDetailRow("पिता / पति का नाम", voter.relativeName)
                VoterDetailRow("क्रमांक / Serial No.", voter.serialNumber)
                VoterDetailRow("वार्ड नंबर", voter.wardNumber.toString())
                VoterDetailRow("EPIC / वोटर ID", voter.epicNumber.ifBlank { "उपलब्ध नहीं" })
                VoterDetailRow("हाजिरी स्थिति", attendance)
                VoterDetailRow("वोट दर्ज", if (voter.votedCandidateId != null) "हाँ" else "नहीं")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("बंद करें") }
        }
    )
}

@Composable
private fun VoterDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "उपलब्ध नहीं" },
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
