# Script pour compresser les cheats en un seul fichier ZIP
# À exécuter AVANT de compiler l'APK

$sourceDir = "app\src\main\assets\GameLibrary-Data\cheats"
$zipFile = "app\src\main\assets\cheats.zip"

Write-Host "=== Packaging Cheats Database ===" -ForegroundColor Cyan
Write-Host ""

# Vérifier que le répertoire source existe
if (-not (Test-Path $sourceDir)) {
    Write-Host "ERROR: Source directory not found: $sourceDir" -ForegroundColor Red
    exit 1
}

Write-Host "Source: $sourceDir" -ForegroundColor Yellow
Write-Host "Output: $zipFile" -ForegroundColor Yellow
Write-Host ""

# Supprimer l'ancien ZIP si existe
if (Test-Path $zipFile) {
    Write-Host "Removing old ZIP file..." -ForegroundColor Gray
    Remove-Item $zipFile -Force
}

Write-Host "Compressing cheats database..." -ForegroundColor Green

# Créer le ZIP
Compress-Archive -Path "$sourceDir\*" -DestinationPath $zipFile -CompressionLevel Optimal

# Afficher la taille
$zipSize = (Get-Item $zipFile).Length / 1MB
$sourceSize = (Get-ChildItem $sourceDir -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB

Write-Host ""
Write-Host "SUCCESS!" -ForegroundColor Green
Write-Host "  Source: $([math]::Round($sourceSize, 2)) MB" -ForegroundColor Gray
Write-Host "  ZIP:    $([math]::Round($zipSize, 2)) MB" -ForegroundColor Gray
Write-Host "  Ratio:  $([math]::Round(($zipSize / $sourceSize) * 100, 1))%" -ForegroundColor Gray
Write-Host ""
Write-Host "Ready to compile APK!" -ForegroundColor Cyan

