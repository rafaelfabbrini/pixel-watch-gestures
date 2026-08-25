package com.rfabbrini.wristmarkread.notification

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import com.rfabbrini.wristmarkread.LOG_TAG
import java.text.Normalizer
import java.util.Locale

/** An action paired with the place it was found, so diagnostics can tell the two apart. */
private typealias Candidate = Pair<Notification.Action, ActionOrigin>

/** A matched action, plus how it was recognised and where it came from. */
private typealias Match = Triple<Notification.Action, MatchSource, ActionOrigin>

/**
 * Finds a "mark as read" action on an arbitrary notification.
 *
 * Detection is deliberately generic - no app-specific (Gmail, WhatsApp or otherwise) knowledge is
 * used. Two independent axes are considered.
 *
 * **Where the action lives.** Two places are searched, because many apps publish watch-specific
 * actions that never appear in [Notification.actions]:
 *
 *  1. [Notification.actions] - the standard action list.
 *  2. The wearable extender bundle (`android.wearable.EXTENSIONS` → `actions`), which is what
 *     `NotificationCompat.WearableExtender.addAction()` writes. Notifications bridged from the
 *     phone routinely carry their "Mark as read" button *only* here, so a watch-side listener that
 *     reads [Notification.actions] alone sees nothing. Read directly from the bundle rather than
 *     through `NotificationCompat`, which keeps this dependency-free and yields real
 *     [Notification.Action] objects, so both sources flow through identical code below.
 *
 * **How the action is recognised.** Across *both* sources, in this order:
 *
 *  1. [Notification.Action.getSemanticAction] equal to
 *     [Notification.Action.SEMANTIC_ACTION_MARK_AS_READ]. This is the authoritative signal and
 *     always wins, wherever the action was found.
 *  2. Only if no semantic match exists anywhere, a normalised comparison of the action's visible
 *     label against a small set of known phrases. Labels are locale-dependent and unreliable,
 *     hence the strict ordering.
 */
object MarkAsReadDetector {

    /** `NotificationCompat.WearableExtender`'s bundle key inside `Notification.extras`. */
    private const val EXTRA_WEARABLE_EXTENSIONS = "android.wearable.EXTENSIONS"

    /** The action list key inside that bundle. */
    private const val KEY_WEARABLE_ACTIONS = "actions"

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
     *   notification has no compatible action in either source.
     */
    fun detect(sbn: StatusBarNotification): MarkReadTarget? {
        val notification = sbn.notification ?: return null

        val candidates = collectActions(notification)
        if (candidates.isEmpty()) return null

        val match = findSemanticMatch(candidates) ?: findLabelMatch(candidates) ?: return null
        val (action, matchSource, origin) = match

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
            actionOrigin = origin,
            postedAtMillis = sbn.postTime,
            actionIntent = actionIntent,
        )
    }

    /** Every action the notification exposes, standard list first, then wearable-only actions. */
    private fun collectActions(notification: Notification): List<Candidate> =
        notification.actions.orEmpty().map { it to ActionOrigin.NOTIFICATION_ACTIONS } +
            wearableActions(notification).map { it to ActionOrigin.WEARABLE_EXTENDER }

    /**
     * Reads the actions a sender attached via `NotificationCompat.WearableExtender`. Returns an
     * empty list - never throws - when the bundle is absent or cannot be unparcelled, since a
     * malformed notification from some other app must not take this listener down.
     */
    private fun wearableActions(notification: Notification): List<Notification.Action> {
        val wearableBundle = notification.extras?.getBundle(EXTRA_WEARABLE_EXTENSIONS) ?: return emptyList()
        return try {
            val actions: ArrayList<Notification.Action>? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    wearableBundle.getParcelableArrayList(
                        KEY_WEARABLE_ACTIONS,
                        Notification.Action::class.java,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    wearableBundle.getParcelableArrayList(KEY_WEARABLE_ACTIONS)
                }
            actions.orEmpty()
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "Could not read wearable extender actions", e)
            emptyList()
        }
    }

    private fun findSemanticMatch(candidates: List<Candidate>): Match? =
        candidates
            .firstOrNull { (action, _) ->
                action.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ
            }
            ?.let { (action, origin) -> Triple(action, MatchSource.SEMANTIC_ACTION, origin) }

    private fun findLabelMatch(candidates: List<Candidate>): Match? =
        candidates
            .firstOrNull { (action, _) -> normalize(action.title) in KNOWN_LABELS }
            ?.let { (action, origin) -> Triple(action, MatchSource.ACTION_LABEL, origin) }

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
