package com.expensetracker.sms.di

import android.content.Context
import androidx.room.Room
import com.expensetracker.sms.data.AppDatabase
import com.expensetracker.sms.data.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "expenses.db").build()

    @Provides
    fun provideDao(db: AppDatabase): TransactionDao = db.transactionDao()
}
