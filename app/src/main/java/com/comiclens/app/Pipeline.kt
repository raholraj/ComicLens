package com.comiclens.app

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class Pipeline(private val ctx: Context) {

    suspend fun run(input: Uri, output: Uri, src: String, tgt: String, onProgress: suspend (Int, Int) -> Unit) {
        val tmp = File(ctx.cacheDir, "input.zip")
        withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(input)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        }
        val opts: TextRecognizerOptionsInterface = when (src) {
            "ja" -> JapaneseTextRecognizerOptions.Builder().build()
            "ko" -> KoreanTextRecognizerOptions.Builder().build()
            "zh" -> ChineseTextRecognizerOptions.Builder().build()
            "hi" -> DevanagariTextRecognizerOptions.Builder().build()
            else -> TextRecognizerOptions.DEFAULT_OPTIONS
        }
        val rec = TextRecognition.getClient(opts)
        val tr = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build()
        )
        try {
            val zip = try { ZipFile(tmp) } catch (e: Exception) {
                throw Exception("ZIP/CBZ open nahi hua: ${e.message}")
            }
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.matches(Regex(".*\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)) }
                .sortedWith { a, b -> natural(a.name, b.name) }
                .toList()
            if (entries.isEmpty()) throw Exception("Koi image nahi mili ZIP me")
            onProgress(0, entries.size)
            tr.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val outTmp = File(ctx.cacheDir, "out.zip")
            ZipOutputStream(BufferedOutputStream(outTmp.outputStream())).use { zos ->
                entries.forEachIndexed { i, e ->
                    coroutineContext.ensureActive()
                    val bytes = zip.getInputStream(e).use { it.readBytes() }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw Exception("Image decode fail: ${e.name}")
                    val processed = processPage(bmp, rec, tr)
                    zos.putNextEntry(ZipEntry(e.name))
                    zos.write(processed)
                    zos.closeEntry()
                    onProgress(i + 1, entries.size)
                }
            }
            zip.close()
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(output)!!.use { o ->
                    outTmp.inputStream().use { it.copyTo(o) }
                }
            }
            outTmp.delete()
        } finally {
            rec.close()
            tr.close()
            tmp.delete()
        }
    }

    private suspend fun processPage(bmp: Bitmap, rec: TextRecognizer, tr: Translator): ByteArray {
        try {
            val img = InputImage.fromBitmap(bmp, 0)
            val result = rec.process(img).await()
            val canvas = Canvas(bmp)
            for (blk in result.textBlocks) {
                val srcText = blk.text.trim()
                if (srcText.isEmpty()) continue
                val t = try { tr.translate(srcText).await() } catch (_: Exception) { srcText }
                val r = Rect(blk.boundingBox!!).apply { inset(-4, -4); intersect(0, 0, bmp.width, bmp.height) }
                val bg = edgeColor(bmp, r)
                canvas.drawRect(r, Paint().apply { color = bg })
                val lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255
                drawFit(canvas, t, r, if (lum > 0.5) Color.BLACK else Color.WHITE)
            }
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            return out.toByteArray()
        } finally { bmp.recycle() }
    }

    private fun edgeColor(b: Bitmap, r: Rect): Int {
        var R = 0; var G = 0; var B = 0; var n = 0
        fun add(x: Int, y: Int) {
            val c = b.getPixel(x.coerceIn(0, b.width - 1), y.coerceIn(0, b.height - 1))
            R += Color.red(c); G += Color.green(c); B += Color.blue(c); n++
        }
        for (k in 0 until 20) {
            val x = r.left + (r.width() - 1) * k / 19
            val y = r.top + (r.height() - 1) * k / 19
            add(x, r.top); add(x, r.bottom - 1); add(r.left, y); add(r.right - 1, y)
        }
        return Color.rgb(R / n, G / n, B / n)
    }

    private fun drawFit(c: Canvas, text: String, r: Rect, color: Int) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; typeface = Typeface.DEFAULT_BOLD }
        val w = r.width().coerceAtLeast(20)
        var size = r.height().toFloat().coerceIn(10f, 64f)
        var lay: StaticLayout
        while (true) {
            p.textSize = size
            lay = StaticLayout.Builder.obtain(text, 0, text.length, p, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
            if (lay.height <= r.height() || size <= 8f) break
            size -= 2f
        }
        c.save(); c.translate(r.left.toFloat(), r.top + (r.height() - lay.height) / 2f); lay.draw(c); c.restore()
    }

    private fun natural(a: String, b: String): Int {
        val re = Regex("\\d+|\\D+")
        val x = re.findAll(a.lowercase()).map { it.value }.toList()
        val y = re.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val d = x[i][0].isDigit() && y[i][0].isDigit()
            val c = if (d) x[i].toBigInteger().compareTo(y[i].toBigInteger()) else x[i].compareTo(y[i])
            if (c != 0) return c
        }
        return x.size - y.size
    }
}
