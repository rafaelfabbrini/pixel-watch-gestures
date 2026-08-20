# WristMarkRead

A standalone Wear OS app for Pixel Watch 4 that marks the current notification as read with a
**wrist turn**.

It watches the notifications the watch can see, finds the ones that publish a real
*Mark as read* action, and — while its screen is open — maps the Wear OS one-handed **dismiss
(wrist turn)** gesture to that action's original `PendingIntent`. The source app does the actual
"mark as read"; WristMarkRead only pulls the trigger.

---

## 1. What it does

```
notification arrives
        ↓
NotificationListenerService sees it
        ↓
find a MARK_AS_READ action, if the notification has one
        ↓
WristMarkRead's UI holds that action as the "current target"
        ↓
wrist turn (or the manual button)
        ↓
send the notification action's original PendingIntent
        ↓
the source app marks its item as read
```

Detection is generic — there is no Gmail-specific (or any app-specific) code:

1. **`Notification.Action.SEMANTIC_ACTION_MARK_AS_READ`** — the authoritative signal, and it always
   wins.
2. **Normalised label match**, used *only* when no action carries the semantic metadata:
   `mark as read`, `mark read`, `marcar como lida`, `marcar como lido`, `marcar como leida`,
   `marcar como leido`. Comparison is lower-cased and accent-stripped, so `Marcar como LÍDA`
   matches too.

Everything stays on the watch: no internet permission, no analytics, no storage of notification
content. The current action lives in memory only, and is rebuilt from the active notifications
whenever the listener service reconnects.

## 2. Platform limitation — read this first

**The wrist turn only works while WristMarkRead's own UI is in the foreground.**

Android does not let a third-party app globally intercept or remap the Pixel Watch wrist-turn
gesture while Google's system notification UI is on screen. The one-handed gesture framework
delivers a gesture to the composable that currently owns it, inside the app that is running. This
project uses that supported API (`Modifier.oneHandedGesture` with
`OneHandedGestureAction.Dismiss`) rather than pretending the system gesture can be hijacked.

So: open WristMarkRead, see the current notification, turn your wrist. Turning your wrist inside
the system notification stream still does what Wear OS does — that behaviour belongs to the system.

Two smaller consequences:

* Only the **newest** notification carrying a mark-as-read action is tracked (an in-memory MVP,
  not an inbox).
* A `PendingIntent` is only valid while the notification that published it is alive. If the source
  app cancels it, the action is dropped and the UI says so.

## 3. Requirements

| | |
|---|---|
| Watch | Pixel Watch 3 or later — **Pixel Watch 4** recommended. One-handed gestures need **Wear OS 7 (API 37)** |
| Hand gestures | Enabled on the watch (see §7) |
| Notification access | Granted to WristMarkRead (see §6) |
| Build | JDK 17, Android SDK with **API 37**, AGP 9.3.1 / Gradle 9.5.0 (both come from the wrapper) |

On an older watch or a non-watch emulator the app still runs: the gesture API safely no-ops, the
screen reports `Gesture: needs Wear OS 7` (or `not a watch`), and the manual button keeps working.

## 4. Build locally

```bash
git clone https://github.com/rafaelfabbrini/pixel-watch-gestures.git
cd pixel-watch-gestures
./gradlew clean assembleDebug
```

The APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Or open the project folder in Android Studio — any release that supports **AGP 9.3** — and run the
`app` configuration.

> The build needs `dl.google.com` (Google's Maven repository and the Android SDK). Behind a
> restricted network, use the GitHub Actions workflow in §9 instead.

## 5. Install with adb

Put the watch in developer mode (**Settings › System › About › Versions** → tap *Build number*
seven times, then **Settings › Developer options › ADB debugging**), then either pair over Wi-Fi
(**Developer options › Wireless debugging › Pair new device**) or plug in the dock if your dock
carries data.

```bash
# 1. Connect (Wi-Fi pairing example; use the IP:port shown on the watch)
adb pair 192.168.1.42:37000          # enter the 6-digit code from the watch
adb connect 192.168.1.42:5555

# 2. Confirm the watch is listed
adb devices -l

# 3. Install (-r replaces an older build)
adb -s 192.168.1.42:5555 install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Launch it
adb -s 192.168.1.42:5555 shell am start -n com.rfabbrini.wristmarkread/.MainActivity
```

Watch the logs while testing:

```bash
adb -s 192.168.1.42:5555 logcat -s WristMarkRead
```

## 6. Enable Notification Access

On the watch: **Settings › Apps › Special app access › Notification access › WristMarkRead**, or
tap **Grant access** on the app's own screen. Some Wear OS builds hide that screen, in which case
grant it over adb:

```bash
adb -s <watch> shell cmd notification allow_listener \
  com.rfabbrini.wristmarkread/com.rfabbrini.wristmarkread.notification.MarkReadNotificationListenerService
```

Verify:

```bash
adb -s <watch> shell settings get secure enabled_notification_listeners
```

The app's first line flips to `Notification access: enabled` and then `Listener: connected`.

## 7. Enable hand gestures on Pixel Watch

**Settings › Gestures** (on some builds **Settings › Accessibility › Gestures**) → turn on
**Hand gestures**, and run the tutorial for *wrist turn* / *double pinch* once. Wrist turn is the
system's dismiss gesture — that is exactly the gesture this app binds to.

## 8. How to test

1. **Produce a notification with a Mark-as-Read action.** Any app that publishes one works — an
   email or messaging app, or push one yourself for a controlled test.
2. **Open WristMarkRead.** The screen should show the source package, the title and text, and
   `Mark as Read: detected`. The debug block shows the notification key, the raw `semanticAction`
   value, and whether it matched by semantic action or by label. If you instead see
   *"No notification with Mark as Read available."*, that notification has no compatible action.
3. **Tap the manual button first.** This is the ground truth: it calls exactly the same function
   the gesture does. Check on the phone or in the source app that the item really became read.
4. **Only then test the wrist turn.** With the app's screen still open, turn your wrist. The
   gesture indicator animates on the button, and the same action fires. `Last action: sent to
   <package>` confirms it, as does `PendingIntent invoked successfully` in Logcat.
5. **Test the empty case.** Dismiss the notification, and turn your wrist again: nothing happens,
   no crash, and Logcat records `no mark-as-read action available, doing nothing`.

If the button works but the gesture does not, the problem is gesture availability (§7 / Wear OS
version), not the notification plumbing.

## 9. Build through GitHub Actions

1. Open the repository's **Actions** tab.
2. Choose the **Build APK** workflow.
3. Press **Run workflow** (it also runs on every push to `main` and on pull requests).
4. When it finishes, download the **`WristMarkRead-debug-apk`** artifact from the run summary. It
   contains `app-debug.apk`; unzip it and install it with §5.

The workflow checks out the repo, installs Temurin JDK 17, configures Gradle caching, runs
`./gradlew clean assembleDebug`, verifies the APK exists, and uploads it.

## 10. Project layout

```
app/src/main/java/com/rfabbrini/wristmarkread/
├── MainActivity.kt                              Compose host; refreshes access state on resume
├── Logging.kt                                   the "WristMarkRead" Logcat tag
├── gesture/GestureSupport.kt                    Wear OS 7 / watch feature detection
├── notification/
│   ├── MarkAsReadDetector.kt                    semantic action first, normalised labels second
│   ├── MarkReadNotificationListenerService.kt    observes notifications, rescans on reconnect
│   ├── MarkReadRepository.kt                    in-memory state + markCurrentNotificationAsRead()
│   ├── MarkReadTarget.kt                        the tracked action, incl. its original PendingIntent
│   └── NotificationAccess.kt                    grant check + settings shortcut
└── ui/MainScreen.kt                             the single screen, and the gesture binding
```

`MarkReadRepository.markCurrentNotificationAsRead()` is the single entry point. The button and the
gesture both call it; it is also the only place that touches a `PendingIntent`, and it handles
`PendingIntent.CanceledException` by dropping the stale target.

## 11. Notable dependency versions

| Component | Version | Why |
|---|---|---|
| Android Gradle plugin | 9.3.1 | Compose 1.12 requires AGP 9 and `compileSdk 37` |
| Gradle | 9.5.0 | AGP 9.3's minimum and default |
| Kotlin (Compose compiler plugin) | 2.3.21 | Kotlin itself comes from AGP's built-in Kotlin support |
| `androidx.wear.compose:compose-material3` | 1.7.0-beta01 | first release with `Modifier.oneHandedGesture` |
| `androidx.compose.ui` tooling | 1.12.0 | matches Wear Compose 1.7 |
| `androidx.activity:activity-compose` | 1.13.0 | `setContent` on `ComponentActivity` |
| compileSdk / targetSdk / minSdk | 37 / 36 / 30 | API 37 = Wear OS 7; minSdk 30 keeps Wear OS 3+ running |

`1.7.0-beta01` is a beta because that is the only channel where the one-handed gesture API exists
today; the API is documented as feature-complete and locked for 1.7.0. No experimental opt-in
annotation is required for it.

## 12. Privacy

* No `INTERNET` permission is declared.
* Notification content is read in-process, shown on the watch, and never persisted or transmitted.
* The notification listener service is protected by
  `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`, so only the platform can bind to it.
