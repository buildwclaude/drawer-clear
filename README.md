# Drawer Clear

Makes the Nothing Launcher's app drawer background transparent, so the wallpaper shows through.
No root needed. It uses Shizuku plus Android 12+ fabricated resource overlays to swap the drawer's background colour inside the launcher.

## Install
Download **[apk/DrawerClear.apk](apk/DrawerClear.apk)** (or the `latest` release) on the phone and install it.

## Set up Shizuku (once, and again after each reboot)
1. Install **Shizuku** from the Play Store.
2. Settings → About phone → tap **Build number** 7 times to unlock Developer options.
3. Settings → System → Developer options → turn on **Wireless debugging**.
4. Open Shizuku → **Start via Wireless debugging** → follow its pairing steps.
5. Open Drawer Clear → **Grant permission**.

## Use (v1 = finder + tester)
1. **Scan launcher.** This lists the launcher's colours. The ones under *Likely drawer colours* are the best guesses.
2. Tap one to make it the target, pick **Clear** (or a dim level), then tap **Apply**.
3. Open the app drawer. If nothing changed, tap **Restart launcher**, or try the next candidate.
4. **Remove all** puts everything back.

If none of them work, tap **Copy full report** and paste it in the chat.

## What it can and can't do
- It only reads and changes `com.nothing.launcher`. Android won't even let it see other apps.
- It runs only four kinds of shell command, all checked in the app before running ([ShizukuShell.kt](app/src/main/java/app/drawerclear/ShizukuShell.kt)): list, create, enable and disable `dc_` colour overlays on the Nothing Launcher, and restart the launcher.
- It requests no permissions: no internet, no storage, nothing else. The build fails if one ever appears.
