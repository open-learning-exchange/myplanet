package org.ole.planet.myplanet.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.data.room.dao.UserChallengeActionsDao
import org.ole.planet.myplanet.data.room.dao.CertificationDao
import org.ole.planet.myplanet.data.room.dao.ChatDao
import org.ole.planet.myplanet.data.room.dao.CommunityDao
import org.ole.planet.myplanet.data.room.dao.DictionaryDao
import org.ole.planet.myplanet.data.room.dao.MyLifeDao
import org.ole.planet.myplanet.data.room.dao.PersonalDao
import org.ole.planet.myplanet.data.room.dao.RetryDao
import org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.model.RealmTeamNotification
import org.ole.planet.myplanet.model.RealmUserChallengeActions

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
        RealmMyLife::class,
        RealmMyPersonal::class,
        RealmRetryOperation::class,
        RealmCommunity::class,
        RealmApkLog::class,
        RealmUserChallengeActions::class,
        RealmTeamNotification::class,
        RealmCertification::class,
        RealmChatHistory::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun myLifeDao(): MyLifeDao
    abstract fun personalDao(): PersonalDao
    abstract fun retryDao(): RetryDao
    abstract fun communityDao(): CommunityDao
    abstract fun apkLogDao(): ApkLogDao
    abstract fun userChallengeActionsDao(): UserChallengeActionsDao
    abstract fun teamNotificationDao(): TeamNotificationDao
    abstract fun certificationDao(): CertificationDao
    abstract fun chatDao(): ChatDao
}
