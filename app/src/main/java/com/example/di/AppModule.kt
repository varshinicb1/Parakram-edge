package com.example.di

import android.content.Context
import com.example.data.MobileServerManager
import com.example.data.firebase.FirebaseManager
import com.example.data.local.AppDatabase
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideFirebaseManager(@ApplicationContext context: Context, db: AppDatabase): FirebaseManager {
        return FirebaseManager(context, db)
    }

    @Provides
    @Singleton
    fun provideMobileServerManager(@ApplicationContext context: Context): MobileServerManager {
        return MobileServerManager(context)
    }
}
