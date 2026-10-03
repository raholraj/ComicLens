package com.comiclens.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val src: String, val tgt: String,
    val status: String, val done: Int, val total: Int, val outUri: String
)

@Dao
interface JobDao {
    @Query("SELECT * FROM JobEntity ORDER BY id DESC") fun all(): Flow<List<JobEntity>>
    @Insert suspend fun insert(j: JobEntity): Long
    @Update suspend fun update(j: JobEntity)
    @Delete suspend fun delete(j: JobEntity)
}

@Database(entities = [JobEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() { abstract fun jobs(): JobDao }

object Langs {
    val all = listOf("ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese", "en" to "English", "hi" to "Hindi")
    fun name(c: String) = all.firstOrNull { it.first == c }?.second ?: c
}
