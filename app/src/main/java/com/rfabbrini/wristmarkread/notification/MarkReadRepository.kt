package com.rfabbrini.wristmarkread.notification

import android.app.PendingIntent
import android.util.Log
import com.rfabbrini.wristmarkread.LOG_TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Outcome of a single [MarkReadRepository.markCurrentNotificationAsRead] call. */
sealed interface MarkReadResult {
    /** The original PendingIntent was handed to the system. */
    data class Invoked(val packageName: String) : MarkReadResult

    /** No notification with a mark-as-read action is currently known - by design, a no-op. */
    data object NoActionAvailable : MarkReadResult

    /** The PendingIntent was already cancelled by the source app, or sending it failed. */
    data class Failed(val reason: String) : MarkReadResult
}

/** Everything the UI renders. */
data class MarkReadUiState(
    val notificationAccessGranted: Boolean = false,
    val listenerConnected: Boolean = false,
    val target: MarkReadTarget? = null,
    val lastResult: MarkReadResult? = null,
)

/**
 * In-memory holder for the newest notification that exposes a mark-as-read action.
 *
 * Deliberately not persisted: a PendingIntent is only meaningful while the notification that
 * carries it is alive, so there is nothing worth surviving a reboot. When the listener service is
 * (re)connected it rebuilds this state from
 * [android.service.notification.NotificationListenerService.getActiveNotifications].
 */
object MarkReadRepository {

    private val _uiState = MutableStateFlow(MarkReadUiState())
    val uiState: StateFlow<MarkReadUiState> = _uiState.asStateFlow()

    /**
     * The single place where a mark-as-read action is invoked.
     *
     * Both the manual button and the wrist-turn gesture call exactly this function, so the two
     * paths can never drift apart.
     */
    fun markCurrentNotificationAsRead(source: String): MarkReadResult {
        val target = _uiState.value.target
        if (target == null) {
            Log.i(LOG_TAG, "markCurrentNotificationAsRead($source): no mark-as-read action available, doing nothing")
            return publish(MarkReadResult.NoActionAvailable)
        }

        return try {
            Log.i(
                LOG_TAG,
                "markCurrentNotificationAsRead($source): sending PendingIntent for " +
                    "${target.packageName} key=${target.notificationKey} label='${target.actionLabel}'",
            )
            target.actionIntent.send()
            Log.i(LOG_TAG, "PendingIntent invoked successfully for ${target.packageName}")
            publish(MarkReadResult.Invoked(target.packageName))
        } catch (e: PendingIntent.CanceledException) {
            Log.w(LOG_TAG, "PendingIntent for ${target.packageName} was cancelled; dropping stale target", e)
            // The action is gone for good - forget it so the UI stops offering it.
            _uiState.update { if (it.target?.notificationKey == target.notificationKey) it.copy(target = null) else it }
            publish(MarkReadResult.Failed("PendingIntent cancelled"))
        }
    }

    fun setNotificationAccessGranted(granted: Boolean) {
        _uiState.update { if (it.notificationAccessGranted == granted) it else it.copy(notificationAccessGranted = granted) }
    }

    fun setListenerConnected(connected: Boolean) {
        Log.i(LOG_TAG, "Notification listener connected=$connected")
        _uiState.update {
            if (connected) it.copy(listenerConnected = true)
            // A disconnected listener means every PendingIntent we hold may be stale.
            else it.copy(listenerConnected = false, target = null)
        }
    }

    /** Replaces the current target; `null` clears it. Used after a full rescan. */
    fun setTarget(target: MarkReadTarget?) {
        if (target == null) {
            Log.i(LOG_TAG, "No notification with a mark-as-read action is currently active")
        } else {
            Log.i(
                LOG_TAG,
                "Current mark-as-read target: pkg=${target.packageName} key=${target.notificationKey} " +
                    "match=${target.matchSource} semanticAction=${target.semanticAction} label='${target.actionLabel}'",
            )
        }
        _uiState.update { it.copy(target = target) }
    }

    /** @return the key of the currently tracked notification, if any. */
    fun currentKey(): String? = _uiState.value.target?.notificationKey

    private fun publish(result: MarkReadResult): MarkReadResult {
        _uiState.update { it.copy(lastResult = result) }
        return result
    }
}
