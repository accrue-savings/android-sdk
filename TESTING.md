# Android SDK — Local Testing & Debugging Guide

A general reference for setting up an Android emulator, connecting it to a
local embed dev server, and using Chrome DevTools to inspect the WebView.

---

## 1. Create an Emulator

1. Open **Android Studio → Device Manager** (or the AVD Manager icon in the
   toolbar).
2. Click **Create Virtual Device**.
3. Choose a hardware profile. **Pixel 9 Pro XL** is a good default — large
   screen, well-supported system images.
4. Select a system image:
   - **API 36 (Android 16)** — latest; required to test Android 16 behavior.
   - **API 35 (Android 15)** — tests forced edge-to-edge (mandatory on
     `targetSdk=35+`).
   - **API 34 (Android 14)** — pre-edge-to-edge baseline; useful as a
     comparison target.
5. Finish and launch the AVD.

> **Tip:** You can have multiple AVDs for different API levels and switch
> between them without rebuilding — helpful for before/after comparisons.

---

## 2. Start the Local Embed Dev Server

```zsh
# From the monorepo root
cd apps/embed

# Live dev server with hot reload (port 5173)
pnpm dev

# OR production build + preview server (port 4173)
pnpm build && pnpm preview
```

---

## 3. Connect the Emulator to Your Host Machine

The emulator's `localhost` is the emulator itself, not your Mac. Use one of
these approaches:

### Option A — `adb reverse` (recommended)

Forwards host ports into the emulator so `localhost` URLs work normally:

```zsh
adb reverse tcp:5173 tcp:5173    # embed — pnpm dev
adb reverse tcp:4173 tcp:4173    # embed — pnpm build && pnpm preview
adb reverse tcp:3001 tcp:3001    # core-api
adb reverse tcp:3007 tcp:3007    # auth-api
```

Re-run these after every emulator restart or adb reconnect. The demo app's
default URL (`http://localhost:5173/webview`) works out of the box once these
are set.

### Option B — `10.0.2.2` (no setup required)

The Android emulator automatically maps `10.0.2.2` to the host's `localhost`.
Set the URL field in the demo app to:

```
http://10.0.2.2:5173/webview     # pnpm dev
http://10.0.2.2:4173/webview     # pnpm preview
```

---

## 4. Load the Wallet in the Demo App

1. Launch the app on the emulator.
2. Tap **Use sample values** — fills in the URL, merchant ID, and user data
   from `Constants.kt → SampleData`, then immediately loads the wallet.

> To change the default URL or merchant data used by "Use sample values", edit
> `androidsdk/src/main/java/com/accruesavings/androidsdk/Constants.kt →
> SampleData`.

---

## 5. Inspect the WebView with Chrome DevTools

Chrome DevTools can attach to any Android WebView with debugging enabled. The
SDK calls `WebView.setWebContentsDebuggingEnabled(true)` automatically in both
`newInstance` and `newInstanceWithEarlyInit`.

### Connect

1. On your Mac, open **Google Chrome** and navigate to `chrome://inspect`.
2. Make sure the emulator is running and the wallet is loaded.
3. Under **Remote Target**, find the WebView entry and click **inspect**.
4. A full DevTools window opens — Console, Elements, Network, Sources all work.

> If no target appears, run `adb devices` to confirm the emulator is listed,
> then try reloading the `chrome://inspect` tab.

### Useful Console Diagnostics

```javascript
// Viewport dimensions
console.log({
    innerHeight: window.innerHeight,
    innerWidth: window.innerWidth,
    visualViewportHeight: window.visualViewport?.height,
    visualViewportOffsetTop: window.visualViewport?.offsetTop,
    documentScrollHeight: document.documentElement.scrollHeight,
});

// SDK-injected CSS variable (bottom inset for Android nav bar)
const style = getComputedStyle(document.documentElement);
console.log({
    accrueBottomInset: style.getPropertyValue('--accrue-bottom-inset'),
});

// Inspect a fixed-bottom element's position
const footer =
    document.querySelector('[class*="ScreenFooter"]') ??
    document.querySelector('[class*="fixedContent"]');
if (footer) {
    const rect = footer.getBoundingClientRect();
    console.log('footer rect:', rect);
    console.log('hidden below viewport:', rect.bottom - window.innerHeight);
}
```

---

## 6. Demo App Layout Scenarios

The demo app has two layout presets that mirror real merchant patterns. The `●`
marker shows the currently active scenario.

| Button | Layout | What it tests |
|--------|--------|---------------|
| **Scenario A (new)** | Full-screen WebView, no bottom nav | WebView extends behind the gesture nav bar; SDK injects nav bar height via overlap detection |
| **Scenario B (old)** | `marginBottom=56dp` on the WebView + overlay `BottomNavigationView` | Old Snipes-style layout; SDK injects nav bar height via proximity detection |

**Toggle Nav** cycles between the two. Use Scenario A when testing a merchant
with a full-screen WebView; use Scenario B when testing a merchant that uses a
fixed bottom margin with an overlay nav bar.

---

## 7. Common adb Commands

```zsh
# List connected devices and emulators
adb devices

# Show current reverse port forwarding rules
adb reverse --list

# Remove a specific reverse rule
adb reverse --remove tcp:5173

# Remove all reverse rules
adb reverse --remove-all

# Stream app logs filtered by tag
adb logcat -s AccrueWebView

# Clear buffer then stream fresh
adb logcat -c && adb logcat -s AccrueWebView

# Install a local APK
adb install path/to/app.apk

# Restart adb server (fixes "device not found" errors)
adb kill-server && adb start-server
```

---

## 8. Troubleshooting

**WebView shows a blank screen**
- Confirm the dev server is running and port forwarding is active
  (`adb reverse --list`).
- Test the server from the emulator: open Chrome on the emulator and navigate
  to `http://localhost:5173`.

**`chrome://inspect` shows no targets**
- Run `adb devices` — if the emulator isn't listed, restart the adb server.
- Make sure the app is in the foreground and the WebView has finished loading.
- Close and reopen the `chrome://inspect` tab.

**Emulator crashes or becomes unresponsive**
- Cold boot: in AVD Manager, click the dropdown arrow next to the device →
  **Cold Boot Now**.
- GPU-related crashes in the emulator (e.g. `Chrome_InProcGpu` SIGTRAP) are
  emulator-specific and unrelated to app code — a cold boot resolves them.

**Embed changes don't appear in the WebView**
- With `pnpm dev`, Vite hot-reloads CSS/JS automatically within a second or two.
- If it doesn't update, tap **Reload** in the demo app or run
  `location.reload()` in the DevTools console.
- For native SDK changes, rebuild and reinstall via Run ▶ in Android Studio.
