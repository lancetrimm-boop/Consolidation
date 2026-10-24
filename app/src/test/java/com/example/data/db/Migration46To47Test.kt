package com.example.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration46To47Test {

    private lateinit var context: Context
    private val testDbName = "test_migration_46_47.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dbFile = context.getDatabasePath(testDbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun testRoomMigration46To47_CreatesSavedMediaTableAndPreservesData() {
        val dbFile = context.getDatabasePath(testDbName)
        dbFile.parentFile?.mkdirs()

        // 1. Create SQLite DB with version 46 schema (media_items and existing tables)
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(46) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `media_items` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `mediaType` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = helperFactory.create(config)
        val db = openHelper.writableDatabase

        // 2. Insert existing application data
        db.execSQL("INSERT INTO media_items (id, title, mediaType) VALUES ('m1', 'Test Media', 'VIDEO')")

        // 3. Execute MIGRATION_46_47
        AuraDatabase.MIGRATION_46_47.migrate(db)

        // 4. Verify saved_media table exists and has correct schema
        val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='saved_media'")
        assertTrue("saved_media table must exist", tableCursor.moveToFirst())
        tableCursor.close()

        // 5. Verify existing data remains intact
        val mediaCursor = db.query("SELECT id, title FROM media_items")
        assertTrue(mediaCursor.moveToFirst())
        assertEquals("m1", mediaCursor.getString(0))
        assertEquals("Test Media", mediaCursor.getString(1))
        mediaCursor.close()

        // 6. Verify inserting into saved_media works
        db.execSQL("INSERT INTO saved_media (mediaId, savedAt) VALUES ('m1', 123456789)")
        val savedCursor = db.query("SELECT mediaId, savedAt FROM saved_media")
        assertTrue(savedCursor.moveToFirst())
        assertEquals("m1", savedCursor.getString(0))
        assertEquals(123456789L, savedCursor.getLong(1))
        savedCursor.close()

        db.close()
    }
}
