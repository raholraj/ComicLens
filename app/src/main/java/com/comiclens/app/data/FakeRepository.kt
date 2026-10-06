package com.comiclens.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeRepository @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _jobs = MutableStateFlow(listOf(
        Job(1, "One Piece ch.1089", "ja", "en", JobStatus.Done, 18, 18),
        Job(2, "Solo Leveling 179", "ko", "en", JobStatus.Running, 7, 22),
        Job(3, "Jujutsu Kaisen 245", "ja", "hi", JobStatus.Queued, 0, 20),
        Job(4, "Naruto 700", "ja", "en", JobStatus.Failed, 5, 15, error = "OCR model missing"),
        Job(5, "Demon Slayer 205", "ja", "en", JobStatus.Paused, 10, 16),
    ))
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val _pages = MutableStateFlow(
        (1..18).map { Page(it.toLong(), 1, "page_%03d.jpg".format(it), if (it <= 18) PageStatus.Done else PageStatus.Pending, it) } +
        (1..22).map { Page(100L + it, 2, "page_%03d.jpg".format(it), when {
            it <= 7 -> PageStatus.Done
            it == 8 -> PageStatus.Failed
            else -> PageStatus.Pending
        }, it) }
    )
    fun pages(jobId: Long): Flow<List<Page>> = _pages.map { list -> list.filter { it.jobId == jobId }.sortedBy { it.order } }

    private val _glossary = MutableStateFlow(listOf(
        GlossaryEntry(1, "俺", "I / me", "male speech"),
        GlossaryEntry(2, "お前", "you", "rough"),
        GlossaryEntry(3, "師匠", "master", ""),
        GlossaryEntry(4, "悪魔", "demon", ""),
    ))
    val glossary: StateFlow<List<GlossaryEntry>> = _glossary.asStateFlow()

    private val _presets = MutableStateFlow(listOf(
        Preset(1, "Manga JP→EN", "ja", "en"),
        Preset(2, "Manhwa KO→EN", "ko", "en"),
        Preset(3, "Manga JP→HI", "ja", "hi"),
    ))
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    private val _history = MutableStateFlow(listOf(
        HistoryItem(1, "One Piece 1088", "ja", "en", 16, System.currentTimeMillis() - 86400000),
        HistoryItem(2, "Chainsaw Man 150", "ja", "en", 20, System.currentTimeMillis() - 172800000),
    ))
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val _languages = MutableStateFlow(listOf(
        Lang("ja", "Japanese", true),
        Lang("ko", "Korean", true),
        Lang("zh", "Chinese", false),
        Lang("en", "English", true),
        Lang("hi", "Hindi", false),
        Lang("es", "Spanish", false),
        Lang("fr", "French", false),
        Lang("de", "German", false),
        Lang("pt", "Portuguese", false),
        Lang("ru", "Russian", false),
        Lang("ar", "Arabic", false),
        Lang("th", "Thai", false),
        Lang("vi", "Vietnamese", false),
        Lang("id", "Indonesian", false),
    ))
    val languages: StateFlow<List<Lang>> = _languages.asStateFlow()

    data class Lang(val code: String, val name: String, val downloaded: Boolean)

    private val downloads = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = downloads.asStateFlow()

    fun addJob(name: String, src: String, tgt: String): Long {
        val id = (_jobs.value.maxOfOrNull { it.id } ?: 0) + 1
        _jobs.update { it + Job(id, name, src, tgt, JobStatus.Queued, 0, 20) }
        return id
    }

    fun deleteJob(id: Long) {
        _jobs.update { it.filter { j -> j.id != id } }
        _pages.update { it.filter { p -> p.jobId != id } }
    }

    fun pauseJob(id: Long) = _jobs.update { list -> list.map { if (it.id == id) it.copy(status = JobStatus.Paused) else it } }
    fun resumeJob(id: Long) = _jobs.update { list -> list.map { if (it.id == id) it.copy(status = JobStatus.Running) else it } }
    fun cancelJob(id: Long) = _jobs.update { list -> list.map { if (it.id == id) it.copy(status = JobStatus.Cancelled) else it } }

    fun addGlossary(src: String, tgt: String, note: String = "") {
        val id = (_glossary.value.maxOfOrNull { it.id } ?: 0) + 1
        _glossary.update { it + GlossaryEntry(id, src, tgt, note) }
    }
    fun deleteGlossary(id: Long) = _glossary.update { it.filter { e -> e.id != id } }

    fun addPreset(name: String, src: String, tgt: String) {
        val id = (_presets.value.maxOfOrNull { it.id } ?: 0) + 1
        _presets.update { it + Preset(id, name, src, tgt) }
    }
    fun deletePreset(id: Long) = _presets.update { it.filter { p -> p.id != id } }

    fun clearHistory() = _history.update { emptyList() }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun downloadModel(code: String) {
        if (code in downloads.value) return
        downloads.update { it + code }
        scope.launch {
            delay(2500)
            _languages.update { l -> l.map { if (it.code == code) it.copy(downloaded = true) else it } }
            downloads.update { it - code }
        }
    }

    fun deleteModel(code: String) = _languages.update { l -> l.map { if (it.code == code) it.copy(downloaded = false) else it } }
}
