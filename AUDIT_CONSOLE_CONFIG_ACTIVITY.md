# AUDIT: ConsoleConfigActivity.java

## Date: 2025-01-XX
## Contexte: Migration vers nouveau format de clés SharedPreferences

---

## PROBLÈMES IDENTIFIÉS

### 1. INCOHÉRENCE DE SHAREDPREFERENCES

**Problème:**
- `ConsoleConfigActivity` utilise `"console_config"` (ligne 94, 423)
- Les autres activités (`RetroArchEmulatorActivity`, `NativeComposeEmulatorActivity`) utilisent maintenant `"compose_gamepad_settings"`

**Impact:**
- Les paramètres sauvegardés dans `ConsoleConfigActivity` ne sont PAS accessibles depuis les activités Compose
- Les paramètres sauvegardés dans les activités Compose ne sont PAS accessibles depuis `ConsoleConfigActivity`
- **INCOMPATIBILITÉ TOTALE** entre les deux systèmes

**Code concerné:**
```java
// Ligne 94
prefs = getSharedPreferences("console_config", Context.MODE_PRIVATE);

// Ligne 423 (getConfig())
SharedPreferences prefs = context.getSharedPreferences("console_config", Context.MODE_PRIVATE);
```

**Solution recommandée:**
- Migrer vers `"compose_gamepad_settings"` OU
- Créer un système de migration/lecture depuis les deux fichiers

---

### 2. FORMAT DE CLÉS INCOHÉRENT

**Problème:**
- `ConsoleConfigActivity` utilise l'ancien format: `{console}_*` (ex: `nes_threads`, `nes_use_dpad`)
- Les activités Compose utilisent le nouveau format: `*_{console}` (ex: `overlay_nes_*`, `gamepad_nes_*`)

**Clés utilisées dans ConsoleConfigActivity:**
```java
// Format: {console}_*
prefix + "threads"                    // nes_threads
prefix + "use_dpad"                   // nes_use_dpad
prefix + "use_custom_tabs"            // nes_use_custom_tabs
prefix + "pak_port1"                  // nes_pak_port1
prefix + "touch_scale"                 // nes_touch_scale
prefix + "touch_alpha"                // nes_touch_alpha
prefix + "n64_resolution"             // nes_n64_resolution (si console = n64)
prefix + "psx_resolution"             // nes_psx_resolution (si console = psx)
// etc.
```

**Clés utilisées dans les activités Compose (nouveau format):**
```kotlin
// Format: *_{console}
"overlay_${console}_enabled"          // overlay_nes_enabled
"gamepad_${console}_variant"          // gamepad_nes_variant
"gamepad_${console}_settings_scale"   // gamepad_nes_settings_scale
"cache_enabled_$console"              // cache_enabled_nes
```

**Impact:**
- **AUCUNE COMPATIBILITÉ** entre les deux formats
- Les paramètres sauvegardés dans `ConsoleConfigActivity` ne sont PAS lus par les activités Compose
- Les paramètres sauvegardés dans les activités Compose ne sont PAS lus par `ConsoleConfigActivity`

---

### 3. FONCTION `resetNativeGamePad()` - PARTIELLEMENT COHÉRENTE

**Analyse:**
La fonction `resetNativeGamePad()` (ligne 764) utilise:
- ✅ `"compose_gamepad_settings"` (cohérent avec les activités Compose)
- ✅ Nouveau format de clés: `"gamepad_" + currentConsole + "_variant"` (cohérent)

**Mais:**
- ❌ Elle supprime des clés qui n'existent peut-être pas encore (overlay settings)
- ❌ Elle ne supprime PAS les clés de l'ancien format (`console_config`)

**Code:**
```java
// Ligne 771
SharedPreferences gamepadPrefs = getSharedPreferences("compose_gamepad_settings", Context.MODE_PRIVATE);
// Ligne 776
editor.remove("gamepad_" + currentConsole + "_variant");  // ✅ Nouveau format
```

---

### 4. FONCTION STATIQUE `getConfig()` - INCOMPATIBLE

**Problème:**
La fonction `getConfig()` (ligne 422) est utilisée par d'autres parties du code pour charger la configuration console.

**Code:**
```java
public static ConsoleConfig getConfig(Context context, String console) {
    SharedPreferences prefs = context.getSharedPreferences("console_config", Context.MODE_PRIVATE);
    String prefix = console + "_";
    
    // Utilise l'ancien format: {console}_*
    config.threads = prefs.getBoolean(prefix + "threads", false);
    config.touchScale = prefs.getFloat(prefix + "touch_scale", 1.0f);
    // etc.
}
```

**Impact:**
- Si d'autres parties du code appellent `getConfig()`, elles ne verront PAS les paramètres sauvegardés dans les activités Compose
- Si les activités Compose essaient de lire ces paramètres, elles ne les trouveront PAS

---

### 5. PARAMÈTRES NON MIGRÉS

**Paramètres gérés par ConsoleConfigActivity:**
1. `threads` (EJS_threads) - Performance
2. `touch_scale` (EJS_VirtualGamepadSettings.scale) - EmulatorJS
3. `touch_alpha` (EJS_VirtualGamepadSettings.opacity) - EmulatorJS
4. `use_dpad` (PSX only) - PSX controller mode
5. `use_custom_tabs` - Chrome Custom Tabs vs WebView
6. `pak_port1-4` (N64) - Controller extensions
7. `n64_resolution`, `n64_antialiasing`, `n64_bilinear` - N64 core options
8. `psx_resolution`, `psx_texture_filtering`, `psx_dithering` - PSX core options
9. `snes_blend_mode`, `snes_hires` - SNES core options

**Question:**
- Ces paramètres sont-ils utilisés par les activités Compose?
- Si oui, où sont-ils lus/écrits?
- Si non, `ConsoleConfigActivity` est-il encore utilisé?

---

## RECOMMANDATIONS

### Option 1: MIGRATION COMPLÈTE (Recommandée)

**Étapes:**
1. Migrer `ConsoleConfigActivity` vers `"compose_gamepad_settings"`
2. Migrer toutes les clés vers le nouveau format: `*_{console}`
3. Créer une fonction de migration pour les anciennes clés
4. Mettre à jour `getConfig()` pour lire depuis le nouveau format

**Nouveau format proposé:**
```java
// Ancien: nes_threads
// Nouveau: threads_nes

// Ancien: nes_touch_scale
// Nouveau: touch_scale_nes

// Ancien: nes_use_dpad
// Nouveau: use_dpad_nes

// Ancien: nes_pak_port1
// Nouveau: pak_port1_nes
```

### Option 2: DOUBLE LECTURE (Temporaire)

**Étapes:**
1. Modifier `getConfig()` pour lire depuis les deux formats
2. Priorité: nouveau format, fallback sur ancien format
3. Écrire toujours dans le nouveau format
4. Créer une fonction de migration automatique

**Code exemple:**
```java
public static ConsoleConfig getConfig(Context context, String console) {
    SharedPreferences newPrefs = context.getSharedPreferences("compose_gamepad_settings", Context.MODE_PRIVATE);
    SharedPreferences oldPrefs = context.getSharedPreferences("console_config", Context.MODE_PRIVATE);
    
    String newPrefix = "threads_" + console;
    String oldPrefix = console + "_threads";
    
    // Lire depuis nouveau format, fallback sur ancien
    boolean threads = newPrefs.getBoolean(newPrefix, oldPrefs.getBoolean(oldPrefix, false));
    
    // Si trouvé dans ancien format, migrer vers nouveau
    if (oldPrefs.contains(oldPrefix) && !newPrefs.contains(newPrefix)) {
        newPrefs.edit().putBoolean(newPrefix, threads).apply();
    }
}
```

### Option 3: DÉPRÉCIATION

**Si `ConsoleConfigActivity` n'est plus utilisé:**
1. Marquer comme `@Deprecated`
2. Rediriger vers les nouvelles activités Compose
3. Supprimer progressivement

---

## QUESTIONS À RÉSOUDRE

1. **`ConsoleConfigActivity` est-il encore utilisé?**
   - Si oui, par qui?
   - Si non, peut-on le supprimer?

2. **Les paramètres EmulatorJS (`touch_scale`, `touch_alpha`) sont-ils encore nécessaires?**
   - Les activités Compose utilisent-elles EmulatorJS?
   - Ou sont-elles 100% RetroArch/Native?

3. **La fonction `getConfig()` est-elle appelée ailleurs?**
   - Chercher: `ConsoleConfig.getConfig()`
   - Vérifier les dépendances

4. **Les paramètres core-specific (N64, PSX, SNES) sont-ils gérés ailleurs?**
   - Dans `RetroArchSettingsDialog`?
   - Dans les activités Compose?

---

## ACTIONS IMMÉDIATES

1. ✅ **Audit complet** - FAIT (ce document)
2. ⏳ **Vérifier l'utilisation de `ConsoleConfigActivity`** - À FAIRE
3. ⏳ **Vérifier les appels à `getConfig()`** - À FAIRE
4. ⏳ **Décider de la stratégie de migration** - À FAIRE
5. ⏳ **Implémenter la migration** - À FAIRE

---

## FICHIERS CONCERNÉS

- `RetroPlay-Android/app/src/main/java/com/retroplay/ConsoleConfigActivity.java`
- `RetroPlay-Android/app/src/main/res/layout/activity_console_config.xml`
- `RetroPlay-Android/app/src/main/java/com/retroplay/RetroArchEmulatorActivity.kt`
- `RetroPlay-Android/app/src/main/java/com/retroplay/NativeComposeEmulatorActivity.kt`
- `RetroPlay-Android/app/src/main/java/com/retroplay/RetroArchSettingsDialog.kt`

---

## NOTES

- Les changements récents de l'utilisateur montrent une migration vers `compose_gamepad_settings` et le format `*_{console}`
- `ConsoleConfigActivity` n'a PAS été mis à jour, créant une incohérence
- Il faut décider: migrer `ConsoleConfigActivity` OU le déprécier

