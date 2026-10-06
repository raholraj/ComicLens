package com.comiclens.app.data

enum class ThemeMode { System, Light, Dark, Amoled }

enum class JobStatus(val label: String) {
    Running("Running"), Paused("Paused"), Done("Done"),
    Failed("Failed"), Cancelled("Cancelled"), Queued("Queued")
}

enum class PageStatus { Done, Failed, Skipped, Pending }

/** Debug switch (Settings > Developer) to preview empty / error states on every screen. */
enum class PreviewMode { Normal, Empty, Error }

data class Job(
    val id: Long,
    val name: String,
    val srcLang: String,
    val tgtLang: String,
    val status: JobStatus,
    val done: Int,
    val total: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val error: String? = null
)

data class Page(
    val id: Long,
    val jobId: Long,
    val name: String,
    val status: PageStatus,
    val order: Int
)

data class GlossaryEntry(
    val id: Long,
    val src: String,
    val tgt: String,
    val note: String = ""
)

data class Preset(
    val id: Long,
    val name: String,
    val srcLang: String,
    val tgtLang: String
)

data class HistoryItem(
    val id: Long,
    val jobName: String,
    val srcLang: String,
    val tgtLang: String,
    val pages: Int,
    val finishedAt: Long
)
