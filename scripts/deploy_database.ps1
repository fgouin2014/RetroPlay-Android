# Deploy libretro-database to Android device
# Usage:
#   .\scripts\deploy_database.ps1 -Console nes           # Deploy NES only (fast, ~3 min)
#   .\scripts\deploy_database.ps1 -All                   # Deploy all consoles (slow, ~20 min)

param(
    [string]$Console = "nes",
    [switch]$All = $false,
    [switch]$MetadataOnly = $false
)

$SOURCE = "c:\repos\libretro-database-master"
$DEST_BASE = "/storage/emulated/0/GameLibrary-Data/database"

Write-Host ""
Write-Host "🚀 libretro-database Deployment Tool" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Cyan

# Check device connected
$device = adb devices | Select-String "device$"
if (-not $device) {
    Write-Host "❌ ERROR: No Android device connected!" -ForegroundColor Red
    Write-Host "   Please connect your device via USB and enable ADB debugging." -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ Device connected" -ForegroundColor Green

# Create base directories
Write-Host "`n📁 Creating database directories on device..." -ForegroundColor Yellow
adb shell "mkdir -p $DEST_BASE/metadata/genre" 2>&1 | Out-Null
adb shell "mkdir -p $DEST_BASE/metadata/developer" 2>&1 | Out-Null
adb shell "mkdir -p $DEST_BASE/metadata/releaseyear" 2>&1 | Out-Null
adb shell "mkdir -p $DEST_BASE/metadata/rumble" 2>&1 | Out-Null
adb shell "mkdir -p $DEST_BASE/metadata/analog" 2>&1 | Out-Null
adb shell "mkdir -p $DEST_BASE/cht" 2>&1 | Out-Null
Write-Host "✅ Directories created" -ForegroundColor Green

# Get console full name for database paths
function Get-ConsoleFullName {
    param([string]$ConsoleId)
    
    switch ($ConsoleId) {
        "nes"           { "Nintendo - Nintendo Entertainment System" }
        "snes"          { "Nintendo - Super Nintendo Entertainment System" }
        "n64"           { "Nintendo - Nintendo 64" }
        "gba"           { "Nintendo - Game Boy Advance" }
        "gbc"           { "Nintendo - Game Boy Color" }
        "gb"            { "Nintendo - Game Boy" }
        "psx"           { "Sony - PlayStation" }
        "psp"           { "Sony - PlayStation Portable" }
        "genesis"       { "Sega - Mega Drive - Genesis" }
        "gamegear"      { "Sega - Game Gear" }
        "mastersystem"  { "Sega - Master System - Mark III" }
        "saturn"        { "Sega - Saturn" }
        "dreamcast"     { "Sega - Dreamcast" }
        "atari2600"     { "Atari - 2600" }
        "lynx"          { "Atari - Lynx" }
        default         { $ConsoleId }
    }
}

if ($All) {
    Write-Host "`n📦 FULL DEPLOYMENT MODE (All Consoles)" -ForegroundColor Cyan
    Write-Host "   This will deploy 24,863 cheat files + metadata" -ForegroundColor Yellow
    Write-Host "   Estimated time: 15-20 minutes" -ForegroundColor Yellow
    Write-Host "   Required space: ~600MB" -ForegroundColor Yellow
    
    $confirm = Read-Host "`nContinue? (Y/N)"
    if ($confirm -ne "Y" -and $confirm -ne "y") {
        Write-Host "❌ Cancelled by user" -ForegroundColor Red
        exit 0
    }
    
    Write-Host "`n📤 Deploying metadata (all consoles)..." -ForegroundColor Yellow
    adb push "$SOURCE\metadat\genre" "$DEST_BASE/metadata/genre/" 2>&1 | Out-Null
    adb push "$SOURCE\metadat\developer" "$DEST_BASE/metadata/developer/" 2>&1 | Out-Null
    adb push "$SOURCE\metadat\releaseyear" "$DEST_BASE/metadata/releaseyear/" 2>&1 | Out-Null
    Write-Host "✅ Metadata deployed" -ForegroundColor Green
    
    if (-not $MetadataOnly) {
        Write-Host "`n📤 Deploying cheats (24,863 files)..." -ForegroundColor Yellow
        Write-Host "   This will take 10-15 minutes..." -ForegroundColor Yellow
        adb push "$SOURCE\cht" "$DEST_BASE/cht/" 2>&1 | Out-Null
        Write-Host "✅ Cheats deployed" -ForegroundColor Green
    }
    
} else {
    # Single console deployment
    $consoleName = Get-ConsoleFullName -ConsoleId $Console
    
    Write-Host "`n📦 CONSOLE-SPECIFIC DEPLOYMENT: $consoleName" -ForegroundColor Cyan
    
    # Check if source exists
    $genreFile = "$SOURCE\metadat\genre\$consoleName.dat"
    if (-not (Test-Path $genreFile)) {
        Write-Host "❌ ERROR: Console not found in database!" -ForegroundColor Red
        Write-Host "   Searched for: $genreFile" -ForegroundColor Yellow
        exit 1
    }
    
    # Deploy metadata
    Write-Host "`n📤 Deploying metadata for $Console..." -ForegroundColor Yellow
    
    if (Test-Path "$SOURCE\metadat\genre\$consoleName.dat") {
        adb push "$SOURCE\metadat\genre\$consoleName.dat" "$DEST_BASE/metadata/genre/" 2>&1 | Out-Null
        Write-Host "   ✓ Genre metadata" -ForegroundColor Green
    }
    
    if (Test-Path "$SOURCE\metadat\developer\$consoleName.dat") {
        adb push "$SOURCE\metadat\developer\$consoleName.dat" "$DEST_BASE/metadata/developer/" 2>&1 | Out-Null
        Write-Host "   ✓ Developer metadata" -ForegroundColor Green
    }
    
    if (Test-Path "$SOURCE\metadat\releaseyear\$consoleName.dat") {
        adb push "$SOURCE\metadat\releaseyear\$consoleName.dat" "$DEST_BASE/metadata/releaseyear/" 2>&1 | Out-Null
        Write-Host "   ✓ Release year metadata" -ForegroundColor Green
    }
    
    if (Test-Path "$SOURCE\metadat\rumble\$consoleName.dat") {
        adb push "$SOURCE\metadat\rumble\$consoleName.dat" "$DEST_BASE/metadata/rumble/" 2>&1 | Out-Null
        Write-Host "   ✓ Rumble metadata" -ForegroundColor Green
    }
    
    if (Test-Path "$SOURCE\metadat\analog\$consoleName.dat") {
        adb push "$SOURCE\metadat\analog\$consoleName.dat" "$DEST_BASE/metadata/analog/" 2>&1 | Out-Null
        Write-Host "   ✓ Analog metadata" -ForegroundColor Green
    }
    
    Write-Host "✅ Metadata deployed for $Console" -ForegroundColor Green
    
    # Deploy cheats
    if (-not $MetadataOnly) {
        $cheatDir = "$SOURCE\cht\$consoleName"
        if (Test-Path $cheatDir) {
            $cheatCount = (Get-ChildItem -Path $cheatDir -Filter "*.cht" | Measure-Object).Count
            
            Write-Host "`n📤 Deploying $cheatCount cheat files for $Console..." -ForegroundColor Yellow
            Write-Host "   Estimated time: 1-3 minutes" -ForegroundColor Yellow
            
            adb push "$cheatDir" "$DEST_BASE/cht/" 2>&1 | Out-Null
            Write-Host "✅ Cheats deployed ($cheatCount files)" -ForegroundColor Green
        } else {
            Write-Host "⚠️  No cheat files found for $Console" -ForegroundColor Yellow
        }
    }
}

# Verify deployment
Write-Host "`n🔍 Verifying deployment..." -ForegroundColor Yellow
$totalFiles = adb shell "find $DEST_BASE -type f 2>/dev/null | wc -l" 2>&1
$totalSize = adb shell "du -sh $DEST_BASE 2>/dev/null" 2>&1

Write-Host "✅ Verification complete:" -ForegroundColor Green
Write-Host "   Total files: $totalFiles" -ForegroundColor Cyan
Write-Host "   Total size: $totalSize" -ForegroundColor Cyan

Write-Host "`n🎉 Database deployment complete!" -ForegroundColor Green
Write-Host "=" * 60 -ForegroundColor Cyan

# Show usage instructions
Write-Host "`n📖 Next Steps:" -ForegroundColor Yellow
Write-Host "   1. Launch RetroPlay app" -ForegroundColor White
Write-Host "   2. Load a $Console game" -ForegroundColor White
Write-Host "   3. Check logcat for:" -ForegroundColor White
Write-Host "      - [DatabaseManager] CRC32 calculated" -ForegroundColor Gray
Write-Host "      - [DatabaseManager] Game identified" -ForegroundColor Gray
Write-Host "      - [DatabaseManager] Cheats available" -ForegroundColor Gray
Write-Host ""

