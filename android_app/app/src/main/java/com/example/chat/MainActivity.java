package com.example.chat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_URI = "ws://34.230.183.187:80";
    private static final int CURRENT_VERSION_CODE = 5;
    private static final String PREF_NAME = "ChatUserSession";
    private static final String KEY_USER = "saved_username";
    private static final String KEY_PASS = "saved_password";

    private LinearLayout headerLayout;
    private TextView tvHeaderTitle;
    private TextView tvBanner;
    private TextView tvConnectionStatus;
    private ScrollView scrollView;
    private LinearLayout chatContainer;
    private LinearLayout authPanel;
    private LinearLayout chatMainPanel;

    private EditText etAuthUser;
    private EditText etAuthPass;
    private EditText etMessage;
    private TextView tvLoggedInUser;

    private Button btnLogin;
    private Button btnRegister;
    private Button btnLogout;
    private Button btnSend;
    private Button btnCheckUpdate;

    private WebSocketClient webSocketClient;
    private Handler mainHandler;
    private SharedPreferences sharedPreferences;

    // Dynamic Patch States
    private boolean enableEmojis = false;
    private boolean showTimestamps = true;
    private int patchVersion = 1;

    private String currentUser = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        headerLayout = findViewById(R.id.headerLayout);
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvBanner = findViewById(R.id.tvBanner);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        scrollView = findViewById(R.id.scrollView);
        chatContainer = findViewById(R.id.chatContainer);
        authPanel = findViewById(R.id.authPanel);
        chatMainPanel = findViewById(R.id.chatMainPanel);

        etAuthUser = findViewById(R.id.etAuthUser);
        etAuthPass = findViewById(R.id.etAuthPass);
        etMessage = findViewById(R.id.etMessage);
        tvLoggedInUser = findViewById(R.id.tvLoggedInUser);

        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnLogout = findViewById(R.id.btnLogout);
        btnSend = findViewById(R.id.btnSend);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);

        btnLogin.setOnClickListener(v -> handleAuthAction("login"));
        btnRegister.setOnClickListener(v -> handleAuthAction("register"));
        btnLogout.setOnClickListener(v -> handleLogout());
        btnSend.setOnClickListener(v -> sendMessage());
        btnCheckUpdate.setOnClickListener(v -> checkUpdate());

        connectWebSocket();
    }

    private void connectWebSocket() {
        URI uri;
        try {
            uri = new URI(SERVER_URI);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                mainHandler.post(() -> {
                    tvConnectionStatus.setText("Terhubung | Auto-Sync Aktif");
                    // Auto-check updates on app open
                    checkUpdate();

                    // Restore user session if saved
                    String savedUser = sharedPreferences.getString(KEY_USER, null);
                    String savedPass = sharedPreferences.getString(KEY_PASS, null);
                    if (!TextUtils.isEmpty(savedUser) && !TextUtils.isEmpty(savedPass)) {
                        performAutoLogin(savedUser, savedPass);
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                mainHandler.post(() -> handleIncomingMessage(message));
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                mainHandler.post(() -> tvConnectionStatus.setText("Terputus | Mencoba menghubungkan..."));
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };

        webSocketClient.connect();
    }

    private void performAutoLogin(String username, String password) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "login");
                json.put("username", username);
                json.put("password", password);
                webSocketClient.send(json.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleAuthAction(String action) {
        String username = etAuthUser.getText().toString().trim();
        String password = etAuthPass.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Nama pengguna & kata sandi wajib diisi.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", action);
                json.put("username", username);
                json.put("password", password);
                webSocketClient.send(json.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Tidak terhubung ke server chat.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLogout() {
        sharedPreferences.edit().clear().apply();
        currentUser = null;
        chatContainer.removeAllViews();
        authPanel.setVisibility(View.VISIBLE);
        chatMainPanel.setVisibility(View.GONE);
        Toast.makeText(this, "Sesi pengguna telah diakhiri.", Toast.LENGTH_SHORT).show();
    }

    private void checkUpdate() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "check_update");
                webSocketClient.send(json.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void fetchHistory() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "get_history");
                webSocketClient.send(json.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleIncomingMessage(String rawJson) {
        try {
            JSONObject data = new JSONObject(rawJson);
            String type = data.optString("type");

            if ("update_info".equals(type)) {
                JSONObject patch = data.getJSONObject("patch");
                processMultiGuardUpdate(patch);
            } else if ("auth_res".equals(type)) {
                boolean success = data.optBoolean("success", false);
                String msg = data.optString("msg", "");
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

                if (success && "login".equals(data.optString("action"))) {
                    currentUser = data.optString("username");
                    // Save session persistence
                    String userTyped = etAuthUser.getText().toString().trim();
                    String passTyped = etAuthPass.getText().toString().trim();
                    if (!TextUtils.isEmpty(userTyped)) {
                        sharedPreferences.edit()
                                .putString(KEY_USER, userTyped)
                                .putString(KEY_PASS, passTyped)
                                .apply();
                    }

                    tvLoggedInUser.setText("Pengguna Aktif: " + currentUser);
                    authPanel.setVisibility(View.GONE);
                    chatMainPanel.setVisibility(View.VISIBLE);
                    chatContainer.removeAllViews();

                    // Load persistent chat history from server
                    fetchHistory();
                }
            } else if ("history".equals(type)) {
                JSONArray messages = data.optJSONArray("messages");
                if (messages != null) {
                    chatContainer.removeAllViews();
                    for (int i = 0; i < messages.length(); i++) {
                        JSONObject msgObj = messages.getJSONObject(i);
                        addMessageToChat(msgObj.optString("sender"), msgObj.optString("text"), msgObj.optString("timestamp"));
                    }
                }
            } else if ("chat".equals(type)) {
                String sender = data.optString("sender", "Anonim");
                String text = data.optString("text", "");
                String timestamp = data.optString("timestamp", "");

                addMessageToChat(sender, text, timestamp);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void processMultiGuardUpdate(JSONObject patchData) {
        try {
            // 1. PLAN A: Dynamic Hot-Patch
            JSONObject planA = patchData.optJSONObject("plan_a_hotpatch");
            if (planA != null && planA.optBoolean("enabled", true)) {
                applyHotPatch(planA);
            }

            // 2. PLAN B: In-App Direct APK Auto-Installer
            JSONObject planB = patchData.optJSONObject("plan_b_apk_installer");
            if (planB != null && planB.optBoolean("enabled", false)) {
                int latestCode = planB.optInt("latest_version_code", CURRENT_VERSION_CODE);
                String apkUrl = planB.optString("apk_download_url", "");
                String updateNotes = planB.optString("update_notes", "Pembaruan versi baru tersedia.");

                if (latestCode > CURRENT_VERSION_CODE && !TextUtils.isEmpty(apkUrl)) {
                    promptInAppApkUpdate(apkUrl, updateNotes);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyHotPatch(JSONObject planA) {
        try {
            JSONObject theme = planA.optJSONObject("theme");
            if (theme != null) {
                String primaryColorStr = theme.optString("primary_color", "#1E3A8A");
                String headerTitleStr = theme.optString("header_title", "Enterprise Secure Chat");
                String welcomeBannerStr = theme.optString("welcome_banner", "Sistem Terhubung.");
                String cardBgColorStr = theme.optString("card_bg_color", "#EFF6FF");

                headerLayout.setBackgroundColor(Color.parseColor(primaryColorStr));
                btnSend.setBackgroundColor(Color.parseColor(primaryColorStr));
                btnLogin.setBackgroundColor(Color.parseColor(primaryColorStr));
                tvHeaderTitle.setText(headerTitleStr);
                tvBanner.setText(welcomeBannerStr);
                tvBanner.setBackgroundColor(Color.parseColor(cardBgColorStr));
            }

            JSONObject features = planA.optJSONObject("features");
            if (features != null) {
                enableEmojis = features.optBoolean("enable_emojis", false);
                showTimestamps = features.optBoolean("show_timestamps", true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void promptInAppApkUpdate(String apkUrl, String updateNotes) {
        new AlertDialog.Builder(this)
                .setTitle("Pembaruan Aplikasi Tersedia")
                .setMessage(updateNotes + "\n\nSistem akan mengunduh dan memperbarui secara otomatis tanpa menghapus data Anda.")
                .setPositiveButton("Unduh & Perbarui", (dialog, which) -> downloadAndInstallApk(apkUrl))
                .setNegativeButton("Nanti", null)
                .show();
    }

    private void downloadAndInstallApk(String apkUrl) {
        Toast.makeText(this, "Mengunduh pembaruan di latar belakang...", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            try {
                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                File apkFile = new File(getExternalFilesDir(null), "update.apk");
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(apkFile);

                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                is.close();

                mainHandler.post(() -> installApk(apkFile));

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Gagal mengunduh berkas pembaruan.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void installApk(File apkFile) {
        if (!apkFile.exists()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                Toast.makeText(this, "Izinkan instalasi aplikasi dari sumber ini untuk melanjutkan.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();

        if (TextUtils.isEmpty(text)) {
            return;
        }

        String timeStr = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "chat");
                json.put("sender", currentUser != null ? currentUser : "Anonim");
                json.put("text", text);
                json.put("timestamp", timeStr);
                webSocketClient.send(json.toString());
                etMessage.setText("");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Gagal mengirim: Koneksi server terputus.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Renders modern message bubbles (Self vs Other sender alignment & styled cards)
     */
    private void addMessageToChat(String sender, String text, String timestamp) {
        boolean isSelf = (currentUser != null && currentUser.equalsIgnoreCase(sender));

        LinearLayout messageWrapper = new LinearLayout(this);
        messageWrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapperParams.setMargins(0, 10, 0, 10);
        messageWrapper.setLayoutParams(wrapperParams);
        messageWrapper.setGravity(isSelf ? Gravity.END : Gravity.START);

        LinearLayout cardLayout = new LinearLayout(this);
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setPadding(24, 16, 24, 16);
        cardLayout.setBackgroundColor(isSelf ? Color.parseColor("#DCF8C6") : Color.WHITE);

        TextView tvSender = new TextView(this);
        tvSender.setText(sender);
        tvSender.setTextSize(12);
        tvSender.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSender.setTextColor(isSelf ? Color.parseColor("#15803D") : Color.parseColor("#1E3A8A"));

        TextView tvText = new TextView(this);
        tvText.setText(text);
        tvText.setTextSize(14);
        tvText.setTextColor(Color.parseColor("#1F2937"));
        tvText.setPadding(0, 4, 0, 4);

        TextView tvTime = new TextView(this);
        tvTime.setText(timestamp);
        tvTime.setTextSize(10);
        tvTime.setTextColor(Color.parseColor("#6B7280"));
        tvTime.setGravity(Gravity.END);

        cardLayout.addView(tvSender);
        cardLayout.addView(tvText);
        if (showTimestamps && !TextUtils.isEmpty(timestamp)) {
            cardLayout.addView(tvTime);
        }

        messageWrapper.addView(cardLayout);
        chatContainer.addView(messageWrapper);

        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
}
