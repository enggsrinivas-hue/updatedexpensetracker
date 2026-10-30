@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.expensetracker.sms.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.sms.data.ExpenseTransaction
import com.expensetracker.sms.data.TransactionType
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun hasNotificationAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

@Composable
fun MainScreen(vm: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showRange by remember { mutableStateOf(false) }

    // Re-check on resume: the user may turn notification access on or off in settings.
    var hasAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = hasNotificationAccess(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val openSettings = {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Expense Tracker") }) }) { pad ->
        val list = if (tab == 0) ui.debits else ui.credits
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            if (!hasAccess) item { AccessCard(openSettings) }
            item {
                FilterSection(
                    filter = filter,
                    cards = cards,
                    onPeriod = { if (it == Period.CUSTOM) showRange = true else vm.setPeriod(it) },
                    onCard = vm::setCard,
                    onQuery = vm::setQuery
                )
            }
            item { SummaryRow(ui.spent, ui.received) }
            stickyHeader {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Expenses") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Income") })
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet.\nNew bank SMS will show up automatically once notification access is on.",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(list, key = { it.id }) {
                    TransactionRow(it)
                    HorizontalDivider()
                }
            }
        }
    }

    if (showRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val s = state.selectedStartDateMillis!!
                        val e = state.selectedEndDateMillis ?: s
                        vm.setCustomRange(utcDayToLocalStart(s), utcDayToLocalEnd(e))
                        showRange = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } }
        ) { DateRangePicker(state = state, modifier = Modifier.height(500.dp)) }
    }
}

@Composable
private fun AccessCard(onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Text("Turn on notification access", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "To log your spending, this app reads the bank alert notifications from your Messages app. " +
                    "Only bank transaction messages are saved. Everything stays on this phone and nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onOpen) { Text("Open settings") }
        }
    }
}

@Composable
private fun FilterSection(
    filter: FilterState,
    cards: List<String>,
    onPeriod: (Period) -> Unit,
    onCard: (String?) -> Unit,
    onQuery: (String) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Period.entries.forEach { p ->
                val label = if (p == Period.CUSTOM && filter.period == Period.CUSTOM && filter.customFrom != null) {
                    rangeLabel(filter.customFrom, filter.customTo ?: filter.customFrom)
                } else p.label
                FilterChip(selected = filter.period == p, onClick = { onPeriod(p) }, label = { Text(label) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                AssistChip(
                    onClick = { menu = true },
                    label = { Text(filter.card ?: "All cards", maxLines = 1) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("All cards") }, onClick = { onCard(null); menu = false })
                    cards.forEach { c ->
                        DropdownMenuItem(text = { Text(c) }, onClick = { onCard(c); menu = false })
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onQuery(it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search merchant") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun SummaryRow(spent: Double, received: Double) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard("Total spent", spent, TxColors.debit, TxColors.debitContainer, Modifier.weight(1f))
        SummaryCard("Total received", received, TxColors.credit, TxColors.creditContainer, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(title: String, amount: Double, color: Color, container: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = color)
            Text(
                "₹" + money(amount, 0),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TransactionRow(t: ExpenseTransaction) {
    val debit = t.transactionType == TransactionType.DEBIT
    val color = if (debit) TxColors.debit else TxColors.credit
    val container = if (debit) TxColors.debitContainer else TxColors.creditContainer
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = if (debit) "Debit" else "Credit",
                tint = color,
                modifier = Modifier.rotate(if (debit) -45f else 135f)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.merchantOrSender, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.cardOrAccount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (debit) "−₹" else "+₹") + money(t.amount, 2),
                color = color,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(formatDate(t.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun money(v: Double, decimals: Int): String =
    NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(v)

private fun formatDate(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(ms).atZone(zone)
    val today = LocalDate.now(zone)
    val time = dt.format(DateTimeFormatter.ofPattern("h:mm a"))
    return when (dt.toLocalDate()) {
        today -> "Today, $time"
        today.minusDays(1) -> "Yesterday, $time"
        else -> dt.format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
    }
}

private fun rangeLabel(from: Long, to: Long): String {
    val f = DateTimeFormatter.ofPattern("d MMM")
    val z = ZoneId.systemDefault()
    return Instant.ofEpochMilli(from).atZone(z).format(f) + " – " + Instant.ofEpochMilli(to).atZone(z).format(f)
}
