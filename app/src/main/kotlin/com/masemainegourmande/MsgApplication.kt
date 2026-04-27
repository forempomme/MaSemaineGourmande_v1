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

    /** v1 → v2 : création tables meals + pantry */
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

    /** v2 → v3 : ajout cookTimeMinutes sur les recettes */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE recipes ADD COLUMN cookTimeMinutes INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v3 → v4 : ajout done sur les repas du planning */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE meals ADD COLUMN done INTEGER NOT NULL DEFAULT 0")
        }
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()   // filet de sécurité
            .build()
    }

    val repository: AppRepository by lazy { AppRepository(database) }

    val recipeImporter: RecipeImporter by lazy { RecipeImporter() }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.seedCategoriesIfEmpty() }
    }
}
