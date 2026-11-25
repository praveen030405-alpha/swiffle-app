"""Swiffle's private stdio MCP server. Each operation runs on the paired phone."""
import asyncio
import base64
import json
import os
import re
import subprocess

from mcp import types
from mcp.server import Server
from mcp.server.stdio import stdio_server

server = Server("Swiffle")
AUTHORITY = "content://com.swiffle.app.plugins"


def device_call(method, request=None):
    command = ["adb"]
    if serial := os.getenv("ANDROID_SERIAL"):
        command.extend(["-s", serial])
    command.extend(["shell", "content", "call", "--uri", AUTHORITY, "--method", method])
    if request is not None:
        raw = json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode()
        encoded = base64.urlsafe_b64encode(raw).rstrip(b"=")
        if len(encoded) > 8192:
            raise ValueError("This request exceeds Swiffle's input limit.")
        command.extend(["--arg", encoded.decode("ascii")])
    try:
        result = subprocess.run(command, capture_output=True, timeout=20, check=False)
    except FileNotFoundError as exc:
        raise RuntimeError("Install Android Platform Tools and add adb to PATH.") from exc
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError("The phone timed out. Check the pack on the phone before repeating a change.") from exc
    payload = re.search(rb"\bpayload=([A-Za-z0-9_-]+)\}", result.stdout)
    if result.returncode or not payload:
        raise RuntimeError("Swiffle did not reply. Check adb devices, the selected phone, and the installed plugin-enabled build.")
    encoded = payload.group(1)
    reply = json.loads(base64.urlsafe_b64decode(encoded + b"=" * (-len(encoded) % 4)))
    if "error" in reply:
        raise ValueError(reply["error"])
    return reply["result"]


@server.list_tools()
async def list_tools():
    result = await asyncio.to_thread(device_call, "list")
    return [types.Tool.model_validate(tool) for tool in result["tools"]]


@server.call_tool()
async def call_tool(name, arguments):
    return await asyncio.to_thread(device_call, "invoke", {"name": name, "arguments": arguments})


async def main():
    async with stdio_server() as (reader, writer):
        await server.run(reader, writer, server.create_initialization_options())


if __name__ == "__main__":
    asyncio.run(main())
