# Test des Extensions N64

**Date:** 2025-01-XX  
**Implémentation:** Configuration des extensions N64 (Controller Pak, Rumble Pak, Transfer Pak)

---

## Prérequis

1. **APK installé:** L'APK avec l'implémentation doit être installé sur le device
2. **Jeu N64:** Avoir un jeu N64 compatible avec les extensions
3. **Configuration préalable:** Configurer les extensions dans les paramètres de console N64

---

## Étapes de Test

### 1. Configuration des Extensions dans l'App

1. Ouvrir RetroPlay
2. Aller dans les paramètres de la console N64
3. Configurer les extensions pour chaque port (1-4):
   - Port 1: Sélectionner "Controller Pak", "Rumble Pak", ou "Transfer Pak"
   - Répéter pour les ports 2, 3, 4 si nécessaire
4. Sauvegarder les paramètres

### 2. Lancement d'un Jeu N64

1. Lancer un jeu N64 qui utilise les extensions (ex: Super Mario 64 avec Controller Pak)
2. Observer les logs pour voir si les extensions sont configurées

### 3. Vérification des Logs

**Commande pour voir les logs:**
```powershell
adb logcat -s RetroArchEmulatorActivity:* | Select-String -Pattern "N64"
```

**Logs attendus:**
```
[N64] Configuring controller extensions...
[N64] Available controllers for each port:
[N64] Port 0: [...]
[N64] Port 1: [...]
...
[N64] Extension configured for port 1: Controller Pak (id=1) via setControllerType()
[N64] Extension configured for port 2: Rumble Pak (id=2) via setControllerType()
```

**Si les extensions ne fonctionnent pas:**
```
[N64] Failed to set extension for port 1 via setControllerType(): [erreur]
```

### 4. Test des Différentes Extensions

**Controller Pak (id=1):**
- Tester avec un jeu qui sauvegarde sur Controller Pak
- Vérifier que les sauvegardes fonctionnent

**Rumble Pak (id=2):**
- Tester avec un jeu qui utilise le Rumble Pak
- Vérifier que la vibration fonctionne (si le device supporte)

**Transfer Pak (id=5):**
- Tester avec un jeu qui utilise le Transfer Pak
- Vérifier que le transfert Game Boy fonctionne

---

## Tests Spécifiques par Core

### Test avec ParaLLEl N64

1. S'assurer que ParaLLEl N64 est le core utilisé
2. Observer les logs pour voir les IDs des contrôleurs disponibles
3. Vérifier que les extensions sont configurées correctement

### Test avec Mupen64Plus Next

1. S'assurer que Mupen64Plus Next est le core utilisé
2. Observer les logs pour voir les IDs des contrôleurs disponibles
3. Vérifier que les extensions sont configurées correctement
4. **Note:** Mupen64Plus peut utiliser des IDs différents

---

## Problèmes Potentiels et Solutions

### Problème 1: Les IDs ne correspondent pas

**Symptôme:** Logs montrent que les extensions sont configurées mais ne fonctionnent pas

**Solution:**
1. Vérifier les IDs retournés par `getControllers()` dans les logs
2. Ajuster le mapping dans `RetroArchEmulatorActivity.kt` lignes 1440-1442
3. Utiliser les IDs corrects pour chaque core

### Problème 2: Les extensions ne sont pas exposées via getControllers()

**Symptôme:** Logs montrent que `getControllers()` ne retourne pas les extensions

**Solution:**
1. Les extensions peuvent être configurées via core options au lieu de `setControllerType()`
2. Chercher les core options N64 spécifiques (ex: "mupen64plus-ControllerPak", "parallel-n64-ControllerPak")
3. Utiliser `CoreVariableManager` pour configurer les extensions via core options

### Problème 3: Erreur lors de setControllerType()

**Symptôme:** Logs montrent "Failed to set extension for port X"

**Solution:**
1. Vérifier que le core est complètement initialisé (délai de 1.5 secondes)
2. Vérifier que le port existe (0-3 pour N64)
3. Vérifier que l'ID est valide pour ce core

---

## Logs de Debug Détaillés

**Commande complète pour voir tous les logs N64:**
```powershell
adb logcat -s RetroArchEmulatorActivity:* LibretroDroid:* | Select-String -Pattern "N64|controller|extension" -Context 2,2
```

**Logs importants à vérifier:**
1. `[N64] Configuring controller extensions...` - Début de la configuration
2. `[N64] Available controllers for each port:` - Types de contrôleurs disponibles
3. `[N64] Extension configured for port X: ...` - Configuration réussie
4. `[N64] Failed to set extension for port X` - Erreur de configuration

---

## Résultats Attendus

### Succès

✅ Les logs montrent que les extensions sont configurées correctement  
✅ Les jeux qui utilisent les extensions fonctionnent correctement  
✅ Les sauvegardes Controller Pak fonctionnent  
✅ Le Rumble Pak fonctionne (si supporté)  

### Échec

❌ Les logs montrent des erreurs lors de la configuration  
❌ Les extensions ne fonctionnent pas dans les jeux  
❌ Les IDs retournés par `getControllers()` ne correspondent pas aux valeurs utilisées  

---

## Prochaines Étapes après Test

1. **Si succès:** Documenter les IDs corrects pour chaque core N64
2. **Si échec:** Ajuster l'implémentation selon les résultats des tests
3. **Documenter:** Créer un document avec les IDs corrects pour chaque core

---

## Notes

- Les IDs peuvent varier selon le core N64 utilisé
- Certains cores peuvent nécessiter des core options au lieu de `setControllerType()`
- Le délai de 1.5 secondes peut nécessiter un ajustement selon le core

