package com.carfbot.app

import android.service.notification.NotificationListenerService

/**
 * Empty on purpose: enabling notification access for this service is what lets CarfBot read the
 * title and artist of whatever music app is playing (MediaSessionManager requires it).
 */
class CarfNotificationListener : NotificationListenerService()
