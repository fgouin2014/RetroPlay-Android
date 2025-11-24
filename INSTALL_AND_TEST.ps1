# Script d'Installation et Test - EmulationSettingsDialog
# PowerShell script pour installer l'APK et préparer les tests

Write-Host "=== Installation et Test EmulationSettingsDialog ===" -ForegroundColor Cyan
Write-Host ""

# Vérifier que ADB est disponible
$adbPath = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbPath) {
    Write-Host "ERREUR: ADB n'est pas dans le PATH" -ForegroundColor Red
    Write-Host "       Assurez-vous que Android SDK Platform Tools est installé" -ForegroundColor Yellow
    exit 1
}

# Vérifier qu'un device est connecté
Write-Host "1. Vérification du device..." -ForegroundColor Yellow
$devices = adb devices | Select-String -Pattern "device$"
if ($devices.Count -eq 0) {
    Write-Host "ERREUR: Aucun device Android connecté" -ForegroundColor Red
    Write-Host "       Connectez votre device et activez le débogage USB" -ForegroundColor Yellow
    exit 1
}
Write-Host "   Device connecté: $($devices[0].ToString().Split("`t")[0])" -ForegroundColor Green
Write-Host ""

# Compiler l'APK
Write-Host "2. Compilation de l'APK..." -ForegroundColor Yellow
./gradlew assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: La compilation a échoué" -ForegroundColor Red
    exit 1
}
Write-Host "   Compilation réussie!" -ForegroundColor Green
Write-Host ""

# Installer l'APK
Write-Host "3. Installation de l'APK..." -ForegroundColor Yellow
$apkPath = "app/build/outputs/apk/debug/app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "ERREUR: APK non trouvé: $apkPath" -ForegroundColor Red
    exit 1
}

adb install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: L'installation a échoué" -ForegroundColor Red
    exit 1
}
Write-Host "   Installation réussie!" -ForegroundColor Green
Write-Host ""

# Nettoyer les logs
Write-Host "4. Nettoyage des logs..." -ForegroundColor Yellow
adb logcat -c
Write-Host "   Logs nettoyés!" -ForegroundColor Green
Write-Host ""

# Instructions
Write-Host "=== Instructions de Test ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Ouvrez RetroPlay sur votre device" -ForegroundColor White
Write-Host "2. Lancez un jeu (recommandé: N64 ou SNES)" -ForegroundColor White
Write-Host "3. Ouvrez le menu principal" -ForegroundColor White
Write-Host "4. Sélectionnez 'Core Options' ou 'Emulation Settings'" -ForegroundColor White
Write-Host ""
Write-Host "Pour surveiller les logs en temps réel:" -ForegroundColor Yellow
Write-Host "   .\test_emulation_settings.ps1" -ForegroundColor Cyan
Write-Host ""
Write-Host "Ou manuellement:" -ForegroundColor Yellow
Write-Host "   adb logcat -s RetroArchEmulatorActivity:V CoreVariableManager:V | Select-String -Pattern 'Emulation|Core Options|Apply'" -ForegroundColor Cyan
Write-Host ""
Write-Host "Guide de test complet:" -ForegroundColor Yellow
Write-Host "   Voir TEST_EMULATION_SETTINGS_DIALOG_PRATIQUE.md" -ForegroundColor Cyan
Write-Host ""

