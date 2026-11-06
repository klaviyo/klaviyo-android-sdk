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

    /**
     * One page of the company's messages, newest first by sort time. A null [afterSortTime]
     * starts at the newest message.
     *
     * Pages by keyset on (sortTime, transmissionId), the order the index and retention use, so a
     * message captured or removed between pages never shifts the page boundary.
     */
    @Query(
        """
        SELECT * FROM ${InboxMessageEntity.TABLE_NAME}
        WHERE companyId = :companyId
            AND (
                :afterSortTime IS NULL OR sortTime < :afterSortTime
                OR (sortTime = :afterSortTime AND transmissionId < :afterId)
            )
        ORDER BY sortTime DESC, transmissionId DESC
        LIMIT :limit
        """
    )
    abstract suspend fun pageNewestFirst(
        companyId: String,
        afterSortTime: Long?,
        afterId: String?,
        limit: Int
    ): List<InboxMessageEntity>

    /**
     * The company's messages that are neither read nor archived
     */
    @Query(
        """
        SELECT COUNT(*) FROM ${InboxMessageEntity.TABLE_NAME}
        WHERE companyId = :companyId AND isRead = 0 AND isArchived = 0
        """
    )
    abstract suspend fun countUnread(companyId: String): Int

    @Query(
        """
        UPDATE ${InboxMessageEntity.TABLE_NAME}
        SET isRead = :isRead, stateUpdatedAt = :updatedAt, isStatePending = 1
        WHERE companyId = :companyId AND transmissionId = :transmissionId AND isRead != :isRead
        """
    )
    protected abstract suspend fun setRead(
        companyId: String,
        transmissionId: String,
        isRead: Boolean,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE ${InboxMessageEntity.TABLE_NAME}
        SET isArchived = :isArchived, stateUpdatedAt = :updatedAt, isStatePending = 1
        WHERE companyId = :companyId AND transmissionId = :transmissionId
            AND isArchived != :isArchived
        """
    )
    protected abstract suspend fun setArchived(
        companyId: String,
        transmissionId: String,
        isArchived: Boolean,
        updatedAt: Long
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM ${InboxMessageEntity.TABLE_NAME}
        WHERE companyId = :companyId AND transmissionId = :transmissionId
        """
    )
    protected abstract suspend fun count(companyId: String, transmissionId: String): Int

    /**
     * Applies [change] to the company's message, marking it as pending sync only when the change
     * alters it, so a repeat of the same change leaves the row and its timestamp alone.
     *
     * @return Whether the change altered the message, left it as it was, or the company has no
     *  such message
     */
    @Transaction
    open suspend fun updateState(
        companyId: String,
        transmissionId: String,
        change: InboxStateChange,
        updatedAt: Long
    ): InboxStateUpdate {
        val altered = when (change) {
            InboxStateChange.MARK_READ -> setRead(companyId, transmissionId, true, updatedAt)
            InboxStateChange.MARK_UNREAD -> setRead(companyId, transmissionId, false, updatedAt)
            InboxStateChange.ARCHIVE -> setArchived(companyId, transmissionId, true, updatedAt)
            InboxStateChange.UNARCHIVE -> setArchived(companyId, transmissionId, false, updatedAt)
        }
        return when {
            altered > 0 -> InboxStateUpdate.ALTERED
            count(companyId, transmissionId) > 0 -> InboxStateUpdate.UNCHANGED
            else -> InboxStateUpdate.NOT_FOUND
        }
    }

    @Query("DELETE FROM ${InboxMessageEntity.TABLE_NAME} WHERE companyId = :companyId")
    abstract suspend fun deleteByCompanyId(companyId: String): Int

    @Query("DELETE FROM ${InboxMessageEntity.TABLE_NAME}")
    abstract suspend fun deleteAll(): Int

    companion object {
        const val IGNORED_ROW_ID = -1L
    }
}

internal enum class InboxStateUpdate {
    ALTERED,
    UNCHANGED,
    NOT_FOUND
}
