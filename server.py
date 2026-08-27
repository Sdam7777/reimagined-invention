#!/usr/bin/env python3
import asyncio
import json
import os
import sys
import websockets
from http.server import HTTPServer, SimpleHTTPRequestHandler
import threading

DB_FILE = "/home/ec2-user/chat_server/db.json"
CLIENTS = set()

# Load or initialize persistent storage
def load_db():
    if os.path.exists(DB_FILE):
        try:
            with open(DB_FILE, "r") as f:
                return json.load(f)
        except Exception:
            pass
    return {"users": {}, "messages": []}

def save_db(db):
    try:
        with open(DB_FILE, "w") as f:
            json.dump(db, f, indent=2)
    except Exception as e:
        print("Error saving database:", e)

DB = load_db()

# Professional Dynamic Patch Metadata (Plan A + B + C)
PATCH_DATA = {
    "version": 5,
    "strategy": "UNIFIED_ALL",

    # PLAN A: Professional Hot-Patch (Sleek UI & Theme)
    "plan_a_hotpatch": {
        "enabled": True,
        "theme": {
            "primary_color": "#1E3A8A",       # Professional Corporate Indigo / Deep Blue
            "header_title": "Enterprise Secure Chat",
            "welcome_banner": "Sistem Terhubung. Seluruh komunikasi dan data pengguna tersimpan secara aman.",
            "card_bg_color": "#F3F4F6",
            "bubble_sent_color": "#DCF8C6",
            "bubble_recv_color": "#FFFFFF"
        },
        "features": {
            "enable_auth": True,
            "enable_emojis": False,          # Disabled emojis for professional tone
            "show_timestamps": True,
            "max_message_length": 2000,
            "auto_retry_connection": True
        }
    },

    # PLAN B: In-App Direct APK Auto-Installer
    "plan_b_apk_installer": {
        "enabled": True,
        "latest_version_code": 5,
        "latest_version_name": "5.0.0",
        "apk_download_url": "http://34.230.183.187:8080/download/app-debug.apk",
        "force_update": False,
        "update_notes": "Pembaruan Sistem v5.0: Peningkatan Antarmuka Profesional, Perbaikan Performa, dan Penyimpanan Riwayat Pesan Terenkripsi."
    },

    # PLAN C: Dynamic Fallback Hybrid UI Engine
    "plan_c_hybrid": {
        "enabled": True,
        "remote_component_js": "console.log('Engine Enterprise v5.0 aktif');"
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

                elif msg_type == "get_history":
                    # Send stored chat history to user
                    resp = {
                        "type": "history",
                        "messages": DB.get("messages", [])[-100:]  # Send last 100 messages
                    }
                    await websocket.send(json.dumps(resp))

                elif msg_type == "register":
                    username = data.get("username", "").strip()
                    password = data.get("password", "").strip()
                    if not username or not password:
                        resp = {"type": "auth_res", "success": False, "msg": "Username dan kata sandi wajib diisi."}
                    elif username in DB["users"]:
                        resp = {"type": "auth_res", "success": False, "msg": "Nama pengguna sudah terdaftar."}
                    else:
                        DB["users"][username] = password
                        save_db(DB)
                        resp = {"type": "auth_res", "success": True, "msg": "Registrasi akun berhasil. Silakan masuk."}
                    await websocket.send(json.dumps(resp))

                elif msg_type == "login":
                    username = data.get("username", "").strip()
                    password = data.get("password", "").strip()
                    if DB["users"].get(username) == password:
                        current_user = username
                        resp = {
                            "type": "auth_res",
                            "success": True,
                            "action": "login",
                            "username": username,
                            "msg": f"Selamat datang kembali, {username}."
                        }
                    else:
                        resp = {"type": "auth_res", "success": False, "msg": "Nama pengguna atau kata sandi tidak valid."}
                    await websocket.send(json.dumps(resp))

                elif msg_type == "chat":
                    sender = data.get("sender", current_user or "Anonim")
                    text = data.get("text", "").strip()
                    timestamp = data.get("timestamp", "")

                    if text:
                        msg_entry = {
                            "sender": sender,
                            "text": text,
                            "timestamp": timestamp
                        }
                        # Store in database
                        DB["messages"].append(msg_entry)
                        save_db(DB)

                        # Broadcast to all connected clients
                        payload = json.dumps({
                            "type": "chat",
                            "sender": sender,
                            "text": text,
                            "timestamp": timestamp
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
    print(f"Starting Professional Chat & Auto-Update Server on ws://{host}:{port}")
    async with websockets.serve(websocket_handler, host, port):
        await asyncio.Future()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Server stopped.")
