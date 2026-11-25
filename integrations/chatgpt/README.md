# Connect ChatGPT to Swiffle

This personal integration pairs one Android device with one trusted computer. ChatGPT calls a private MCP server on the computer. The server passes each operation to Swiffle through Android Debug Bridge (ADB). The app reads and writes its existing Evidence Pack store. The phone can stay on its home screen.

## Set up on Windows

1. Install the plugin-enabled Swiffle debug APK from this branch's **Swiffle plugins** Actions build. Keep existing app data. Android requires a matching signing key to update an installed app. If installation reports a signature mismatch, use a spare phone or emulator, or build with your existing signing key.
2. On the phone, open **Swiffle → Settings → Plugins → Evidence Packs**. Enable **Allow reading**. Enable **Allow changes** if you want ChatGPT to create packs or mark items reviewed. Reading exposes the titles, saved excerpts, notes, source filenames and page references of all Evidence Packs in this Android profile.
3. Install the [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) and Python 3.11 or newer on your computer. Add the platform-tools directory to PATH. Enable USB debugging on the phone, connect it to the computer and accept the phone's debugging prompt. Run `adb devices` and confirm that the phone shows up as `device`. If several devices are listed, set `$env:ANDROID_SERIAL` to the intended serial in the PowerShell session used below.
4. Download the source from this branch. In PowerShell, open the extracted `integrations/chatgpt` folder and run:

   ```powershell
   py -m venv .venv
   .\.venv\Scripts\python.exe -m pip install -r requirements.txt
   .\.venv\Scripts\python.exe check_connection.py
   ```

   The check prints available tools and the pack count. To also create and read back one empty test pack on your phone, add `--mode create`.
5. Follow [OpenAI's Secure MCP Tunnel setup](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels). Create a tunnel associated with your ChatGPT workspace and configure its control-plane credential on your computer. Choose a personal workspace/tunnel accessible only to you. Everyone with access to this connector can use the same paired phone. OpenAI controls tunnel availability and workspace permissions.
6. With the tunnel client on PATH, use the following PowerShell commands in the same folder. Substitute your tunnel ID. Quoting keeps paths containing spaces together.

   ```powershell
   $python = (Resolve-Path .\.venv\Scripts\python.exe).Path
   $server = (Resolve-Path .\server.py).Path
   $mcpCommand = '"{0}" "{1}"' -f ($python -replace '\\','/'), ($server -replace '\\','/')
   tunnel-client init --sample sample_mcp_stdio_local --profile swiffle --tunnel-id YOUR_TUNNEL_ID --mcp-command $mcpCommand
   tunnel-client doctor --profile swiffle --explain
   tunnel-client run --profile swiffle
   ```

7. On ChatGPT web, open **Plugins → + → Add custom MCP server**. Name it **Swiffle**, select **Tunnel**, select your tunnel, and choose **No authentication** for this single-user private tunnel. Create the plugin and select it in a chat. Keep the computer awake, the tunnel running and the phone connected.

Try “Use Swiffle to create an Evidence Pack called November GST audit.” Open **Evidence packs** on the phone to see it. To review evidence, ask “Read the November GST audit pack and list the source page for each saved item.” Scanned clips have a filename and page reference. Only captures with saved text contain an excerpt.

Switch off **Allow changes** to keep reading available. Switching off **Allow reading** disables all tools for that plugin. Swiffle checks permission on every call. Refresh the ChatGPT plugin after changing permissions to update the displayed tools. Disconnecting the phone or stopping the tunnel ends remote access.

## Extend Swiffle

A developer can add a bundled plugin in `app/src/main/java/com/swiffle/app/plugins`. Implement `SwifflePlugin` with a unique ID, title, access description and a list of `PluginTool` values. Each tool declares its JSON input schema, annotations and suspending Android handler. Validate arguments inside the handler and return a bounded JSON object. Register the implementation in `SwifflePlugins.installed`, build and install the updated APK, then enable it in Settings. The settings UI and MCP discovery use the same registry. The Python bridge needs no edits. This version distributes developer-written Kotlin extensions with the APK.

| Tool | On-device action |
| --- | --- |
| swiffle_list_packs | Return titles, stable IDs and item counts, 25 packs per page |
| swiffle_read_pack | Return up to 10 items with text previews, source filename, page, group, note and review flag |
| swiffle_create_pack | Create an empty pack and select it for the next capture |
| swiffle_set_reviewed | Set one item's review flag while preserving its other fields |

Writes happen before success is returned. If the USB connection drops during creation, list the packs before repeating the request. Captured images and full PDF files stay on the phone.
