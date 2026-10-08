package com.carfbot.app

import android.Manifest
import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore

/** Local command engine: turns typed/spoken requests into real device actions. */
object Actions {

    /** True if the last handled command opened another app/screen. */
    @Volatile var launched = false

    fun permissions(): Array<String> {
        val base = mutableListOf(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) {
            base += listOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else base += Manifest.permission.READ_EXTERNAL_STORAGE
        return base.toTypedArray()
    }

    /** Returns a reply if the request was handled on-device, or null to hand it to the AI. */
    fun handle(ctx: Context, input: String): String? {
        launched = false
        val l = input.trim().replace(Regex("[.!?]+$"), "").lowercase()
        if (l.isEmpty()) return null

        Regex("^(?:please\\s+)?(?:find|search for|show|open|get|play)\\s+(?:my\\s+|a\\s+|the\\s+)?(files?|documents?|docs?|pdfs?|photos?|pictures?|images?|videos?|songs?|music|audio)\\s+(?:called\\s+|named\\s+)?(.+)$")
            .find(l)?.let { return findMedia(ctx, it.groupValues[1], it.groupValues[2]) }

        Regex("^(?:please\\s+)?(?:call|dial|phone|ring)\\s+(.+)$").find(l)
            ?.let { return call(ctx, it.groupValues[1]) }

        Regex("^(?:please\\s+)?(?:open\\s+)?contact\\s+(.+)$").find(l)
            ?.let { return openContact(ctx, it.groupValues[1]) }

        Regex("^(?:please\\s+)?(?:play|listen to)\\s+(.+)$").find(l)
            ?.let { return play(ctx, it.groupValues[1]) }

        Regex("^(?:please\\s+)?(?:navigate|directions|take me|go|drive) to\\s+(.+)$").find(l)
            ?.let { return navigate(ctx, it.groupValues[1]) }

        Regex("^(?:please\\s+)?(?:open|launch|start|run)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$").find(l)
            ?.let { return openApp(ctx, it.groupValues[1]) }

        Regex("^(?:please\\s+)?(?:google|search the web for|search for|search|look up)\\s+(.+)$").find(l)
            ?.let { return webSearch(ctx, it.groupValues[1]) }

        return null
    }

    private fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        launched = true
        Haptics.confirm(ctx)
        true
    } catch (e: Exception) { false }

    private fun has(ctx: Context, p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun mediaPerm(type: String): String =
        if (Build.VERSION.SDK_INT >= 33) when (type) {
            "audio" -> Manifest.permission.READ_MEDIA_AUDIO
            "video" -> Manifest.permission.READ_MEDIA_VIDEO
            else -> Manifest.permission.READ_MEDIA_IMAGES
        } else Manifest.permission.READ_EXTERNAL_STORAGE

    // ---------- Apps ----------
    private fun openApp(ctx: Context, name: String): String {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val q = name.trim()
        val best = pm.queryIntentActivities(main, 0)
            .map { it to it.loadLabel(pm).toString().lowercase() }
            .filter { (_, label) -> label.contains(q) || (label.length >= 3 && q.contains(label)) }
            .minWithOrNull(compareBy(
                { (_, label) -> if (label == q) 0 else if (label.startsWith(q)) 1 else 2 },
                { (_, label) -> kotlin.math.abs(label.length - q.length) }
            ))
        if (best != null) {
            val pkg = best.first.activityInfo.packageName
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null && start(ctx, launch)) return "Opening ${best.second.replaceFirstChar { it.uppercase() }}"
        }
        return "I couldn't find an app called \"$q\" on this phone."
    }

    // ---------- Contacts ----------
    private fun call(ctx: Context, who: String): String {
        val digits = who.replace(Regex("[\\s-]"), "")
        if (digits.matches(Regex("\\+?\\d{3,}"))) {
            start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
            return "Dialing $digits"
        }
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return "Allow contacts access so I can find \"$who\"."
        val p = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        ctx.contentResolver.query(
            p,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$who%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)
                val num = c.getString(1)
                start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(num)}")))
                return "Calling $name"
            }
        }
        return "I couldn't find a contact named \"$who\"."
    }

    private fun openContact(ctx: Context, who: String): String {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return "Allow contacts access so I can find \"$who\"."
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME),
            "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?",
            arrayOf("%$who%"), null
        )?.use { c ->
            if (c.moveToFirst()) {
                val uri = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                start(ctx, Intent(Intent.ACTION_VIEW, uri))
                return "Opening ${c.getString(2)}"
            }
        }
        return "I couldn't find a contact named \"$who\"."
    }

    // ---------- Music ----------
    private fun play(ctx: Context, q: String): String {
        localMedia(ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio", q, titleCols = listOf("title", "artist"))
            ?.let { (title, uri) ->
                if (start(ctx, Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))) return "Playing $title"
            }
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, q)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        if (start(ctx, i)) return "Searching \"$q\" in your music app"
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")))
        return "Opening YouTube results for \"$q\""
    }

    // ---------- Files, photos, videos, documents ----------
    private fun findMedia(ctx: Context, kind: String, q: String): String {
        val k = kind.lowercase()
        if (k.startsWith("song") || k == "music" || k == "audio") return play(ctx, q)
        val (uri, type, mime) = when {
            k.startsWith("photo") || k.startsWith("picture") || k.startsWith("image") ->
                Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image", "image/*")
            k.startsWith("video") -> Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video", "video/*")
            else -> Triple(MediaStore.Files.getContentUri("external"), "file", "*/*")
        }
        localMedia(ctx, uri, type, q, titleCols = listOf("_display_name"))?.let { (name, u) ->
            val m = ctx.contentResolver.getType(u) ?: mime
            if (start(ctx, Intent(Intent.ACTION_VIEW).setDataAndType(u, m)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))) return "Opening $name"
        }
        // Documents (PDF, DOCX…) are hidden from apps by Android, so hand off to the system file picker.
        start(ctx, Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))
        return "I couldn't match \"$q\" directly, so I opened your files. Search for it there."
    }

    private fun localMedia(
        ctx: Context, base: Uri, type: String, q: String, titleCols: List<String>
    ): Pair<String, Uri>? {
        if (!has(ctx, mediaPerm(type))) return null
        val nameCol = titleCols.first()
        val where = titleCols.joinToString(" OR ") { "$it LIKE ?" }
        return try {
            ctx.contentResolver.query(
                base, arrayOf("_id", nameCol), where,
                Array(titleCols.size) { "%$q%" }, "$nameCol ASC"
            )?.use { c ->
                if (c.moveToFirst()) c.getString(1) to ContentUris.withAppendedId(base, c.getLong(0)) else null
            }
        } catch (e: Exception) { null }
    }

    // ---------- Maps & web ----------
    private fun navigate(ctx: Context, place: String): String {
        return if (start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}"))))
            "Showing $place on the map" else "No maps app found."
    }

    private fun webSearch(ctx: Context, q: String): String {
        val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
        if (!start(ctx, i)) start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}")))
        return "Searching the web for \"$q\""
    }

    fun openAssistantSettings(ctx: Context) {
        val options = listOf(
            Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (o in options) if (start(ctx, o)) return
    }
}
