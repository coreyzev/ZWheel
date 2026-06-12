package com.zwheel.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOpenSettings: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xffeeeeee))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = advice.title,
            color = Color(0xff111111),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
        Text(
            text = advice.summary,
            color = Color(0xff333333),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        AdviceSection(title = "App Battery", steps = advice.steps)
        if (advice.secondarySteps.isNotEmpty()) {
            AdviceSection(title = "Samsung Background Limits", steps = advice.secondarySteps)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onOpenSettings) {
                Text("Open Settings")
            }
            OutlinedButton(onClick = onDone) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun AdviceSection(
    title: String,
    steps: List<String>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        contentColor = Color(0xff111111),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
            steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. $step",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}

@Preview
@Composable
private fun OemBatteryAdviceScreenPreview() {
    ZWheelTheme {
        OemBatteryAdviceScreen(advice = samsungBatteryAdvice)
    }
}
