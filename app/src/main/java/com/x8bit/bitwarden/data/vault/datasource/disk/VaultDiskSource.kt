package com.x8bit.bitwarden.data.vault.datasource.disk

import com.x8bit.bitwarden.data.vault.datasource.network.model.SyncResponseJson
import kotlinx.coroutines.flow.Flow

/**
 * Primary access point for disk information related to vault data.
 */
interface VaultDiskSource {

    /**
     * Retrieves all ciphers from the data source for a given [userId].
     */
    fun getCiphers(userId: String): Flow<List<SyncResponseJson.Cipher>>

    /**
     * Retrieves all collections from the data source for a given [userId].
     */
    fun getCollections(userId: String): Flow<List<SyncResponseJson.Collection>>

    /**
     * Retrieves all folders from the data source for a given [userId].
     */
    fun getFolders(userId: String): Flow<List<SyncResponseJson.Folder>>

    /**
     * Retrieves all sends from the data source for a given [userId].
     */
    fun getSends(userId: String): Flow<List<SyncResponseJson.Send>>

    /**
     * Replaces all [vault] data for a given [userId] with the new `vault`.
     *
     * This will always cause the [getCiphers], [getCollections], and [getFolders] functions to
     * re-emit even if the underlying data has not changed.
     */
    suspend fun replaceVaultData(userId: String, vault: SyncResponseJson)

    /**
     * Deletes all stored vault data from the data source for a given [userId].
     */
    suspend fun deleteVaultData(userId: String)
}
