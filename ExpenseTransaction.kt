package com.expensetracker.sms.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType { DEBIT, CREDIT }

enum class InstrumentType { DEBIT_CARD, CREDIT_CARD, UPI, ACCOUNT, OTHER }

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["smsHash"], unique = true), Index(value = ["timestamp"])]
)
data class ExpenseTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val transactionType: TransactionType,
    val amount: Double,
    val cardOrAccount: String,
    val instrumentType: InstrumentType,
    val merchantOrSender: String,
    val rawSms: String,
    /** SHA-256 of sender + message + day. Unique index makes duplicates impossible. */
    val smsHash: String
)
