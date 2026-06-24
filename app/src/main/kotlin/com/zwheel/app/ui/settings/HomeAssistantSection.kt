package com.zwheel.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.service.HaPushResult
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily

@Composable
internal fun HomeAssistantSection(
    haUrl: String,
    haToken: String,
    haTestResult: HaPushResult?,
    onUrlChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current

    // Local draft state — decoupled from the async DataStore/EncryptedSharedPreferences
    // round-trip. Without this, every keystroke triggers an async write → re-read cycle
    // that races the cursor and makes the token field appear uneditable.
    var localUrl by remember { mutableStateOf("") }
    var localToken by remember { mutableStateOf("") }
    var urlInitialized by remember { mutableStateOf(false) }
    var tokenInitialized by remember { mutableStateOf(false) }
    if (!urlInitialized && haUrl.isNotEmpty()) { localUrl = haUrl; urlInitialized = true }
    if (!tokenInitialized && haToken.isNotEmpty()) { localToken = haToken; tokenInitialized = true }

    // Expand by default if already configured; otherwise collapsed until user opens it.
    // Independent of localUrl/localToken so toggling off doesn't destroy the draft values.
    var expanded by remember { mutableStateOf(haUrl.isNotBlank() && haToken.isNotBlank()) }
    var showToken by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionEyebrow("HOME ASSISTANT (OPTIONAL)")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Push battery % to HA",
                style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W600),
                color = c.textSecondary,
            )
            Switch(
                checked = expanded,
                onCheckedChange = { expanded = it },
                colors = settingsSwitchColors(),
            )
        }
        if (expanded) {
            OutlinedTextField(
                value = localUrl,
                onValueChange = { localUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://homeassistant.local:8123") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) onUrlChanged(localUrl) },
                singleLine = true,
                colors = darkTextFieldColors(),
            )
            OutlinedTextField(
                value = localToken,
                onValueChange = { localToken = it },
                label = { Text("Long-lived access token") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) onTokenChanged(localToken) },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    androidx.compose.material3.TextButton(onClick = { showToken = !showToken }) {
                        Text(if (showToken) "Hide" else "Show", color = c.textSecondary)
                    }
                },
                colors = darkTextFieldColors(),
            )
            if (localUrl.startsWith("http://")) {
                WarningCallout("Token sent over unencrypted HTTP. Use https:// if available.")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        // Flush drafts before testing so the VM has the current values
                        onUrlChanged(localUrl)
                        onTokenChanged(localToken)
                        onTestConnection()
                    },
                    enabled = localUrl.isNotBlank() && localToken.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.cardElevated,
                        contentColor = c.lime,
                        disabledContainerColor = c.cardElevated,
                        disabledContentColor = c.textDim,
                    ),
                ) {
                    Text("Test connection", fontFamily = SairaFamily, fontWeight = FontWeight.W600)
                }
                if (haTestResult != null) {
                    Text(
                        text = haTestResult.label(),
                        color = haTestResult.color(),
                        style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 10.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningCallout(text: String) {
    val c = LocalZWheelColors.current
    Surface(
        color = c.cardElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, c.rampDanger.copy(alpha = 0.4f)),
    ) {
        Text(
            text = text,
            color = c.rampDanger,
            style = TextStyle(fontFamily = SairaFamily, fontSize = 12.sp, fontWeight = FontWeight.W400),
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalZWheelColors.current.textPrimary,
    unfocusedTextColor = LocalZWheelColors.current.textPrimary,
    focusedBorderColor = LocalZWheelColors.current.lime,
    unfocusedBorderColor = LocalZWheelColors.current.buttonBorder,
    cursorColor = LocalZWheelColors.current.lime,
    focusedLabelColor = LocalZWheelColors.current.textSecondary,
    unfocusedLabelColor = LocalZWheelColors.current.textLabel,
    focusedPlaceholderColor = LocalZWheelColors.current.textDim,
    unfocusedPlaceholderColor = LocalZWheelColors.current.textDim,
)

@Composable
private fun HaPushResult.label(): String = when (this) {
    HaPushResult.Success -> "Connected"
    HaPushResult.AuthFailed -> "Auth failed"
    HaPushResult.Unreachable -> "Unreachable"
    HaPushResult.BadUrl -> "Bad URL"
}

@Composable
private fun HaPushResult.color() = when (this) {
    HaPushResult.Success -> LocalZWheelColors.current.rampGood
    HaPushResult.AuthFailed -> LocalZWheelColors.current.rampDanger
    HaPushResult.Unreachable -> LocalZWheelColors.current.rampCaution
    HaPushResult.BadUrl -> LocalZWheelColors.current.rampDanger
}
