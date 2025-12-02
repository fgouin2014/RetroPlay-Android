# Audit Parsing Core Options

## CONTEXTE

Les **Core Options** sont les paramètres spécifiques à chaque core (ex: résolution N64, filtres PSX, etc.). Elles sont parsées depuis:
1. **Variables LibretroDroid** (format `description = "DisplayName; value1|value2|value3"`)
2. **Fichiers `.cfg`** (format `option_name = "value"`)

---

## SPÉCIFICATION RETROARCH

**Source:** `libretro.h` lignes 6354-6357

```c
* When used this way, it must be formatted as follows:
* @li The text before the first ';' is the option's human-readable title.
* @li A single space follows the ';'.
* @li The rest of the string is a '|'-delimited list of possible values,
*     with the first one being the default.
```

**Format attendu:**
```
"DisplayName; value1|value2|value3"
         ^^^^
    Espace après ';'
```

**Exemple:**
```c
{ "foo_speedhack", "Speed hack; false|true" }
{ "foo_displayscale", "Display scale factor; 1|2|3|4" }
```

---

## PARSING RETROPLAY

### 1. CoreVariableManager.parseVariables()

**Fichier:** `CoreVariableManager.kt` lignes 22-99

**Parsing:**
```kotlin
// Format: "DisplayName; value1|value2|value3"
val parts = description.split(";")
if (parts.isEmpty()) return@mapNotNull null

var displayName = parts[0].trim()

var possibleValues = if (parts.size > 1) {
    parts[1].trim().split("|").map { it.trim() }
} else {
    emptyList()
}
```

**Analyse:**
- ✅ Split sur `";"` (sans espace) - OK car `.trim()` gère l'espace
- ✅ `.trim()` sur `parts[1]` avant de split sur `"|"` - OK
- ✅ `.trim()` sur chaque valeur après split - OK
- ⚠️ **POTENTIEL:** Ne vérifie pas explicitement l'espace après `;` (mais `.trim()` le gère)

**Comparaison avec Lemuroid:**
```kotlin
// Lemuroid-GameLibrary/lemuroid-app/.../CoreOption.kt ligne 14-15
val name = variable.description?.split(";")?.get(0)!!
val values = variable.description?.split(";")?.get(1)?.trim()?.split('|') ?: listOf()
```
- ✅ Même approche (split sur `;` puis trim)

**Verdict:** ✅ **PARSING CORRECT**
- Le `.trim()` gère automatiquement l'espace après `;` spécifié par RetroArch
- Compatible avec la spécification RetroArch

---

### 2. CoreConfigManager.loadConfig()

**Fichier:** `CoreConfigManager.kt` lignes 39-73

**Parsing:**
```kotlin
// Parser: option_name = "value"
val parts = trimmed.split("=", limit = 2)
if (parts.size == 2) {
    val key = parts[0].trim()
    val value = parts[1].trim().removeSurrounding("\"")
    config[key] = value
}
```

**Analyse:**
- ✅ Split sur `"="` avec `limit = 2` - OK (gère les `=` dans la valeur)
- ✅ `.trim()` sur key et value - OK
- ✅ `.removeSurrounding("\"")` pour les guillemets - OK
- ✅ Gestion des commentaires (`startsWith("#")`) - OK
- ✅ Gestion des lignes vides - OK

**Comparaison avec RetroPlayConfigManager:**
```kotlin
// RetroPlayConfigManager.kt ligne 190-194
val parts = trimmed.split("=", limit = 2)
if (parts.size == 2) {
    val key = parts[0].trim()
    val value = parts[1].trim().removeSurrounding("\"")
    configMap[key] = value
}
```
- ✅ **IDENTIQUE** - Même parsing

**Verdict:** ✅ **PARSING CORRECT**
- Format simple `key = "value"` bien géré
- Compatible avec le format RetroArch `.cfg`

---

## COMPARAISON AVEC RETROARCH

### Parsing description (variables)

**RetroArch:** Utilise `strtok_r` ou équivalent pour parser `"DisplayName; value1|value2|value3"`

**RetroPlay:** Utilise `split(";")` puis `.trim()` et `split("|")`

**Différence:**
- RetroArch peut utiliser `strtok_r` (plus robuste pour les cas edge)
- RetroPlay utilise `split()` (plus simple, mais fonctionnel)

**Impact:** ⚠️ **THÉORIQUE**
- Si un core envoie `"DisplayName;value1|value2"` (sans espace), RetroPlay gère via `.trim()`
- Si un core envoie `"DisplayName; value1 | value2"` (espaces multiples), RetroPlay gère via `.trim()`
- **Pas de problème identifié** - le parsing est robuste

---

## PROBLÈMES IDENTIFIÉS

### Aucun problème critique

1. ✅ **Parsing description:** Correct (gère l'espace après `;` via `.trim()`)
2. ✅ **Parsing `.cfg`:** Correct (format simple bien géré)
3. ✅ **Gestion des erreurs:** Try/catch présent dans `parseVariables()`
4. ✅ **Valeurs par défaut:** Gestion des booléens numériques (ligne 67-69)

---

## RECOMMANDATIONS

### Aucune correction nécessaire

Le parsing des core options est **correct et compatible** avec RetroArch:
- Format description: Géré via `.trim()` (compatible avec spécification RetroArch)
- Format `.cfg`: Parsing simple et robuste
- Gestion d'erreur: Présente

**Note:** Si on veut être 100% conforme à RetroArch, on pourrait vérifier explicitement l'espace après `;`, mais ce n'est pas nécessaire car `.trim()` le gère déjà.

---

## CONCLUSION

### Statut: ✅ **PARSING CORRECT**

**Core Options parsing:**
- ✅ Format description: Compatible RetroArch (via `.trim()`)
- ✅ Format `.cfg`: Parsing simple et robuste
- ✅ Gestion d'erreur: Présente
- ✅ Valeurs par défaut: Gestion des booléens

**Aucun problème identifié** - Le parsing est correct et compatible avec RetroArch.

---

**Dernière mise à jour:** 2025-01-XX - Audit core options terminé

