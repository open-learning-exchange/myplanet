package org.ole.planet.myplanet.data

import io.mockk.mockk
import io.realm.DynamicRealm
import org.junit.Assert.assertThrows
import org.junit.Test

class RealmMigrationsTest {

    private val migrations = RealmMigrations()

    @Test
    fun `migrate throws for schema versions below the supported floor`() {
        val realm = mockk<DynamicRealm>(relaxed = true)

        for (version in 0L until RealmMigrations.MINIMUM_SUPPORTED_VERSION) {
            assertThrows(RealmMigrations.UnsupportedSchemaVersionException::class.java) {
                migrations.migrate(realm, version, 13)
            }
        }
    }

    @Test
    fun `migrate accepts the minimum supported version`() {
        val realm = mockk<DynamicRealm>(relaxed = true)

        migrations.migrate(realm, RealmMigrations.MINIMUM_SUPPORTED_VERSION, 13)
    }

    @Test
    fun `migrate accepts an already current version`() {
        val realm = mockk<DynamicRealm>(relaxed = true)

        migrations.migrate(realm, 13, 13)
    }
}
