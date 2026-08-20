# The notification listener service is instantiated by the system by name, so it must survive
# shrinking even though nothing in the app references it directly.
-keep class com.rfabbrini.wristmarkread.notification.MarkReadNotificationListenerService { *; }
