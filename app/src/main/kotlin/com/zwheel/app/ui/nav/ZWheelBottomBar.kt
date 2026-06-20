package com.zwheel.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily

sealed class TopLevelRoute(val route: String, val label: String) {
    object Ride : TopLevelRoute("ride", "Ride")
    object History : TopLevelRoute("history", "History")
    object Settings : TopLevelRoute("settings", "Settings")

    companion object {
        val all = listOf(Ride, History, Settings)
    }
}

@Composable
fun ZWheelBottomBar(
    currentRoute: String?,
    onSelect: (TopLevelRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current

    NavigationBar(
        modifier = modifier,
        containerColor = c.navBg,
        tonalElevation = 0.dp,
    ) {
        TopLevelRoute.all.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            TopLevelRoute.Ride -> Icons.Filled.Speed
                            TopLevelRoute.History -> Icons.Filled.Timeline
                            TopLevelRoute.Settings -> Icons.Filled.Tune
                        },
                        contentDescription = tab.label,
                        modifier = Modifier.padding(top = 10.dp, bottom = 15.dp),
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontFamily = SairaFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W600,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = c.lime,
                    selectedTextColor = c.lime,
                    unselectedIconColor = c.textLabel,
                    unselectedTextColor = c.textLabel,
                ),
                alwaysShowLabel = true,
            )
        }
    }
}
