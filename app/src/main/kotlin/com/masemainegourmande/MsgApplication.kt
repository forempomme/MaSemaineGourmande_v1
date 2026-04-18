package com.masemainegourmande

import android.app.Application
import androidx.room.Room
import com.masemainegourmande.data.db.AppDatabase
import com.masemainegourmande.data.repository.AppRepository
import com.masemainegourmande.importer.RecipeImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MsgApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    val repository: AppRepository by lazy { AppRepository(database) }

    val recipeImporter: RecipeImporter by lazy { RecipeImporter() }

    override fun onCreate() {
        super.onCreate()
        // Seed default categories on first run
        appScope.launch { repository.seedCategoriesIfEmpty() }
    }
}
