# Script pour capturer les logs Zapper pendant 30 secondes
# Instructions:
# 1. Lancez ce script
# 2. Lancez Duck Hunt sur le device
# 3. Tapez 5-10 fois sur l'écran
# 4. Attendez que le script se termine (30 sec)

Write-Host "Nettoyage des logs..." -ForegroundColor Yellow
adb logcat -c

Write-Host "Capture des logs pendant 30 secondes..." -ForegroundColor Green
Write-Host "LANCEZ DUCK HUNT ET TAPEZ SUR L'ECRAN MAINTENANT!" -ForegroundColor Cyan

$logFile = "zapper_test_logs_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt"

# Capturer pendant 30 secondes
$job = Start-Job -ScriptBlock {
    adb logcat
}

Start-Sleep -Seconds 30

Stop-Job $job
Receive-Job $job | Out-File -FilePath $logFile -Encoding UTF8
Remove-Job $job

Write-Host "`nLogs sauvegardés dans: $logFile" -ForegroundColor Green
Write-Host "Filtrage des lignes importantes..." -ForegroundColor Yellow

# Extraire les lignes pertinentes
$importantLines = Select-String -Path $logFile -Pattern "NATIVE MOTION|NATIVE POINTER|ZAPPER|Zapper configured" | Select-Object -First 100

$outputFile = "zapper_important_logs_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt"
$importantLines | Out-File -FilePath $outputFile -Encoding UTF8

Write-Host "`nLignes importantes extraites dans: $outputFile" -ForegroundColor Green
Write-Host "Nombre de lignes trouvées: $($importantLines.Count)" -ForegroundColor Cyan

# Afficher les premières lignes
Write-Host "`nPREMIÈRES LIGNES:" -ForegroundColor Yellow
$importantLines | Select-Object -First 30 | ForEach-Object { Write-Host $_.Line }

