package com.rfabbrini.wristmarkread.notification

import android.app.PendingIntent

/** How a notification action was recognised as "mark as read". */
enum class MatchSource {
    /** [android.app.Notification.Action.getSemanticAction] reported SEMANTIC_ACTION_MARK_AS_READ. */
    SEMANTIC_ACTION,

    /** No semantic metadata; the action's visible label matched a known "mark as read" phrase. */
    ACTION_LABEL,
}

/** Where on the notification the matched action was found. */
enum class ActionOrigin {
    /** The standard [android.app.Notification.actions] list. */
    NOTIFICATION_ACTIONS,

    /**
     * The `android.wearable.EXTENSIONS` bundle written by
     * `NotificationCompat.WearableExtender`. Watch-only actions live here and are invisible to
     * code that reads [android.app.Notification.actions] alone - which is how most bridged
     * phone notifications expose their "Mark as read" button.
     */
    WEARABLE_EXTENDER,
}

/**
 * An immutable snapshot of the newest notification that exposes a Mark-as-Read action.
 *
 * [actionIntent] is the *original* [PendingIntent] published by the source app. Sending it runs
 * inside that app's identity, which is what actually marks the underlying item as read - this app
 * never needs to know anything about the source app's API.
 */
data class MarkReadTarget(
    val notificationKey: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    /** The visible label of the matched action, e.g. "Mark as read". */
    val actionLabel: String,
    /** Raw value of [android.app.Notification.Action.getSemanticAction] for the matched action. */
    val semanticAction: Int,
    val matchSource: MatchSource,
    /** Which of the notification's two action lists the match came from. */
    val actionOrigin: ActionOrigin,
    val postedAtMillis: Long,
    val actionIntent: PendingIntent,
)
