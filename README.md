# LADB Reborn 3.0

A local ADB shell for Android — rebuilt from the ground up with a modern, clean,
professional GUI. Talks to your own device over Wireless debugging. No PC required,
no data leaves the device.

## What's new vs. upstream LADB

| Area | Upstream 2.6 | Reborn 3.0 |
|---|---|---|
| UI | XML Views, single Activity | Jetpack Compose + Material 3 (dynamic color) |
| Terminal | Plain TextView + 100 ms file polling, 16 KB cap | Termux terminal-emulator engine, real pty, full ANSI/colors, 5000-line scrollback |
| TUI apps | Not supported | vim / htop / top work (`adb shell -t` + pty resize via SIGWINCH) |
| Input | Single last command | Persistent history (500) + ↑/↓ + inline autocomplete (history, commands, package names) |
| App manager | None | Full list w/ search + filters, launch / force-stop / uninstall / clear data / disable / enable / grant-revoke permissions / extract APK |
| Pairing | Single dialog | Guided wizard, mDNS auto-detects both connect & pairing ports, live status console, manual port fallback |
| Bloat | PiracyChecker, Play OSS-licenses | Removed |

## Architecture

```
app/src/main/java/com/ladb/reborn/
├── LadbApp.kt / MainActivity.kt        # app shell + connection gate
├── core/
│   ├── Adb.kt                          # bundled AOSP adb client wrapper (libadb.so)
│   ├── MdnsDiscovery.kt                # _adb-tls-connect + _adb-tls-pairing discovery
│   ├── AdbManager.kt                   # pair/connect state machine + shell cmd runner
│   ├── TerminalController.kt           # TerminalSession (Termux) owner, pty shell -t
│   ├── AppsRepository.kt               # package catalog + full-power shell actions
│   └── Settings/History repositories
├── ui/
│   ├── terminal/                       # Compose Canvas renderer over TerminalBuffer
│   ├── apps/                           # App manager + detail sheet
│   ├── PairingScreen.kt / BootScreen / MainScaffold
│   └── settings/
└── services/QsTileService.kt           # Quick Settings tile
```

## Build

```bash
./gradlew assembleRelease   # signed, R8-minified (12 MB)
./gradlew assembleDebug     # debug variant, installs alongside official LADB
```

Requirements: JDK 17+, Android SDK 35.

## First run

1. Enable **Developer options → Wireless debugging**
2. Tap **"Pair device with pairing code"** in Settings (keep it open)
3. LADB Reborn auto-detects the pairing port via mDNS — enter the 6-digit code
4. After first connect the app self-grants `WRITE_SECURE_SETTINGS` for future
   auto-cycling of Wireless debugging

## Credits

- Original LADB concept & bundled `libadb.so` — draco / Tyler Nijmeh (Apache-2.0, see LICENSE-LADB)
- Terminal engine — Termux terminal-emulator v0.118.0 (Apache-2.0)
- AOSP adb client — The Android Open Source Project (Apache-2.0)
