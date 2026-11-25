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
     * Removes the company's messages beyond the newest [limit], by send time
     */
    @Query(
        """
        DELETE FROM ${InboxMessageEntity.TABLE_NAME}
        WHERE companyId = :companyId AND transmissionId NOT IN (
            SELECT transmissionId FROM ${InboxMessageEntity.TABLE_NAME}
            WHERE companyId = :companyId
            ORDER BY sentAt DESC, transmissionId DESC
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
     * The company's transmission IDs, newest first by send time
     */
    @Query(
        """
        SELECT transmissionId FROM ${InboxMessageEntity.TABLE_NAME}
        WHERE companyId = :companyId
        ORDER BY sentAt DESC, transmissionId DESC
        """
    )
    abstract suspend fun transmissionIdsByCompanyId(companyId: String): List<String>

    /**
     * One page of the company's messages, newest first by send time. A null [afterSentAt]
     * starts at the newest message. A null [isRead] or [isArchived] matches either state.
     *
     * Pages by keyset on (sentAt, transmissionId), the order the index and retention use, so a
     * message captured or removed between pages never shifts the page boundary.
     */
    @Query(
        """
        SELECT * FROM ${InboxMessageEntity.TABLE_NAME}
        WHERE companyId = :companyId
            AND (
                :afterSentAt IS NULL OR sentAt < :afterSentAt
                OR (sentAt = :afterSentAt AND transmissionId < :afterId)
            )
            AND (:isRead IS NULL OR isRead = :isRead)
            AND (:isArchived IS NULL OR isArchived = :isArchived)
        ORDER BY sentAt DESC, transmissionId DESC
        LIMIT :limit
        """
    )
    abstract suspend fun pageNewestFirst(
        companyId: String,
        afterSentAt: Long?,
        afterId: String?,
        isRead: Boolean?,
        isArchived: Boolean?,
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
        SET isRead = :isRead, updatedAt = :updatedAt
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
        SET isArchived = :isArchived, updatedAt = :updatedAt
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

    /**
     * Sets the company's messages that match [matchRead] and [matchArchived] to [isRead], stamping
     * [updatedAt] on the ones that change. A null [matchRead] or [matchArchived] matches either
     * state. A message already at [isRead] is not matched, so it keeps its timestamp.
     *
     * @return How many messages changed
     */
    @Query(
        """
        UPDATE ${InboxMessageEntity.TABLE_NAME}
        SET isRead = :isRead, updatedAt = :updatedAt
        WHERE companyId = :companyId AND isRead != :isRead
            AND (:matchRead IS NULL OR isRead = :matchRead)
            AND (:matchArchived IS NULL OR isArchived = :matchArchived)
        """
    )
    abstract suspend fun setReadWhere(
        companyId: String,
        isRead: Boolean,
        matchRead: Boolean?,
        matchArchived: Boolean?,
        updatedAt: Long
    ): Int

    /**
     * Sets the company's messages that match [matchRead] and [matchArchived] to [isArchived],
     * stamping [updatedAt] on the ones that change. A null [matchRead] or [matchArchived] matches
     * either state. A message already at [isArchived] is not matched, so it keeps its timestamp.
     *
     * @return How many messages changed
     */
    @Query(
        """
        UPDATE ${InboxMessageEntity.TABLE_NAME}
        SET isArchived = :isArchived, updatedAt = :updatedAt
        WHERE companyId = :companyId AND isArchived != :isArchived
            AND (:matchRead IS NULL OR isRead = :matchRead)
            AND (:matchArchived IS NULL OR isArchived = :matchArchived)
        """
    )
    abstract suspend fun setArchivedWhere(
        companyId: String,
        isArchived: Boolean,
        matchRead: Boolean?,
        matchArchived: Boolean?,
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
     * Applies [change] to the company's message, stamping
     * [InboxMessageEntity.updatedAt] only when the change alters it, so a repeat of the same
     * change leaves the row and its timestamp alone.
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

    /**
     * Applies [change] to every company message that matches [matchRead] and [matchArchived], as
     * [updateState] does for one, in a single statement.
     *
     * @return How many messages the change altered
     */
    open suspend fun updateStateWhere(
        companyId: String,
        change: InboxStateChange,
        matchRead: Boolean?,
        matchArchived: Boolean?,
        updatedAt: Long
    ): Int = when (change) {
        InboxStateChange.MARK_READ ->
            setReadWhere(companyId, true, matchRead, matchArchived, updatedAt)
        InboxStateChange.MARK_UNREAD ->
            setReadWhere(companyId, false, matchRead, matchArchived, updatedAt)
        InboxStateChange.ARCHIVE ->
            setArchivedWhere(companyId, true, matchRead, matchArchived, updatedAt)
        InboxStateChange.UNARCHIVE ->
            setArchivedWhere(companyId, false, matchRead, matchArchived, updatedAt)
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
