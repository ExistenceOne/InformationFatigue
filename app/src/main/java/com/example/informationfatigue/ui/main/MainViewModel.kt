package com.example.informationfatigue.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.informationfatigue.data.DataRecord
import com.example.informationfatigue.data.DataRepository
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository(application)

    val allRecords: LiveData<List<DataRecord>> = repository.allRecords

    fun getAllRecordsSync(callback: (List<DataRecord>) -> Unit) {
        viewModelScope.launch {
            val records = repository.getAll()
            callback(records)
        }
    }
}
