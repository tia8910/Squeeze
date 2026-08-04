package com.squeeze.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squeeze.app.data.crypto.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        MeasurementEntity::class,
        LoggedSetEntity::class,
        MesocycleEntity::class,
        ProfileEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SqueezeDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun mesocycleDao(): MesocycleDao
    abstract fun profileDao(): ProfileDao
}

/**
 * Builds the database on top of SQLCipher.
 *
 * Whole-file encryption is used rather than per-column encryption because the metadata is
 * as revealing as the values: knowing that someone measured their waist every morning for
 * six months says a lot even without the numbers. Encrypting the file leaves nothing
 * legible on disk if the device is compromised or the storage is recovered.
 */
object SqueezeDatabaseFactory {

    private const val DATABASE_NAME = "squeeze.db"

    /**
     * Adds the scan photograph reference.
     *
     * Written out rather than relying on destructive fallback. This app's entire value is an
     * accumulated history with no cloud copy to restore from, so a schema change that wipes
     * it is not a migration failure — it is the worst thing the app could do to someone.
     * Existing rows get NULL, which is exactly right: those scans never kept a photograph.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE measurements ADD COLUMN photoId TEXT")
        }
    }

    fun create(context: Context, keyManager: DatabaseKeyManager): SqueezeDatabase {
        System.loadLibrary("sqlcipher")

        val passphrase = keyManager.getOrCreatePassphrase()
        return try {
            Room.databaseBuilder(context, SqueezeDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2)
                // No fallbackToDestructiveMigration: silently wiping a user's measurement
                // history on a schema change would destroy the one thing this app exists to
                // accumulate, and with no cloud backup it would be unrecoverable. Every
                // schema change must ship a real migration.
                .build()
        } finally {
            // SQLCipher copies the passphrase into native memory during open, so the JVM
            // copy can be cleared. This shortens the window in which it sits in a heap dump.
            passphrase.fill(0)
        }
    }
}
