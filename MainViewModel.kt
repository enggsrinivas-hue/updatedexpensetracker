package com.expensetracker.sms.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.sms.data.ExpenseTransaction
import com.expensetracker.sms.data.TransactionRepository
import com.expensetracker.sms.data.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class Period(val label: String) { TODAY("Today"), WEEK("This week"), MONTH("This month"), CUSTOM("Custom") }

data class FilterState(
    val period: Period = Period.MONTH,
    val customFrom: Long? = null,
    val customTo: Long? = null,
    val card: String? = null,
    val query: String = ""
) {
    /** Start and end (epoch millis) of the selected period. */
    fun range(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun start(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        fun end(d: LocalDate) = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return when (period) {
            Period.TODAY -> start(today) to end(today)
            Period.WEEK -> start(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) to end(today)
            Period.MONTH -> start(today.withDayOfMonth(1)) to end(today.withDayOfMonth(today.lengthOfMonth()))
            Period.CUSTOM -> (customFrom ?: start(today.withDayOfMonth(1))) to (customTo ?: end(today))
        }
    }
}

data class UiState(
    val debits: List<ExpenseTransaction> = emptyList(),
    val credits: List<ExpenseTransaction> = emptyList(),
    val spent: Double = 0.0,
    val received: Double = 0.0
)

/** The date picker returns UTC midnight; convert to local start/end of that day. */
fun utcDayToLocalStart(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun utcDayToLocalEnd(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: TransactionRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(FilterState())
    val filter: StateFlow<FilterState> = _filter.asStateFlow()

    val cards: StateFlow<List<String>> = repo.cards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<UiState> = _filter.flatMapLatest { f ->
        val (from, to) = f.range()
        combine(
            repo.transactions(TransactionType.DEBIT, from, to, f.card, f.query),
            repo.transactions(TransactionType.CREDIT, from, to, f.card, f.query),
            repo.total(TransactionType.DEBIT, from, to, f.card),
            repo.total(TransactionType.CREDIT, from, to, f.card)
        ) { debits, credits, spent, received -> UiState(debits, credits, spent, received) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setPeriod(p: Period) = _filter.update { it.copy(period = p) }
    fun setCustomRange(from: Long, to: Long) =
        _filter.update { it.copy(period = Period.CUSTOM, customFrom = from, customTo = to) }
    fun setCard(card: String?) = _filter.update { it.copy(card = card) }
    fun setQuery(q: String) = _filter.update { it.copy(query = q) }

}
