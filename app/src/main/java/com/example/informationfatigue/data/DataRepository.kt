package com.example.informationfatigue.data

import android.content.Context
import androidx.lifecycle.LiveData

class DataRepository(context: Context) {

    private val dao: DataRecordDao = AppDatabase.getInstance(context).dataRecordDao()

    val allRecords: LiveData<List<DataRecord>> = dao.getAllOrderedByTime()

    suspend fun insert(record: DataRecord) {
        dao.insert(record)
    }

    suspend fun getAll(): List<DataRecord> {
        return dao.getAll()
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
