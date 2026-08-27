#!/usr/bin/env python3
import asyncio
import json
import os
import sys
import websockets

CLIENTS = set()
USERS = {}  # username -> password memory store

# Dynamic Patch v3.0 (Major UI & Feature Overhaul)
PATCH_DATA = {
    "version": 3,
    "theme": {
        "primary_color": "#2E7D32",       # Dark Green theme for major update
        "header_title": "ChatApp Ultimate (v3.0 Dynamic)",
        "welcome_banner": "🔥 Update Besai (v3.0): Sistem Auth & UI Baru Terapkan Tanpa Reinstall!",
        "card_bg_color": "#E8F5E9"
    },
    "features": {
        "enable_auth": True,
        "enable_emojis": True,
        "show_timestamps": True,
        "max_message_length": 1000,
        "require_login": True
    }
}

async def handler(websocket, path):
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
    host = "0.0.0.0"
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 80
    print(f"Starting Chat & Auth Server on ws://{host}:{port}")
    async with websockets.serve(handler, host, port):
        await asyncio.Future()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Server stopped.")
