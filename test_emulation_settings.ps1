# Script de Test - EmulationSettingsDialog
# PowerShell script pour faciliter les tests

Write-Host "=== Test EmulationSettingsDialog ===" -ForegroundColor Cyan
Write-Host ""

# Vérifier que ADB est disponible
$adbPath = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbPath) {
    Write-Host "ERREUR: ADB n'est pas dans le PATH" -ForegroundColor Red
    exit 1
}

Write-Host "1. Nettoyage des logs..." -ForegroundColor Yellow
adb logcat -c

Write-Host "2. Démarrage du monitoring des logs..." -ForegroundColor Yellow
Write-Host "   (Appuyez sur Ctrl+C pour arrêter)" -ForegroundColor Gray
Write-Host ""

# Filtrer les logs pertinents
adb logcat -s RetroArchEmulatorActivity:V CoreVariableManager:V | Select-String -Pattern "Emulation|Core Options|Apply|Fast Forward|Audio Volume|Category|Video|Audio|Input|Performance|Emulation|Other"

