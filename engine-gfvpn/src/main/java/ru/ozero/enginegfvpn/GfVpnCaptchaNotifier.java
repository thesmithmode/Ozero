package ru.ozero.enginegfvpn;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** The notification carries no challenge URL. Opening Ozero shows the in-memory prompt. */
final class GfVpnCaptchaNotifier {
    private static final String CHANNEL = "gfvpn_manual_verification";
    private static final int ID = 0x4746;
    private final Context context;

    GfVpnCaptchaNotifier(Context context) { this.context = context; }

    @SuppressWarnings("deprecation")
    void show() {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null || !manager.areNotificationsEnabled()) return;
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch == null) return;
            PendingIntent action = PendingIntent.getActivity(context, ID, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    context.getString(R.string.gfvpn_verification_channel), NotificationManager.IMPORTANCE_HIGH));
                builder = new Notification.Builder(context, CHANNEL);
            } else {
                builder = new Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH);
            }
            manager.notify(ID, builder.setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(context.getString(R.string.gfvpn_verification_notification_title))
                .setContentText(context.getString(R.string.gfvpn_verification_notification_body))
                .setContentIntent(action).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_STATUS).setOnlyAlertOnce(true).build());
        } catch (RuntimeException unavailable) {
            // Foreground UI remains available even if the notification cannot be posted.
            android.util.Log.w("GfVpnCaptcha", "Verification notification unavailable");
        }
    }

    void cancel() {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(ID);
        } catch (RuntimeException ignored) {
            // The verification URL is never included in the notification.
        }
    }
}
