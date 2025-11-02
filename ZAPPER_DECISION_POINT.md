# ⚠️ Point de Décision - Zapper Investigation

**Date:** 2025-11-01 04:30  
**Situation:** Tous les fixes appliqués, mais Zapper ne fonctionne toujours pas  
**Décision requise:** Comment procéder?

---

## 📊 ÉTAT ACTUEL

### Fixes Appliqués (8 fixes majeurs)

1. ✅ Device type: `setControllerType(1, 258)` = RETRO_DEVICE_ZAPPER
2. ✅ Core options: `fceumm_zapper_mode = touchscreen`
3. ✅ Bounds capture: `onGloballyPositioned()` avec offset
4. ✅ Touch propagation: Overlay ne bloque plus le Zapper
5. ✅ ACTION_MOVE: Coordonnées se mettent à jour
6. ✅ mousedata[3]: FCEUmm recompilé avec fix
7. ✅ Conversions: Toutes correctes (Écran → [0-1] → int16 → NES coords)
8. ✅ Logs détaillés: Chaque étape tracée

### Résultat

❌ **Les canards ne tombent toujours pas**

---

## 🔍 CONVERSIONS VÉRIFIÉES

```log
Touch 1: Écran (407, 1144) → NES (96, 172) ✅ VALIDE
Touch 2: (451, 946) → NES (107, 152) ✅ VALIDE
Touch 3: (800, 1116) → NES (189, 169) ✅ VALIDE
```

**Toutes les coordonnées sont dans les limites NES [0-256] x [0-240]!**

---

## 🐛 PROBLÈME IDENTIFIÉ

### CheckColor() ne détecte jamais la lumière

**Fonction:** `zapper.c` ligne 107-114

```c
static INLINE int CheckColor(int w) {
    FCEUPPU_LineUpdate();
    
    if ((ZD[w].zaphit + 100) >= (timestampbase + timestamp)
        && !(ZD[w].mzb & 2)) return(0);
    
    return(1);
}
```

**`CheckColor()` dépend de `ZD[w].zaphit`** qui est rempli par `ZapperFrapper()`.

### ZapperFrapper() jamais appelé?

**Fonction:** `zapper.c` ligne 49-105

Appelée **pendant le rendu PPU** (chaque scanline) pour détecter RGB >= 300 à la position (X, Y).

**Problème possible:**
- `ZapperFrapper()` n'est peut-être pas appelé par LibretroDroid?
- Ou Duck Hunt ne flash pas en blanc comme attendu?
- Ou le timing ne coïncide jamais?

---

## 🎯 OPTIONS

### Option A: Test RetroArch Officiel (1h)

**Procédure:**
1. Installer RetroArch depuis Google Play
2. Télécharger core FCEUmm
3. Tester Duck Hunt avec Zapper touchscreen
4. Comparer le résultat

**Si ça marche:**
→ Le problème est LibretroDroid ou notre implémentation  
→ On analyse leur code pour trouver la différence

**Si ça ne marche pas:**
→ Le problème est FCEUmm ou Duck Hunt  
→ On documente et on passe à autre chose

### Option B: Créer RetroArchNativeActivity (8-12h)

**Procédure:**
1. Intégrer le code natif RetroArch dans RetroPlay
2. Créer une nouvelle activité basée sur leur architecture C/C++
3. Utiliser leur driver `android_input.c` au lieu de LibretroDroid
4. Tester le Zapper

**Avantages:**
- Code officiel RetroArch (prouvé)
- Support complet de tous les devices (Zapper, Mouse, etc.)
- Pas de dépendance LibretroDroid

**Inconvénients:**
- Réécriture majeure
- Architecture C/C++ complexe
- Perte potentielle de features Kotlin (overlays, Quick Wins)

### Option C: Forker LibretroDroid (6-8h)

**Procédure:**
1. Cloner LibretroDroid dans notre projet
2. Ajouter des hooks pour `ZapperFrapper()` callback
3. Modifier le rendering pipeline pour appeler les callbacks
4. Tester

**Avantages:**
- Garde notre architecture Kotlin
- Améliore LibretroDroid pour tout le monde
- On peut contribuer upstream

**Inconvénients:**
- Modification d'une dépendance externe
- Maintenance à long terme
- Complexité C++/JNI

### Option D: Documenter et Abandonner (30min)

**Procédure:**
1. Créer un rapport final complet
2. Marquer le Zapper comme "non supporté"
3. Retourner aux autres priorités (Quick Wins terminés, Run Ahead bloqué, etc.)

**Avantages:**
- On passe à des features fonctionnelles
- Documentation complète pour référence future
- Pas de temps perdu sur un problème potentiellement impossible

**Inconvénients:**
- Le Zapper ne fonctionnera jamais

---

## 📊 TEMPS INVESTI vs RÉSULTAT

| Phase | Temps | Résultat |
|-------|-------|----------|
| Recherche initiale | 2h | 5 bugs identifiés |
| Fixes Kotlin | 2h | Tous appliqués |
| Compilation FCEUmm | 1h | mousedata[3] corrigé |
| Debug conversions | 1h | Toutes validées |
| **TOTAL** | **6h** | **0% fonctionnel** |

---

## 💭 RECOMMANDATION

**Ordre de préférence:**

1. **Option A** (Test RetroArch) → 1h, clarification rapide
2. **Option D** (Documenter) → Si RetroArch ne marche pas non plus
3. **Option B** (Native) ou **Option C** (Fork) → Seulement si RetroArch marche et qu'on identifie une vraie solution

---

## 🎯 DÉCISION

**Quelle option choisissez-vous?** (A/B/C/D)

Ou voulez-vous continuer à investiguer d'une autre manière?

