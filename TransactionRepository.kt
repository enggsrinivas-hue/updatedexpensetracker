package com.expensetracker.sms.data

import com.expensetracker.sms.parser.SmsParser
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao
) {
    fun transactions(type: TransactionType, from: Long, to: Long, card: String?, query: String): Flow<List<ExpenseTransaction>> =
        dao.observe(type, from, to, card, query.trim())

    fun total(type: TransactionType, from: Long, to: Long, card: String?): Flow<Double> =
        dao.total(type, from, to, card)

    fun cards(): Flow<List<String>> = dao.cards()

    /** Parses one message and stores it. Returns true only if a NEW transaction was saved. */
    suspend fun ingest(sender: String, body: String, timestamp: Long): Boolean {
        val parsed = SmsParser.parse(sender, body) ?: return false
        val tx = ExpenseTransaction(
            timestamp = timestamp,
            transactionType = parsed.type,
            amount = parsed.amount,
            cardOrAccount = parsed.cardOrAccount,
            instrumentType = parsed.instrument,
            merchantOrSender = parsed.merchant,
            rawSms = body,
            smsHash = hash(sender, body, timestamp)
        )
        return dao.insert(tx) != -1L
    }

    private fun hash(sender: String, body: String, timestamp: Long): String {
        val day = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val input = "${sender.lowercase()}|${body.trim()}|$day"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
