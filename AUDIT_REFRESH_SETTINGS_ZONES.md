# Audit: Refresh Settings et Zones Debug vs Détection

**Date:** 2025-01-XX  
**Problèmes:** 
1. Settings ne se rafraîchissent pas en temps réel
2. Zones rouges (debug) ne correspondent pas aux zones de détection réelles

---

## PROBLÈME 1: Settings ne se rafraîchissent pas

### GamePadLayoutManager.kt (ligne 274)

**Code actuel:**
```kotlin
val advancedSettings = remember(console, orientation) {
    OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
        ?: AdvancedOverlaySettings()
}
```

**Problème:** `remember(console, orientation)` ne se rafraîchit que si `console` ou `orientation` change, PAS si les settings changent dans SharedPreferences!

**Solution:** Ajouter un listener SharedPreferences ou utiliser `LaunchedEffect` avec un listener

---

## PROBLÈME 2: Zones debug vs détection

### Debug (ligne 385):
```kotlin
val debugButtonWidth = button.rangeXHitbox * viewport.width * scaledLayout.rangeModifier * overlayScale
```

**Utilise:** `scaledLayout.rangeModifier` (toujours, même valeur)

### Détection (ligne 1326):
```kotlin
val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier * overlayScale
```

**Utilise:** `rangeModifier` passé en paramètre (peut être `1.0f` si premier touch, ou `layout.rangeModifier` si touch actif)

**Problème:** Debug utilise toujours `rangeModifier`, détection conditionnelle → zones visuelles plus grandes que zones réelles!

**Solution:** Debug doit utiliser le même calcul que détection (conditionnel selon `useRangeModForThisButton`)

---

## CORRECTIONS REQUISES

1. ✅ Ajouter listener SharedPreferences dans GamePadLayoutManager pour rafraîchir settings
2. ✅ Aligner debug avec détection (utiliser même logique conditionnelle)

---

**Dernière mise à jour:** 2025-01-XX

