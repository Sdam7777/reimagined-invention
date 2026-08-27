#!/usr/bin/env python3
import asyncio
import json
import os
import sys
import websockets

# Store connected websocket clients: set of websocket instances
CLIENTS = set()

# Current dynamic hot patch definition for the native Android app
PATCH_DATA = {
    "version": 2,
    "theme": {
        "primary_color": "#1E88E5",
        "header_title": "ChatApp Pro (Patched v2.0)",
        "welcome_banner": "Selamat Datang di Chat App (Dynamic Patched Mode)!"
    },
    "features": {
        "enable_emojis": True,
        "show_timestamps": True,
        "max_message_length": 500
    },
    "dynamic_js": """
        function processMessage(msg) {
            return "[Live Server Patch v2] " + msg;
        }
    """
}

async def handler(websocket, path):
    print(f"Client connected from {websocket.remote_address}, path: {path}")
    CLIENTS.add(websocket)
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                msg_type = data.get("type")

                if msg_type == "check_update":
                    # Respond with update info
                    resp = {
                        "type": "update_info",
                        "patch": PATCH_DATA
                    }
                    await websocket.send(json.dumps(resp))

                elif msg_type == "chat":
                    # Broadcast chat message to all clients
                    payload = json.dumps({
                        "type": "chat",
                        "sender": data.get("sender", "Anonymous"),
                        "text": data.get("text", ""),
                        "timestamp": data.get("timestamp", "")
                    })
                    # Send to all connected clients
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
    port = 8080
    print(f"Starting Chat & Update Server on ws://{host}:{port}")
    async with websockets.serve(handler, host, port):
        await asyncio.Future()  # run forever

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Server stopped.")
