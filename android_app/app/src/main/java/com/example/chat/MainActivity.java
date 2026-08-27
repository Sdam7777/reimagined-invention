package com.example.chat;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
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
    private static final int CURRENT_VERSION_CODE = 3;

    private LinearLayout headerLayout;
    private TextView tvHeaderTitle;
    private TextView tvBanner;
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
    private Button btnSend;
    private Button btnCheckUpdate;

    private WebSocketClient webSocketClient;
    private Handler mainHandler;

    // Hot-patch feature flags & dynamic configurations
    private boolean enableEmojis = false;
    private boolean showTimestamps = true;
    private int patchVersion = 1;

    private String currentUser = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());

        headerLayout = findViewById(R.id.headerLayout);
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvBanner = findViewById(R.id.tvBanner);
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
        btnSend = findViewById(R.id.btnSend);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);

        btnLogin.setOnClickListener(v -> handleAuthAction("login"));
        btnRegister.setOnClickListener(v -> handleAuthAction("register"));
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
                    Toast.makeText(MainActivity.this, "Terhubung ke server chat!", Toast.LENGTH_SHORT).show();
                    // Automatic update check on app open (Plan A + Plan B + Plan C combined)
                    checkUpdate();
                });
            }

            @Override
            public void onMessage(String message) {
                mainHandler.post(() -> handleIncomingMessage(message));
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Koneksi terputus.", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };

        webSocketClient.connect();
    }

    private void handleAuthAction(String action) {
        String username = etAuthUser.getText().toString().trim();
        String password = etAuthPass.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Username & Password wajib diisi!", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Tidak terhubung ke server", Toast.LENGTH_SHORT).show();
        }
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
                    tvLoggedInUser.setText("Login sebagai: " + currentUser);
                    authPanel.setVisibility(View.GONE);
                }
            } else if ("chat".equals(type)) {
                String sender = data.optString("sender", "Anon");
                String text = data.optString("text", "");
                String timestamp = data.optString("timestamp", "");

                addMessageToChat(sender, text, timestamp);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unified Multi-Guard Auto-Update Handler:
     * Plan A: Dynamic Hot-Patching (Themes, Colors, Features without restart)
     * Plan B: In-App Direct APK Auto-Installer (Download & Install latest APK in-app without uninstalling)
     * Plan C: Dynamic Hybrid Engine Fallback
     */
    private void processMultiGuardUpdate(JSONObject patchData) {
        try {
            // 1. PLAN A: Apply Dynamic Hot-Patch
            JSONObject planA = patchData.optJSONObject("plan_a_hotpatch");
            if (planA != null && planA.optBoolean("enabled", true)) {
                applyHotPatch(planA);
            }

            // 2. PLAN B: In-App APK Auto-Installer
            JSONObject planB = patchData.optJSONObject("plan_b_apk_installer");
            if (planB != null && planB.optBoolean("enabled", false)) {
                int latestCode = planB.optInt("latest_version_code", CURRENT_VERSION_CODE);
                String apkUrl = planB.optString("apk_download_url", "");
                String updateNotes = planB.optString("update_notes", "Versi baru tersedia!");

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
                String primaryColorStr = theme.optString("primary_color", "#1976D2");
                String headerTitleStr = theme.optString("header_title", "Simple Native Chat");
                String welcomeBannerStr = theme.optString("welcome_banner", "Welcome");
                String cardBgColorStr = theme.optString("card_bg_color", "#E8F5E9");

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

            Toast.makeText(this, "🛡️ Multi-Guard Auto-Update Active! Bebas Uninstall.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void promptInAppApkUpdate(String apkUrl, String updateNotes) {
        new AlertDialog.Builder(this)
                .setTitle("Update Aplikasi Baru Tersedia")
                .setMessage(updateNotes + "\n\nIngin mengunduh dan memasang update otomatis sekarang? (Tanpa uninstall)")
                .setPositiveButton("Update Otomatis", (dialog, which) -> downloadAndInstallApk(apkUrl))
                .setNegativeButton("Nanti", null)
                .show();
    }

    private void downloadAndInstallApk(String apkUrl) {
        Toast.makeText(this, "Mengunduh update APK di latar belakang...", Toast.LENGTH_LONG).show();
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
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Gagal mengunduh APK update", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void installApk(File apkFile) {
        if (!apkFile.exists()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                Toast.makeText(this, "Izinkan pemasangan aplikasi untuk melanjutkan update", Toast.LENGTH_LONG).show();
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

        if (enableEmojis) {
            text += " 😊🔥";
        }

        String timeStr = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "chat");
                json.put("sender", currentUser != null ? currentUser : "Guest");
                json.put("text", text);
                json.put("timestamp", timeStr);
                webSocketClient.send(json.toString());
                etMessage.setText("");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Gagal mengirim: Tidak terhubung ke server", Toast.LENGTH_SHORT).show();
        }
    }

    private void addMessageToChat(String sender, String text, String timestamp) {
        TextView textView = new TextView(this);
        String displayMsg = sender + ": " + text;
        if (showTimestamps && !TextUtils.isEmpty(timestamp)) {
            displayMsg = "[" + timestamp + "] " + displayMsg;
        }
        textView.setText(displayMsg);
        textView.setTextSize(15);
        textView.setPadding(16, 12, 16, 12);
        textView.setTextColor(Color.BLACK);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 8);
        textView.setLayoutParams(params);
        textView.setBackgroundColor(Color.WHITE);

        chatContainer.addView(textView);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
}
