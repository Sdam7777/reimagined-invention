#!/usr/bin/env python3
import asyncio
import json
import os
import sys
import websockets
from http.server import HTTPServer, SimpleHTTPRequestHandler
import threading

CLIENTS = set()
USERS = {}  # username -> password memory store

# Unified Multi-Layer Update Metadata (Plan A + B + C)
PATCH_DATA = {
    "version": 4,
    "strategy": "UNIFIED_ALL",  # Plan A (Hot Patch) + Plan B (In-App APK Auto-Install) + Plan C (Dynamic Fallback)

    # PLAN A: Dynamic Hot-Patch (Live UI & Config Overhaul without restart/reinstall)
    "plan_a_hotpatch": {
        "enabled": True,
        "theme": {
            "primary_color": "#D32F2F",       # Deep Red Premium Update
            "header_title": "ChatApp Multi-Guard (v4.0)",
            "welcome_banner": "🛡️ Unified Auto-Update Active (Plan A+B+C): Bebas Reinstall Selamanya!",
            "card_bg_color": "#FFEBEE"
        },
        "features": {
            "enable_auth": True,
            "enable_emojis": True,
            "show_timestamps": True,
            "max_message_length": 2000,
            "auto_retry_connection": True
        }
    },

    # PLAN B: In-App Direct APK Auto-Installer (Auto-Download & Install without browser/uninstall)
    "plan_b_apk_installer": {
        "enabled": True,
        "latest_version_code": 4,
        "latest_version_name": "4.0.0",
        "apk_download_url": "http://34.230.183.187:8080/download/app-debug.apk",
        "force_update": False,
        "update_notes": "Update v4.0: Fitur Keamanan Multi-Layer, Auto-Repair, dan In-App Installer!"
    },

    # PLAN C: Dynamic Fallback Hybrid UI Engine
    "plan_c_hybrid": {
        "enabled": True,
        "remote_component_js": "console.log('Plan C Dynamic Engine active');"
    }
}

class ApkHttpHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/download/app-debug.apk":
            apk_path = "/home/ec2-user/chat_server/app-debug.apk"
            if os.path.exists(apk_path):
                self.send_response(200)
                self.send_header('Content-Type', 'application/vnd.android.package-archive')
                self.send_header('Content-Length', os.path.getsize(apk_path))
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                with open(apk_path, 'rb') as f:
                    self.wfile.write(f.read())
                return
        self.send_error(404, "File not found")

def start_http_server():
    httpd = HTTPServer(('0.0.0.0', 8080), ApkHttpHandler)
    print("HTTP Server running on port 8080")
    httpd.serve_forever()

async def websocket_handler(websocket, path):
    print(f"Client connected from {websocket.remote_address}")
    CLIENTS.add(websocket)
    current_user = None
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                msg_type = data.get("type")

                if msg_type == "check_update":
                    resp = {
                        "type": "update_info",
                        "patch": PATCH_DATA
                    }
                    await websocket.send(json.dumps(resp))

                elif msg_type == "register":
                    username = data.get("username", "").strip()
                    password = data.get("password", "").strip()
                    if not username or not password:
                        resp = {"type": "auth_res", "success": False, "msg": "Username dan password tidak boleh kosong!"}
                    elif username in USERS:
                        resp = {"type": "auth_res", "success": False, "msg": "Username sudah terdaftar!"}
                    else:
                        USERS[username] = password
                        resp = {"type": "auth_res", "success": True, "msg": "Pendaftaran berhasil! Silakan login."}
                    await websocket.send(json.dumps(resp))

                elif msg_type == "login":
                    username = data.get("username", "").strip()
                    password = data.get("password", "").strip()
                    if USERS.get(username) == password:
                        current_user = username
                        resp = {"type": "auth_res", "success": True, "action": "login", "username": username, "msg": f"Selamat datang kembali, {username}!"}
                    else:
                        resp = {"type": "auth_res", "success": False, "msg": "Username atau password salah!"}
                    await websocket.send(json.dumps(resp))

                elif msg_type == "chat":
                    sender = data.get("sender", current_user or "Anonymous")
                    payload = json.dumps({
                        "type": "chat",
                        "sender": sender,
                        "text": data.get("text", ""),
                        "timestamp": data.get("timestamp", "")
                    })
                    for client in list(CLIENTS):
                        if client.open:
                            await client.send(payload)

                else:
                    print(f"Unknown message type: {msg_type}")

            except json.JSONDecodeError:
                print("Received non-JSON message:", message)
    except websockets.exceptions.ConnectionClosed:
        pass
    finally:
        CLIENTS.remove(websocket)
        print(f"Client disconnected: {websocket.remote_address}")

async def main():
    threading.Thread(target=start_http_server, daemon=True).start()
    host = "0.0.0.0"
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 80
    print(f"Starting Multi-Guard Chat Server on ws://{host}:{port}")
    async with websockets.serve(websocket_handler, host, port):
        await asyncio.Future()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Server stopped.")
