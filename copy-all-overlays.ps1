# Script pour copier tous les overlays RetroArch de c:\repos vers le projet
# Usage: .\copy-all-overlays.ps1

$sourceDir = "c:\repos\common-overlays-master\gamepads\flat"
$targetDir = "app\src\main\assets\overlays"

# Créer le répertoire cible si nécessaire
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
}

# Mapping des noms de fichiers source -> noms de répertoires cible
$overlays = @{
    "nes.cfg" = "flat-nes"
    "snes.cfg" = "flat-snes"
    "genesis.cfg" = "flat-genesis"
    "psx.cfg" = "flat-psx"
    "neogeo.cfg" = "flat-neogeo"
    "arcade.cfg" = "flat-arcade"
    "gameboy.cfg" = "flat-gameboy"
    "gba.cfg" = "flat-gba"
    "sms.cfg" = "flat-sms"
    "saturn.cfg" = "flat-saturn"
    "nintendo64.cfg" = "flat-n64"
    "dreamcast.cfg" = "flat-dreamcast"
    "psp.cfg" = "flat-psp"
    "atari2600.cfg" = "flat-atari2600"
    "atari7800.cfg" = "flat-atari7800"
    "atari_lynx.cfg" = "flat-atarilynx"
    "turbografx-16.cfg" = "flat-pce"
    "wonderswan.cfg" = "flat-wonderswan"
    "virtualboy.cfg" = "flat-virtualboy"
    "neogeo_pocket.cfg" = "flat-ngp"
    "pokemini.cfg" = "flat-pokemini"
    "pc-fx.cfg" = "flat-pcfx"
    "gamecube.cfg" = "flat-gamecube"
}

Write-Host "`n=== COPIE DES OVERLAYS RETROARCH ===" -ForegroundColor Cyan
Write-Host "Source: $sourceDir" -ForegroundColor Yellow
Write-Host "Target: $targetDir`n" -ForegroundColor Yellow

$copied = 0
$skipped = 0
$failed = 0

foreach ($entry in $overlays.GetEnumerator()) {
    $sourceCfg = Join-Path $sourceDir $entry.Key
    $targetFolder = Join-Path $targetDir $entry.Value
    $targetCfg = Join-Path $targetFolder "$($entry.Value).cfg"
    
    # Vérifier si l'overlay existe déjà
    if (Test-Path $targetCfg) {
        Write-Host "[SKIP] $($entry.Value) (déjà présent)" -ForegroundColor Gray
        $skipped++
        continue
    }
    
    # Vérifier si le fichier source existe
    if (-not (Test-Path $sourceCfg)) {
        Write-Host "[FAIL] $($entry.Value) (source non trouvée: $($entry.Key))" -ForegroundColor Red
        $failed++
        continue
    }
    
    Write-Host "[COPY] $($entry.Value)..." -ForegroundColor Yellow -NoNewline
    
    try {
        # Créer le répertoire cible
        if (-not (Test-Path $targetFolder)) {
            New-Item -ItemType Directory -Force -Path $targetFolder | Out-Null
        }
        
        # Copier le .cfg
        Copy-Item -Path $sourceCfg -Destination $targetCfg -Force
        
        # Copier le dossier img/ (partagé pour tous les overlays)
        $sourceImgDir = Join-Path $sourceDir "img"
        $targetImgDir = Join-Path $targetFolder "img"
        
        if (Test-Path $sourceImgDir) {
            if (-not (Test-Path $targetImgDir)) {
                Copy-Item -Path $sourceImgDir -Destination $targetImgDir -Recurse -Force
            }
        }
        
        Write-Host " OK" -ForegroundColor Green
        $copied++
    }
    catch {
        Write-Host " ERREUR" -ForegroundColor Red
        Write-Host "  Détails: $($_.Exception.Message)" -ForegroundColor Red
        $failed++
    }
}

Write-Host "`n=== RÉSUMÉ ===" -ForegroundColor Cyan
Write-Host "Copiés: $copied" -ForegroundColor Green
Write-Host "Ignorés (déjà présents): $skipped" -ForegroundColor Gray
Write-Host "Échecs: $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Gray" })

Write-Host "`n=== OVERLAYS ACTUELS ===" -ForegroundColor Cyan
Get-ChildItem $targetDir -Directory | Sort-Object Name | ForEach-Object {
    $cfgFile = Get-ChildItem $_.FullName -Filter "*.cfg" | Select-Object -First 1
    if ($cfgFile) {
        Write-Host "  $($_.Name)" -ForegroundColor White
    }
}

Write-Host "`nTerminé ! Les overlays sont prêts pour la compilation." -ForegroundColor Yellow



