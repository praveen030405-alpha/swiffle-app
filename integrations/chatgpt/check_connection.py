"""Run a real MCP handshake with the connected Android build."""
import argparse
import asyncio
import os
import sys
from pathlib import Path
from uuid import uuid4

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

SERVER = Path(__file__).with_name("server.py").resolve()


async def check(mode):
    params = StdioServerParameters(command=sys.executable, args=[str(SERVER)],
                                  env=os.environ.copy())
    async with stdio_client(params) as streams, ClientSession(*streams) as session:
        await session.initialize()
        discovered = {tool.name for tool in (await session.list_tools()).tools}
        print("Available tools:", ", ".join(sorted(discovered)) or "(none)")
        if mode in {"disabled", "read-only"}:
            expected = set() if mode == "disabled" else {"swiffle_list_packs", "swiffle_read_pack"}
            assert discovered == expected, discovered
            denied = await session.call_tool("swiffle_create_pack", {"title": "Denied test"})
            assert denied.isError, "Disabled write was accepted"
            return
        if not discovered:
            raise RuntimeError("Enable Evidence Packs in Swiffle Settings > Plugins, then try again.")
        if mode == "create":
            title = f"Connection test {uuid4().hex[:8]}"
            created = await session.call_tool("swiffle_create_pack", {"title": title})
            assert not created.isError, created.content
            pack_id = created.structuredContent["id"]
            fetched = await session.call_tool("swiffle_read_pack", {"packId": pack_id})
            assert not fetched.isError and fetched.structuredContent["title"] == title, fetched.content
            missing = await session.call_tool("swiffle_set_reviewed", {
                "packId": pack_id, "itemId": "missing", "reviewed": True})
            assert missing.isError
            print(f"Created and read back '{title}' on the phone.")
        result = await session.call_tool("swiffle_list_packs", {})
        assert not result.isError, result.content
        print(f"Connected. Swiffle reports {result.structuredContent['total']} packs.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["list", "disabled", "read-only", "create"], default="list")
    asyncio.run(check(parser.parse_args().mode))
