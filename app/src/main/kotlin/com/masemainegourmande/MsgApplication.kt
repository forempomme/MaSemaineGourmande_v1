package com.masemainegourmande

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.masemainegourmande.data.db.AppDatabase
import com.masemainegourmande.data.repository.AppRepository
import com.masemainegourmande.importer.RecipeImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MsgApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Migration from v1 (no meals/pantry) to v2 */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `meals` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `weekKey` TEXT NOT NULL,
                    `recipeId` TEXT NOT NULL,
                    `persons` INTEGER NOT NULL,
                    `addedAt` INTEGER NOT NULL
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `pantry` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `checked` INTEGER NOT NULL DEFAULT 0,
                    `addedAt` INTEGER NOT NULL
                )
            """)
        }
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    val repository: AppRepository by lazy { AppRepository(database) }

    val recipeImporter: RecipeImporter by lazy { RecipeImporter() }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.seedCategoriesIfEmpty() }
    }
}
