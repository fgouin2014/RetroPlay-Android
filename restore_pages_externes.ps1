# Script de restauration des pages externes depuis un backup
# Usage: .\restore_pages_externes.ps1 -BackupTimestamp "20251026_1345" -App "RetroPlay"
# Apps valides: RetroPlay, ChatAI, GameLibrary, All

param(
    [Parameter(Mandatory=$true, HelpMessage="Timestamp du backup (format: yyyyMMdd_HHmm)")]
    [string]$BackupTimestamp,
    
    [Parameter(Mandatory=$true, HelpMessage="App à restaurer (RetroPlay, ChatAI, GameLibrary, All)")]
    [ValidateSet("RetroPlay", "ChatAI", "GameLibrary", "All")]
    [string]$App
)

$backupDir = "backups/$BackupTimestamp"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RESTAURATION DEPUIS BACKUP" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier que le backup existe
if (-not (Test-Path $backupDir)) {
    Write-Host "❌ ERREUR: Backup introuvable: $backupDir" -ForegroundColor Red
    Write-Host ""
    Write-Host "Backups disponibles:" -ForegroundColor Yellow
    Get-ChildItem -Path "backups" -Directory | ForEach-Object {
        Write-Host "  - $($_.Name)" -ForegroundColor Cyan
    }
    Write-Host ""
    exit 1
}

Write-Host "Backup source: $backupDir" -ForegroundColor Yellow
Write-Host "App(s) à restaurer: $App" -ForegroundColor Yellow
Write-Host ""

# Vérifier que ADB est disponible
try {
    $null = adb devices
} catch {
    Write-Host "❌ ERREUR: ADB non trouvé ou device non connecté" -ForegroundColor Red
    exit 1
}

# Confirmation
Write-Host "⚠️  ATTENTION: Cette opération va écraser les fichiers actuels sur le device!" -ForegroundColor Red
Write-Host ""
$confirmation = Read-Host "Continuer? (o/N)"
if ($confirmation -ne "o" -and $confirmation -ne "O") {
    Write-Host "Opération annulée." -ForegroundColor Yellow
    exit 0
}

Write-Host ""

# RetroPlay
if ($App -eq "RetroPlay" -or $App -eq "All") {
    if (Test-Path "$backupDir/RetroPlay") {
        Write-Host "Restauration RetroPlay..." -ForegroundColor Green
        adb push "$backupDir/RetroPlay/" /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/
        Write-Host "  ✅ RetroPlay restauré" -ForegroundColor Green
        
        # Redémarrer l'app
        Write-Host "  Redémarrage de l'app..." -ForegroundColor Yellow
        adb shell am force-stop com.retroplay 2>&1 | Out-Null
        Start-Sleep -Seconds 1
        adb shell am start -n com.retroplay/.GameListActivity 2>&1 | Out-Null
    } else {
        Write-Host "  ⚠️ Pas de backup RetroPlay dans $backupDir" -ForegroundColor Yellow
    }
}

# ChatAI
if ($App -eq "ChatAI" -or $App -eq "All") {
    if (Test-Path "$backupDir/ChatAI") {
        Write-Host "Restauration ChatAI..." -ForegroundColor Green
        adb push "$backupDir/ChatAI/" /storage/emulated/0/ChatAI-Files/sites/gamelibrary/
        Write-Host "  ✅ ChatAI restauré" -ForegroundColor Green
        
        # Redémarrer l'app
        Write-Host "  Redémarrage de l'app..." -ForegroundColor Yellow
        adb shell am force-stop com.chatai 2>&1 | Out-Null
        Start-Sleep -Seconds 1
        adb shell am start -n com.chatai/.MainActivity 2>&1 | Out-Null
    } else {
        Write-Host "  ⚠️ Pas de backup ChatAI dans $backupDir" -ForegroundColor Yellow
    }
}

# GameLibrary (OBSOLÈTE)
if ($App -eq "GameLibrary" -or $App -eq "All") {
    Write-Host ""
    Write-Host "⚠️  ATTENTION: GameLibrary-Android est OBSOLÈTE!" -ForegroundColor Red
    Write-Host "    Utilisez RetroPlay-Android à la place." -ForegroundColor Yellow
    Write-Host "    Voir: GAMELIBRARY_OBSOLETE.md" -ForegroundColor Yellow
    Write-Host ""
    
    $confirmGL = Read-Host "Voulez-vous vraiment restaurer GameLibrary (obsolète)? (o/N)"
    if ($confirmGL -eq "o" -or $confirmGL -eq "O") {
        if (Test-Path "$backupDir/GameLibrary") {
            Write-Host "Restauration GameLibrary (obsolète)..." -ForegroundColor DarkGray
            adb push "$backupDir/GameLibrary/" /storage/emulated/0/GameLibrary-Files/sites/gamelibrary/
            Write-Host "  ✅ GameLibrary restauré (mais toujours obsolète)" -ForegroundColor DarkYellow
            
            # Redémarrer l'app
            Write-Host "  Redémarrage de l'app..." -ForegroundColor Yellow
            adb shell am force-stop com.gamelibrary 2>&1 | Out-Null
            Start-Sleep -Seconds 1
            adb shell am start -n com.gamelibrary/.GameListActivity 2>&1 | Out-Null
        } else {
            Write-Host "  ⚠️ Pas de backup GameLibrary dans $backupDir" -ForegroundColor Yellow
        }
    } else {
        Write-Host "  Restauration GameLibrary annulée." -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RESTAURATION TERMINÉE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "✅ Les fichiers ont été restaurés depuis: $backupDir" -ForegroundColor Green
Write-Host "✅ Les apps ont été redémarrées" -ForegroundColor Green
Write-Host ""
Write-Host "📝 Vérifiez que tout fonctionne correctement!" -ForegroundColor Yellow
Write-Host ""

