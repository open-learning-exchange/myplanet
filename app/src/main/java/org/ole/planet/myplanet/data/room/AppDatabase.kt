package org.ole.planet.myplanet.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.ole.planet.myplanet.data.room.dao.DictionaryDao
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity

/**
 * Room database that is progressively replacing the legacy Realm store.
 *
 * Entities and DAOs are added here as each domain is migrated off Realm. Because the migration
 * uses a drop-and-resync strategy (data is re-pulled from the Planet/CouchDB server on first
 * launch), destructive schema migrations are acceptable and configured in the Hilt module.
 */
@Database(
    entities = [
        DictionaryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
}
