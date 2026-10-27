package com.klaviyo.inbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal abstract class InboxMessageDao {

    /**
     * @return The new row ID, or [IGNORED_ROW_ID] when a message with the same ID is already stored
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(message: InboxMessageEntity): Long

    /**
     * Removes the company's messages beyond the newest [limit], by sort time
     */
    @Query(
        """
        DELETE FROM ${InboxMessageEntity.TABLE_NAME}
        WHERE companyId = :companyId AND transmissionId NOT IN (
            SELECT transmissionId FROM ${InboxMessageEntity.TABLE_NAME}
            WHERE companyId = :companyId
            ORDER BY sortTime DESC, transmissionId DESC
            LIMIT :limit
        )
        """
    )
    abstract suspend fun trim(companyId: String, limit: Int): Int

    /**
     * Stores the message unless its ID is already present, then trims the company to [limit]
     *
     * @return True when the message was stored, false when it was a duplicate
     */
    @Transaction
    open suspend fun insertAndTrim(message: InboxMessageEntity, limit: Int): Boolean {
        val inserted = insert(message) != IGNORED_ROW_ID
        if (inserted) trim(message.companyId, limit)
        return inserted
    }

    @Query("SELECT * FROM ${InboxMessageEntity.TABLE_NAME} WHERE transmissionId = :transmissionId")
    abstract suspend fun get(transmissionId: String): InboxMessageEntity?

    /**
     * The company's transmission IDs, newest first by sort time
     */
    @Query(
        """
        SELECT transmissionId FROM ${InboxMessageEntity.TABLE_NAME}
        WHERE companyId = :companyId
        ORDER BY sortTime DESC, transmissionId DESC
        """
    )
    abstract suspend fun transmissionIdsByCompanyId(companyId: String): List<String>

    @Query("DELETE FROM ${InboxMessageEntity.TABLE_NAME} WHERE companyId = :companyId")
    abstract suspend fun deleteByCompanyId(companyId: String): Int

    @Query("DELETE FROM ${InboxMessageEntity.TABLE_NAME}")
    abstract suspend fun deleteAll(): Int

    companion object {
        const val IGNORED_ROW_ID = -1L
    }
}
