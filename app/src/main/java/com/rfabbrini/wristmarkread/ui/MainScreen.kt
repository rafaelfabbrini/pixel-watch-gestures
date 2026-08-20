package com.rfabbrini.wristmarkread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureAction
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureClickIndicator
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureClickIndicatorState
import androidx.wear.compose.material3.onehandedgesture.OneHandedGesturePriority
import androidx.wear.compose.material3.onehandedgesture.oneHandedGesture
import androidx.wear.compose.material3.onehandedgesture.rememberOneHandedGestureConfiguration
import com.rfabbrini.wristmarkread.gesture.GestureSupport
import com.rfabbrini.wristmarkread.notification.MarkReadResult
import com.rfabbrini.wristmarkread.notification.MarkReadUiState
import com.rfabbrini.wristmarkread.notification.MatchSource
import kotlinx.coroutines.launch

/** Identifies which input path triggered a mark-as-read, for logging only. */
const val SOURCE_GESTURE: String = "wrist-turn gesture"
const val SOURCE_BUTTON: String = "manual button"

@Composable
fun WristMarkReadApp(
    state: MarkReadUiState,
    gestureSupport: GestureSupport,
    onOpenNotificationSettings: () -> Unit,
    onMarkAsRead: (source: String) -> Unit,
) {
    MaterialTheme {
        val target = state.target
        val hasTarget = target != null

        val coroutineScope = rememberCoroutineScope()
        val interactionSource = remember { MutableInteractionSource() }
        val indicatorState = remember { OneHandedGestureClickIndicatorState() }

        // Wrist turn is the "dismiss" gesture of the Wear OS one-handed gesture framework.
        // Clickable priority makes the button win over any container handler.
        val buttonGesture = rememberOneHandedGestureConfiguration(
            action = OneHandedGestureAction.Dismiss,
            gestureId = "wristmarkread.mark_as_read.button",
            priority = OneHandedGesturePriority.Clickable,
        )
        // Registered only while there is no action to invoke, so the gesture still reaches
        // markCurrentNotificationAsRead() - which then deliberately does nothing.
        val screenGesture = rememberOneHandedGestureConfiguration(
            action = OneHandedGestureAction.Dismiss,
            gestureId = "wristmarkread.mark_as_read.screen",
            priority = OneHandedGesturePriority.Unspecified,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(
                    if (hasTarget) {
                        Modifier
                    } else {
                        Modifier.oneHandedGesture(
                            gestureConfiguration = screenGesture,
                            onGestureLabel = "mark as read",
                            onGesture = { onMarkAsRead(SOURCE_GESTURE) },
                        )
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(PaddingValues(horizontal = 14.dp, vertical = 28.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "WristMarkRead",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )

                StatusLine(
                    label = "Notification access",
                    value = if (state.notificationAccessGranted) "enabled" else "disabled",
                    ok = state.notificationAccessGranted,
                )
                if (!state.notificationAccessGranted) {
                    Text(
                        text = "WristMarkRead needs Notification Access to see notifications and " +
                            "their actions. Nothing ever leaves the watch.",
                        style = MaterialTheme.typography.bodyExtraSmall,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onOpenNotificationSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Grant access", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    StatusLine(
                        label = "Listener",
                        value = if (state.listenerConnected) "connected" else "waiting",
                        ok = state.listenerConnected,
                    )
                }

                SectionTitle("Latest notification")
                if (target == null) {
                    Text(
                        text = "No notification with Mark as Read available.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = target.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    target.title?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyExtraSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                    target.text?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyExtraSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                        )
                    }
                    StatusLine(label = "Mark as Read", value = "detected", ok = true)

                    // The manual button and the gesture below call the very same lambda, which
                    // calls the very same MarkReadRepository.markCurrentNotificationAsRead().
                    Button(
                        onClick = { onMarkAsRead(SOURCE_BUTTON) },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .oneHandedGesture(
                                gestureConfiguration = buttonGesture,
                                onGestureLabel = "mark as read",
                                interactionSource = interactionSource,
                                onGestureAvailable = {
                                    coroutineScope.launch { indicatorState.showIndicator() }
                                },
                                onGesture = { onMarkAsRead(SOURCE_GESTURE) },
                            ),
                    ) {
                        OneHandedGestureClickIndicator(buttonGesture, indicatorState) {
                            Text(
                                text = "Mark as read",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                SectionTitle("Wrist turn")
                StatusLine(
                    label = "Gesture",
                    value = when (gestureSupport) {
                        GestureSupport.SUPPORTED -> "available"
                        GestureSupport.UNSUPPORTED_OS_VERSION -> "needs Wear OS 7"
                        GestureSupport.NOT_A_WATCH -> "not a watch"
                    },
                    ok = gestureSupport.isSupported,
                )
                Text(
                    text = "Turn your wrist to mark the current notification as read.",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Works while this screen is open. Hand gestures must be enabled in " +
                        "Settings › Gestures.",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.lastResult?.let { result ->
                    SectionTitle("Last action")
                    val (message, ok) = when (result) {
                        is MarkReadResult.Invoked -> "sent to ${result.packageName}" to true
                        MarkReadResult.NoActionAvailable -> "nothing to mark as read" to false
                        is MarkReadResult.Failed -> result.reason to false
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        textAlign = TextAlign.Center,
                        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }

                DebugSection(state = state, gestureSupport = gestureSupport)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}

/** Optional diagnostics; handy when checking why a given app is or is not picked up. */
@Composable
private fun DebugSection(state: MarkReadUiState, gestureSupport: GestureSupport) {
    val target = state.target
    SectionTitle("Debug")
    val lines = buildList {
        add("package: ${target?.packageName ?: "-"}")
        add("key: ${target?.notificationKey ?: "-"}")
        add("semanticAction: ${target?.semanticAction?.toString() ?: "-"}")
        add(
            "matchedBy: " + when (target?.matchSource) {
                MatchSource.SEMANTIC_ACTION -> "SEMANTIC_ACTION_MARK_AS_READ"
                MatchSource.ACTION_LABEL -> "action label '${target.actionLabel}'"
                null -> "-"
            },
        )
        add("gestureSupport: $gestureSupport")
    }
    lines.forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodyExtraSmall,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 220, heightDp = 220)
@Composable
private fun MainScreenEmptyPreview() {
    WristMarkReadApp(
        state = MarkReadUiState(notificationAccessGranted = true, listenerConnected = true),
        gestureSupport = GestureSupport.SUPPORTED,
        onOpenNotificationSettings = {},
        onMarkAsRead = {},
    )
}
