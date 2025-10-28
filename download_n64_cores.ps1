# Script pour télécharger les cores N64 manquants
# Usage: .\download_n64_cores.ps1

$baseUrl = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"
$targetDir = "app\src\main\jniLibs\arm64-v8a"

# Créer le répertoire si nécessaire
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
}

# Liste des cores N64 à télécharger
# Format: nom court utilisé sur buildbot → nom complet dans l'APK
$coresMap = @{
    "mupen64plus_next_gles3" = "mupen64plus_next_libretro_android.so"
    "mupen64plus_next_gles2" = "mupen64plus_next_gles2_libretro_android.so"
}

Write-Host "`n=== TÉLÉCHARGEMENT DES CORES N64 ===" -ForegroundColor Cyan
Write-Host "Source: $baseUrl`n" -ForegroundColor Yellow

$downloaded = 0
$failed = 0
$skipped = 0

foreach ($coreShortName in $coresMap.Keys) {
    $coreFullName = $coresMap[$coreShortName]
    $targetFile = Join-Path $targetDir $coreFullName
    
    # Vérifier si le core existe déjà
    if (Test-Path $targetFile) {
        Write-Host "[SKIP] $coreFullName (déjà présent)" -ForegroundColor Gray
        $skipped++
        continue
    }
    
    Write-Host "[DOWNLOAD] $coreShortName..." -ForegroundColor Yellow -NoNewline
    
    try {
        # Télécharger le fichier .so.zip (format buildbot)
        $zipUrl = "$baseUrl/${coreShortName}_libretro_android.so.zip"
        $zipFile = Join-Path $env:TEMP "${coreShortName}.zip"
        
        Invoke-WebRequest -Uri $zipUrl -OutFile $zipFile -ErrorAction Stop
        
        # Extraire le .so et le renommer si nécessaire
        $extractPath = Join-Path $env:TEMP $coreShortName
        Expand-Archive -Path $zipFile -DestinationPath $extractPath -Force
        
        # Trouver le fichier .so extrait
        $extractedFile = Get-ChildItem -Path $extractPath -Filter "*.so" | Select-Object -First 1
        
        if ($extractedFile) {
            # Copier avec le bon nom
            Copy-Item -Path $extractedFile.FullName -Destination $targetFile -Force
            
            # Nettoyer
            Remove-Item $zipFile -Force
            Remove-Item $extractPath -Recurse -Force
            
            $fileSize = (Get-Item $targetFile).Length / 1MB
            Write-Host " OK ($('{0:N2}' -f $fileSize) MB)" -ForegroundColor Green
            $downloaded++
        } else {
            Write-Host " ERREUR (fichier .so non trouvé après extraction)" -ForegroundColor Red
            Remove-Item $zipFile -Force -ErrorAction SilentlyContinue
            Remove-Item $extractPath -Recurse -Force -ErrorAction SilentlyContinue
            $failed++
        }
    }
    catch {
        Write-Host " ERREUR" -ForegroundColor Red
        Write-Host "  Détails: $($_.Exception.Message)" -ForegroundColor Red
        $failed++
    }
}

Write-Host "`n=== RÉSUMÉ ===" -ForegroundColor Cyan
Write-Host "Téléchargés: $downloaded" -ForegroundColor Green
Write-Host "Ignorés (déjà présents): $skipped" -ForegroundColor Gray
Write-Host "Échecs: $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Gray" })

Write-Host "`n=== CORES N64 ACTUELS ===" -ForegroundColor Cyan
Get-ChildItem $targetDir -Filter "*n64*.so" | Sort-Object Name | ForEach-Object {
    $size = $_.Length / 1MB
    Write-Host "  $($_.Name) ($('{0:N2}' -f $size) MB)" -ForegroundColor White
}
Get-ChildItem $targetDir -Filter "*mupen*.so" | Sort-Object Name | ForEach-Object {
    $size = $_.Length / 1MB
    Write-Host "  $($_.Name) ($('{0:N2}' -f $size) MB)" -ForegroundColor White
}
Get-ChildItem $targetDir -Filter "*parallel*.so" | Sort-Object Name | ForEach-Object {
    $size = $_.Length / 1MB
    Write-Host "  $($_.Name) ($('{0:N2}' -f $size) MB)" -ForegroundColor White
}

Write-Host "`nTerminé ! Vous pouvez maintenant compiler RetroPlay avec les nouveaux cores N64." -ForegroundColor Yellow
Write-Host "`nAppuyez sur une touche pour continuer..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
