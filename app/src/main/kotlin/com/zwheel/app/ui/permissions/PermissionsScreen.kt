package com.zwheel.app.ui.permissions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.app.ui.ZWheelTheme

@Composable
fun PermissionsScreen(
    bleGranted: Boolean,
    blePermanentlyDenied: Boolean,
    locationGranted: Boolean,
    locationPermanentlyDenied: Boolean,
    onRequestBle: () -> Unit,
    onOpenBleSettings: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onSkipLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current

    Column(
        modifier
            .fillMaxSize()
            .background(c.screenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Permissions",
            fontFamily = SairaFamily,
            fontWeight = FontWeight.W800,
            fontSize = 26.sp,
            letterSpacing = (-0.4).sp,
            color = c.textPrimary,
        )
        Text(
            "ZWheel needs Nearby Devices access to find and connect to your board.",
            fontFamily = SairaFamily,
            fontWeight = FontWeight.W400,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = c.textMuted,
        )
        PermissionCard(
            label = "Nearby devices",
            description = "Required to scan for and connect to your Onewheel over Bluetooth.",
            granted = bleGranted,
            permanentlyDenied = blePermanentlyDenied,
            onRequest = onRequestBle,
            onOpenSettings = onOpenBleSettings,
        )
        PermissionCard(
            label = "Location",
            description = "Used for GPS ride tracking. You can skip this and connect without GPS.",
            granted = locationGranted,
            permanentlyDenied = locationPermanentlyDenied,
            onRequest = onRequestLocation,
            onOpenSettings = onOpenLocationSettings,
        )
        PermissionLegend()
        Spacer(Modifier.height(32.dp))
        TextButton(
            onClick = onSkipLocation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Skip for now · connect without GPS",
                fontFamily = SairaFamily,
                fontWeight = FontWeight.W400,
                fontSize = 13.sp,
                color = c.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    label: String,
    description: String,
    granted: Boolean,
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = LocalZWheelColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.card,
        border = BorderStroke(1.dp, when {
            granted -> c.borderGreen
            permanentlyDenied -> c.borderRed
            else -> c.border
        }),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    fontFamily = SairaFamily,
                    fontWeight = FontWeight.W700,
                    fontSize = 15.sp,
                    color = c.textPrimary,
                )
                Text(
                    if (granted) "● GRANTED" else "● DENIED",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    color = if (granted) c.rampGood else c.rampDanger,
                    letterSpacing = 1.5.sp,
                )
            }
            Text(
                description,
                fontFamily = SairaFamily,
                fontWeight = FontWeight.W400,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = c.textMuted,
            )
            if (!granted) {
                Button(
                    onClick = if (permanentlyDenied) onOpenSettings else onRequest,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.lime,
                        contentColor = c.screenBg,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (permanentlyDenied) "Open settings" else "Grant permission",
                        fontFamily = SairaFamily,
                        fontWeight = FontWeight.W700,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionLegend() {
    val c = LocalZWheelColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.legendCard,
        border = BorderStroke(1.dp, c.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "PERMISSION STATES HANDLED",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 1.5.sp,
                color = c.textDimmest,
            )
            LegendRow(dot = c.rampGood, label = "Granted — feature available")
            LegendRow(dot = c.rampCaution, label = "Not yet requested — will prompt")
            LegendRow(dot = c.rampDanger, label = "Denied — open Settings to fix")
        }
    }
}

@Composable
private fun LegendRow(dot: Color, label: String) {
    val c = LocalZWheelColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(
            label,
            fontFamily = SairaFamily,
            fontWeight = FontWeight.W400,
            fontSize = 13.sp,
            color = c.textSecondary,
        )
    }
}

@Preview
@Composable
private fun PermissionsScreenPreview() {
    ZWheelTheme {
        PermissionsScreen(
            bleGranted = true,
            blePermanentlyDenied = false,
            locationGranted = false,
            locationPermanentlyDenied = true,
            onRequestBle = {},
            onOpenBleSettings = {},
            onRequestLocation = {},
            onOpenLocationSettings = {},
            onSkipLocation = {},
        )
    }
}
