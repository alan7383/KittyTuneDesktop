package com.alananasss.kittytune.data.local

import kotlinx.coroutines.flow.Flow
import java.sql.ResultSet

class FolderDao(private val db: AppDatabase) {

    private fun folder(rs: ResultSet): LibraryFolder {
        val id = rs.getLong("id")
        val name = rs.getString("name")
        val parentFolderId = rs.getLong("parentFolderId").let { if (rs.wasNull()) null else it }
        val isPinned = rs.getInt("isPinned") == 1
        val createdAt = rs.getLong("createdAt")
        return LibraryFolder(
            id = id,
            name = name,
            parentFolderId = parentFolderId,
            isPinned = isPinned,
            createdAt = createdAt
        )
    }

    private fun itemMeta(rs: ResultSet): LibraryItemMeta {
        val itemKey = rs.getString("itemKey")
        val folderId = rs.getLong("folderId").let { if (rs.wasNull()) null else it }
        val isPinned = rs.getInt("isPinned") == 1
        val addedAt = rs.getLong("addedAt")
        return LibraryItemMeta(
            itemKey = itemKey,
            folderId = folderId,
            isPinned = isPinned,
            addedAt = addedAt
        )
    }

    suspend fun insertFolder(folder: LibraryFolder): Long {
        db.exec(
            "INSERT INTO library_folders(name, parentFolderId, isPinned, createdAt) VALUES(?, ?, ?, ?)",
            folder.name, folder.parentFolderId, folder.isPinned, folder.createdAt
        )
        return db.scalarLong("SELECT last_insert_rowid()")
    }

    suspend fun updateFolder(folder: LibraryFolder) =
        db.exec(
            "UPDATE library_folders SET name = ?, parentFolderId = ?, isPinned = ? WHERE id = ?",
            folder.name, folder.parentFolderId, folder.isPinned, folder.id
        )

    suspend fun renameFolder(folderId: Long, newName: String) =
        db.exec("UPDATE library_folders SET name = ? WHERE id = ?", newName, folderId)

    suspend fun setFolderPinned(folderId: Long, isPinned: Boolean) =
        db.exec("UPDATE library_folders SET isPinned = ? WHERE id = ?", isPinned, folderId)

    suspend fun moveFolder(folderId: Long, newParentFolderId: Long?) =
        db.exec("UPDATE library_folders SET parentFolderId = ?, isPinned = 0 WHERE id = ?", newParentFolderId, folderId)

    fun getAllFolders(): Flow<List<LibraryFolder>> = db.observe {
        db.query("SELECT * FROM library_folders ORDER BY createdAt DESC", mapper = ::folder)
    }

    suspend fun getFolder(folderId: Long): LibraryFolder? =
        db.queryOne("SELECT * FROM library_folders WHERE id = ?", folderId, mapper = ::folder)

    suspend fun deleteFolderDirect(folderId: Long) =
        db.exec("DELETE FROM library_folders WHERE id = ?", folderId)

    suspend fun reassignItemsFromDeletedFolder(deletedFolderId: Long, newParentFolderId: Long?) =
        db.exec("UPDATE library_item_meta SET folderId = ? WHERE folderId = ?", newParentFolderId, deletedFolderId)

    suspend fun reassignFoldersFromDeletedFolder(deletedFolderId: Long, newParentFolderId: Long?) =
        db.exec("UPDATE library_folders SET parentFolderId = ? WHERE parentFolderId = ?", newParentFolderId, deletedFolderId)

    suspend fun deleteFolderSafely(folderId: Long) {
        val folder = getFolder(folderId) ?: return
        val parentId = folder.parentFolderId
        reassignItemsFromDeletedFolder(folderId, parentId)
        reassignFoldersFromDeletedFolder(folderId, parentId)
        deleteFolderDirect(folderId)
    }

    suspend fun upsertItemMeta(meta: LibraryItemMeta) =
        db.exec(
            "INSERT OR REPLACE INTO library_item_meta(itemKey, folderId, isPinned, addedAt) VALUES(?, ?, ?, ?)",
            meta.itemKey, meta.folderId, meta.isPinned, meta.addedAt
        )

    fun getAllItemMetas(): Flow<List<LibraryItemMeta>> = db.observe {
        db.query("SELECT * FROM library_item_meta", mapper = ::itemMeta)
    }

    suspend fun getItemMeta(itemKey: String): LibraryItemMeta? =
        db.queryOne("SELECT * FROM library_item_meta WHERE itemKey = ?", itemKey, mapper = ::itemMeta)

    suspend fun moveItemToFolder(itemKey: String, folderId: Long?) {
        val existing = getItemMeta(itemKey)
        if (existing != null) {
            db.exec("UPDATE library_item_meta SET folderId = ?, isPinned = 0 WHERE itemKey = ?", folderId, itemKey)
        } else {
            upsertItemMeta(LibraryItemMeta(itemKey = itemKey, folderId = folderId, isPinned = false))
        }
    }

    suspend fun setItemPinned(itemKey: String, isPinned: Boolean) {
        val existing = getItemMeta(itemKey)
        if (existing != null) {
            db.exec("UPDATE library_item_meta SET isPinned = ? WHERE itemKey = ?", isPinned, itemKey)
        } else {
            upsertItemMeta(LibraryItemMeta(itemKey = itemKey, folderId = null, isPinned = isPinned))
        }
    }

    suspend fun deleteItemMeta(itemKey: String) =
        db.exec("DELETE FROM library_item_meta WHERE itemKey = ?", itemKey)
}
