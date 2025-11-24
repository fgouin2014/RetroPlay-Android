# Script d'Analyse des Logs - EmulationSettingsDialog
# Analyse les logs pour vérifier le comportement du dialog

Write-Host "=== Analyse des Logs EmulationSettingsDialog ===" -ForegroundColor Cyan
Write-Host ""

# Filtrer les logs pertinents
Write-Host "Recherche des logs pertinents..." -ForegroundColor Yellow
Write-Host ""

# Commandes pour filtrer les logs
Write-Host "1. Logs du dialog:" -ForegroundColor Green
Write-Host "   adb logcat -s EmulationSettingsDialog:V" -ForegroundColor Cyan
Write-Host ""

Write-Host "2. Logs Core Options:" -ForegroundColor Green
Write-Host "   adb logcat -s CoreVariableManager:V | Select-String -Pattern 'Parsed|Filtered|Applied'" -ForegroundColor Cyan
Write-Host ""

Write-Host "3. Logs RetroArchEmulatorActivity (ouverture dialog):" -ForegroundColor Green
Write-Host "   adb logcat -s RetroArchEmulatorActivity:V | Select-String -Pattern 'Core Options|Emulation Settings|showCoreOptionsDialog'" -ForegroundColor Cyan
Write-Host ""

Write-Host "4. Tous les logs pertinents:" -ForegroundColor Green
Write-Host "   adb logcat -s RetroArchEmulatorActivity:V CoreVariableManager:V EmulationSettingsDialog:V | Select-String -Pattern 'Emulation|Core Options|Apply|Category|Video|Audio|Input|Performance|Other|Fast Forward|Audio Volume'" -ForegroundColor Cyan
Write-Host ""

Write-Host "=== Points à Vérifier ===" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Le dialog s'ouvre-t-il?" -ForegroundColor White
Write-Host "   Chercher: 'Opening EmulationSettingsDialog for game:'" -ForegroundColor Gray
Write-Host ""

Write-Host "2. Les core options sont-elles catégorisées?" -ForegroundColor White
Write-Host "   Chercher: 'Categorized X options into Y categories'" -ForegroundColor Gray
Write-Host ""

Write-Host "3. Les catégories sont-elles affichées?" -ForegroundColor White
Write-Host "   Chercher: 'Video: X options', 'Audio: X options', etc." -ForegroundColor Gray
Write-Host ""

Write-Host "4. Les modifications sont-elles appliquées?" -ForegroundColor White
Write-Host "   Chercher: 'Apply clicked - X core options modified'" -ForegroundColor Gray
Write-Host "   Chercher: 'Applied X core option changes and global settings'" -ForegroundColor Gray
Write-Host ""

Write-Host "=== Commandes Rapides ===" -ForegroundColor Yellow
Write-Host ""
Write-Host "Surveiller en temps réel:" -ForegroundColor Green
Write-Host "   adb logcat -s EmulationSettingsDialog:V CoreVariableManager:V RetroArchEmulatorActivity:V" -ForegroundColor Cyan
Write-Host ""

Write-Host "Nettoyer et surveiller:" -ForegroundColor Green
Write-Host "   adb logcat -c && adb logcat -s EmulationSettingsDialog:V CoreVariableManager:V RetroArchEmulatorActivity:V" -ForegroundColor Cyan
Write-Host ""

