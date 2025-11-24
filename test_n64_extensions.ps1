# Script de Test - Extensions N64
# Usage: .\test_n64_extensions.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test des Extensions N64 - RetroPlay" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Nettoyer les logs précédents
Write-Host "[1/4] Nettoyage des logs..." -ForegroundColor Yellow
adb logcat -c
if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✓ Logcat nettoyé" -ForegroundColor Green
} else {
    Write-Host "   ✗ Erreur lors du nettoyage (continuer quand même)" -ForegroundColor Red
}

Write-Host ""
Write-Host "[2/4] Instructions:" -ForegroundColor Yellow
Write-Host "   1. Ouvrir RetroPlay sur le device" -ForegroundColor White
Write-Host "   2. Aller dans Paramètres → Console N64" -ForegroundColor White
Write-Host "   3. Configurer les extensions pour chaque port:" -ForegroundColor White
Write-Host "      - Port 1: Controller Pak (ou Rumble Pak)" -ForegroundColor White
Write-Host "      - Port 2: Rumble Pak (optionnel)" -ForegroundColor White
Write-Host "   4. Sauvegarder les paramètres" -ForegroundColor White
Write-Host "   5. Lancer un jeu N64 (ex: Super Mario 64)" -ForegroundColor White
Write-Host ""

# Attendre que l'utilisateur soit prêt
Write-Host "[3/4] Appuyez sur ENTER quand vous êtes prêt à lancer le jeu..." -ForegroundColor Yellow
Read-Host

# Démarrer le monitoring des logs
Write-Host ""
Write-Host "[4/4] Monitoring des logs (Ctrl+C pour arrêter)..." -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Filtrer les logs N64
adb logcat -s RetroArchEmulatorActivity:* LibretroDroid:* | Select-String -Pattern "N64|controller|extension|Port" -Context 1,1

