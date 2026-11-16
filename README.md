# ClipForge – offline video converter & compressor

Convert videos between **MP4, WMV, AVI, MKV, MOV, WebM, MPG, FLV, 3GP, GIF** (and extract MP3/M4A/WAV audio) and **shrink them** by quality level or to an exact target size in MB. Everything runs on the device; nothing is uploaded. Engine: FFmpeg 6 compiled to WebAssembly (ffmpeg.wasm 0.12).

## Android app (APK)
1. On the phone open **Releases** → latest **ClipForge-vX.Y.Z.apk** → download.
2. Open the downloaded file. If Android asks, allow *Install unknown apps* for Chrome / My Files, then tap **Install**.
3. Open **ClipForge** from the app drawer. Results are saved to **Download/ClipForge**; *Share* sends them to WhatsApp, email etc.; *Open in player* plays WMV/AVI/MPG in your video player app.

Keep the app in the foreground while converting (the screen stays on automatically) – Android may pause apps in the background.

Updates: every push to `main` builds a new APK; install it over the old one (same signing key, settings kept).

## Web version
`www/` is the same app as a web page/PWA (https://kjaf1978-cmr.github.io/clipforge/ once GitHub Pages is set to *Source: GitHub Actions* in Settings → Pages). On Windows, unzip and run `www/Start-ClipForge.bat`.

## Repository layout
| Path | Content |
|---|---|
| `www/` | the app (HTML/JS UI, FFmpeg engine in `core/`, service worker for offline use) |
| `android/` | Android shell: a WebView that serves `www/` from inside the APK (`MainActivity.java`) with a native bridge for file picking, saving to Downloads, sharing and keeping the screen on |
| `android/signing/` | the APK signing key. Committed on purpose so every automated build can update the installed app. For stricter security move it to repository secrets `CLIPFORGE_KEYSTORE_B64` and `CLIPFORGE_KS_PASS` and delete the folder – the workflow uses the secrets automatically when present. |
| `.github/workflows/build.yml` | builds the signed APK, publishes it as a GitHub Release, deploys `www/` to GitHub Pages |
