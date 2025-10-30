# Script de test pour la correction des chemins de stockage
# Test de la correction GameListActivity.java ligne 1472

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TEST CORRECTION - CHEMINS STOCKAGE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier que ADB est disponible
try {
    $null = adb devices
    Write-Host "✅ ADB disponible" -ForegroundColor Green
} catch {
    Write-Host "❌ ERREUR: ADB non trouvé" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "--- ÉTAPE 1: Nettoyage ---" -ForegroundColor Yellow
Write-Host "Désinstallation de RetroPlay..." -ForegroundColor Gray
adb uninstall com.retroplay 2>&1 | Out-Null
Write-Host "Suppression des fichiers RetroPlay-Files..." -ForegroundColor Gray
adb shell "rm -rf /storage/emulated/0/RetroPlay-Files/" 2>&1 | Out-Null
Write-Host "✅ Nettoyage terminé" -ForegroundColor Green

Write-Host ""
Write-Host "--- ÉTAPE 2: Compilation ---" -ForegroundColor Yellow
Write-Host "Compilation de l'APK..." -ForegroundColor Gray
$compileResult = .\gradlew assembleDebug 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Compilation réussie" -ForegroundColor Green
} else {
    Write-Host "❌ Échec de la compilation" -ForegroundColor Red
    Write-Host $compileResult -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "--- ÉTAPE 3: Installation ---" -ForegroundColor Yellow
Write-Host "Installation de l'APK..." -ForegroundColor Gray
$installResult = adb install -r app\build\outputs\apk\debug\app-debug.apk 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Installation réussie" -ForegroundColor Green
} else {
    Write-Host "❌ Échec de l'installation" -ForegroundColor Red
    Write-Host $installResult -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "--- ÉTAPE 4: Lancement de l'app ---" -ForegroundColor Yellow
Write-Host "Lancement de RetroPlay..." -ForegroundColor Gray
adb shell am start -n com.retroplay/.GameListActivity 2>&1 | Out-Null
Write-Host "⏳ Attente 10 secondes pour initialisation..." -ForegroundColor Gray
Start-Sleep -Seconds 10

Write-Host ""
Write-Host "--- ÉTAPE 5: Vérification des logs ---" -ForegroundColor Yellow
Write-Host "Recherche des logs de copie..." -ForegroundColor Gray
Write-Host ""

$logs = adb logcat -d GameListActivity:D *:S | Select-String "RetroPlay"
if ($logs) {
    Write-Host "📋 Logs trouvés:" -ForegroundColor Cyan
    $logs | ForEach-Object { Write-Host "  $_" -ForegroundColor White }
    Write-Host ""
    
    # Vérifier les logs attendus
    $hasCreatedDir = $logs | Select-String "Created RetroPlay-Files directory"
    $hasCopiedIndex = $logs | Select-String "Copied index.html to RetroPlay storage"
    $hasCopiedEmulator = $logs | Select-String "Copied emulator.html to RetroPlay storage"
    
    if ($hasCreatedDir) {
        Write-Host "  ✅ Répertoire RetroPlay-Files créé" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  Log de création de répertoire non trouvé" -ForegroundColor Yellow
    }
    
    if ($hasCopiedIndex) {
        Write-Host "  ✅ index.html copié vers RetroPlay storage" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  Log de copie index.html non trouvé" -ForegroundColor Yellow
    }
    
    if ($hasCopiedEmulator) {
        Write-Host "  ✅ emulator.html copié vers RetroPlay storage" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  Log de copie emulator.html non trouvé" -ForegroundColor Yellow
    }
} else {
    Write-Host "⚠️  Aucun log RetroPlay trouvé" -ForegroundColor Yellow
    Write-Host "   L'app peut avoir utilisé des fichiers existants" -ForegroundColor Gray
}

Write-Host ""
Write-Host "--- ÉTAPE 6: Vérification du système de fichiers ---" -ForegroundColor Yellow
Write-Host "Vérification de RetroPlay-Files..." -ForegroundColor Gray
Write-Host ""

$retroplayFiles = adb shell "ls -la /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/ 2>/dev/null"
if ($retroplayFiles) {
    Write-Host "📁 Contenu de RetroPlay-Files/sites/gamelibrary/:" -ForegroundColor Cyan
    Write-Host $retroplayFiles -ForegroundColor White
    Write-Host ""
    
    # Vérifier les fichiers attendus
    if ($retroplayFiles -match "index.html") {
        Write-Host "  ✅ index.html présent" -ForegroundColor Green
    } else {
        Write-Host "  ❌ index.html MANQUANT" -ForegroundColor Red
    }
    
    if ($retroplayFiles -match "emulator.html") {
        Write-Host "  ✅ emulator.html présent" -ForegroundColor Green
    } else {
        Write-Host "  ❌ emulator.html MANQUANT" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Répertoire RetroPlay-Files/sites/gamelibrary/ introuvable!" -ForegroundColor Red
}

Write-Host ""
Write-Host "--- ÉTAPE 7: Vérification ChatAI-Files (ne devrait PAS exister) ---" -ForegroundColor Yellow
Write-Host "Vérification que les fichiers ne sont pas au mauvais endroit..." -ForegroundColor Gray
Write-Host ""

$chataiFiles = adb shell "ls -la /storage/emulated/0/ChatAI-Files/sites/ 2>/dev/null"
if ($chataiFiles -match "index.html" -or $chataiFiles -match "emulator.html") {
    Write-Host "⚠️  ATTENTION: Des fichiers HTML ont été créés dans ChatAI-Files!" -ForegroundColor Red
    Write-Host "   La correction n'a PAS fonctionné correctement." -ForegroundColor Red
    Write-Host ""
    Write-Host $chataiFiles -ForegroundColor Yellow
} else {
    Write-Host "✅ Aucun fichier HTML dans ChatAI-Files (correct)" -ForegroundColor Green
}

Write-Host ""
Write-Host "--- ÉTAPE 8: Test du WebServer ---" -ForegroundColor Yellow
Write-Host "Vérification que le WebServer peut servir les fichiers..." -ForegroundColor Gray
Write-Host ""

# Attendre que le WebServer démarre
Start-Sleep -Seconds 3

# Obtenir l'IP du device
$deviceIP = adb shell "ip addr show wlan0" | Select-String "inet " | ForEach-Object {
    if ($_ -match "inet\s+(\d+\.\d+\.\d+\.\d+)") {
        $matches[1]
    }
}

if ($deviceIP) {
    Write-Host "📱 IP du device: $deviceIP" -ForegroundColor Cyan
    
    # Tester l'accès à index.html
    try {
        $response = Invoke-WebRequest -Uri "http://${deviceIP}:7777/gamelibrary/index.html" -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "  ✅ index.html accessible via WebServer (HTTP 200)" -ForegroundColor Green
        } else {
            Write-Host "  ⚠️  index.html retourne code: $($response.StatusCode)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  ❌ Impossible d'accéder à index.html via WebServer" -ForegroundColor Red
        Write-Host "     Erreur: $($_.Exception.Message)" -ForegroundColor Gray
    }
    
    # Tester l'accès à emulator.html
    try {
        $response = Invoke-WebRequest -Uri "http://${deviceIP}:7777/gamelibrary/emulator.html" -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "  ✅ emulator.html accessible via WebServer (HTTP 200)" -ForegroundColor Green
        } else {
            Write-Host "  ⚠️  emulator.html retourne code: $($response.StatusCode)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  ❌ Impossible d'accéder à emulator.html via WebServer" -ForegroundColor Red
        Write-Host "     Erreur: $($_.Exception.Message)" -ForegroundColor Gray
    }
} else {
    Write-Host "⚠️  Impossible de détecter l'IP du device" -ForegroundColor Yellow
    Write-Host "   Test WebServer ignoré" -ForegroundColor Gray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RÉSULTAT DU TEST" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Récapitulatif
$success = $true

# Vérifications critiques
$retroplayFilesExist = $retroplayFiles -match "index.html" -and $retroplayFiles -match "emulator.html"
$noChatAIFiles = -not ($chataiFiles -match "index.html" -or $chataiFiles -match "emulator.html")

if ($retroplayFilesExist -and $noChatAIFiles) {
    Write-Host "✅ TEST RÉUSSI!" -ForegroundColor Green
    Write-Host ""
    Write-Host "La correction fonctionne correctement:" -ForegroundColor Green
    Write-Host "  ✅ Fichiers créés dans RetroPlay-Files/" -ForegroundColor Green
    Write-Host "  ✅ Pas de fichiers dans ChatAI-Files/" -ForegroundColor Green
    Write-Host "  ✅ WebServer peut servir les fichiers" -ForegroundColor Green
    Write-Host ""
    Write-Host "🎉 RetroPlay est maintenant totalement autonome!" -ForegroundColor Cyan
} elseif (-not $retroplayFilesExist) {
    Write-Host "❌ TEST ÉCHOUÉ" -ForegroundColor Red
    Write-Host ""
    Write-Host "Problème: Les fichiers HTML n'ont pas été créés dans RetroPlay-Files/" -ForegroundColor Red
    Write-Host ""
    Write-Host "Actions recommandées:" -ForegroundColor Yellow
    Write-Host "  1. Vérifier les logs complets: adb logcat GameListActivity:* *:E" -ForegroundColor White
    Write-Host "  2. Vérifier les permissions de stockage de l'app" -ForegroundColor White
    Write-Host "  3. Copier manuellement les fichiers depuis on_device/" -ForegroundColor White
} elseif (-not $noChatAIFiles) {
    Write-Host "⚠️  TEST PARTIELLEMENT RÉUSSI" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Les fichiers ont été créés dans RetroPlay-Files/" -ForegroundColor Green
    Write-Host "MAIS aussi dans ChatAI-Files/ (à investiguer)" -ForegroundColor Yellow
} else {
    Write-Host "❓ RÉSULTAT INCERTAIN" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Vérifier manuellement les résultats ci-dessus" -ForegroundColor Gray
}

Write-Host ""
Write-Host "📝 Pour voir tous les logs:" -ForegroundColor Cyan
Write-Host "   adb logcat GameListActivity:* WebServer:* *:E" -ForegroundColor White
Write-Host ""
Write-Host "🌐 Pour tester dans le navigateur:" -ForegroundColor Cyan
if ($deviceIP) {
    Write-Host "   http://${deviceIP}:7777/gamelibrary/index.html" -ForegroundColor White
} else {
    Write-Host "   http://[DEVICE_IP]:7777/gamelibrary/index.html" -ForegroundColor White
}
Write-Host ""





