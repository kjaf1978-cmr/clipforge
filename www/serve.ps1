# ClipForge local server - serves this folder on http://localhost:8765 (only reachable from this PC)
# Browsers refuse to run the conversion engine from a double-clicked file, so this tiny server is needed.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$port = 8765
$mime = @{
  ".html"="text/html; charset=utf-8"; ".js"="text/javascript"; ".wasm"="application/wasm";
  ".svg"="image/svg+xml"; ".png"="image/png"; ".webmanifest"="application/manifest+json"; ".json"="application/json"
}
$listener = New-Object System.Net.HttpListener
$started = $false
foreach ($p in $port..($port+10)) {
  try { $listener.Prefixes.Clear(); $listener.Prefixes.Add("http://localhost:$p/"); $listener.Start(); $port = $p; $started = $true; break } catch { $listener = New-Object System.Net.HttpListener }
}
if (-not $started) { Write-Host "Could not open a local port (8765-8775)."; Read-Host "Press Enter to close"; exit 1 }
$url = "http://localhost:$port/index.html"
Write-Host ""
Write-Host "  ClipForge is running at $url"
Write-Host "  Keep this window open while converting. Close it to stop the app."
Write-Host ""
try { Start-Process $url } catch { Write-Host "  Open $url in your browser." }
while ($listener.IsListening) {
  try {
    $ctx = $listener.GetContext()
    $rel = [Uri]::UnescapeDataString($ctx.Request.Url.AbsolutePath.TrimStart('/'))
    if ($rel -eq "") { $rel = "index.html" }
    $path = [System.IO.Path]::GetFullPath((Join-Path $root $rel))
    $res = $ctx.Response
    if ($path.StartsWith($root) -and (Test-Path $path -PathType Leaf)) {
      $ext = [System.IO.Path]::GetExtension($path).ToLower()
      $res.ContentType = if ($mime.ContainsKey($ext)) { $mime[$ext] } else { "application/octet-stream" }
      $bytes = [System.IO.File]::ReadAllBytes($path)
      $res.ContentLength64 = $bytes.Length
      $res.OutputStream.Write($bytes, 0, $bytes.Length)
    } else { $res.StatusCode = 404 }
    $res.OutputStream.Close()
  } catch { }
}
