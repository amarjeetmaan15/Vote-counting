package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.security.MessageDigest

/**
 * The single app PIN. Same storage as the File section ("file_security" / "pin_hash"),
 * so one PIN protects both file deletion and the reset buttons.
 */
object PinStore {
    private const val PREFS = "file_security"
    private const val KEY = "pin_hash"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }

    fun hasPin(context: Context): Boolean = !prefs(context).getString(KEY, null).isNullOrBlank()

    fun save(context: Context, pin: String) {
        prefs(context).edit().putString(KEY, hash(pin)).apply()
    }

    fun verify(context: Context, pin: String): Boolean = hash(pin) == prefs(context).getString(KEY, null)
}

/**
 * Asks for the app PIN before a destructive action.
 * If no PIN has been set yet, it first asks the user to create one (4-8 digits),
 * then asks for it again to confirm the action.
 */
@Composable
fun PinGateDialog(
    actionLabel: String,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var needSet by remember { mutableStateOf(!PinStore.hasPin(context)) }
    var pinInput by remember { mutableStateOf("") }

    val pinField: @Composable (String) -> Unit = { label ->
        OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
    }

    if (needSet) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("PIN सेट करें") },
            text = {
                Column {
                    Text("रीसेट से पहले PIN ज़रूरी है। 4 से 8 अंकों का PIN बनाएं। यही PIN File सेक्शन में भी लगेगा। इसे याद रखें।")
                    pinField("नया PIN")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length in 4..8) {
                        PinStore.save(context, pinInput)
                        pinInput = ""
                        needSet = false
                        Toast.makeText(context, "PIN सेट हो गया। अब PIN डालकर पुष्टि करें", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "PIN 4 से 8 अंकों का होना चाहिए", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("सेव करें") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("रद्द") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("PIN से पुष्टि करें") },
            text = { pinField("PIN") },
            confirmButton = {
                TextButton(onClick = {
                    if (PinStore.verify(context, pinInput)) {
                        pinInput = ""
                        onConfirmed()
                    } else {
                        Toast.makeText(context, "गलत PIN", Toast.LENGTH_SHORT).show()
                    }
                }) { Text(actionLabel) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("रद्द") } }
        )
    }
}
