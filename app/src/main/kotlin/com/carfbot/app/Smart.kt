package com.carfbot.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Smarter commands: music control, lyrics, now playing, clipboard tools, screenshot text. */
object Smart {
    private val pauseR = Regex("^(?:please )?(?:pause|stop)(?: (?:the )?(?:music|song|playback|audio|video|it))?$")
    private val resumeR = Regex("^(?:please )?(?:resume|continue|play)(?: (?:the )?(?:music|song|playback|audio))?$")
    private val nextR = Regex("^(?:please )?(?:next|skip)(?: (?:the )?(?:song|track|music))?$")
    private val prevR = Regex("^(?:please )?(?:previous|prev|go back|play previous)(?: (?:the )?(?:song|track))?$")
    private val nowR = Regex("^(?:what(?:'s| is) (?:currently )?playing(?: now| right now)?|what song is (?:this|playing)|what(?:'s| is) this song|(?:current|now playing)(?: song| track)?)$")
    private val identR = Regex("^(?:identify|recognize|recognise|shazam)(?: (?:this|the|that))?(?: (?:song|music|track))?$")
    private val lyricsR = Regex("^(?:show |get |find |open )?(?:the )?lyrics(?: (?:for|of|to) (.+))?$")
    private val clipShowR = Regex("^(?:what'?s on|what is on|grab|show|read|get)(?: (?:my|the))? clipboard$")
    private val clipSumR = Regex("^summari[sz]e(?: (?:my|the))? clipboard$")
    private val clipTransR = Regex("^translate(?: (?:my|the))? (?:clipboard|this|that|it)(?: (?:to|into) (.+))?$")
    private val clipOrgR = Regex("^organi[sz]e(?: (?:my|the))? clipboard$")
    private val shotSumR = Regex("^summari[sz]e(?: (?:my|the))?(?: (?:latest|last|recent))? (screenshot|photo|picture|image)$")
    private val shotTextR = Regex("^(?:extract|read|get|copy|scan)(?: (?:the|all))? text (?:from|in|of)(?: (?:my|the))?(?: (?:latest|last|recent))? (screenshot|photo|picture|image)$")
    private val shotOcrR = Regex("^(?:ocr|scan)(?: (?:my|the))?(?: (?:latest|last|recent))? (screenshot|photo|picture|image)$")

    private const val NO_CLIP =
        "I can’t see anything in your clipboard. Copy some text first, and keep CarfBot open on screen " +
        "(Android only lets the visible app read the clipboard)."

    /** Returns a reply if this was a smart command, otherwise null. */
    suspend fun handle(ctx: Context, input: String): String? {
        val l = input.trim().replace(Regex("[.!?]+$"), "").replace('’', '\'').lowercase()
        if (l.isEmpty()) return null

        // ----- music control (works with any music app, no setup) -----
        if (pauseR.matches(l)) return mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PAUSE, "Paused")
        if (resumeR.matches(l)) return mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY, "Playing")
        if (nextR.matches(l)) return mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT, "Next track")
        if (prevR.matches(l)) return mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous track")

        // ----- now playing / identify / lyrics -----
        if (nowR.matches(l)) return describe(ctx)
        if (identR.matches(l)) {
            if (listenerOn(ctx)) track(ctx)?.let { return "That looks like: " + it.label() }
            if (Actions.launch(ctx, Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH")))
                return "Listening for music with Google Sound Search…"
            return Actions.openApp(ctx, "shazam")
        }
        lyricsR.matchEntire(l)?.let { m ->
            val q = m.groupValues[1].ifBlank { null } ?: track(ctx)?.label()
            if (q == null) {
                if (!listenerOn(ctx)) {
                    Actions.launch(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    return "Turn on notification access for CarfBot so I can see the song, or say “lyrics for <song>”."
                }
                return "I can’t tell what’s playing. Say “lyrics for <song>”."
            }
            Actions.launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode("$q lyrics"))))
            return "Opening lyrics for $q"
        }

        // ----- clipboard -----
        if (clipShowR.matches(l)) {
            val t = clip(ctx) ?: return NO_CLIP
            return "Your clipboard:\n" + t.take(800)
        }
        if (clipSumR.matches(l)) {
            val t = clip(ctx) ?: return NO_CLIP
            return AiClient.once(ctx, "Summarize this text in a few short bullet points:\n\n" + t.take(6000))
        }
        clipTransR.matchEntire(l)?.let { m ->
            val t = clip(ctx) ?: return NO_CLIP
            val lang = m.groupValues[1].ifBlank { "English" }
            return AiClient.once(ctx, "Translate the following text to $lang. Reply with only the translation:\n\n" + t.take(6000))
        }
        if (clipOrgR.matches(l)) {
            val t = clip(ctx) ?: return NO_CLIP
            return AiClient.once(ctx, "Organize this text into clean, tidy notes with short headings or bullets:\n\n" + t.take(6000))
        }

        // ----- screenshots / images -----
        shotSumR.matchEntire(l)?.let { m ->
            return withImageText(ctx, m.groupValues[1]) { text ->
                if (!AiClient.hasKey(ctx)) "Text found in the image:\n\n" + text.take(900) +
                    "\n\n(Add your API key in Settings to get a summary.)"
                else AiClient.once(ctx, "Summarize the key points of this text taken from a screenshot:\n\n" + text.take(6000))
            }
        }
        val ex = shotTextR.matchEntire(l) ?: shotOcrR.matchEntire(l)
        if (ex != null) {
            val kind = ex.groupValues[1]
            return withImageText(ctx, kind) { text ->
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("CarfBot", text))
                "Copied the text from your $kind:\n\n" + text.take(900)
            }
        }
        return null
    }

    // ---------------- helpers ----------------

    private fun mediaKey(ctx: Context, code: Int, reply: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        Haptics.click(ctx)
        return reply
    }

    private class Track(val title: String, val artist: String?) {
        fun label() = if (artist.isNullOrBlank()) title else "$title $artist"
    }

    private fun listenerOn(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    private fun track(ctx: Context): Track? {
        try {
            val mm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = mm.getActiveSessions(ComponentName(ctx, CarfNotificationListener::class.java))
            val c = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull() ?: return null
            val md = c.metadata ?: return null
            val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (title.isNullOrBlank()) return null
            return Track(title, md.getString(MediaMetadata.METADATA_KEY_ARTIST))
        } catch (e: Exception) {
            return null
        }
    }

    private fun describe(ctx: Context): String {
        if (!listenerOn(ctx)) {
            Actions.launch(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return "To see what’s playing, turn on notification access for CarfBot, then ask again."
        }
        val t = track(ctx) ?: return "Nothing is playing right now."
        return "Now playing: " + t.title + (t.artist?.let { " by $it" } ?: "")
    }

    private fun clip(ctx: Context): String? {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val c = cm.primaryClip ?: return null
        if (c.itemCount == 0) return null
        val t = c.getItemAt(0).coerceToText(ctx)?.toString()
        return t?.takeIf { it.isNotBlank() }
    }

    private suspend fun withImageText(ctx: Context, kind: String, block: suspend (String) -> String): String {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ctx.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED)
            return "Allow photo access for CarfBot first (Settings > Apps > CarfBot > Permissions)."
        val uri = latestImage(ctx, kind == "screenshot") ?: return "I couldn’t find a recent $kind."
        val text = ocr(ctx, uri)
        if (text.isBlank()) return "I couldn’t find any text in that $kind."
        return block(text)
    }

    /** Newest image on the phone (or the newest screenshot). */
    fun latestImage(ctx: Context, screenshot: Boolean): Uri? {
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return try {
            val sel = if (!screenshot) null else if (Build.VERSION.SDK_INT >= 29) "relative_path LIKE ?" else "_data LIKE ?"
            val args = if (!screenshot) null else arrayOf("%Screenshots%")
            ctx.contentResolver.query(base, arrayOf("_id"), sel, args, "date_added DESC")?.use { c ->
                if (c.moveToFirst()) ContentUris.withAppendedId(base, c.getLong(0)) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun ocr(ctx: Context, uri: Uri): String = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(ctx, uri)
            val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            rec.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        } catch (e: Exception) {
            cont.resume("")
        }
    }
}
