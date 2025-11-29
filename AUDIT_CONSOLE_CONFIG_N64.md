# AUDIT: activity_console_config - Variante N64

## Date: 2025-01-XX
## Focus: Configuration N64 dans ConsoleConfigActivity

---

## RÉSUMÉ EXÉCUTIF

**Problèmes critiques identifiés:**
1. ❌ **Incohérence de SharedPreferences**: `ConsoleConfigActivity` utilise `console_config`, les activités Compose utilisent `PreferenceManager.getDefaultSharedPreferences()`
2. ⚠️ **Mapping des valeurs spinner incorrect**: Les positions spinner ne correspondent pas aux IDs Libretro
3. ⚠️ **Application des extensions N64 non fonctionnelle**: Les valeurs sont sauvegardées mais pas appliquées correctement
4. ⚠️ **Options core N64 non appliquées**: Resolution, Anti-Aliasing, Bilinear ne sont pas appliquées au core

---

## 1. CONFIGURATION DES EXTENSIONS N64 (Controller Pak, Rumble Pak, Transfer Pak)

### 1.1 UI dans activity_console_config.xml

**Localisation:** Lignes 391-558

**Structure:**
- 4 spinners (Port 1-4)
- Options disponibles:
  - Position 0: "🎯 Controller Pak (Memory)"
  - Position 1: "🔊 Rumble Pak (Vibration)"
  - Position 2: "🎮 Transfer Pak (Game Boy)"

**Code XML:**
```xml
<Spinner android:id="@+id/n64PakSpinner1" ... />
<Spinner android:id="@+id/n64PakSpinner2" ... />
<Spinner android:id="@+id/n64PakSpinner3" ... />
<Spinner android:id="@+id/n64PakSpinner4" ... />
```

### 1.2 Chargement dans ConsoleConfigActivity.java

**Localisation:** Lignes 313-317

**Code:**
```java
// Load N64 Pak settings (default: Controller Pak = 0)
if (n64PakSpinner1 != null) n64PakSpinner1.setSelection(prefs.getInt(prefix + "pak_port1", 0));
if (n64PakSpinner2 != null) n64PakSpinner2.setSelection(prefs.getInt(prefix + "pak_port2", 0));
if (n64PakSpinner3 != null) n64PakSpinner3.setSelection(prefs.getInt(prefix + "pak_port3", 0));
if (n64PakSpinner4 != null) n64PakSpinner4.setSelection(prefs.getInt(prefix + "pak_port4", 0));
```

**Clés SharedPreferences:**
- `n64_pak_port1` (valeur: 0, 1, ou 2 = position spinner)
- `n64_pak_port2`
- `n64_pak_port3`
- `n64_pak_port4`

**Problème identifié:**
- ✅ Format de clé cohérent: `{console}_pak_port{1-4}`
- ❌ Valeur = position spinner (0, 1, 2), PAS l'ID Libretro (1, 2, 5)

### 1.3 Sauvegarde dans ConsoleConfigActivity.java

**Localisation:** Lignes 366-370

**Code:**
```java
// Save N64 Pak settings
if (n64PakSpinner1 != null) editor.putInt(prefix + "pak_port1", n64PakSpinner1.getSelectedItemPosition());
if (n64PakSpinner2 != null) editor.putInt(prefix + "pak_port2", n64PakSpinner2.getSelectedItemPosition());
if (n64PakSpinner3 != null) editor.putInt(prefix + "pak_port3", n64PakSpinner3.getSelectedItemPosition());
if (n64PakSpinner4 != null) editor.putInt(prefix + "pak_port4", n64PakSpinner4.getSelectedItemPosition());
```

**Analyse:**
- ✅ Sauvegarde la position du spinner (0, 1, 2)
- ❌ Ne sauvegarde PAS l'ID Libretro correspondant

### 1.4 Application dans RetroArchEmulatorActivity.kt

**Localisation:** Lignes 1589-1621

**Code:**
```kotlin
val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this@RetroArchEmulatorActivity)
val prefix = "n64_"

// Mapping des positions spinner vers les valeurs Libretro :
// 0 = None (pas d'extension)
// 1 = Controller Pak (mémoire de sauvegarde)
// 2 = Rumble Pak (vibration)
// 5 = Transfer Pak (transfert Game Boy)
val pakValues = intArrayOf(0, 1, 2, 5)  // 0 = None, 1 = Controller Pak, 2 = Rumble Pak, 5 = Transfer Pak

// ...
val pakPosition = prefs.getInt(prefix + "pak_port" + (port + 1), 0) // Default: 0 = None
val pakValue = pakValues.getOrElse(pakPosition) { 0 } // Fallback to None
```

**Problèmes identifiés:**

1. **❌ INCOHÉRENCE DE SHAREDPREFERENCES:**
   - `ConsoleConfigActivity` lit/écrit dans: `"console_config"`
   - `RetroArchEmulatorActivity` lit depuis: `PreferenceManager.getDefaultSharedPreferences()` (fichier par défaut)
   - **RÉSULTAT:** Les paramètres sauvegardés dans `ConsoleConfigActivity` ne sont PAS lus par `RetroArchEmulatorActivity`!

2. **❌ MAPPING INCORRECT:**
   - Spinner position 0 = "Controller Pak" → Libretro ID 1
   - Spinner position 1 = "Rumble Pak" → Libretro ID 2
   - Spinner position 2 = "Transfer Pak" → Libretro ID 5
   - **MAIS** `pakValues = intArrayOf(0, 1, 2, 5)` inclut `0 = None` qui n'existe PAS dans le spinner!
   - **RÉSULTAT:** Mapping incorrect entre spinner et IDs Libretro

3. **❌ APPLICATION NON FONCTIONNELLE:**
   - Le code lit les valeurs mais ne les applique pas (voir ligne 1621+)
   - Commentaire: "TODO: Configurer les extensions contrôleur N64"

### 1.5 Application dans NativeComposeEmulatorActivity.kt

**Localisation:** Lignes 1234-1270

**Code:**
```kotlin
val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this@NativeComposeEmulatorActivity)
val prefix = "n64_"

// Mapping des positions spinner vers les valeurs Libretro :
// 0 = Controller Pak (1), 1 = Rumble Pak (2), 2 = Transfer Pak (5)
val pakValues = intArrayOf(1, 2, 5)

// TODO: Configurer les extensions contrôleur N64
// Les valeurs sont sauvegardées dans les préférences mais l'application dans LibretroDroid
// nécessite une investigation supplémentaire pour la méthode correcte
```

**Problèmes identifiés:**

1. **❌ MÊME PROBLÈME DE SHAREDPREFERENCES:**
   - Lit depuis `PreferenceManager.getDefaultSharedPreferences()` (fichier par défaut)
   - `ConsoleConfigActivity` écrit dans `"console_config"`
   - **RÉSULTAT:** Incompatibilité totale

2. **❌ MAPPING DIFFÉRENT:**
   - `pakValues = intArrayOf(1, 2, 5)` (sans le 0 = None)
   - Mais le spinner n'a PAS d'option "None"!
   - **RÉSULTAT:** Mapping incorrect

3. **❌ APPLICATION NON FONCTIONNELLE:**
   - Commentaire: "TODO: Configurer les extensions contrôleur N64"
   - Les valeurs sont lues mais pas appliquées

---

## 2. OPTIONS CORE N64 (Resolution, Anti-Aliasing, Bilinear)

### 2.1 UI dans activity_console_config.xml

**Localisation:** Lignes 560-628

**Options:**
- `n64ResolutionSpinner`: Internal Resolution
  - Options: "320x240 (Native)", "640x480 (2x)", "960x720 (3x)", "1280x960 (4x)"
- `n64AntiAliasingSpinner`: Anti-Aliasing
  - Options: "Off", "2x MSAA", "4x MSAA", "8x MSAA"
- `n64BilinearSwitch`: Bilinear Filtering (on/off)

### 2.2 Chargement dans ConsoleConfigActivity.java

**Localisation:** Lignes 320-322

**Code:**
```java
if (n64ResolutionSpinner != null) n64ResolutionSpinner.setSelection(prefs.getInt(prefix + "n64_resolution", 0));
if (n64AntiAliasingSpinner != null) n64AntiAliasingSpinner.setSelection(prefs.getInt(prefix + "n64_antialiasing", 0));
if (n64BilinearSwitch != null) n64BilinearSwitch.setChecked(prefs.getBoolean(prefix + "n64_bilinear", false));
```

**Clés SharedPreferences:**
- `n64_n64_resolution` (valeur: 0-3 = position spinner)
- `n64_n64_antialiasing` (valeur: 0-3 = position spinner)
- `n64_n64_bilinear` (valeur: boolean)

**Problème identifié:**
- ⚠️ Format de clé redondant: `n64_n64_*` (le préfixe `n64_` est déjà dans `prefix`)

### 2.3 Sauvegarde dans ConsoleConfigActivity.java

**Localisation:** Lignes 373-375

**Code:**
```java
if (n64ResolutionSpinner != null) editor.putInt(prefix + "n64_resolution", n64ResolutionSpinner.getSelectedItemPosition());
if (n64AntiAliasingSpinner != null) editor.putInt(prefix + "n64_antialiasing", n64AntiAliasingSpinner.getSelectedItemPosition());
if (n64BilinearSwitch != null) editor.putBoolean(prefix + "n64_bilinear", n64BilinearSwitch.isChecked());
```

**Analyse:**
- ✅ Sauvegarde correctement
- ❌ Dans `"console_config"` (pas accessible depuis les activités Compose)

### 2.4 Application dans RetroArchEmulatorActivity.kt

**Localisation:** Lignes 1263-1265

**Code:**
```kotlin
val resolution = corePrefs.getInt("${prefix}n64_resolution", 0)
val antialiasing = corePrefs.getInt("${prefix}n64_antialiasing", 0)
val bilinear = corePrefs.getBoolean("${prefix}n64_bilinear", false)
```

**Problèmes identifiés:**

1. **❌ INCOHÉRENCE DE SHAREDPREFERENCES:**
   - `corePrefs` = `PreferenceManager.getDefaultSharedPreferences()` (fichier par défaut)
   - `ConsoleConfigActivity` écrit dans `"console_config"`
   - **RÉSULTAT:** Les paramètres ne sont PAS lus

2. **❌ APPLICATION NON FONCTIONNELLE:**
   - Les valeurs sont lues mais pas appliquées au core
   - Pas d'appel à `retroView.setCoreOption()` ou équivalent

### 2.5 Application dans NativeComposeEmulatorActivity.kt

**Localisation:** Lignes 896-898

**Code:**
```kotlin
val resolution = corePrefs.getInt("${prefix}n64_resolution", 0)
val antialiasing = corePrefs.getInt("${prefix}n64_antialiasing", 0)
val bilinear = corePrefs.getBoolean("${prefix}n64_bilinear", false)
```

**Même problème:**
- ❌ Lit depuis le mauvais fichier SharedPreferences
- ❌ Ne les applique pas au core

---

## 3. PROBLÈMES GLOBAUX IDENTIFIÉS

### 3.1 INCOHÉRENCE DE SHAREDPREFERENCES (CRITIQUE)

**Problème:**
- `ConsoleConfigActivity` utilise: `"console_config"`
- `RetroArchEmulatorActivity` et `NativeComposeEmulatorActivity` utilisent: `PreferenceManager.getDefaultSharedPreferences()` (fichier par défaut Android)

**Impact:**
- ❌ Les paramètres sauvegardés dans `ConsoleConfigActivity` ne sont **JAMAIS** lus par les activités Compose
- ❌ Les utilisateurs configurent les paramètres N64 mais ils ne sont **JAMAIS** appliqués

**Solution:**
- Option 1: Migrer `ConsoleConfigActivity` vers `PreferenceManager.getDefaultSharedPreferences()`
- Option 2: Migrer les activités Compose vers `"console_config"`
- Option 3: Créer un système de migration/lecture depuis les deux fichiers

### 3.2 MAPPING INCORRECT DES VALEURS SPINNER

**Problème:**
- Spinner position 0 = "Controller Pak" → Devrait être Libretro ID 1
- Spinner position 1 = "Rumble Pak" → Devrait être Libretro ID 2
- Spinner position 2 = "Transfer Pak" → Devrait être Libretro ID 5
- Mais `RetroArchEmulatorActivity` utilise `pakValues = intArrayOf(0, 1, 2, 5)` (inclut 0 = None qui n'existe pas)

**Solution:**
- Corriger le mapping: `pakValues = intArrayOf(1, 2, 5)` (correspond aux positions spinner 0, 1, 2)

### 3.3 APPLICATION NON FONCTIONNELLE

**Problème:**
- Les valeurs sont lues depuis SharedPreferences mais **JAMAIS** appliquées au core
- Commentaires "TODO" dans le code

**Solution:**
- Implémenter l'application des extensions via `retroView.setControllerType(port, extensionId)`
- Implémenter l'application des options core via `retroView.setCoreOption()` ou équivalent

---

## 4. RECOMMANDATIONS

### Priorité 1 (CRITIQUE): Corriger l'incohérence SharedPreferences

**Action:**
1. Modifier `RetroArchEmulatorActivity` et `NativeComposeEmulatorActivity` pour lire depuis `"console_config"` au lieu de `PreferenceManager.getDefaultSharedPreferences()`
2. OU modifier `ConsoleConfigActivity` pour écrire dans `PreferenceManager.getDefaultSharedPreferences()`

**Code proposé:**
```kotlin
// Dans RetroArchEmulatorActivity.kt et NativeComposeEmulatorActivity.kt
val prefs = getSharedPreferences("console_config", Context.MODE_PRIVATE)
val prefix = "n64_"
```

### Priorité 2: Corriger le mapping des valeurs spinner

**Action:**
1. Corriger `pakValues` dans `RetroArchEmulatorActivity.kt`:
   ```kotlin
   // Avant: val pakValues = intArrayOf(0, 1, 2, 5)
   // Après:
   val pakValues = intArrayOf(1, 2, 5)  // Correspond aux positions spinner 0, 1, 2
   ```

2. Ajouter une option "None" dans le spinner OU gérer le cas où aucune extension n'est sélectionnée

### Priorité 3: Implémenter l'application des extensions

**Action:**
1. Implémenter l'application des extensions N64:
   ```kotlin
   for (port in 0..3) {
       val pakPosition = prefs.getInt(prefix + "pak_port" + (port + 1), 0)
       if (pakPosition >= 0 && pakPosition < pakValues.size) {
           val pakValue = pakValues[pakPosition]
           if (pakValue > 0) {  // 0 = None, ne pas configurer
               retroView.setControllerType(port, pakValue)
               Log.i(TAG, "[N64] Port ${port + 1} configured with extension ID $pakValue")
           }
       }
   }
   ```

### Priorité 4: Implémenter l'application des options core

**Action:**
1. Implémenter l'application des options core N64 via CoreOptionsDialog ou directement:
   ```kotlin
   // Resolution
   val resolutionOptions = arrayOf("320x240", "640x480", "960x720", "1280x960")
   val resolution = resolutionOptions[prefs.getInt(prefix + "n64_resolution", 0)]
   retroView.setCoreOption("parallel-n64-internal_resolution", resolution)
   
   // Anti-Aliasing
   val aaOptions = arrayOf("0", "2", "4", "8")
   val aa = aaOptions[prefs.getInt(prefix + "n64_antialiasing", 0)]
   retroView.setCoreOption("parallel-n64-msaa", aa)
   
   // Bilinear
   val bilinear = if (prefs.getBoolean(prefix + "n64_bilinear", false)) "enabled" else "disabled"
   retroView.setCoreOption("parallel-n64-bilinear_filtering", bilinear)
   ```

---

## 5. TESTS À EFFECTUER

### Test 1: Vérifier la lecture depuis console_config

**Étapes:**
1. Configurer les extensions N64 dans `ConsoleConfigActivity`
2. Vérifier que les valeurs sont sauvegardées dans `"console_config"`
3. Lancer un jeu N64
4. Vérifier dans les logs que les valeurs sont lues depuis `"console_config"`

### Test 2: Vérifier l'application des extensions

**Étapes:**
1. Configurer Controller Pak sur Port 1
2. Lancer un jeu N64 qui utilise Controller Pak
3. Vérifier dans les logs que `setControllerType(0, 1)` est appelé
4. Tester que les sauvegardes fonctionnent

### Test 3: Vérifier l'application des options core

**Étapes:**
1. Configurer Resolution = 2x (640x480)
2. Lancer un jeu N64
3. Vérifier dans les logs que `setCoreOption("parallel-n64-internal_resolution", "640x480")` est appelé
4. Vérifier visuellement que la résolution est appliquée

---

## CONCLUSION

**Status actuel:** ❌ **NON FONCTIONNEL**

Les paramètres N64 configurés dans `ConsoleConfigActivity` ne sont **JAMAIS** appliqués car:
1. Incohérence de SharedPreferences (fichiers différents)
2. Mapping incorrect des valeurs
3. Application non implémentée

**Action immédiate requise:** Corriger l'incohérence SharedPreferences (Priorité 1)

