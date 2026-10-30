package com.expensetracker.sms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /** Returns -1 when the row already exists (duplicate hash). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: ExpenseTransaction): Long

    @Query(
        """SELECT * FROM transactions
           WHERE transactionType = :type
             AND timestamp BETWEEN :from AND :to
             AND (:card IS NULL OR cardOrAccount = :card)
             AND (:query = '' OR merchantOrSender LIKE '%' || :query || '%')
           ORDER BY timestamp DESC"""
    )
    fun observe(type: TransactionType, from: Long, to: Long, card: String?, query: String): Flow<List<ExpenseTransaction>>

    @Query(
        """SELECT COALESCE(SUM(amount), 0) FROM transactions
           WHERE transactionType = :type
             AND timestamp BETWEEN :from AND :to
             AND (:card IS NULL OR cardOrAccount = :card)"""
    )
    fun total(type: TransactionType, from: Long, to: Long, card: String?): Flow<Double>

    @Query("SELECT DISTINCT cardOrAccount FROM transactions ORDER BY cardOrAccount")
    fun cards(): Flow<List<String>>
}
