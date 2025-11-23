# Validation N64 Extensions - Documentation Complète

**Date:** 2025-01-XX  
**Status:** Implémentation complétée, nécessite validation par tests

---

## Implémentation Actuelle

### Code Implémenté

**Fichier:** `RetroArchEmulatorActivity.kt` (lignes 1494-1588)

**Fonctionnalités:**
- Configuration automatique des extensions N64 au chargement du core
- Support 4 ports (0-3)
- Mapping des préférences vers IDs Libretro
- Dialog informatif affichant les extensions configurées
- Logs détaillés pour debugging

**Mapping actuel:**
```kotlin
// Valeurs standard Libretro N64
0 = None (pas d'extension)
1 = Controller Pak (mémoire de sauvegarde)
2 = Rumble Pak (vibration)
5 = Transfer Pak (transfert Game Boy)
```

---

## IDs par Core N64

### Mupen64Plus Next

**IDs attendus (à valider):**
- Controller Pak: `1`
- Rumble Pak: `2`
- Transfer Pak: `5`

**Core Options alternatives (si setControllerType() ne fonctionne pas):**
- `mupen64plus-ControllerPak` = "enabled"/"disabled"
- `mupen64plus-RumblePak` = "enabled"/"disabled"

### ParaLLEl N64

**IDs attendus (à valider):**
- Controller Pak: `1`
- Rumble Pak: `2`
- Transfer Pak: `5`

**Core Options alternatives (si setControllerType() ne fonctionne pas):**
- `parallel-n64-ControllerPak` = "enabled"/"disabled"
- `parallel-n64-RumblePak` = "enabled"/"disabled"

---

## Procédure de Validation

### Étape 1: Vérifier les IDs Disponibles

1. Lancer un jeu N64
2. Observer les logs pour voir les IDs retournés par `getControllers()`

**Commande:**
```powershell
adb logcat -s RetroArchEmulatorActivity:* | Select-String -Pattern "N64.*Available controllers"
```

**Logs attendus:**
```
[N64] Available controllers for each port:
[N64] Port 0: [id=0 desc='Nintendo 64 Controller', id=1 desc='Controller Pak', id=2 desc='Rumble Pak', id=5 desc='Transfer Pak']
[N64] Port 1: [...]
```

### Étape 2: Configurer les Extensions

1. Aller dans les paramètres de la console N64
2. Configurer les extensions pour chaque port
3. Sauvegarder

### Étape 3: Vérifier la Configuration

**Commande:**
```powershell
adb logcat -s RetroArchEmulatorActivity:* | Select-String -Pattern "N64.*Extension configured"
```

**Logs attendus:**
```
[N64] Extension configured for port 0: Controller Pak (id=1) via setControllerType()
[N64] Extension configured for port 1: Rumble Pak (id=2) via setControllerType()
```

### Étape 4: Tester dans un Jeu

**Controller Pak:**
- Jeu recommandé: Super Mario 64 (sauvegarde sur Controller Pak)
- Vérifier que les sauvegardes fonctionnent

**Rumble Pak:**
- Jeu recommandé: Star Fox 64 (vibration lors des impacts)
- Vérifier que la vibration fonctionne (si device supporte)

**Transfer Pak:**
- Jeu recommandé: Pokémon Stadium (transfert Game Boy)
- Vérifier que le transfert fonctionne

---

## Problèmes Potentiels et Solutions

### Problème 1: IDs Incorrects

**Symptôme:** Les extensions sont configurées mais ne fonctionnent pas dans les jeux

**Solution:**
1. Vérifier les IDs retournés par `getControllers()` dans les logs
2. Ajuster le mapping dans `RetroArchEmulatorActivity.kt` lignes 1512
3. Documenter les IDs corrects pour chaque core

### Problème 2: setControllerType() Ne Fonctionne Pas

**Symptôme:** Logs montrent "Failed to set extension" ou les extensions ne fonctionnent pas

**Solution:**
1. Utiliser les core options au lieu de `setControllerType()`
2. Chercher les core options spécifiques (ex: "mupen64plus-ControllerPak")
3. Utiliser `CoreVariableManager.updateVariable()` pour configurer

### Problème 3: Délai Insuffisant

**Symptôme:** Les extensions ne sont pas configurées (core pas encore initialisé)

**Solution:**
1. Augmenter le délai de 1.5 secondes à 2-3 secondes
2. Vérifier que le core est complètement chargé avant configuration

---

## Améliorations Futures

### 1. Détection Automatique des IDs

Au lieu d'utiliser des IDs hardcodés, détecter automatiquement les IDs disponibles via `getControllers()` et mapper les descriptions vers les extensions.

### 2. Support Core Options

Si `setControllerType()` ne fonctionne pas pour un core, fallback vers les core options.

### 3. Documentation par Core

Créer un fichier JSON/YAML qui documente les IDs corrects pour chaque core N64.

---

## Checklist de Validation

- [ ] Tester avec Mupen64Plus Next
  - [ ] Controller Pak fonctionne
  - [ ] Rumble Pak fonctionne
  - [ ] Transfer Pak fonctionne
  - [ ] IDs corrects documentés

- [ ] Tester avec ParaLLEl N64
  - [ ] Controller Pak fonctionne
  - [ ] Rumble Pak fonctionne
  - [ ] Transfer Pak fonctionne
  - [ ] IDs corrects documentés

- [ ] Documenter les IDs pour chaque core
- [ ] Créer un guide utilisateur pour configurer les extensions
- [ ] Ajouter des tests automatisés si possible

---

## Notes

- Les IDs peuvent varier selon la version du core
- Certains cores peuvent nécessiter des core options au lieu de `setControllerType()`
- Le délai de 1.5 secondes peut nécessiter un ajustement selon le core
- Le dialog informatif aide à confirmer que les extensions sont configurées

---

**Status:** Implémentation complète, nécessite validation par tests réels avec différents cores N64.

