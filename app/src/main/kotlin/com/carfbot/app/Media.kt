package com.carfbot.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Recent notifications, kept in memory only (never saved to disk, never sent anywhere unless you ask for a summary). */
object NotifStore {
    class Item(val app: String, val title: String, val text: String, val time: Long)

    private val items = ArrayDeque<Item>()

    @Synchronized fun add(i: Item) {
        items.addFirst(i)
        while (items.size > 60) items.removeLast()
    }

    @Synchronized fun recent(n: Int): List<Item> = items.take(n)

    @Synchronized fun clear() = items.clear()
}

/**
 * Enabling notification access for this service lets CarfBot read the title and artist of the music that is
 * playing, and build a short digest of your recent notifications.
 */
class CarfNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach { remember(it) }
        } catch (e: Exception) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) remember(sbn)
    }

    private fun remember(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (sbn.packageName == packageName) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.isOngoing) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        NotifStore.add(NotifStore.Item(app, title, text.take(300), sbn.postTime))
    }
}
