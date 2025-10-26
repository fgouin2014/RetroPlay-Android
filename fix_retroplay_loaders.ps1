# Script pour adapter les loaders de ChatAI vers RetroPlay (port 8888 -> 7777)

$loaders = @(
    "loader_32x.js", "loader_3do.js", "loader_arcade.js", "loader_atari2600.js",
    "loader_atari5200.js", "loader_atari7800.js", "loader_atarilynx.js", "loader_fbneo.js",
    "loader_gamegear.js", "loader_gb.js", "loader_gba.js", "loader_gbc.js",
    "loader_jaguar.js", "loader_lynx.js", "loader_mame.js", "loader_mastersystem.js",
    "loader_megadrive.js", "loader_n64.js", "loader_nds.js", "loader_nes.js",
    "loader_ngp.js", "loader_pce.js", "loader_psp.js", "loader_psx.js",
    "loader_saturn.js", "loader_segacd.js", "loader_sms.js", "loader_snes.js",
    "loader_vb.js", "loader_virtualboy.js", "loader_ws.js",
    "psxcontrol.js", "psxcontroldpad.js",
    "pspcontrol.js", "pspcontroldpad.js",
    "segacontrol.js"
)

Write-Host "Modification des loaders pour RetroPlay (port 8888 -> 7777)..." -ForegroundColor Cyan

foreach ($loader in $loaders) {
    $localFile = "temp_$loader"
    $remotePath = "/storage/emulated/0/RetroPlay-Files/sites/gamelibrary/$loader"
    
    # Pull file
    Write-Host "  Processing: $loader" -ForegroundColor Yellow
    adb pull $remotePath $localFile 2>$null
    
    if (Test-Path $localFile) {
        # Replace 8888 with 7777
        $content = Get-Content $localFile -Raw -Encoding UTF8
        $content = $content -replace ':8888', ':7777'
        Set-Content $localFile -Value $content -Encoding UTF8 -NoNewline
        
        # Push back
        adb push $localFile $remotePath | Out-Null
        Remove-Item $localFile
        Write-Host "    -> Updated!" -ForegroundColor Green
    } else {
        Write-Host "    -> Not found, skipped" -ForegroundColor Gray
    }
}

Write-Host "`nDone! All loaders updated for RetroPlay (port 7777)" -ForegroundColor Green

