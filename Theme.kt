package com.expensetracker.sms.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00513F),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFD8E8E4),
    onSecondaryContainer = Color(0xFF0B201B),
    background = Color(0xFFFEF7FF),
    surface = Color(0xFFFEF7FF)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7ED4BC),
    onPrimary = Color(0xFF00382C),
    secondaryContainer = Color(0xFF2F4A43),
    onSecondaryContainer = Color(0xFFD8E8E4)
)

/** Red = money out, green = money in. */
object TxColors {
    val debit: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF2B8B5) else Color(0xFFB3261D)
    val debitContainer: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF601410) else Color(0xFFFCE8E6)
    val credit: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8FD5A0) else Color(0xFF146C2E)
    val creditContainer: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0A3818) else Color(0xFFE3F4E8)
}

@Composable
fun ExpenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content
    )
}
