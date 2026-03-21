package com.example.informationfatigue.data

import android.content.Context
import androidx.lifecycle.LiveData

class DataRepository(context: Context) {

    private val dao: DataRecordDao = AppDatabase.getInstance(context).dataRecordDao()

    val allRecords: LiveData<List<DataRecord>> = dao.getAllOrderedByTime()

    suspend fun insert(record: DataRecord) {
        dao.insert(record)
    }

    suspend fun insertAll(records: List<DataRecord>) {
        dao.insertAll(records)
    }

    suspend fun getAll(): List<DataRecord> {
        return dao.getAll()
    }

    suspend fun getLatestScreenOffUnix(): Long? {
        return dao.getLatestScreenOffUnix()
    }

    suspend fun getTotalCount(): Int {
        return dao.getTotalCount()
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
