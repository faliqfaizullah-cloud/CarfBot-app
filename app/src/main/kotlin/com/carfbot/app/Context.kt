package com.carfbot.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Calendar

data class Pill(val label: String, val icon: ImageVector, val command: String, val run: Boolean)

/** Time-of-day awareness for the home screen. */
object Ctx {
    fun hour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    /** 0 = morning, 1 = afternoon, 2 = evening / night */
    private fun period(h: Int): Int = when (h) {
        in 5..11 -> 0
        in 12..17 -> 1
        else -> 2
    }

    fun pills(h: Int = hour()): List<Pill> {
        val contextual = when (period(h)) {
            0 -> listOf(
                Pill("Music", Icons.Outlined.MusicNote, "play music", true),
                Pill("Calendar", Icons.Outlined.CalendarMonth, "open calendar", true),
                Pill("Weather", Icons.Outlined.WbSunny, "search weather today", true)
            )
            1 -> listOf(
                Pill("Camera", Icons.Outlined.CameraAlt, "open camera", true),
                Pill("Maps", Icons.Outlined.Map, "open maps", true),
                Pill("Mail", Icons.Outlined.Email, "open email", true)
            )
            else -> listOf(
                Pill("Reading", Icons.Outlined.MenuBook, "open books", true),
                Pill("Notes", Icons.Outlined.Edit, "open notes", true),
                Pill("Alarm", Icons.Outlined.Alarm, "open alarm", true)
            )
        }
        val base = listOf(
            Pill("Apps", Icons.Outlined.Apps, "open ", false),
            Pill("Files", Icons.Outlined.Description, "find file ", false),
            Pill("Songs", Icons.Outlined.MusicNote, "play ", false),
            Pill("Contacts", Icons.Outlined.Person, "call ", false)
        )
        return contextual + base
    }

    fun tips(h: Int = hour()): List<Tip> {
        val contextual = when (period(h)) {
            0 -> listOf(
                Tip("Good morning", "Morning routine", "Start your day",
                    "Play your music or check today’s calendar with one tap.", "play music",
                    listOf("Play music" to "play music", "Calendar" to "open calendar")),
                Tip("Lyrics & music", "Songs", "Now playing",
                    "Say “lyrics” for the song that’s playing, or “next song” to skip.", "lyrics",
                    listOf("Lyrics" to "lyrics", "Next song" to "next song"))
            )
            1 -> listOf(
                Tip("Chain commands", "Macros", "Do it all at once",
                    "Say “open camera, take a photo and share it on WhatsApp” in one go.",
                    "open camera, take a photo and share it on whatsapp",
                    listOf("Try it" to "open camera, take a photo and share it on whatsapp")),
                Tip("Clipboard", "Smart tools", "Make sense of it",
                    "Copy any text, then say “summarize clipboard” or “translate clipboard to Spanish”.",
                    "summarize clipboard",
                    listOf("Summarize" to "summarize clipboard", "Translate" to "translate clipboard to english"))
            )
            else -> listOf(
                Tip("Wind down", "Evening", "Read or jot it down",
                    "Open your reading app or notes with one tap.", "open books",
                    listOf("Reading" to "open books", "Notes" to "open notes")),
                Tip("Screenshots", "Smart tools", "Read any screenshot",
                    "Say “summarize my screenshot” or “extract text from my screenshot”.",
                    "summarize my screenshot",
                    listOf("Summarize" to "summarize my screenshot", "Extract text" to "extract text from my screenshot"))
            )
        }
        val base = listOf(
            Tip("Open any app", "Today’s command", "Today’s command",
                "Say “open camera” and CarfBot launches it for you, no hunting through your app drawer.",
                "open camera", listOf("Open camera" to "open camera", "Maps" to "open maps")),
            Tip("Call a contact", "Contacts", "Reach anyone",
                "Say “call Mom” and I’ll find her in your contacts and get the dialer ready.",
                "call mom", listOf("Call" to "call ")),
            Tip("Find a file", "Files", "Find it fast",
                "Say “find photo beach” or “find document invoice” and I’ll open the match.",
                "find photo beach", listOf("Find photo" to "find photo "))
        )
        return contextual + base
    }
}
