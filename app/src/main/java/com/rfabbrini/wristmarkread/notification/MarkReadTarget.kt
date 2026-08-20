package com.rfabbrini.wristmarkread.notification

import android.app.PendingIntent

/** How a notification action was recognised as "mark as read". */
enum class MatchSource {
    /** [android.app.Notification.Action.getSemanticAction] reported SEMANTIC_ACTION_MARK_AS_READ. */
    SEMANTIC_ACTION,

    /** No semantic metadata; the action's visible label matched a known "mark as read" phrase. */
    ACTION_LABEL,
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
    val postedAtMillis: Long,
    val actionIntent: PendingIntent,
)
