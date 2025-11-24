# 🧪 Guide de Test - Scan Automatique au Démarrage

**Date:** 20 novembre 2025  
**Status:** ✅ Prêt pour tests

---

## 📋 PRÉREQUIS

1. **APK compilé** : `RetroPlay-Android/app/build/outputs/apk/debug/app-debug.apk`
2. **Device/Emulator** : Android avec permissions storage
3. **ROMs** : Au moins un répertoire console avec ROMs dans `/storage/emulated/0/GameLibrary-Data/`

---

## 🧪 TEST 1: Premier Démarrage

### Objectif
Vérifier que le scan complet fonctionne au premier lancement de l'app.

### Étapes

1. **Préparer l'environnement**
   ```bash
   # Supprimer les préférences pour simuler premier démarrage
   adb shell run-as com.retroplay rm /data/data/com.retroplay/shared_prefs/gamelist_prefs.xml
   
   # OU réinitialiser l'app complètement
   adb uninstall com.retroplay
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Lancer l'app**
   - Ouvrir RetroPlay
   - Accorder les permissions storage si demandées

3. **Observer le comportement**
   - ✅ **ProgressDialog** doit apparaître : "Initial Scan - Scanning ROM directories..."
   - ✅ Le scan doit se faire en arrière-plan
   - ✅ **Toast** doit apparaître : "Scanned X consoles (Y games)"
   - ✅ `GameListActivity` doit se lancer normalement

4. **Vérifier les résultats**
   ```bash
   # Vérifier que gamelist.json ont été créés
   adb shell ls -la /storage/emulated/0/GameLibrary-Data/*/gamelist.json
   
   # Vérifier le contenu d'un gamelist.json
   adb shell cat /storage/emulated/0/GameLibrary-Data/nes/gamelist.json | head -20
   ```

5. **Vérifier les logs**
   ```bash
   adb logcat -s GamelistScanner MainActivity | grep -E "First launch|Scanning|Scanned|games"
   ```

### Résultats Attendus

- ✅ ProgressDialog affiché pendant le scan
- ✅ Toast avec nombre de consoles et jeux scannés
- ✅ `gamelist.json` créés dans chaque répertoire console avec ROMs
- ✅ Format JSON correct (`description`, `releaseDate` en camelCase)
- ✅ Logs montrent le processus de scan

### Logs Attendus
```
I/GamelistScanner: First launch detected - Starting full console scan
I/GamelistScanner: Scanning console directory: nes
I/GamelistScanner: Generated gamelist for nes: 25 games
I/GamelistScanner: Scan completed: 5 consoles, 150 games total
I/MainActivity: Initial scan completed: 5 consoles, 150 games
```

---

## 🧪 TEST 2: Démarrages Suivants (Auto-scan Activé)

### Objectif
Vérifier que le scan incrémental fonctionne aux démarrages suivants.

### Étapes

1. **Préparer l'environnement**
   - L'app a déjà été lancée une fois (premier démarrage terminé)
   - Auto-scan activé par défaut (toggle ON)

2. **Ajouter une nouvelle ROM**
   ```bash
   # Copier une nouvelle ROM dans un répertoire console
   adb push test_rom.nes /storage/emulated/0/GameLibrary-Data/nes/
   ```

3. **Lancer l'app**
   - Ouvrir RetroPlay
   - Attendre quelques secondes

4. **Observer le comportement**
   - ✅ **Pas de ProgressDialog** (scan en arrière-plan)
   - ✅ **Toast** doit apparaître : "Updated X consoles (Y new games)"
   - ✅ `GameListActivity` se lance normalement

5. **Vérifier les résultats**
   ```bash
   # Vérifier que le gamelist.json a été mis à jour
   adb shell cat /storage/emulated/0/GameLibrary-Data/nes/gamelist.json | grep "test_rom"
   ```

### Résultats Attendus

- ✅ Pas de dialog bloquant
- ✅ Toast si nouvelles ROMs trouvées
- ✅ `gamelist.json` mis à jour avec nouvelles ROMs
- ✅ Logs montrent le rescan

### Logs Attendus
```
I/GamelistScanner: Auto-scan enabled - Scanning for new ROMs
I/GamelistScanner: Rescanning console directory: nes (ROMs modified)
I/GamelistScanner: Updated gamelist for nes: 26 games
I/GamelistScanner: Rescan completed: 1 consoles updated, 1 games
I/MainActivity: Rescan completed: 1 consoles updated, 1 games
```

---

## 🧪 TEST 3: Démarrages Suivants (Auto-scan Désactivé)

### Objectif
Vérifier que le scan ne se fait pas si auto-scan est désactivé.

### Étapes

1. **Désactiver auto-scan**
   ```bash
   # Via code ou SharedPreferences
   adb shell run-as com.retroplay cat /data/data/com.retroplay/shared_prefs/gamelist_prefs.xml
   # Modifier auto_scan_enabled à false
   ```

   **OU** via l'app (si toggle implémenté):
   - Aller dans Paramètres
   - Désactiver "Auto-scan on startup"

2. **Lancer l'app**
   - Ouvrir RetroPlay
   - Attendre quelques secondes

3. **Observer le comportement**
   - ✅ **Pas de ProgressDialog**
   - ✅ **Pas de Toast** (pas de scan)
   - ✅ `GameListActivity` se lance normalement

4. **Vérifier les logs**
   ```bash
   adb logcat -s GamelistScanner | grep "Auto-scan disabled"
   ```

### Résultats Attendus

- ✅ Pas de scan effectué
- ✅ Log indique "Auto-scan disabled"
- ✅ App fonctionne normalement

### Logs Attendus
```
I/GamelistScanner: Auto-scan disabled - Skipping scan
```

---

## 🧪 TEST 4: PSX Serial Numbers

### Objectif
Vérifier que les numéros de série PSX sont extraits correctement.

### Étapes

1. **Préparer une ROM PSX**
   - Avoir une ROM PSX avec numéro de série (ex: SLUS-01234)

2. **Scanner le répertoire PSX**
   - Lancer le scan (premier démarrage ou manuel)

3. **Vérifier le gamelist.json**
   ```bash
   adb shell cat /storage/emulated/0/GameLibrary-Data/psx/gamelist.json | grep -A 5 "serial"
   ```

### Résultats Attendus

- ✅ Champ `serial` présent dans le JSON
- ✅ Format correct : `"SLUS-01234"` ou `"SLES-00567"`
- ✅ Champ `crc32` absent ou null pour PSX

### Exemple JSON
```json
{
  "name": "Final Fantasy VII",
  "path": "./Final Fantasy VII.bin",
  "serial": "SLUS-01234",
  "crc32": null,
  ...
}
```

---

## 🧪 TEST 5: Format JSON (Migration)

### Objectif
Vérifier que le nouveau format JSON est utilisé et que la migration fonctionne.

### Étapes

1. **Vérifier format généré**
   ```bash
   adb shell cat /storage/emulated/0/GameLibrary-Data/nes/gamelist.json | head -30
   ```

2. **Vérifier format lu**
   - Lancer l'app
   - Ouvrir une console
   - Vérifier que les jeux s'affichent correctement

### Résultats Attendus

- ✅ Format nouveau : `"description"`, `"releaseDate"` (camelCase)
- ✅ Pas de format legacy : `"desc"`, `"releasedate"` (sauf si migration)
- ✅ Jeux affichés correctement dans `GameListActivity`

### Format Attendu
```json
{
  "games": [
    {
      "id": "1",
      "name": "Super Mario Bros",
      "description": "...",
      "releaseDate": "19850913",
      ...
    }
  ]
}
```

---

## 🐛 DÉBOGAGE

### Problèmes Courants

#### 1. Scan ne démarre pas
**Symptômes:**
- Pas de ProgressDialog au premier démarrage
- Pas de logs GamelistScanner

**Solutions:**
- Vérifier permissions storage
- Vérifier que `/storage/emulated/0/GameLibrary-Data` existe
- Vérifier logs MainActivity pour erreurs

#### 2. Toast "0 consoles"
**Symptômes:**
- Toast affiche "Scanned 0 consoles (0 games)"

**Solutions:**
- Vérifier que répertoires consoles existent
- Vérifier que ROMs sont présents
- Vérifier extensions ROMs dans `GamelistManager.getDefaultExtensions()`
- Vérifier logs pour erreurs de scan

#### 3. Format JSON incorrect
**Symptômes:**
- `gamelist.json` contient `"desc"` au lieu de `"description"`

**Solutions:**
- Vérifier `GamelistManager.gameToJson()` utilise nouveau format
- Vérifier `GamelistManager.generateGamelistJsonString()` utilise nouveau format

#### 4. PSX serial non extrait
**Symptômes:**
- `serial` est null dans `gamelist.json` pour PSX

**Solutions:**
- Vérifier que ROM PSX contient un numéro de série valide
- Vérifier logs `PSXSerialExtractor`
- Vérifier que `PSXSerialExtractor.extractSerial()` est appelé

---

## 📊 CHECKLIST DE VALIDATION

### Premier Démarrage
- [ ] ProgressDialog affiché
- [ ] Toast avec résultats
- [ ] `gamelist.json` créés
- [ ] Format JSON correct
- [ ] Logs montrent scan complet

### Démarrages Suivants
- [ ] Pas de dialog (scan en arrière-plan)
- [ ] Toast si nouvelles ROMs
- [ ] `gamelist.json` mis à jour
- [ ] Logs montrent rescan

### Auto-scan Désactivé
- [ ] Pas de scan
- [ ] Log "Auto-scan disabled"
- [ ] App fonctionne normalement

### PSX Serial
- [ ] `serial` extrait correctement
- [ ] Format correct (SLUS-01234)
- [ ] `crc32` null pour PSX

### Format JSON
- [ ] Nouveau format utilisé (`description`, `releaseDate`)
- [ ] Migration fonctionne (lecture ancien format)
- [ ] Jeux affichés correctement

---

## 📝 LOGS À SURVEILLER

```bash
# Tous les logs du scan
adb logcat -s GamelistScanner MainActivity PSXSerialExtractor GamelistManager

# Logs spécifiques
adb logcat -s GamelistScanner | grep -E "First launch|Auto-scan|Scanning|Scanned|games"

# Erreurs uniquement
adb logcat -s GamelistScanner MainActivity | grep -E "Error|Exception|Failed"
```

---

## ✅ CRITÈRES DE SUCCÈS

Le scan automatique est **fonctionnel** si:

1. ✅ Premier démarrage : Scan complet avec dialog et toast
2. ✅ Démarrages suivants : Scan incrémental en arrière-plan
3. ✅ Auto-scan désactivé : Pas de scan
4. ✅ Format JSON : Nouveau format utilisé
5. ✅ PSX Serial : Extraction fonctionnelle
6. ✅ Pas d'erreurs dans les logs
7. ✅ `gamelist.json` créés/mis à jour correctement

---

## 🚀 PROCHAINES ÉTAPES APRÈS TESTS

1. **Corriger les bugs** identifiés
2. **Ajouter toggle** dans paramètres
3. **Optimiser performance** si nécessaire
4. **Ajouter scan manuel** dans ConsoleManagerActivity




