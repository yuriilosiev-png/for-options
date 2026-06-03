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

// v06 пункт 4: для бейджа на иконке (стоковый Android + MIUI Xiaomi)
import android.app.Notification;
import android.content.ComponentName;

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
    // v06 пункт 4: счётчик непрочитанных уведомлений для бейджа на иконке
    public  static final String KEY_BADGE  = "fcm_badge_count";

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
    // onMessageReceived — вызывается при получении FCM-сообщения.
    // v06 пункт 4 (data-only): Worker шлёт ТОЛЬКО data (без notification-блока),
    // поэтому этот метод вызывается ВСЕГДА — и на переднем плане, и в фоне
    // (при android.priority=high). Баннер рисует наш showNotification(), а не
    // система — это обходит подавление авто-баннеров FCM на MIUI.
    // ─────────────────────────────────────────────────────────────────
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = null;
        String body  = null;

        // Основной путь (data-only): читаем из data
        Map<String, String> data = remoteMessage.getData();
        if (data != null) {
            title = data.get("title");
            body  = data.get("body");
        }

        // Fallback: поле notification (на случай если payload всё же содержит его)
        if (title == null && remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body  = remoteMessage.getNotification().getBody();
        }

        if (title == null) title = "Options Alert";
        if (body  == null) body  = "";

        showNotification(title, body);
    }

    // ─────────────────────────────────────────────────────────────────
    // Показ нативного уведомления
    // v06 пункт 4: залоченный экран (VISIBILITY_PUBLIC) + heads-up (IMPORTANCE_HIGH
    // + звук + вибрация на канале) + бейдж-цифра (setShowBadge + setNumber)
    // + бейдж для MIUI Xiaomi (broadcast APPLICATION_MESSAGE_UPDATE).
    // ─────────────────────────────────────────────────────────────────
    private void showNotification(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        // v06 пункт 4: увеличиваем счётчик непрочитанных для бейджа
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int badgeCount = prefs.getInt(KEY_BADGE, 0) + 1;
        prefs.edit().putInt(KEY_BADGE, badgeCount).apply();

        // Создаём канал (для Android 8+ обязательно, на старых версиях игнорируется)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH   // heads-up + звук по умолчанию
            );
            channel.setDescription("Options price alert notifications");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 250, 250, 250});
            // v06 пункт 4: полное содержимое на залоченном экране (не "новое уведомление")
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            // v06 пункт 4: разрешаем бейдж-кружок на иконке
            channel.setShowBadge(true);
            // v06 пункт 4: звук уведомления (heads-up со звуком). Системный дефолт.
            channel.setSound(
                android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_NOTIFICATION),
                new android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            );
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)          // heads-up на Android 7 и ниже
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)       // приоритет в шторке
            .setDefaults(NotificationCompat.DEFAULT_ALL)            // звук+вибрация на старых API
            // v06 пункт 4: полный текст на залоченном экране
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // v06 пункт 4: цифра-бейдж на иконке (стоковые лаунчеры читают setNumber)
            .setNumber(badgeCount)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(new long[]{0, 250, 250, 250});

        Notification notification = builder.build();
        nm.notify(notifCounter.incrementAndGet(), notification);

        // v06 пункт 4: бейдж для MIUI (Xiaomi/Redmi). MIUI игнорирует setNumber и
        // требует собственный broadcast с именем launcher-активити и числом.
        updateMiuiBadge(notification, badgeCount);
    }

    // ─────────────────────────────────────────────────────────────────
    // v06 пункт 4: бейдж-кружок для MIUI Xiaomi (Redmi 9 — тестовое устройство).
    // MIUI не поддерживает стандартный setNumber, использует свой broadcast
    // android.intent.action.APPLICATION_MESSAGE_UPDATE с extra-полями.
    // На не-MIUI устройствах broadcast просто игнорируется (try/catch).
    // ─────────────────────────────────────────────────────────────────
    private void updateMiuiBadge(Notification notification, int count) {
        try {
            // Способ 1 (MIUI 6+): рефлексия по android.app.MiuiNotification на самом
            // объекте уведомления — самый надёжный для свежих MIUI.
            Object miui = notification.getClass()
                .getField("extraNotification").get(notification);
            miui.getClass().getMethod("setMessageCount", int.class)
                .invoke(miui, count);
        } catch (Throwable ignored) {
            // extraNotification нет — не MIUI или старая версия, пропускаем.
        }
        try {
            // Способ 2 (универсальный MIUI broadcast): имя launcher-активити + число.
            Intent badge = new Intent("android.intent.action.APPLICATION_MESSAGE_UPDATE");
            ComponentName cn = new ComponentName(
                getPackageName(),
                getPackageName() + ".MainActivity"
            );
            badge.putExtra("android.intent.extra.update_application_component_name",
                cn.flattenToString());
            badge.putExtra("android.intent.extra.update_application_message_text",
                count > 0 ? String.valueOf(count) : "");
            sendBroadcast(badge);
        } catch (Throwable ignored) {
            // Не MIUI — broadcast никто не примет, это нормально.
        }
    }
}
