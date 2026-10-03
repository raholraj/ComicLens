package com.comiclens.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainVm(app: Application) : AndroidViewModel(app) {
    private val dao = Room.databaseBuilder(app, AppDb::class.java, "comiclens.db").build().jobs()
    val jobs = dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val progress = MutableStateFlow(0 to 0)
    val running = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var task: kotlinx.coroutines.Job? = null

    fun start(name: String, input: Uri, output: Uri, src: String, tgt: String) {
        if (running.value) return
        running.value = true; error.value = null; progress.value = 0 to 0
        task = viewModelScope.launch(Dispatchers.Default) {
            var j = JobEntity(name = name, src = src, tgt = tgt, status = "Running", done = 0, total = 0, outUri = output.toString())
            j = j.copy(id = dao.insert(j))
            try {
                Pipeline(getApplication()).run(input, output, src, tgt) { d, t ->
                    j = j.copy(done = d, total = t); progress.value = d to t; dao.update(j)
                }
                dao.update(j.copy(status = "Done"))
            } catch (e: CancellationException) {
                withContext(NonCancellable) { dao.update(j.copy(status = "Cancelled")) }
            } catch (e: Throwable) {
                error.value = e.message ?: "Kuch gadbad hui"
                dao.update(j.copy(status = "Failed"))
            } finally { running.value = false }
        }
    }

    fun cancel() { task?.cancel() }
    fun delete(j: JobEntity) { viewModelScope.launch { dao.delete(j) } }
}
