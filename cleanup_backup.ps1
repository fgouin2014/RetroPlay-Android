# Script de backup avant nettoyage des fichiers obsolètes
# Date: 2025-10-31
# Utilisation: .\cleanup_backup.ps1

$backupDir = ".\backup_obsolete_files_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Path $backupDir -Force

Write-Host "📦 Création du backup dans: $backupDir" -ForegroundColor Green

# ========================================
# NIVEAU 1: Layouts orphelins (12 fichiers)
# ========================================
Write-Host "`n🟥 NIVEAU 1: Layouts orphelins et obsolètes" -ForegroundColor Red

$layoutsLevel1 = @(
    "app\src\main\res\layout\activity_ai_configuration.xml",
    "app\src\main\res\layout\activity_kitt.xml",
    "app\src\main\res\layout\activity_configuration.xml",
    "app\src\main\res\layout\activity_database.xml",
    "app\src\main\res\layout\activity_endpoints_list.xml",
    "app\src\main\res\layout\fragment_endpoints_list.xml",
    "app\src\main\res\layout\activity_server_configuration.xml",
    "app\src\main\res\layout\activity_server.xml",
    "app\src\main\res\layout\activity_settings.xml",
    "app\src\main\res\layout\activity_webserver_config.xml",
    "app\src\main\res\layout\activity_relax_webview13.xml",
    "app\src\main\res\layout\dialog_gamepad_settings.xml"
)

foreach ($file in $layoutsLevel1) {
    if (Test-Path $file) {
        $destPath = Join-Path $backupDir "level1_layouts"
        New-Item -ItemType Directory -Path $destPath -Force | Out-Null
        Copy-Item $file -Destination $destPath
        Write-Host "  ✓ Backed up: $file" -ForegroundColor Yellow
    } else {
        Write-Host "  ⚠ Not found: $file" -ForegroundColor DarkYellow
    }
}

# ========================================
# NIVEAU 2: Activités mortes (non dans Manifest)
# ========================================
Write-Host "`n🟨 NIVEAU 2: Activités mortes (non déclarées)" -ForegroundColor Yellow

$filesLevel2 = @(
    "app\src\main\java\com\retroplay\activities\RelaxWebViewActivity.kt",
    "app\src\main\res\layout\activity_relax_webview.xml",
    "app\src\main\java\com\retroplay\activities\GameLibraryWebViewActivity.kt",
    "app\src\main\res\layout\activity_game_library_webview.xml"
)

foreach ($file in $filesLevel2) {
    if (Test-Path $file) {
        $destPath = Join-Path $backupDir "level2_dead_activities"
        New-Item -ItemType Directory -Path $destPath -Force | Out-Null
        Copy-Item $file -Destination $destPath
        Write-Host "  ✓ Backed up: $file" -ForegroundColor Yellow
    } else {
        Write-Host "  ⚠ Not found: $file" -ForegroundColor DarkYellow
    }
}

# ========================================
# NIVEAU 3: Kitt complet (inutile selon utilisateur)
# ========================================
Write-Host "`n🟦 NIVEAU 3: Système Kitt (complet)" -ForegroundColor Cyan

$kittFiles = @(
    "app\src\main\java\com\retroplay\fragments\KittFragment.kt",
    "app\src\main\java\com\retroplay\fragments\KittDrawerFragment.kt",
    "app\src\main\res\layout\fragment_kitt.xml",
    "app\src\main\res\layout\fragment_kitt_drawer.xml"
)

foreach ($file in $kittFiles) {
    if (Test-Path $file) {
        $destPath = Join-Path $backupDir "level3_kitt_system"
        New-Item -ItemType Directory -Path $destPath -Force | Out-Null
        Copy-Item $file -Destination $destPath
        Write-Host "  ✓ Backed up: $file" -ForegroundColor Yellow
    } else {
        Write-Host "  ⚠ Not found: $file" -ForegroundColor DarkYellow
    }
}

# ========================================
# NIVEAU 4: Doublons (anciennes versions)
# ========================================
Write-Host "`n🟪 NIVEAU 4: Doublons (anciennes versions)" -ForegroundColor Magenta

$doublons = @(
    "app\src\main\res\layout\activity_game_details.xml",
    "app\src\main\res\layout\item_game.xml",
    "app\src\main\res\layout\activity_native_emulator.xml"
)

foreach ($file in $doublons) {
    if (Test-Path $file) {
        $destPath = Join-Path $backupDir "level4_doublons"
        New-Item -ItemType Directory -Path $destPath -Force | Out-Null
        Copy-Item $file -Destination $destPath
        Write-Host "  ✓ Backed up: $file" -ForegroundColor Yellow
    } else {
        Write-Host "  ⚠ Not found: $file" -ForegroundColor DarkYellow
    }
}

# ========================================
# Résumé
# ========================================
Write-Host "`n✅ Backup complet terminé!" -ForegroundColor Green
Write-Host "📂 Backup location: $backupDir" -ForegroundColor Cyan
Write-Host "`n📊 Statistiques:" -ForegroundColor White
Write-Host "  - Niveau 1 (Layouts orphelins): $($layoutsLevel1.Count) fichiers"
Write-Host "  - Niveau 2 (Activités mortes): $($filesLevel2.Count) fichiers"
Write-Host "  - Niveau 3 (Système Kitt): $($kittFiles.Count) fichiers"
Write-Host "  - Niveau 4 (Doublons): $($doublons.Count) fichiers"
Write-Host "  - TOTAL: $($layoutsLevel1.Count + $filesLevel2.Count + $kittFiles.Count + $doublons.Count) fichiers" -ForegroundColor Green

Write-Host "`n⚠️  ATTENTION: MainActivity.java devra être modifié pour retirer KittFragment!" -ForegroundColor Red
Write-Host "Exécutez ensuite: .\cleanup_delete.ps1" -ForegroundColor Cyan

