# Audit: Advanced Overlay Settings - Options branchées vs non branchées

**Date:** 2025-10-31  
**Découverte:** L'utilisateur a identifié que certaines options du menu ne sont pas branchées  
**Impact:** CRITIQUE - Options visibles mais non fonctionnelles = mauvaise UX

---

## 📊 RÉSULTAT DE L'AUDIT

### ✅ OPTIONS BRANCHÉES (fonctionnent réellement)

| Option | Fichier | Ligne | Statut | Impact |
|--------|---------|-------|--------|--------|
| **opacity** | OverlayRenderer.kt | Multiple | ✅ Fonctionnel | Modifie alpha des overlays |
| **dpadDiagonalSensitivity** | TouchEventHandler.kt | ? | ✅ Fonctionnel | Zones diagonales D-Pad |
| **abxyDiagonalSensitivity** | TouchEventHandler.kt | ? | ✅ Fonctionnel | Zones diagonales ABXY |
| **hideInMenu** | ComposeEmulatorScreen | ? | ✅ Fonctionnel | Cache overlays dans menu |
| **hideWhenGamepad** | ? | ? | ⚠️ À vérifier | Cache si gamepad physique |
| **showInputs** | ? | ? | ⚠️ À vérifier | Affiche inputs actifs |
| **allowOffscreen** | RetroArchEmulatorActivity.kt | 425-430 | ✅ Fonctionnel | Zapper hors zone |

---

### ❌ OPTIONS NON BRANCHÉES (UI seulement!)

| Option | Valeur sauvegardée | Code d'utilisation | Statut |
|--------|-------------------|-------------------|--------|
| **triggerOnTouch** | ✅ Oui | ❌ NON (juste log ligne 463) | **BROKEN** |
| **lightgunTriggerDelay** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **lightgunPort** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **lightgunTwoTouchInput** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **lightgunThreeTouchInput** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **lightgunFourTouchInput** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **mouseSpeed** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **mouseSwipeThreshold** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **mouseHoldToDrag** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **mouseHoldMsec** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **mouseDoubleTapToDrag** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **mouseDtapMsec** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |
| **showMouseCursor** | ✅ Oui | ❌ NON (jamais utilisé) | **BROKEN** |

---

## 🚨 DÉCOUVERTE CRITIQUE

**13 OPTIONS SUR 20** sont **NON BRANCHÉES**! 

Elles ont:
- ✅ Interface UI dans le dialog
- ✅ Sliders/Switches fonctionnels
- ✅ Sauvegarde dans SharedPreferences
- ❌ **AUCUN code qui les utilise réellement**

C'est comme avoir un **panneau de contrôle factice**! 🎛️💀

---

## 🎯 FOCUS: Options Lightgun/Zapper

### Code actuel dans `handleZapperTouch()`

```kotlin
private fun handleZapperTouch(
    event: MotionEvent,
    gameViewBounds: Rect?,
    triggerOnTouch: Boolean = false,     // ← Paramètre REÇU mais PAS UTILISÉ!
    allowOffscreen: Boolean = true       // ← Paramètre UTILISÉ ✅
): Boolean {
    // ...
    
    // allowOffscreen est UTILISÉ (ligne 425-430)
    if (!isInGameArea) {
        if (!allowOffscreen) {
            return false  // ← Option branchée ✅
        }
    }
    
    // triggerOnTouch est INUTILISÉ! (ligne 463-465)
    if (triggerOnTouch) {
        Log.d(TAG, "[ZAPPER] Trigger on touch enabled - instant shot")
        // ← AUCUNE ACTION! ❌
    }
    
    // Les autres options (delay, port, multi-touch) ne sont JAMAIS mentionnées ❌
}
```

---

## 💡 EXPLICATION DU PROBLÈME

### Pourquoi `triggerOnTouch` n'est PAS utilisé?

**Dans le code actuel:**
```kotlin
when (event.actionMasked) {
    ACTION_DOWN, ACTION_MOVE -> {
        retroView.sendMotionEvent(POINTER, x, y, port)  // Position envoyée
        // ← Pas de trigger envoyé!
    }
    ACTION_UP -> {
        Log.d(TAG, "Touch UP - POINTER released")
        // ← Pas de trigger envoyé non plus!
    }
}
```

**Le code envoie SEULEMENT la position**, jamais un signal de trigger explicite!

**Selon la logique FCEUmm (d'après nos recherches):**
```c
// Dans FCEUmm RetroPointer mode:
POINTER_PRESSED = (input_state(POINTER, X) >= 0 && input_state(POINTER, Y) >= 0)

// Si POINTER_PRESSED détecté → Trigger automatique
```

**MAIS:** Cette logique automatique ne fonctionnait PAS dans vos tests!

---

## 🔧 OPTIONS POUR BRANCHER LES PARAMÈTRES

### Option A: Implémenter triggerOnTouch correctement

```kotlin
when (event.actionMasked) {
    ACTION_DOWN -> {
        retroView.sendMotionEvent(POINTER, x, y, port)  // Position
        
        if (triggerOnTouch) {
            // Envoyer trigger IMMÉDIATEMENT
            // MAIS COMMENT? sendKeyEvent? Quel keycode?
            // C'est là le VRAI problème qu'on avait!
        }
    }
    ACTION_UP -> {
        if (!triggerOnTouch) {
            // Envoyer trigger au RELEASE
        }
    }
}
```

**Problème:** On ne sait toujours pas **COMMENT envoyer le trigger** au core!

---

### Option B: Retirer les options non branchées du menu

Si elles ne fonctionnent pas, **autant les cacher** pour éviter confusion!

---

### Option C: Faire une recherche approfondie dans c:\repos

Chercher EXACTEMENT comment RetroArch envoie le trigger du Zapper en mode touchscreen.

---

## 🎯 RÉPONSE À VOTRE QUESTION

**"Si les options de ce menu ne sont pas branchées/fonctionnelles?"**

→ **EXACTEMENT!** Vous avez identifié que:
- ✅ `allowOffscreen` est branchée (fonctionne)
- ❌ `triggerOnTouch` n'est PAS branchée (juste un log)
- ❌ Les 11 autres options lightgun/mouse ne sont PAS branchées non plus

**Donc NON, ce n'est PAS le problème** - car elles ne font rien du tout! 😅

Le vrai problème du Zapper est ailleurs (comment envoyer le trigger au core).

---

**Voulez-vous qu'on:**
1. **Cherche dans c:\repos** comment RetroArch envoie le trigger en mode touchscreen?
2. **Retire les options non branchées** du menu (cleanup)?
3. **Laisse tomber le Zapper** et continue avec autre chose?
