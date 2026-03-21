package com.example.informationfatigue.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DataRecordDao {

    @Insert
    suspend fun insert(record: DataRecord)

    @Insert
    suspend fun insertAll(records: List<DataRecord>)

    @Query("SELECT * FROM data_records ORDER BY screen_on_timestamp_unix DESC")
    fun getAllOrderedByTime(): LiveData<List<DataRecord>>

    @Query("SELECT * FROM data_records ORDER BY screen_on_timestamp_unix ASC")
    suspend fun getAll(): List<DataRecord>

    @Query("SELECT screen_off_timestamp_unix FROM data_records ORDER BY screen_off_timestamp_unix DESC LIMIT 1")
    suspend fun getLatestScreenOffUnix(): Long?

    @Query("SELECT COUNT(*) FROM data_records")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM data_records")
    suspend fun deleteAll()
}
