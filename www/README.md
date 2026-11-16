# ClipForge v1.0.0 – Video Converter & Compressor

Converts videos between formats (MP4, WMV, AVI, MKV, MOV, WebM, MPG, FLV, 3GP, GIF, MP3/M4A/WAV)
and makes files smaller. Runs completely offline; nothing is uploaded. Engine: FFmpeg 6 compiled
to WebAssembly (ffmpeg.wasm 0.12), bundled in the `core/` folder.

## Start it

| Device | How |
|---|---|
| **Windows PC** | Unzip, double-click **`Start-ClipForge.bat`**. A small black window opens (keep it open) and the app opens in your browser at `http://localhost:8765`. Close the black window to stop. No installation, no admin rights. |
| Linux / macOS | Run `./start.sh` (needs python3). |
| **Android phone** | Put the folder on any web host (e.g. GitHub Pages), open the address in Chrome once, then menu ⋮ → *Add to Home screen / Install app*. After the first load it works without internet (all ~32 MB of engine files are cached). |

Do **not** open `index.html` by double-clicking it: browsers block the conversion engine on `file://` pages.

## Use it

1. Add one or more videos (tap/click or drag & drop).
2. Choose the output format.
3. Choose a compression mode:
   - **Quality** – slider 0–100 % (100 % ≈ visually lossless, CRF 18; 60 % "Balanced" = CRF 27). Smallest file for the chosen look.
   - **Target size** – enter MB; the bitrate is calculated from the duration. Lands within about ±8 %; retries automatically (max. 3 passes) and lowers the resolution itself when a target is too small for a clean picture. Never makes a file larger than the original.
   - **Copy streams** – change the container only (seconds, no quality loss), when the codecs fit.
4. Optional: max. resolution (short side), frame rate, audio bitrate or remove audio, encoder speed.
5. **Convert**, then **Save** each result. The card shows old → new size and the % saved. *Preview* plays it if the browser supports the format (WMV/AVI/MPG/FLV play in Windows Media Player or VLC instead).

The built-in guide explains every term (container, codec, CRF, bitrate, 720p, fps …).

## Limits
- Single processor core inside the browser: roughly 0.3–1× real-time for 720p H.264 (WMV/AVI/MPG are ~2–3× faster).
- File size up to ~1.5 GB on a PC, a few hundred MB on a phone (browser memory).
- WMV output uses WMV2 (Windows Media Video 8) – plays in every Windows Media Player; WMV9/VC-1 has no free encoder.

## Verified (automated tests in headless Chrome, outputs checked with ffprobe)
21 cases: every output format; MP4/WMV/AVI target size (1 MB / 1 MB / 2 MB → 0.96 / 1.08 / 2.02 MB);
MKV→MP4 copy mode; resolution limits on landscape, portrait and 90°-rotated phone video;
WMV→MP4 and AVI→WMV as inputs; batch of 2 files; cancel + restart; offline reload via service worker;
the Windows launcher's server script (run under PowerShell 7).

## Files
`index.html` app · `lib/` ffmpeg.wasm loader · `core/` FFmpeg engine (WebAssembly) · `sw.js` offline cache ·
`manifest.webmanifest` + icons (phone install) · `Start-ClipForge.bat` + `serve.ps1` Windows launcher · `start.sh`.
