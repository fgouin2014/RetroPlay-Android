# Audit Phase 1 - Parsing Autoconfig Alternatives (Bytes par Bytes)

**Date:** 2025-12-01  
**Phase:** 1 - Parsing et Mapping  
**Objectif:** Vérifier que le parsing des alternatives (`input_device_alt1`, `input_vendor_id_alt1`, etc.) est identique à RetroArch

**Sources analysées:**
- `c:\repos\RetroArch-master\input\` (parsing autoconfig)
- `c:\repos\retroarch-joypad-autoconfig-master\android\8BitDo_Pro2.cfg` (exemple réel avec alternatives)
- `RetroPlay-Android/app/src/main/java/com/retroplay/input/AutoconfigManager.kt` (parsing actuel)

---

## EXEMPLE RÉEL: 8BitDo_Pro2.cfg

```ini
input_driver = "android"
input_device = "8BitDo Pro 2"
input_device_display_name = "8BitDo Pro 2"
input_vendor_id = "11720"
input_product_id = "24836"

# Alternative 1: Old firmware Bluetooth
input_device_alt1 = "8BitDo Pro 2"
input_device_display_name_alt1 = "8BitDo Pro 2 (old firmware, Bluetooth)"
input_vendor_id_alt1 = "11720"
input_product_id_alt1 = "24835"

# Alternative 2: Old firmware USB
input_device_alt2 = "8BitDo 8BitDo Pro 2"
input_device_display_name_alt2 = "8BitDo Pro 2 (old firmware, USB)"
input_vendor_id_alt2 = "11720"
input_product_id_alt2 = "24579"

# Mappings (partagés entre principal et alternatives)
input_b_btn = "96"
input_y_btn = "99"
...
```

**Structure observée:**
- Principal: `input_device`, `input_vendor_id`, `input_product_id`
- Alternative 1: `input_device_alt1`, `input_vendor_id_alt1`, `input_product_id_alt1`
- Alternative 2: `input_device_alt2`, `input_vendor_id_alt2`, `input_product_id_alt2`
- Mappings (`input_*_btn`, `input_*_axis`, `input_*_label`) sont **partagés** (pas de `_alt` pour les mappings)

---

## COMPARAISON RETROARCH vs RETROPLAY

### 1. PARSING DES ALTERNATIVES

#### RetroArch (à trouver dans input_autoconfigure.c ou équivalent)

**À VÉRIFIER:**
- Comment RetroArch parse `input_device_alt1`, `input_vendor_id_alt1`, etc.
- Comment les alternatives sont stockées (structure de données)
- Comment les mappings sont partagés ou non

#### RetroPlay (AutoconfigManager.kt:363-405)

**Code actuel:**
```kotlin
// Détecter si c'est une alternative (_alt1, _alt2, etc.)
val isAlternative = key.contains("_alt")
val altMatch = Regex("_alt(\\d+)").find(key)
val altNum = altMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

if (isAlternative && altNum > 0) {
    // Alternative détectée
    if (currentAltIndex != altNum) {
        // Nouvelle alternative, sauvegarder la précédente
        if (currentAltIndex > 0 && altDeviceName.isNotEmpty()) {
            alternatives.add(DeviceConfig(...))
            altButtonMappings.clear()
            altAxisMappings.clear()
            altLabels.clear()
        }
        currentAltIndex = altNum
        // Reset alt variables
    }
    
    // Parser champ alternative
    val cleanKey = key.replace("_alt$altNum", "")
    when {
        cleanKey == "input_device" -> altDeviceName = value
        cleanKey == "input_vendor_id" -> altVendorId = value.toIntOrNull()
        cleanKey == "input_product_id" -> altProductId = value.toIntOrNull()
        cleanKey.endsWith("_btn") -> altButtonMappings[cleanKey] = value
        cleanKey.endsWith("_axis") -> altAxisMappings[cleanKey] = value
        cleanKey.endsWith("_label") -> altLabels[cleanKey] = value
    }
}
```

**⚠️ PROBLÈME IDENTIFIÉ:**
- Les mappings (`input_*_btn`, `input_*_axis`, `input_*_label`) sont parsés dans `altButtonMappings` pour les alternatives
- **MAIS** dans l'exemple réel `8BitDo_Pro2.cfg`, les mappings n'ont PAS de `_alt` suffix
- Les mappings sont **partagés** entre principal et alternatives

**Question:** Les alternatives doivent-elles hériter des mappings du principal, ou peuvent-elles avoir leurs propres mappings?

---

### 2. MATCHING DES ALTERNATIVES

#### RetroArch (à trouver)

**À VÉRIFIER:**
- Algorithme de matching avec alternatives
- Score de matching (VID/PID principal vs alternative)
- Priorité: principal > alt1 > alt2 > ...

#### RetroPlay (AutoconfigManager.kt:180-202)

**Code actuel:**
```kotlin
private fun findConfigByVidPid(vendorId: Int, productId: Int): DeviceConfig? {
    for (configFileRef in configFiles) {
        val config = parseConfigFile(configFileRef)
        if (config != null) {
            // Vérifier VID/PID principal
            if (config.vendorId == vendorId && config.productId == productId) {
                return config
            }
            
            // Vérifier alternatives
            for (alt in config.alternatives) {
                if (alt.vendorId == vendorId && alt.productId == productId) {
                    return alt  // ⚠️ Retourne l'alternative, pas le principal!
                }
            }
        }
    }
    return null
}
```

**⚠️ PROBLÈME IDENTIFIÉ:**
- Si une alternative match, on retourne l'alternative (`alt`)
- **MAIS** l'alternative n'a peut-être pas les mappings (si ils sont partagés dans le principal)
- Il faudrait peut-être retourner le principal avec les mappings, ou copier les mappings dans l'alternative

---

## RÉPONSES TROUVÉES (RetroArch task_autodetect.c)

### 1. PARSING DES ALTERNATIVES (RetroArch)

**Fonction:** `input_autoconfigure_get_config_file_affinity()` (lignes 113-194)

**Algorithme:**
```c
// Boucle sur 10 entrées (principal + 9 alternatives)
for (i = 0; i < 10; i++) {
    // Construire clé avec _alt{i} si i > 0
    if (i == 0)
        config_key_postfix[0] = '\0';
    else
        snprintf(config_key_postfix, sizeof(config_key_postfix), "_alt%d", i);
    
    // Lire VID/PID avec suffix
    strlcpy(config_key, "input_vendor_id", sizeof(config_key));
    strlcpy(config_key + _len, config_key_postfix, sizeof(config_key) - _len);
    config_get_int(config, config_key, &tmp_int);
    
    // Lire device name avec suffix
    strlcpy(config_key, "input_device", sizeof(config_key));
    strlcpy(config_key + _len, config_key_postfix, sizeof(config_key) - _len);
    config_get_entry(config, config_key);
    
    // Calculer score d'affinité
    if (VID/PID match) affinity += 30;
    if (device name match) affinity += 20;
    affinity += i;  // Identifier l'alternative (0-9)
    
    max_affinity = max(max_affinity, affinity);
}
```

**Score d'affinité:**
- `0`: Pas de match
- `20-29`: Device name match (principal ou alternative)
- `30-39`: VID+PID match (principal ou alternative)
- `50-59`: Device name + VID+PID match (parfait)
- Dernier chiffre (0-9): Identifie l'alternative sélectionnée

### 2. MAPPINGS PARTAGÉS

**CONFIRMÉ:** Les mappings (`input_*_btn`, `input_*_axis`, `input_*_label`) sont **partagés** entre principal et alternatives.

**Preuve:**
- Exemple `8BitDo_Pro2.cfg`: Les mappings n'ont PAS de `_alt` suffix
- RetroArch utilise `input_config_set_autoconfig_binds()` qui lit les mappings SANS `_alt` suffix
- Les alternatives héritent automatiquement des mappings du principal

### 3. MATCHING ET RETOUR

**Quand une alternative match:**
- RetroArch retourne le **même fichier config** (principal)
- L'alternative est identifiée par `max_affinity % 10` (0-9)
- Les mappings sont appliqués depuis le fichier principal (partagés)
- Seul `input_device_display_name_alt{i}` est lu avec le suffix `_alt{i}`

**Fonction:** `input_autoconfigure_set_config_file()` (lignes 198-235)
```c
// Seul display_name est lu avec _alt suffix
if (alternative > 0)
    snprintf(config_key + _len, sizeof(config_key) - _len, "_alt%d", alternative);
config_get_entry(config, "input_device_display_name_alt{i}");
```

---

## PROBLÈMES IDENTIFIÉS DANS RETROPLAY

### ❌ PROBLÈME 1: Mappings parsés dans alternatives

**Code actuel (AutoconfigManager.kt:402-404):**
```kotlin
cleanKey.endsWith("_btn") -> altButtonMappings[cleanKey] = value
cleanKey.endsWith("_axis") -> altAxisMappings[cleanKey] = value
cleanKey.endsWith("_label") -> altLabels[cleanKey] = value
```

**Problème:** Les mappings sont parsés dans `altButtonMappings` pour les alternatives, mais ils n'existent PAS avec `_alt` suffix dans les fichiers .cfg.

**Correction:** Les mappings doivent être **partagés** (hérités du principal).

### ❌ PROBLÈME 2: Alternative retournée au lieu du principal

**Code actuel (AutoconfigManager.kt:194-196):**
```kotlin
for (alt in config.alternatives) {
    if (alt.vendorId == vendorId && alt.productId == productId) {
        return alt  // ⚠️ Retourne l'alternative, pas le principal!
    }
}
```

**Problème:** Si une alternative match, on retourne l'alternative qui n'a pas les mappings (car ils sont partagés dans le principal).

**Correction:** Retourner le **principal** avec les mappings, et identifier l'alternative matchée.

---

## CORRECTIONS À APPLIQUER

1. **Supprimer parsing mappings pour alternatives** - Les mappings n'ont pas de `_alt` suffix
2. **Retourner principal avec alternative matchée** - Les mappings sont dans le principal
3. **Hériter mappings dans alternatives** - Copier les mappings du principal vers les alternatives lors du matching

---

## CORRECTIONS APPLIQUÉES ✅

### 1. Parsing mappings supprimé pour alternatives
- **Lignes 402-404 supprimées:** Les mappings (`input_*_btn`, `input_*_axis`, `input_*_label`) ne sont plus parsés avec `_alt` suffix
- **Compatible RetroArch:** Les mappings sont partagés entre principal et alternatives

### 2. Retour principal avec alternative matchée
- **`findConfigByVidPid()` corrigé:** Retourne le principal (avec mappings) au lieu de l'alternative seule
- **`findConfigByName()` corrigé:** Retourne le principal (avec mappings) au lieu de l'alternative seule
- **Compatible RetroArch:** Même comportement que `input_autoconfigure_get_config_file_affinity()`

### 3. Héritage mappings du principal vers alternatives
- **`parseConfigLines()` corrigé:** Les alternatives héritent automatiquement des mappings du principal lors de la création
- **Compatible RetroArch:** Les mappings sont partagés, pas dupliqués

**Fichier modifié:** `AutoconfigManager.kt`

