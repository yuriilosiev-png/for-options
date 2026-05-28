package com.options.analyst;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;

import com.google.firebase.messaging.FirebaseMessaging;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity {

    private static final String TAG = "OptionsMain";

    private WebView webView;
    private static final String APP_URL = "https://yuriilosiev-png.github.io/for-options/index.html";

    // Статическая слабая ссылка для доступа из OptionsFirebaseMessagingService
    private static WeakReference<MainActivity> sInstance;

    // FCM-токен, полученный до загрузки страницы — инжектируем в onPageFinished
    private volatile String pendingFcmToken = null;

    public static MainActivity getInstance() {
        return sInstance != null ? sInstance.get() : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Сохраняем слабую ссылку для OptionsFirebaseMessagingService
        sInstance = new WeakReference<>(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        // ── Создаём канал уведомлений (Android 8+) ──
        createNotificationChannel();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(
            settings.getUserAgentString() + " OptionsAnalystApp/1.0"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // ── JS Bridge: AndroidBridge.requestFcmToken() вызывается из index.html ──
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                // Восстанавливаем иммерсивный режим
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
                // Инжектируем FCM-токен если уже получен (eager path)
                if (pendingFcmToken != null) {
                    injectFcmToken(pendingFcmToken);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.loadUrl(APP_URL);

        // ── Получаем FCM-токен сразу при старте ──
        fetchFcmToken();
    }

    // ─────────────────────────────────────────────────────────────────
    // fetchFcmToken — запрашивает токен у Firebase при старте Activity
    // Если токен уже есть в SharedPreferences — используем его,
    // параллельно запрашиваем актуальный у Firebase (может ротироваться)
    // ─────────────────────────────────────────────────────────────────
    private void fetchFcmToken() {
        // Сначала проверяем локально сохранённый токен (быстрый путь)
        SharedPreferences prefs = getSharedPreferences(
            OptionsFirebaseMessagingService.PREFS_NAME, Context.MODE_PRIVATE);
        String cachedToken = prefs.getString(OptionsFirebaseMessagingService.KEY_TOKEN, null);
        if (cachedToken != null) {
            Log.d(TAG, "FCM cached token found: " + cachedToken.substring(0, 20) + "...");
            pendingFcmToken = cachedToken;
            // Инжектируем если страница уже загружена (редкий случай)
            injectFcmToken(cachedToken);
        }

        // Всегда запрашиваем актуальный токен у Firebase
        FirebaseMessaging.getInstance().getToken()
            .addOnSuccessListener(token -> {
                if (token == null) return;
                Log.d(TAG, "FCM fresh token: " + token.substring(0, 20) + "...");
                pendingFcmToken = token;
                // Обновляем кеш
                getSharedPreferences(OptionsFirebaseMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(OptionsFirebaseMessagingService.KEY_TOKEN, token)
                    .apply();
                // Инжектируем в WebView
                injectFcmToken(token);
            })
            .addOnFailureListener(e -> {
                Log.w(TAG, "FCM token fetch failed: " + e.getMessage());
            });
    }

    // ─────────────────────────────────────────────────────────────────
    // injectFcmToken — передаёт FCM-токен в JavaScript через evaluateJavascript
    // Вызывается из fetchFcmToken() и из OptionsFirebaseMessagingService.onNewToken()
    // ─────────────────────────────────────────────────────────────────
    public void injectFcmToken(String token) {
        if (token == null || webView == null) return;
        // Экранируем спецсимволы (токен — URL-safe Base64, но перестрахуемся)
        final String safe = token.replace("\\", "\\\\").replace("'", "\\'");
        // evaluateJavascript обязан выполняться на главном потоке
        webView.post(() -> {
            String js =
                "window.__fcmToken = '" + safe + "';" +
                "if (typeof window.onFcmToken === 'function') {" +
                "  window.onFcmToken('" + safe + "');" +
                "}";
            webView.evaluateJavascript(js, null);
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // AndroidBridge — JS-интерфейс, методы вызываются из index.html
    // webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge")
    // В JS: AndroidBridge.requestFcmToken()
    // ─────────────────────────────────────────────────────────────────
    public static class AndroidBridge {
        private final WeakReference<MainActivity> activityRef;

        public AndroidBridge(MainActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        /**
         * Вызывается из JS: AndroidBridge.requestFcmToken()
         * Получает актуальный FCM-токен и инжектирует его обратно через window.onFcmToken(token)
         */
        @JavascriptInterface
        public void requestFcmToken() {
            MainActivity activity = activityRef.get();
            if (activity == null) return;

            FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token != null) {
                        activity.injectFcmToken(token);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AndroidBridge", "requestFcmToken failed: " + e.getMessage());
                    // Сообщаем JS об ошибке
                    if (activity.webView != null) {
                        activity.webView.post(() ->
                            activity.webView.evaluateJavascript(
                                "if(typeof window.onFcmTokenError==='function'){window.onFcmTokenError('" + e.getMessage() + "');}",
                                null
                            )
                        );
                    }
                });
        }

        /**
         * Вызывается из JS: AndroidBridge.getVersion()
         * Возвращает версию APK — полезно для отладки
         */
        @JavascriptInterface
        public String getVersion() {
            return "2.0";
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Создание канала уведомлений (Android 8+)
    // ─────────────────────────────────────────────────────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "price_alerts",
                "Price Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Options price alert notifications");
            channel.enableVibration(true);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sInstance = null;
        webView.destroy();
    }
}
