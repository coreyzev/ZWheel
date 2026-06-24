package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.SairaFamily

@Composable
fun ErrorCodeOverlay(
    errorCode: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(Color(0xFFB00020), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Text(
                text = "BOARD ERROR",
                style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 2.sp,
                ),
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "ERR $errorCode",
                style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.W700,
                ),
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss) {
                Text(
                    "Dismiss",
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = Color.White,
                )
            }
        }
    }
}
