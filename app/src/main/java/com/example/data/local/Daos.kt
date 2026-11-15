package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Transaction
    @Query("SELECT * FROM providers ORDER BY isBuiltIn DESC, createdAt ASC")
    fun observeProvidersWithModels(): Flow<List<ProviderWithModels>>

    @Transaction
    @Query("SELECT * FROM providers WHERE isEnabled = 1 ORDER BY isBuiltIn DESC, createdAt ASC")
    fun observeEnabledProvidersWithModels(): Flow<List<ProviderWithModels>>

    @Query("SELECT * FROM providers WHERE id = :id LIMIT 1")
    suspend fun getProviderById(id: String): ProviderEntity?

    @Query("SELECT COUNT(*) FROM providers")
    suspend fun getProviderCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(provider: ProviderEntity)

    @Update
    suspend fun updateProvider(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomProvider(id: String)

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): ModelEntity?

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY createdAt ASC")
    suspend fun getModelsForProvider(providerId: String): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(model: ModelEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertModelsIgnore(models: List<ModelEntity>)

    @Query("DELETE FROM models WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomModel(id: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSession(sessionId: String): Flow<ChatSessionEntity?>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, title: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET systemPrompt = :systemPrompt, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionSystemPrompt(sessionId: String, systemPrompt: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET modelRecordId = :modelRecordId, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionModel(sessionId: String, modelRecordId: String?, updatedAt: Long)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(sessionId: String, updatedAt: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET feedback = :feedback WHERE id = :messageId")
    suspend fun updateMessageFeedback(messageId: String, feedback: Int)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND timestamp >= :fromTimestamp")
    suspend fun deleteMessagesFrom(sessionId: String, fromTimestamp: Long)
}
