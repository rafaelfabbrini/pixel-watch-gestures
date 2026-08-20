package com.rfabbrini.wristmarkread.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.util.Log
import com.rfabbrini.wristmarkread.LOG_TAG
import java.text.Normalizer
import java.util.Locale

/**
 * Finds a "mark as read" action on an arbitrary notification.
 *
 * Detection is deliberately generic - no app-specific (Gmail or otherwise) knowledge is used:
 *
 *  1. [Notification.Action.getSemanticAction] equal to
 *     [Notification.Action.SEMANTIC_ACTION_MARK_AS_READ]. This is the authoritative signal and
 *     always wins.
 *  2. Only if no semantic match exists, a normalised comparison of the action's visible label
 *     against a small set of known phrases. Labels are locale-dependent and unreliable, hence the
 *     strict ordering.
 */
object MarkAsReadDetector {

    /**
     * Accent-free, lower-case phrases. Compared against a label normalised the same way, so
     * "Marcar como lida" and "MARCAR COMO LÍDA" both match "marcar como lida".
     */
    private val KNOWN_LABELS: Set<String> = setOf(
        // English
        "mark as read",
        "mark read",
        // Portuguese
        "marcar como lida",
        "marcar como lido",
        // Spanish
        "marcar como leida",
        "marcar como leido",
    )

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")

    /**
     * @return a [MarkReadTarget] describing [sbn]'s mark-as-read action, or `null` when the
     *   notification has no compatible action.
     */
    fun detect(sbn: StatusBarNotification): MarkReadTarget? {
        val notification = sbn.notification ?: return null
        val actions = notification.actions
        if (actions.isNullOrEmpty()) return null

        val match = findSemanticMatch(actions) ?: findLabelMatch(actions) ?: return null
        val (action, matchSource) = match

        val actionIntent = action.actionIntent
        if (actionIntent == null) {
            Log.w(LOG_TAG, "Ignoring mark-as-read action without a PendingIntent (${sbn.packageName})")
            return null
        }

        if (!action.remoteInputs.isNullOrEmpty()) {
            // A mark-as-read action normally carries no RemoteInput. If one shows up we still
            // invoke the intent, but say so, because the source app may expect extra input.
            Log.w(
                LOG_TAG,
                "Mark-as-read action from ${sbn.packageName} declares ${action.remoteInputs.size} " +
                    "RemoteInput(s); sending the PendingIntent without any",
            )
        }

        val extras = notification.extras
        return MarkReadTarget(
            notificationKey = sbn.key,
            packageName = sbn.packageName,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            actionLabel = action.title?.toString().orEmpty(),
            semanticAction = action.semanticAction,
            matchSource = matchSource,
            postedAtMillis = sbn.postTime,
            actionIntent = actionIntent,
        )
    }

    private fun findSemanticMatch(
        actions: Array<Notification.Action>,
    ): Pair<Notification.Action, MatchSource>? =
        actions
            .firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }
            ?.let { it to MatchSource.SEMANTIC_ACTION }

    private fun findLabelMatch(
        actions: Array<Notification.Action>,
    ): Pair<Notification.Action, MatchSource>? =
        actions
            .firstOrNull { normalize(it.title) in KNOWN_LABELS }
            ?.let { it to MatchSource.ACTION_LABEL }

    /** Lower-cases, strips diacritics and collapses whitespace so labels compare reliably. */
    private fun normalize(label: CharSequence?): String {
        if (label.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(label, Normalizer.Form.NFD)
        return COMBINING_MARKS.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .trim()
            .replace(WHITESPACE, " ")
    }
}
