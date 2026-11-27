package com.tripguide.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Evenly spaced frame times (µs) across a video, skipping the very start and end (often black or titles). */
fun frameTimesUs(durationMs: Long, n: Int = 8): List<Long> =
    if (durationMs <= 0) listOf(0L) else List(n) { i -> durationMs * 1000 * (2 * i + 1) / (2 * n) }

private fun Bitmap.fit(max: Int): Bitmap {
    val scale = max.toFloat() / maxOf(width, height)
    return if (scale < 1) Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true) else this
}

/** Frames from a video, or the picture itself for a photo. All on the phone. */
suspend fun mediaFrames(ctx: Context, uri: Uri, n: Int = 8): List<Bitmap> = withContext(Dispatchers.IO) {
    if (ctx.contentResolver.getType(uri)?.startsWith("image/") == true) {
        return@withContext listOfNotNull(ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.fit(1600))
    }
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(ctx, uri)
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        frameTimesUs(dur, n).mapNotNull { t -> r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.fit(1280) }
    } finally { r.release() }
}

/** Offline text reading (Tesseract) for English and Indian scripts. Language packs download once from GitHub. */
object Ocr {
    private fun root(ctx: Context) = File(ctx.filesDir, "ocr")
    private fun pack(ctx: Context, code: String) = File(root(ctx), "tessdata/$code.traineddata")
    fun installed(ctx: Context, code: String) = pack(ctx, code).length() > 0

    suspend fun download(ctx: Context, code: String) = withContext(Dispatchers.IO) {
        val f = pack(ctx, code).apply { parentFile!!.mkdirs() }
        val tmp = File(f.path + ".part")
        URL("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$code.traineddata").openStream().use { i -> tmp.outputStream().use { i.copyTo(it) } }
        check(tmp.length() > 100_000) { "Language pack $code didn't download completely." }
        tmp.renameTo(f)
    }

    fun delete(ctx: Context, code: String) = pack(ctx, code).delete()

    /** Text from all frames, with lines repeated across frames kept once. Unreadable fragments are dropped. */
    suspend fun read(ctx: Context, frames: List<Bitmap>, codes: List<String>, progress: (Int) -> Unit = {}): String = withContext(Dispatchers.Default) {
        val api = TessBaseAPI()
        try {
            check(api.init(root(ctx).absolutePath, codes.joinToString("+"))) { "Couldn't start text reading." }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT // overlays and signboards, not paragraphs
            val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
            frames.flatMapIndexed { i, bmp ->
                progress(i + 1)
                api.setImage(bmp)
                api.utF8Text // runs recognition; lines are then read with their confidence
                val it = api.resultIterator
                val lines = mutableListOf<String>()
                try {
                    it.begin()
                    do {
                        val line = it.getUTF8Text(level)?.trim().orEmpty()
                        if (Extract.ocrLineOk(line, it.confidence(level))) lines += Extract.normalize(line)
                    } while (it.next(level))
                } finally { it.delete() }
                lines
            }.distinctBy { it.lowercase() }.joinToString("\n")
        } finally { api.recycle() }
    }
}

/** Speech in a video, through the phone's own speech recognition (Android 13+). */
object Speech {
    val supported get() = Build.VERSION.SDK_INT >= 33
    private const val MAX_SECONDS = 180

    @RequiresApi(33)
    suspend fun transcribe(ctx: Context, uri: Uri, locale: String): String {
        check(SpeechRecognizer.isRecognitionAvailable(ctx)) { "No speech recognition on this phone. Install or update the Google app." }
        val ex = MediaExtractor().apply { setDataSource(ctx, uri, null) }
        val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: run { ex.release(); error("This video has no sound track.") }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val (read, write) = ParcelFileDescriptor.createPipe()
        // Decode the audio to 16-bit mono PCM and stream it into the recognizer through a pipe.
        val writer = CoroutineScope(Dispatchers.IO).launch {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { out -> runCatching { decodePcm(ex, fmt, rate) { out.write(it) } } }
            ex.release()
        }
        return try {
            withContext(Dispatchers.Main) { recognize(ctx, read, rate, locale) }
        } finally { writer.cancel(); read.close() }
    }

    private fun decodePcm(ex: MediaExtractor, fmt: MediaFormat, rate: Int, emit: (ByteArray) -> Unit) {
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var written = 0L
        val limit = rate.toLong() * 2 * MAX_SECONDS
        try {
            while (written < limit) {
                if (!inputDone) codec.dequeueInputBuffer(10_000).takeIf { it >= 0 }?.let { i ->
                    val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                if (o < 0) continue
                val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val frames = buf.remaining() / channels
                val mono = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
                repeat(frames) { var sum = 0; repeat(channels) { sum += buf.get() }; mono.putShort((sum / channels).toShort()) }
                emit(mono.array()); written += mono.capacity()
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } finally { codec.stop(); codec.release() }
    }

    @RequiresApi(33)
    private suspend fun recognize(ctx: Context, audio: ParcelFileDescriptor, rate: Int, locale: String): String =
        suspendCancellableCoroutine { cont ->
            val sr = SpeechRecognizer.createSpeechRecognizer(ctx)
            val parts = mutableListOf<String>()
            fun best(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            fun finish() { sr.destroy(); if (cont.isActive) cont.resume(parts.joinToString(" ")) }
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onSegmentResults(b: Bundle) { best(b)?.let(parts::add) }
                override fun onEndOfSegmentedSession() = finish()
                override fun onResults(b: Bundle?) { best(b)?.let(parts::add); finish() }
                override fun onError(code: Int) {
                    sr.destroy()
                    if (!cont.isActive) return
                    when {
                        parts.isNotEmpty() || code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> cont.resume(parts.joinToString(" "))
                        code == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || code == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            cont.resumeWithException(IllegalStateException("Your phone's speech recognition doesn't support this language. Try downloading it in the Google app's voice settings."))
                        code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> cont.resumeWithException(IllegalStateException("Microphone permission is needed for speech recognition."))
                        else -> cont.resumeWithException(IllegalStateException("Speech recognition failed (code $code)."))
                    }
                }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audio)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, rate)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE) // ends when the pipe closes
            })
            cont.invokeOnCancellation { sr.destroy() }
        }
}

/** Hand-offs to apps the user already has. */
object Share {
    /** Sends text and up to 4 frames to the user's AI app (Gemini, ChatGPT, Claude…) through the share sheet. */
    suspend fun toAi(ctx: Context, prompt: String, frames: List<Bitmap>) {
        val uris = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
            frames.take(4).mapIndexed { i, b ->
                val f = File(dir, "frame_$i.jpg").apply { outputStream().use { b.fit(1024).compress(Bitmap.CompressFormat.JPEG, 80, it) } }
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            }
        }
        val send = if (uris.isEmpty()) Intent(Intent.ACTION_SEND).setType("text/plain")
        else Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)).apply {
            clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send.putExtra(Intent.EXTRA_TEXT, prompt), "Ask your AI app"))
    }

    /** Google Translate app if installed, else translate.google.com. Auto-detects the source language. */
    fun translate(ctx: Context, text: String) {
        val app = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage("com.google.android.apps.translate")
        try { ctx.startActivity(app) } catch (_: ActivityNotFoundException) {
            Util.open(ctx, "https://translate.google.com/?sl=auto&tl=en&op=translate&text=" + Uri.encode(text.take(4000)))
        }
    }
}
