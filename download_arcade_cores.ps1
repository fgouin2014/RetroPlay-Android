# Script pour télécharger les cores arcade manquants
# Usage: .\download_arcade_cores.ps1

$baseUrl = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"
$targetDir = "app\src\main\jniLibs\arm64-v8a"

# Créer le répertoire si nécessaire
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
}

# Liste des cores arcade à télécharger
$cores = @(
    "mame2003_libretro_android.so",           # MAME 2003
    "fbalpha2012_cps1_libretro_android.so",   # FBalpha CPS1
    "fbalpha2012_cps2_libretro_android.so",   # FBalpha CPS2
    "flycast_libretro_android.so"             # Flycast (Dreamcast Arcade)
)

Write-Host "`n=== TÉLÉCHARGEMENT DES CORES ARCADE ===" -ForegroundColor Cyan
Write-Host "Source: $baseUrl`n" -ForegroundColor Yellow

$downloaded = 0
$failed = 0
$skipped = 0

foreach ($core in $cores) {
    $targetFile = Join-Path $targetDir $core
    
    # Vérifier si le core existe déjà
    if (Test-Path $targetFile) {
        Write-Host "[SKIP] $core (déjà présent)" -ForegroundColor Gray
        $skipped++
        continue
    }
    
    Write-Host "[DOWNLOAD] $core..." -ForegroundColor Yellow -NoNewline
    
    try {
        # Télécharger le fichier .zip
        $zipUrl = "$baseUrl/$core.zip"
        $zipFile = Join-Path $env:TEMP "$core.zip"
        
        Invoke-WebRequest -Uri $zipUrl -OutFile $zipFile -ErrorAction Stop
        
        # Extraire le .so
        Expand-Archive -Path $zipFile -DestinationPath $targetDir -Force
        
        # Nettoyer le .zip
        Remove-Item $zipFile -Force
        
        # Vérifier que le fichier a bien été extrait
        if (Test-Path $targetFile) {
            $fileSize = (Get-Item $targetFile).Length / 1MB
            Write-Host " OK ($('{0:N2}' -f $fileSize) MB)" -ForegroundColor Green
            $downloaded++
        } else {
            Write-Host " ERREUR (fichier non trouvé après extraction)" -ForegroundColor Red
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

Write-Host "`n=== CORES ARCADE ACTUELS ===" -ForegroundColor Cyan
Get-ChildItem $targetDir -Filter "*mame*.so" | Sort-Object Name | ForEach-Object {
    $size = $_.Length / 1MB
    Write-Host "  $($_.Name) ($('{0:N2}' -f $size) MB)" -ForegroundColor White
}
Get-ChildItem $targetDir -Filter "*fbalpha*.so" | Sort-Object Name | ForEach-Object {
    $size = $_.Length / 1MB
    Write-Host "  $($_.Name) ($('{0:N2}' -f $size) MB)" -ForegroundColor White
}
Get-ChildItem $targetDir -Filter "*fbneo*.so" | Sort-Object Name | ForEach-Object {
    $size = $_.Length / 1MB
    Write-Host "  $($_.Name) ($('{0:N2}' -f $size) MB)" -ForegroundColor White
}
Get-ChildItem $targetDir -Filter "*flycast*.so" | Sort-Object Name | ForEach-Object {
    $size = $_.Length / 1MB
    Write-Host "  $($_.Name) ($('{0:N2}' -f $size) MB)" -ForegroundColor White
}

Write-Host "`nTerminé ! Vous pouvez maintenant compiler RetroPlay avec les nouveaux cores arcade." -ForegroundColor Yellow
Write-Host "`nAppuyez sur une touche pour continuer..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
