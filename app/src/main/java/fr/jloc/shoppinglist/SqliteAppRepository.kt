package fr.jloc.shoppinglist

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.getStringOrNull
import androidx.core.database.sqlite.transaction
import fr.jloc.shoppinglist.business.AppRepository
import fr.jloc.shoppinglist.business.PadSyncParams
import fr.jloc.shoppinglist.business.Pad
import fr.jloc.shoppinglist.business.PadItem
import fr.jloc.shoppinglist.business.PadItemContent
import fr.jloc.shoppinglist.business.sync.PadKey
import kotlin.collections.iterator
import kotlin.collections.set

/**
 * Implementation of [fr.jloc.shoppinglist.business.AppRepository] based on SQLite. A single instance of this class should be
 * created at the start of the application, and then kept around for all uses.
 */
class SqliteAppRepository private constructor(private val dbOpenHelper: DBOpenHelper) :
    AppRepository, AutoCloseable {

    companion object {

        fun openGlobal(context: Context): SqliteAppRepository {
            return SqliteAppRepository(DBOpenHelper(context, "app.sqlite"))
        }

        fun openInMemory(context: Context): SqliteAppRepository {
            return SqliteAppRepository(DBOpenHelper(context, null))
        }
    }

    override fun close() {
        dbOpenHelper.close()
    }

    override suspend fun getPads(): List<Pad> {
        val db = dbOpenHelper.readableDatabase
        val out = mutableListOf<Pad>()
        db.query(
            "pads LEFT JOIN pad_sync_params ON pads.id = pad_sync_params.id",
            arrayOf("pads.id", "name"),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow("id")
            val nameCol = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                out.add(
                    Pad(
                        id = cursor.getString(idCol),
                        name = cursor.getString(nameCol),
                    )
                )
            }
        }
        return out
    }

    override suspend fun addPad(
        id: String,
        name: String,
        syncParams: PadSyncParams?,
    ): Pad {
        val db = dbOpenHelper.writableDatabase
        db.insertOrThrow(
            "pads", null,
            ContentValues().apply {
                put("id", id)
                put("name", name)
            },
        )
        if (syncParams != null) {
            db.insertOrThrow(
                "pad_sync_params",
                null,
                ContentValues().apply {
                    put("id", id)
                    put("url", syncParams.url)
                    put("key", syncParams.key.toBytes())
                },
            )
        }
        return Pad(
            id = id,
            name = name,
        )
    }

    override suspend fun deletePad(padId: String) {
        val db = dbOpenHelper.writableDatabase
        val affectedCount = db.delete(
            "pads",
            "id = ?",
            arrayOf(padId),
        )
        if (affectedCount > 1) {
            throw RuntimeException("Deleted more rows than expected.")
        }
        if (affectedCount == 0) {
            throw RuntimeException("No pad with this id.")
        }
    }

    override suspend fun renamePad(padId: String, name: String) {
        val db = dbOpenHelper.writableDatabase
        val affectedCount = db.update(
            "pads",
            ContentValues().apply {
                put("name", name)
            },
            "id = ?",
            arrayOf(padId),
        )
        if (affectedCount > 1) {
            throw RuntimeException("Updated more rows than expected.")
        }
        if (affectedCount == 0) {
            throw RuntimeException("No such pad.")
        }
    }

    override suspend fun setupPadSync(
        padId: String,
        syncParams: PadSyncParams,
    ) {
        val db = dbOpenHelper.writableDatabase
        db.insertOrThrow(
            "pad_sync_params",
            null,
            ContentValues().apply {
                put("id", padId)
                put("url", syncParams.url)
                put("key", syncParams.key.toBytes())
            },
        )
    }

    override suspend fun unSetupPadSync(padId: String) {
        val db = dbOpenHelper.writableDatabase
        val affectedCount = db.delete(
            "pad_sync_params",
            "id = ?",
            arrayOf(padId),
        )
        if (affectedCount > 1) {
            throw RuntimeException("Deleted more rows than expected.")
        }
        if (affectedCount == 0) {
            throw RuntimeException("No such pad item.")
        }
    }

    override suspend fun getPadSyncParams(padId: String): PadSyncParams? {
        val db = dbOpenHelper.readableDatabase
        return db.query(
            "pads LEFT JOIN pad_sync_params ON pads.id = pad_sync_params.id",
            arrayOf("url", "key"),
            "pads.id = ?",
            arrayOf(padId),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToNext()) {
                throw RuntimeException("No such pad: $padId")
            }
            val syncUrlCol = cursor.getColumnIndexOrThrow("url")
            val syncKeyCol = cursor.getColumnIndexOrThrow("key")
            val out = PadSyncParams(
                url = cursor.getStringOrNull(syncUrlCol) ?: return null,
                key = PadKey.fromBytes(cursor.getBlob(syncKeyCol)),
            )
            if (cursor.moveToNext()) {
                throw RuntimeException("Query returned too man rows.")
            }
            out
        }
    }

    override suspend fun getPadItems(padId: String): List<PadItem> {
        val db = dbOpenHelper.readableDatabase
        return getPadItems(db, "pad_items", padId)
    }

    override suspend fun addPadItem(
        padId: String, name: String, note: String
    ): PadItem {
        val db = dbOpenHelper.writableDatabase
        db.insertOrThrow(
            "pad_items", null,
            ContentValues().apply {
                put("pad", padId)
                put("name", name)
                put("note", note)
                put("checked", 0)
            },
        )
        return PadItem(
            checked = false,
            name = name,
            note = note,
        )
    }

    override suspend fun updatePadItem(
        padId: String,
        oldName: String,
        name: String?,
        note: String?,
        checked: Boolean?,
    ) {
        val db = dbOpenHelper.writableDatabase
        val affectedCount = db.update(
            "pad_items",
            ContentValues().apply {
                if (name != null) put("name", name)
                if (note != null) put("note", note)
                if (checked != null) put("checked", if (checked) 1 else 0)
            },
            "pad = ? AND name = ?",
            arrayOf(padId, oldName),
        )
        if (affectedCount > 1) {
            throw RuntimeException("Updated more rows than expected.")
        }
        if (affectedCount == 0) {
            throw RuntimeException("No such pad item.")
        }
    }

    override suspend fun setPadItemChecked(
        padId: String, name: String, checked: Boolean
    ) {
        val db = dbOpenHelper.writableDatabase
        val affectedCount = db.update(
            "pad_items",
            ContentValues().apply {
                put("checked", if (checked) 1 else 0)
            },
            "pad = ? AND name = ?",
            arrayOf(padId, name),
        )
        if (affectedCount > 1) {
            throw RuntimeException("Updated more rows than expected.")
        }
        if (affectedCount == 0) {
            throw RuntimeException("No such pad item.")
        }
    }

    override suspend fun deletePadItem(
        padId: String, name: String
    ) {
        val db = dbOpenHelper.writableDatabase
        val affectedCount = db.delete(
            "pad_items",
            "pad = ? AND name = ?",
            arrayOf(padId, name),
        )
        if (affectedCount > 1) {
            throw RuntimeException("Deleted more rows than expected.")
        }
        if (affectedCount == 0) {
            throw RuntimeException("No such pad item.")
        }
    }

    override suspend fun getPadSynchronizedTag(padId: String): String? {
        val db = dbOpenHelper.readableDatabase
        return db.query(
            "pads LEFT JOIN pad_sync_params ON pads.id = pad_sync_params.id",
            arrayOf("url", "sync_tag"),
            "pads.id = ?",
            arrayOf(padId),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToNext()) {
                throw RuntimeException("No pad with id $padId")
            }
            if (cursor.isNull(cursor.getColumnIndexOrThrow("url"))) {
                throw RuntimeException("Pad not configured for sync: $padId")
            }
            val value = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("sync_tag"))
            if (cursor.moveToNext()) {
                throw RuntimeException("Too many rows returned by query.")
            }
            value
        }
    }

    override suspend fun getPadSynchronizedVersion(padId: String): Map<String, PadItemContent> {
        val db = dbOpenHelper.readableDatabase
        val out = mutableMapOf<String, PadItemContent>()
        db.query(
            "pad_sync_items",
            arrayOf("name", "note", "checked"),
            "pad = ?",
            arrayOf(padId),
            null,
            null,
            null,
        ).use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow("name")
            val noteCol = cursor.getColumnIndexOrThrow("note")
            val checkedCol = cursor.getColumnIndexOrThrow("checked")
            while (cursor.moveToNext()) {
                out[cursor.getString(nameCol)] = PadItemContent(
                    note = cursor.getString(noteCol),
                    checked = cursor.getInt(checkedCol) != 0,
                )
            }
        }
        return out
    }

    override suspend fun synchronizePad(
        padId: String,
        remoteVersion: Map<String, PadItemContent>,
    ): Map<String, PadItemContent> {

        return dbOpenHelper.writableDatabase.transaction {

            val syncVersion = getPadItems(this, "pad_sync_items", padId)

            // listing remote changes

            val remoteChanges: MutableMap<String, PadItemContent?> = remoteVersion.toMutableMap()

            for (item in syncVersion) {
                val remote = remoteChanges[item.name]
                if (remote != null) {
                    if (remote == item.content) {
                        // unchanged
                        remoteChanges.remove(item.name)
                    }
                } else {
                    // deletion
                    remoteChanges[item.name] = null
                }
            }

            // listing local changes

            val localChanges: MutableMap<String, PadItemContent?> = getPadItems(
                this,
                "pad_items",
                padId,
            ).associate { item -> item.name to item.content }.toMutableMap()

            for (item in syncVersion) {
                val local = localChanges[item.name]
                if (local != null) {
                    if (local == item.content) {
                        // unchanged
                        localChanges.remove(item.name)
                    }
                } else {
                    // deletion
                    localChanges[item.name] = null
                }
            }

            // merging

            for (remote in remoteChanges.entries) {
                val remoteValue = remote.value
                if (localChanges.contains(remote.key)) {
                    val localValue = localChanges[remote.key]
                    if (localValue != remoteValue) {
                        // conflict
                        if (localValue != null) {
                            // the local version wins
                        } else {
                            remoteValue!!
                            // cancelling local deletion
                            this.insertOrThrow(
                                "pad_items", null,
                                ContentValues().apply {
                                    put("pad", padId)
                                    put("name", remote.key)
                                    put("note", remoteValue.note)
                                    put("checked", if (remoteValue.checked) 1 else 0)
                                },
                            )
                        }
                    }
                } else if (remoteValue != null) {
                    // applying insertion or update
                    this.insertWithOnConflict(
                        "pad_items", null, ContentValues().apply {
                            put("pad", padId)
                            put("name", remote.key)
                            put("note", remoteValue.note)
                            put("checked", if (remoteValue.checked) 1 else 0)
                        }, SQLiteDatabase.CONFLICT_REPLACE
                    )
                } else {
                    // applying deletion
                    this.delete(
                        "pad_items",
                        "pad = ? AND name = ?",
                        arrayOf(padId, remote.key),
                    )
                }
            }

            // returning the new local version

            getPadItems(
                this,
                "pad_items",
                padId,
            ).associate { item -> item.name to item.content }
        }
    }

    override suspend fun commitPadSynchronization(
        padId: String,
        syncVersion: Map<String, PadItemContent>,
        tag: String,
    ) {
        dbOpenHelper.writableDatabase.transaction {

            // updating sync tag

            val affectedCount = this.update(
                "pad_sync_params",
                ContentValues().apply {
                    put("sync_tag", tag)
                },
                "id = ?",
                arrayOf(padId),
            )
            if (affectedCount > 1) {
                throw RuntimeException("Updated more rows than expected.")
            }
            if (affectedCount == 0) {
                throw RuntimeException("No pad with id $padId, or not configured for sync.")
            }

            // clearing old rows

            this.delete(
                "pad_sync_items",
                "pad = ?",
                arrayOf(padId),
            )

            // storing new synchronized items

            for (item in syncVersion) {
                this.insertOrThrow(
                    "pad_sync_items",
                    null,
                    ContentValues().apply {
                        put("pad", padId)
                        put("name", item.key)
                        put("note", item.value.note)
                        put("checked", if (item.value.checked) 1 else 0)
                    },
                )
            }
        }
    }

    private fun getPadItems(
        db: SQLiteDatabase,
        table: String,
        padId: String,
    ): List<PadItem> {
        val out = mutableListOf<PadItem>()
        db.query(
            table,
            arrayOf("name", "note", "checked"),
            "pad = ?",
            arrayOf(padId),
            null,
            null,
            null,
        ).use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow("name")
            val noteCol = cursor.getColumnIndexOrThrow("note")
            val checkedCol = cursor.getColumnIndexOrThrow("checked")
            while (cursor.moveToNext()) {
                out.add(
                    PadItem(
                        name = cursor.getString(nameCol),
                        note = cursor.getString(noteCol),
                        checked = cursor.getInt(checkedCol) != 0,
                    )
                )
            }
        }
        return out
    }
}

private class DBOpenHelper(context: Context, name: String?) :
    SQLiteOpenHelper(context, name, null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        // pads
        db.execSQL(
            """
            CREATE TABLE pads (
                id TEXT NOT NULL PRIMARY KEY CHECK (id <> ''),
                name TEXT NOT NULL CHECK (name <> '')
            );
            """.trimIndent()
        )
        // pad synchronization parameters
        db.execSQL(
            """
            CREATE TABLE pad_sync_params (
                id TEXT NOT NULL PRIMARY KEY CHECK (id <> '') REFERENCES pads(id) ON DELETE CASCADE,
                url TEXT NOT NULL,
                key BLOB NOT NULL,
                sync_tag TEXT
            );
            """.trimIndent()
        )
        // current items of pads
        db.execSQL(
            """
            CREATE TABLE pad_items (
                pad TEXT NOT NULL REFERENCES pads(id) ON DELETE CASCADE,
                name TEXT NOT NULL CHECK (name <> ''),
                note TEXT NOT NULL,
                checked INTEGER NOT NULL CHECK (checked = 0 OR checked = 1),
                UNIQUE (pad, name)
            );
            """.trimIndent()
        )
        // like the previous table, but represents the state of the pads when they were last synchronized
        db.execSQL(
            """
            CREATE TABLE pad_sync_items (
                pad TEXT NOT NULL REFERENCES pads(id) ON DELETE CASCADE,
                name TEXT NOT NULL CHECK (name <> ''),
                note TEXT NOT NULL,
                checked INTEGER NOT NULL CHECK (checked = 0 OR checked = 1),
                UNIQUE (pad, name)
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase, oldVersion: Int, newVersion: Int
    ) {
        throw RuntimeException("the database doesn't have any migration yet")
    }
}
