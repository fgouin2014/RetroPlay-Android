# Script de backup automatique des pages externes
# Usage: .\backup_pages_externes.ps1

$timestamp = Get-Date -Format "yyyyMMdd_HHmm"
$backupDir = "backups/$timestamp"

# Créer le dossier de backup
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
New-Item -ItemType Directory -Force -Path "$backupDir/RetroPlay" | Out-Null
New-Item -ItemType Directory -Force -Path "$backupDir/ChatAI" | Out-Null
New-Item -ItemType Directory -Force -Path "$backupDir/GameLibrary" | Out-Null

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  BACKUP DES PAGES EXTERNES" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Timestamp: $timestamp" -ForegroundColor Yellow
Write-Host "Destination: $backupDir" -ForegroundColor Yellow
Write-Host ""

# Vérifier que ADB est disponible
try {
    $null = adb devices
} catch {
    Write-Host "❌ ERREUR: ADB non trouvé ou device non connecté" -ForegroundColor Red
    exit 1
}

# RetroPlay
Write-Host "Backup RetroPlay..." -ForegroundColor Green
try {
    adb pull /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/ "$backupDir/RetroPlay/" 2>&1 | Out-Null
    Write-Host "  ✅ RetroPlay sauvegardé" -ForegroundColor Green
} catch {
    Write-Host "  ⚠️ RetroPlay non trouvé sur le device" -ForegroundColor Yellow
}

# ChatAI
Write-Host "Backup ChatAI..." -ForegroundColor Green
try {
    adb pull /storage/emulated/0/ChatAI-Files/sites/gamelibrary/ "$backupDir/ChatAI/" 2>&1 | Out-Null
    Write-Host "  ✅ ChatAI sauvegardé" -ForegroundColor Green
} catch {
    Write-Host "  ⚠️ ChatAI non trouvé sur le device" -ForegroundColor Yellow
}

# GameLibrary (OBSOLÈTE - backup pour historique uniquement)
Write-Host "Backup GameLibrary (obsolète)..." -ForegroundColor DarkGray
try {
    adb pull /storage/emulated/0/GameLibrary-Files/sites/gamelibrary/ "$backupDir/GameLibrary/" 2>&1 | Out-Null
    Write-Host "  ⚠️ GameLibrary sauvegardé (app obsolète, utilisez RetroPlay)" -ForegroundColor DarkYellow
} catch {
    Write-Host "  ℹ️ GameLibrary non installé (normal, app obsolète)" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  BACKUP TERMINÉ" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "📁 Emplacement: $backupDir" -ForegroundColor Green
Write-Host ""

# Lister les fichiers sauvegardés
Get-ChildItem -Path $backupDir -Recurse -File | ForEach-Object {
    $relativePath = $_.FullName.Substring((Get-Location).Path.Length + 1)
    Write-Host "  - $relativePath" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "✅ Backup terminé avec succès!" -ForegroundColor Green
Write-Host ""

