# Audit Parsing - Valeurs par Défaut

## PROBLÈME CRITIQUE: Valeurs par défaut incorrectes

Comparaison RetroArch vs RetroPlay pour les valeurs par défaut:

### 1. `overlayN_normalized`

**RetroArch (task_overlay.c lignes ~1950-1955):**
```c
if (config_get_bool(conf, conf_key, &tmp_bool) && tmp_bool)
   overlay->config.normalized = tmp_bool;
else
   overlay->config.normalized = false;  // DEFAULT = false
```

**RetroPlay (ligne 147):**
```kotlin
val normalized = lines.find { it.trim().startsWith("${prefix}normalized = ") }
    ?.substringAfter("= ")?.trim()?.toBoolean() ?: true  // DEFAULT = true ❌
```

**Impact:** Si `overlayN_normalized` n'est pas spécifié, RetroArch assume `false` (pixel coordinates), RetroPlay assume `true` (normalized). Cela peut causer des problèmes de conversion.

---

### 2. `overlayN_full_screen`

**RetroArch (task_overlay.c lignes ~1960-1965):**
```c
if (config_get_bool(conf, conf_key, &tmp_bool) && tmp_bool)
   overlay->flags |=  OVERLAY_FULL_SCREEN;
else
   overlay->flags &= ~OVERLAY_FULL_SCREEN;  // DEFAULT = false
```

**RetroPlay (ligne 144):**
```kotlin
val fullScreen = lines.find { it.trim().startsWith("${prefix}full_screen = ") }
    ?.substringAfter("= ")?.trim()?.toBoolean() ?: true  // DEFAULT = true ❌
```

**Impact:** Si `overlayN_full_screen` n'est pas spécifié, RetroArch assume `false`, RetroPlay assume `true`.

---

### 3. `overlayN_auto_x_separation`

**RetroArch (task_overlay.c lignes ~2010-2020):**
```c
overlay->flags |=  OVERLAY_AUTO_X_SEPARATION;  // DEFAULT = true
if (config_get_bool(conf, conf_key, &tmp_bool))
{
   if (!tmp_bool)
      overlay->flags &= ~OVERLAY_AUTO_X_SEPARATION;
}
else
{
   if (overlay->flags & OVERLAY_BLOCK_X_SEPARATION
         || overlay->image.width != 0)
      overlay->flags &= ~OVERLAY_AUTO_X_SEPARATION;  // Désactivé si block_x OU image.width != 0
}
```

**RetroPlay (ligne 218):**
```kotlin
val autoXSeparation = lines.find { it.trim().startsWith("${prefix}auto_x_separation = ") }
    ?.substringAfter("= ")?.trim()?.toBoolean() ?: true  // DEFAULT = true ✅
```

**Impact:** La logique de désactivation conditionnelle (si `block_x_separation` OU `image.width != 0`) n'est pas implémentée.

---

### 4. `overlayN_auto_y_separation`

**RetroArch (task_overlay.c lignes ~2025-2030):**
```c
if (config_get_bool(conf, conf_key, &tmp_bool) && tmp_bool)
   overlay->flags |=  OVERLAY_AUTO_Y_SEPARATION;
else
   overlay->flags &= ~OVERLAY_AUTO_Y_SEPARATION;  // DEFAULT = false
```

**RetroPlay (ligne 220):**
```kotlin
val autoYSeparation = lines.find { it.trim().startsWith("${prefix}auto_y_separation = ") }
    ?.substringAfter("= ")?.trim()?.toBoolean() ?: true  // DEFAULT = true ❌
```

**Impact:** Si `overlayN_auto_y_separation` n'est pas spécifié, RetroArch assume `false`, RetroPlay assume `true`.

---

## CORRECTIONS REQUISES

1. ✅ **`normalized`**: Changer default de `true` → `false`
2. ✅ **`fullScreen`**: Changer default de `true` → `false`
3. ✅ **`autoYSeparation`**: Changer default de `true` → `false`
4. ⚠️ **`autoXSeparation`**: Ajouter logique conditionnelle (si `blockXSeparation` OU `backgroundImage != null`)

---

**Dernière mise à jour:** 2025-01-XX - Audit valeurs par défaut terminé

