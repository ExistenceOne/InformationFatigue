package com.example.informationfatigue.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DataRecordDao {

    @Insert
    suspend fun insert(record: DataRecord)

    @Query("SELECT * FROM data_records ORDER BY start_time DESC")
    fun getAllOrderedByTime(): LiveData<List<DataRecord>>

    @Query("SELECT * FROM data_records ORDER BY start_time ASC")
    suspend fun getAll(): List<DataRecord>

    @Query("DELETE FROM data_records")
    suspend fun deleteAll()
}
