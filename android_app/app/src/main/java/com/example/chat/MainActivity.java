package com.example.chat;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_URI = "ws://34.230.183.187:8080";

    private LinearLayout headerLayout;
    private TextView tvHeaderTitle;
    private TextView tvBanner;
    private ScrollView scrollView;
    private LinearLayout chatContainer;
    private EditText etUsername;
    private EditText etMessage;
    private Button btnSend;
    private Button btnCheckUpdate;

    private WebSocketClient webSocketClient;
    private Handler mainHandler;

    // Hot-patch feature flags & dynamic configurations
    private boolean enableEmojis = false;
    private boolean showTimestamps = true;
    private int patchVersion = 1;

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
        etUsername = findViewById(R.id.etUsername);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);

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
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Terhubung ke server chat!", Toast.LENGTH_SHORT).show());
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

    private void checkUpdate() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "check_update");
                webSocketClient.send(json.toString());
                Toast.makeText(this, "Mengecek update dari server...", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "WebSocket tidak terhubung.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleIncomingMessage(String rawJson) {
        try {
            JSONObject data = new JSONObject(rawJson);
            String type = data.optString("type");

            if ("update_info".equals(type)) {
                JSONObject patch = data.getJSONObject("patch");
                applyHotPatch(patch);
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
     * Applying Hot Patch dynamically to Native Android App without reinstalling APK!
     */
    private void applyHotPatch(JSONObject patch) {
        try {
            patchVersion = patch.optInt("version", 1);
            JSONObject theme = patch.optJSONObject("theme");
            if (theme != null) {
                String primaryColorStr = theme.optString("primary_color", "#1976D2");
                String headerTitleStr = theme.optString("header_title", "Simple Native Chat");
                String welcomeBannerStr = theme.optString("welcome_banner", "Welcome");

                headerLayout.setBackgroundColor(Color.parseColor(primaryColorStr));
                btnSend.setBackgroundColor(Color.parseColor(primaryColorStr));
                tvHeaderTitle.setText(headerTitleStr);
                tvBanner.setText(welcomeBannerStr + " (Patch v" + patchVersion + " Applied Live)");
            }

            JSONObject features = patch.optJSONObject("features");
            if (features != null) {
                enableEmojis = features.optBoolean("enable_emojis", false);
                showTimestamps = features.optBoolean("show_timestamps", true);
            }

            Toast.makeText(this, "Dynamic Hot-Patch v" + patchVersion + " berhasil diterapkan tanpa install ulang!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String username = etUsername.getText().toString().trim();
        String text = etMessage.getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            username = "User";
        }
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
                json.put("sender", username);
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
