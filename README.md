# PadMapper
Map keyboard, mouse and gamepad to on-screen touch on Android (built for Xiaomi Pad 6, crDroid, Android 16).

**Backends:** Shizuku (real multi-touch via `IInputManager`, recommended) and Accessibility `dispatchGesture` (taps/swipes only).

## Setup
1. Install and start [Shizuku](https://shizuku.rikka.app/), then open PadMapper and tap *Shizuku access*.
2. App info, three dots, *Allow restricted settings*, then enable PadMapper under Accessibility.
3. Toggle the dashboard with **Ctrl+Shift+M**. Drag markers, tap one then press a key to bind, long-press empty space to add a tap. Hide it and the mappings go live. **Scroll Lock** pauses mapping.

Profile is JSON (see `Mapping.kt` for types: TAP, HOLD, SWIPE, WASD, STICK, LOOK, SCROLL; mouse buttons are 1000/1001/1002).

## Release
Add secrets `KEYSTORE_B64`, `KS_PASS`, `KEY_ALIAS`, `KEY_PASS` (optional; falls back to debug signing), then `git tag v0.1.0 && git push --tags`.
