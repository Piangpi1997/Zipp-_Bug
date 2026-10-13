package com.zipbug.base.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ApkArtifactDao {
    @Query("SELECT * FROM apk_artifacts ORDER BY discoveredAt DESC")
    suspend fun listAll(): List<ApkArtifactEntity>

    @Query("SELECT * FROM apk_artifacts ORDER BY discoveredAt DESC")
    fun observeAll(): Flow<List<ApkArtifactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(artifact: ApkArtifactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(artifacts: List<ApkArtifactEntity>)

    @Query("DELETE FROM apk_artifacts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM apk_artifacts WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String)
}
