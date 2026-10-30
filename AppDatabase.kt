package com.expensetracker.sms.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ExpenseTransaction::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
