# Script de suppression des fichiers obsolètes
# Date: 2025-10-31
# Utilisation: .\cleanup_delete.ps1
# ATTENTION: Exécutez d'abord .\cleanup_backup.ps1 !

Write-Host "🗑️  SCRIPT DE SUPPRESSION DES FICHIERS OBSOLÈTES" -ForegroundColor Red
Write-Host "================================================" -ForegroundColor Red
Write-Host ""

$response = Read-Host "⚠️  Avez-vous exécuté cleanup_backup.ps1 ? (oui/non)"
if ($response -ne "oui") {
    Write-Host "❌ Exécutez d'abord: .\cleanup_backup.ps1" -ForegroundColor Red
    exit 1
}

Write-Host "`n📋 Fichiers à supprimer:" -ForegroundColor Yellow

# ========================================
# NIVEAU 1: Layouts orphelins (12 fichiers)
# ========================================
Write-Host "`n🟥 NIVEAU 1: Layouts orphelins et obsolètes (12 fichiers)" -ForegroundColor Red

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

Write-Host "Supprimer le Niveau 1 ?" -ForegroundColor Yellow
$response = Read-Host "(oui/non)"
if ($response -eq "oui") {
    foreach ($file in $layoutsLevel1) {
        if (Test-Path $file) {
            Remove-Item $file -Force
            Write-Host "  🗑️  Supprimé: $file" -ForegroundColor Green
        } else {
            Write-Host "  ⚠️  Déjà supprimé: $file" -ForegroundColor DarkYellow
        }
    }
    Write-Host "✅ Niveau 1 terminé: 12 fichiers supprimés" -ForegroundColor Green
} else {
    Write-Host "⏭️  Niveau 1 ignoré" -ForegroundColor Cyan
}

# ========================================
# NIVEAU 2: Activités mortes (4 fichiers)
# ========================================
Write-Host "`n🟨 NIVEAU 2: Activités mortes (4 fichiers)" -ForegroundColor Yellow

$filesLevel2 = @(
    "app\src\main\java\com\retroplay\activities\RelaxWebViewActivity.kt",
    "app\src\main\res\layout\activity_relax_webview.xml",
    "app\src\main\java\com\retroplay\activities\GameLibraryWebViewActivity.kt",
    "app\src\main\res\layout\activity_game_library_webview.xml"
)

Write-Host "Supprimer le Niveau 2 ?" -ForegroundColor Yellow
$response = Read-Host "(oui/non)"
if ($response -eq "oui") {
    foreach ($file in $filesLevel2) {
        if (Test-Path $file) {
            Remove-Item $file -Force
            Write-Host "  🗑️  Supprimé: $file" -ForegroundColor Green
        } else {
            Write-Host "  ⚠️  Déjà supprimé: $file" -ForegroundColor DarkYellow
        }
    }
    Write-Host "✅ Niveau 2 terminé: 4 fichiers supprimés" -ForegroundColor Green
} else {
    Write-Host "⏭️  Niveau 2 ignoré" -ForegroundColor Cyan
}

# ========================================
# NIVEAU 3: Système Kitt complet (4 fichiers)
# ========================================
Write-Host "`n🟦 NIVEAU 3: Système Kitt complet (4 fichiers)" -ForegroundColor Cyan

$kittFiles = @(
    "app\src\main\java\com\retroplay\fragments\KittFragment.kt",
    "app\src\main\java\com\retroplay\fragments\KittDrawerFragment.kt",
    "app\src\main\res\layout\fragment_kitt.xml",
    "app\src\main\res\layout\fragment_kitt_drawer.xml"
)

Write-Host "⚠️  ATTENTION: Ceci va supprimer TOUT le système Kitt!" -ForegroundColor Red
Write-Host "MainActivity.java devra être modifié!" -ForegroundColor Red
Write-Host "Supprimer le Niveau 3 ?" -ForegroundColor Yellow
$response = Read-Host "(oui/non)"
if ($response -eq "oui") {
    foreach ($file in $kittFiles) {
        if (Test-Path $file) {
            Remove-Item $file -Force
            Write-Host "  🗑️  Supprimé: $file" -ForegroundColor Green
        } else {
            Write-Host "  ⚠️  Déjà supprimé: $file" -ForegroundColor DarkYellow
        }
    }
    Write-Host "✅ Niveau 3 terminé: 4 fichiers supprimés" -ForegroundColor Green
    Write-Host "⚠️  N'oubliez pas de modifier MainActivity.java!" -ForegroundColor Red
} else {
    Write-Host "⏭️  Niveau 3 ignoré" -ForegroundColor Cyan
}

# ========================================
# NIVEAU 4: Doublons (3 fichiers)
# ========================================
Write-Host "`n🟪 NIVEAU 4: Doublons (3 fichiers)" -ForegroundColor Magenta

$doublons = @(
    "app\src\main\res\layout\activity_game_details.xml",
    "app\src\main\res\layout\item_game.xml",
    "app\src\main\res\layout\activity_native_emulator.xml"
)

Write-Host "Supprimer le Niveau 4 ?" -ForegroundColor Yellow
$response = Read-Host "(oui/non)"
if ($response -eq "oui") {
    foreach ($file in $doublons) {
        if (Test-Path $file) {
            Remove-Item $file -Force
            Write-Host "  🗑️  Supprimé: $file" -ForegroundColor Green
        } else {
            Write-Host "  ⚠️  Déjà supprimé: $file" -ForegroundColor DarkYellow
        }
    }
    Write-Host "✅ Niveau 4 terminé: 3 fichiers supprimés" -ForegroundColor Green
} else {
    Write-Host "⏭️  Niveau 4 ignoré" -ForegroundColor Cyan
}

# ========================================
# Nettoyage du dossier activities vide
# ========================================
Write-Host "`n🧹 Nettoyage des dossiers vides..." -ForegroundColor Cyan

$activitiesDir = "app\src\main\java\com\retroplay\activities"
if (Test-Path $activitiesDir) {
    $filesInDir = Get-ChildItem $activitiesDir
    if ($filesInDir.Count -eq 0) {
        Remove-Item $activitiesDir -Force
        Write-Host "  🗑️  Dossier vide supprimé: $activitiesDir" -ForegroundColor Green
    }
}

# ========================================
# Résumé final
# ========================================
Write-Host "`n✅ NETTOYAGE TERMINÉ!" -ForegroundColor Green
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Green

Write-Host "`n📊 Prochaines étapes:" -ForegroundColor Cyan
Write-Host "  1. Si Niveau 3 supprimé: Modifier MainActivity.java" -ForegroundColor Yellow
Write-Host "  2. Rebuild le projet: .\gradlew clean assembleDebug" -ForegroundColor Yellow
Write-Host "  3. Tester l'application" -ForegroundColor Yellow
Write-Host "  4. Commit: git add -A && git commit -m 'chore: cleanup obsolete files'" -ForegroundColor Yellow

Write-Host "`n💾 Les backups sont dans: backup_obsolete_files_*" -ForegroundColor Cyan

