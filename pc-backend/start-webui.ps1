# 简易静态 Web 服务器：把 webui.html 服务到 http://127.0.0.1:1421/webui.html
# 用 Windows 自带的 HttpListener，不需要 Python
$port = 1421
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# 优先用同目录的 SdAiBackend.exe 后端（1420），WebUI 跑 1421
Add-Type -AssemblyName System.Net.Http
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$port/")
$listener.Start()
Write-Host "WebUI: http://127.0.0.1:$port/webui.html" -ForegroundColor Cyan
Write-Host "(后端应该在 1420 端口跑着，本 WebUI 调它出图)" -ForegroundColor Yellow
Write-Host "关掉这个窗口就停了" -ForegroundColor Gray

# 浏览器自动开
Start-Process "http://127.0.0.1:$port/webui.html"

while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $req = $ctx.Request
    $resp = $ctx.Response
    $path = $req.Url.LocalPath.TrimStart('/')
    if ([string]::IsNullOrEmpty($path)) { $path = 'webui.html' }
    $full = Join-Path $root $path
    if (Test-Path $full -PathType Leaf) {
        $bytes = [System.IO.File]::ReadAllBytes($full)
        $ext = [System.IO.Path]::GetExtension($full).ToLower()
        $mime = switch ($ext) {
            '.html' { 'text/html; charset=utf-8' }
            '.css'  { 'text/css' }
            '.js'   { 'application/javascript' }
            '.png'  { 'image/png' }
            '.jpg'  { 'image/jpeg' }
            '.json' { 'application/json' }
            default { 'application/octet-stream' }
        }
        $resp.ContentType = $mime
        $resp.ContentLength64 = $bytes.Length
        $resp.OutputStream.Write($bytes, 0, $bytes.Length)
    } else {
        $resp.StatusCode = 404
        $msg = [System.Text.Encoding]::UTF8.GetBytes("Not found: $path")
        $resp.OutputStream.Write($msg, 0, $msg.Length)
    }
    $resp.Close()
}
