package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagementScreen(
    onBack: () -> Unit,
    onUploadExcel: (Uri) -> Unit,
    onSaveBackup: (Uri) -> Unit,
    onLoadBackup: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("file_security", Context.MODE_PRIVATE) }
    var uploadedName by remember { mutableStateOf(prefs.getString("uploaded_name", null)) }
    var pinInput by remember { mutableStateOf("") }
    var showSetPin by remember { mutableStateOf(false) }
    var showVerifyPin by remember { mutableStateOf(false) }

    val excelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val name = copyExcelIntoApp(context, uri)
                uploadedName = name
                prefs.edit().putString("uploaded_name", name).apply()
                onUploadExcel(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "फाइल सेव नहीं हुई: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onSaveBackup(uri)
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onLoadBackup(uri)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("File | फाइल", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "वापस")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Text(" Excel फाइल", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = uploadedName?.let { "अपलोड की गई फाइल: $it" } ?: "अभी कोई Excel फाइल सेव नहीं है",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            excelPicker.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel", "text/csv", "text/comma-separated-values", "*/*"
                            ))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Upload Excel")
                        }
                        IconButton(
                            onClick = {
                                if (prefs.getString("pin_hash", null).isNullOrBlank()) {
                                    pinInput = ""
                                    showSetPin = true
                                } else {
                                    pinInput = ""
                                    showVerifyPin = true
                                }
                            },
                            enabled = uploadedName != null
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "PIN से फाइल हटाएं")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            if (prefs.getString("pin_hash", null).isNullOrBlank()) "फाइल डिलीट करने से पहले PIN सेट करें"
                            else "फाइल डिलीट करने के लिए PIN जरूरी है",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.weight(1f))
                        if (prefs.getString("pin_hash", null).isNullOrBlank()) {
                            TextButton(onClick = { pinInput = ""; showSetPin = true }) { Text("PIN सेट करें") }
                        } else {
                            Text("PIN सुरक्षा सक्रिय", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text(" Data Save | डेटा बैकअप", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("वोटर सूची, वार्ड, चेक किए गए स्टेटस और उम्मीदवारों के वोट सुरक्षित रखने के लिए बैकअप फोन के बाहर सेव करें।")
                    Button(onClick = {
                        backupSaver.launch("VoteCounting_Backup.json")
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Text("  Save Data (बैकअप सेव करें)")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Text(" Load Data | डेटा रिस्टोर", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("पहले Excel वोटर लिस्ट अपलोड करें, फिर पहले सेव की गई JSON बैकअप फाइल चुनें।")
                    Button(onClick = {
                        backupPicker.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Text("  Load Backup (डेटा रिस्टोर करें)")
                    }
                }
            }
        }
    }

    if (showSetPin) {
        AlertDialog(
            onDismissRequest = { showSetPin = false; pinInput = "" },
            title = { Text("PIN सेट करें") },
            text = {
                Column {
                    Text("4 से 8 अंकों का PIN बनाएं। इसे याद रखें; फाइल डिलीट करने के लिए यही PIN लगेगा।")
                    OutlinedTextField(
                        value = pinInput, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text("नया PIN") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length in 4..8) {
                        prefs.edit().putString("pin_hash", hashPin(pinInput)).apply()
                        pinInput = ""; showSetPin = false
                        Toast.makeText(context, "PIN सेट हो गया", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "PIN 4 से 8 अंकों का होना चाहिए", Toast.LENGTH_SHORT).show()
                }) { Text("सेव करें") }
            },
            dismissButton = { TextButton(onClick = { showSetPin = false; pinInput = "" }) { Text("रद्द") } }
        )
    }

    if (showVerifyPin) {
        AlertDialog(
            onDismissRequest = { showVerifyPin = false; pinInput = "" },
            title = { Text("PIN से पुष्टि करें") },
            text = {
                OutlinedTextField(
                    value = pinInput, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                    label = { Text("PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (hashPin(pinInput) == prefs.getString("pin_hash", null)) {
                        val fileName = prefs.getString("uploaded_name", null)
                        if (fileName != null) File(File(context.filesDir, "uploaded_excel"), fileName).delete()
                        prefs.edit().remove("uploaded_name").apply()
                        uploadedName = null
                        Toast.makeText(context, "अपलोड की गई फाइल हटा दी गई", Toast.LENGTH_SHORT).show()
                        showVerifyPin = false; pinInput = ""
                    } else Toast.makeText(context, "गलत PIN", Toast.LENGTH_SHORT).show()
                }) { Text("पुष्टि करें और हटाएं") }
            },
            dismissButton = { TextButton(onClick = { showVerifyPin = false; pinInput = "" }) { Text("रद्द") } }
        )
    }
}

private fun copyExcelIntoApp(context: Context, uri: Uri): String {
    var displayName = "Voter_List.xlsx"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) displayName = cursor.getString(0) ?: displayName
    }
    displayName = File(displayName).name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Voter_List.xlsx" }
    val folder = File(context.filesDir, "uploaded_excel").apply { mkdirs() }
    val target = File(folder, displayName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: throw IllegalStateException("चुनी गई फाइल पढ़ी नहीं जा सकी")
    return displayName
}

private fun hashPin(pin: String): String =
    MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
