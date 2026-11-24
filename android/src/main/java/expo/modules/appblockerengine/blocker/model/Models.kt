package expo.modules.appblockerengine.blocker.model

import java.util.Calendar

// A daily stretch, in minutes after local midnight, that blocks even while the
// blocker is paused. endMinute is exclusive; 1440 is midnight.
data class BlockWindow(
    val startMinute: Int,
    val endMinute: Int
) {
    fun isValid(): Boolean = startMinute in 0 until endMinute && endMinute <= MINUTES_PER_DAY

    companion object {
        const val MINUTES_PER_DAY = 1440
    }
}

data class BlockerState(
    val isBlocking: Boolean = false,
    val blockedApps: List<String> = emptyList(),
    val blockAll: Boolean = false,
    val scheduledTime: String? = null,
    val scheduledAtMillis: Long? = null,
    val scheduleActivated: Boolean = false,
    val excludeApps: List<String> = emptyList(),
    val pausedUntilMillis: Long? = null,
    val windows: List<BlockWindow> = emptyList(),
    val windowsPausedUntilMillis: Long? = null
) {
    fun isPausedAt(nowMillis: Long): Boolean =
        pausedUntilMillis != null && nowMillis < pausedUntilMillis

    fun isInWindowAt(nowMillis: Long): Boolean {
        if (windowsPausedUntilMillis != null && nowMillis < windowsPausedUntilMillis) return false
        val minute = minuteOfDay(nowMillis)
        return windows.any { minute >= it.startMinute && minute < it.endMinute }
    }

    // A window holds the apps blocked through a pause. Only that pause opens
    // the apps: pausing the windows lets them through it, nothing more.
    fun isHeldOpenAt(nowMillis: Long): Boolean =
        isPausedAt(nowMillis) && !isInWindowAt(nowMillis)

    // So a paused service wakes when the next window starts, not up to a poll
    // interval later. Null with no windows.
    fun millisUntilNextWindowStart(nowMillis: Long): Long? {
        if (windows.isEmpty()) return null
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val secondOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
            calendar.get(Calendar.MINUTE) * 60 + calendar.get(Calendar.SECOND)
        return windows.minOf { window ->
            val startSecond = window.startMinute * 60
            val wait = if (startSecond > secondOfDay) startSecond - secondOfDay
            else startSecond + BlockWindow.MINUTES_PER_DAY * 60 - secondOfDay
            wait.toLong() * 1000
        }
    }

    companion object {
        fun minuteOfDay(nowMillis: Long): Int {
            val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
            return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        }
    }
}

data class OverlayConfig(
    val title: String = "App Blocked",
    val message: String? = null,
    val description: String? = null,
    val backgroundColor: String = "#1A1A1A",
    val textColor: String = "#FFFFFF",
    val titleTextSize: Float = 32f,
    val messageTextSize: Float = 18f,
    val descriptionTextSize: Float = 16f,
    val showAppIcon: Boolean = true,
    val showAppName: Boolean = true,
    val showUsageStats: Boolean = false,
    val showTodayUsage: Boolean = false,
    val blockerAppName: String? = null,
    val buttonText: String? = null,
    val buttonLink: String? = null,
    val buttonColor: String = "#4CAF50",
    val buttonTextColor: String = "#FFFFFF",
    val buttonBorderRadius: Float = 50f,
    val buttonWidth: Float = 280f,
    val buttonHeight: Float = 60f,
    val buttonMarginTop: Float = 40f,
    val showCloseButton: Boolean = false,
    val closeButtonColor: String = "#666666",
    val notificationTitle: String = DEFAULT_NOTIFICATION_TITLE,
    val notificationText: String = DEFAULT_NOTIFICATION_TEXT,
    val notificationChannelName: String = DEFAULT_NOTIFICATION_CHANNEL_NAME
) {
    companion object {
        const val DEFAULT_NOTIFICATION_TITLE = "App Blocker Active"
        const val DEFAULT_NOTIFICATION_TEXT = "Monitoring apps in background"
        const val DEFAULT_NOTIFICATION_CHANNEL_NAME = "App Blocker"
    }
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val iconBase64: String?,
    val usageTime: Long
)

data class AppUsageStats(
    val packageName: String,
    val appName: String,
    val iconBase64: String?,
    val usageTime: Long,
    val lastTimeUsed: Long
)

data class ScreenSession(
    val startMillis: Long,
    val endMillis: Long
)

fun String.toColorInt(): Int {
    val hex = this.removePrefix("#")
    return android.graphics.Color.parseColor("#$hex")
}
