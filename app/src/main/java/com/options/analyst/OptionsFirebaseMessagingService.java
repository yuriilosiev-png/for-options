package com.options.analyst;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OptionsFirebaseMessagingService
 *
 * Отвечает за:
 * 1. onNewToken()        — когда Firebase выдаёт новый/обновлённый FCM-токен
 * 2. onMessageReceived() — когда приходит push (приложение на переднем плане)
 *
 * При получении нового токена:
 *  - Сохраняем в SharedPreferences (ключ "fcm_token")
 *  - Сохраняем флаг "fcm_pending_send" = true (токен не отправлен на Worker)
 *  - Если MainActivity активна — вызываем injectFcmToken() через статическую ссылку
 */
public class OptionsFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG        = "OptionsFCM";
    public  static final String PREFS_NAME = "OptionsAnalystPrefs";
    public  static final String KEY_TOKEN  = "fcm_token";
    public  static final String KEY_PENDING= "fcm_pending_send";

    private static final String CHANNEL_ID   = "price_alerts";
    private static final String CHANNEL_NAME = "Price Alerts";
    // AtomicInteger для уникальных ID уведомлений (чтобы не перезаписывать друг друга)
    private static final AtomicInteger notifCounter = new AtomicInteger(1000);

    // ─────────────────────────────────────────────────────────────────
    // onNewToken — Firebase вызывает при первом запуске и при ротации токена
    // ─────────────────────────────────────────────────────────────────
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "onNewToken: " + token.substring(0, Math.min(20, token.length())) + "...");

        // Сохраняем токен локально
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
             .putString(KEY_TOKEN, token)
             .putBoolean(KEY_PENDING, true)   // нужно отправить на Worker
             .apply();

        // Пробуем инжектировать токен в WebView (если MainActivity открыта)
        MainActivity activity = MainActivity.getInstance();
        if (activity != null) {
            activity.injectFcmToken(token);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // onMessageReceived — вызывается когда приложение на переднем плане
    // (когда в фоне — Firebase сам показывает уведомление из поля notification)
    // ─────────────────────────────────────────────────────────────────
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = null;
        String body  = null;

        // Сначала пробуем поле notification
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body  = remoteMessage.getNotification().getBody();
        }

        // Fallback: поле data (Worker шлёт оба поля одновременно)
        Map<String, String> data = remoteMessage.getData();
        if (title == null) title = data.getOrDefault("title", "Options Alert");
        if (body  == null) body  = data.getOrDefault("body",  "");

        showNotification(title, body);
    }

    // ─────────────────────────────────────────────────────────────────
    // Показ нативного уведомления
    // ─────────────────────────────────────────────────────────────────
    private void showNotification(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Создаём канал (для Android 8+ обязательно, на старых версиях игнорируется)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Options price alert notifications");
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }

        // Интент: открыть MainActivity при нажатии на уведомление
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔔 " + title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(new long[]{0, 250, 250, 250});

        nm.notify(notifCounter.incrementAndGet(), builder.build());
    }
}
