package com.zwheel.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.app.ui.ZWheelTheme

data class OemBatteryAdvice(
    val title: String,
    val summary: String,
    val steps: List<String>,
    val secondarySteps: List<String> = emptyList(),
)

fun batteryAdviceForManufacturer(manufacturer: String): OemBatteryAdvice =
    if (manufacturer.equals("samsung", ignoreCase = true)) {
        samsungBatteryAdvice
    } else {
        genericBatteryAdvice
    }

val samsungBatteryAdvice = OemBatteryAdvice(
    title = "Samsung Battery Setup",
    summary = "Use Unrestricted battery mode so the ride service can keep BLE, GPS, and the watch feed alive with the screen off.",
    steps = listOf(
        "Open Settings > Apps > ZWheel > Battery.",
        "Select Unrestricted.",
        "Return to ZWheel and keep the app allowed to run in the background.",
    ),
    secondarySteps = listOf(
        "Open Device Care > Battery > Background usage limits.",
        "Confirm ZWheel is not in Sleeping apps or Deep sleeping apps.",
        "Add ZWheel to Never sleeping apps if Samsung still restricts it.",
    ),
)

val genericBatteryAdvice = OemBatteryAdvice(
    title = "Battery Setup",
    summary = "Allow ZWheel to keep running during a ride so the board connection and watch feed stay active.",
    steps = listOf(
        "Open system battery settings for ZWheel.",
        "Disable battery restrictions for this app.",
        "Return to ZWheel before starting a ride.",
    ),
)

@Composable
fun OemBatteryAdviceScreen(
    advice: OemBatteryAdvice,
    modifier: Modifier = Modifier,
    deviceLabel: String = "",
    onOpenSettings: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    val c = LocalZWheelColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.screenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (deviceLabel.isNotBlank()) {
            ManufacturerPill(deviceLabel = deviceLabel)
        }
        Text(
            text = advice.title,
            color = c.textPrimary,
            fontFamily = SairaFamily,
            fontSize = 24.sp,
            fontWeight = FontWeight.W800,
            letterSpacing = 0.sp,
        )
        Text(
            text = advice.summary,
            color = c.textMuted,
            fontFamily = SairaFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 21.sp,
            letterSpacing = 0.sp,
        )
        AdviceSection(title = "App Battery", steps = advice.steps, firstNumber = 1)
        if (advice.secondarySteps.isNotEmpty()) {
            AdviceSection(
                title = "Samsung Background Limits",
                steps = advice.secondarySteps,
                firstNumber = advice.steps.size + 1,
            )
        }
        ConfirmationCallout()
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.lime,
                contentColor = c.screenBg,
            ),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                text = "Open battery settings",
                fontFamily = SairaFamily,
                fontWeight = FontWeight.W800,
            )
        }
        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "I've done this",
                color = c.textMuted,
                fontFamily = SairaFamily,
            )
        }
    }
}

@Composable
private fun ManufacturerPill(deviceLabel: String) {
    val c = LocalZWheelColors.current

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = c.card,
        contentColor = c.textSecondary,
        border = BorderStroke(1.dp, c.buttonBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(c.lime, CircleShape),
            )
            Text(
                text = deviceLabel,
                color = c.textSecondary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

@Composable
private fun AdviceSection(
    title: String,
    steps: List<String>,
    firstNumber: Int,
) {
    val c = LocalZWheelColors.current

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = c.textLabel,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
        )
        steps.forEachIndexed { index, step ->
            AdviceStepCard(number = firstNumber + index, step = step)
        }
    }
}

@Composable
private fun AdviceStepCard(
    number: Int,
    step: String,
) {
    val c = LocalZWheelColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = c.card,
        contentColor = c.textSecondary,
        border = BorderStroke(1.dp, c.border),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(c.lime, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    color = c.screenBg,
                    fontFamily = SairaFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W800,
                )
            }
            Text(
                text = step,
                color = c.textSecondary,
                fontFamily = SairaFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun ConfirmationCallout() {
    val c = LocalZWheelColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = c.rampGood.copy(alpha = 0.08f),
        contentColor = c.textSecondary,
        border = BorderStroke(1.dp, c.borderGreen),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = c.rampGood,
            )
            Text(
                text = "Once set, rides keep recording with the phone pocketed and screen off.",
                color = c.textSecondary,
                fontFamily = SairaFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Preview
@Composable
private fun OemBatteryAdviceScreenPreview() {
    ZWheelTheme {
        OemBatteryAdviceScreen(
            advice = samsungBatteryAdvice,
            deviceLabel = "SAMSUNG DETECTED",
        )
    }
}
