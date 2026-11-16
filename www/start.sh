#!/bin/sh
# ClipForge launcher for Linux / macOS (needs python3)
cd "$(dirname "$0")"
URL="http://localhost:8765/index.html"
( sleep 1; xdg-open "$URL" 2>/dev/null || open "$URL" 2>/dev/null ) &
echo "ClipForge running at $URL - press Ctrl+C to stop."
python3 -m http.server 8765 --bind 127.0.0.1
