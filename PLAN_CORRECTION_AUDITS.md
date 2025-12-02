# Plan de Correction - Problèmes Identifiés dans les Audits

## PROBLÈMES IDENTIFIÉS

### 1. Mélange de sources dans l'onglet General (RetroArch Settings Dialog)
**Sévérité:** MOYEN  
**Fichier:** `RetroArchSettingsDialog.kt` (onglet 2: General)

**Problème:**
- VSync, Rewind, Run-Ahead: depuis `.cfg` (RetroPlayConfigManager)
- Shader, Aspect Ratio, Audio, Fast Forward: depuis SharedPreferences

**Impact:** Incohérence, difficile à maintenir

**Solution proposée:**
- **Option A:** Tout mettre dans SharedPreferences (plus simple, plus rapide)
- **Option B:** Tout mettre dans `.cfg` (plus RetroArch-like)
- **Option C:** Documenter la séparation (VSync/Rewind/Run-Ahead = globaux RetroPlay, reste = RetroArch)

**Recommandation:** Option A (SharedPreferences) car:
- Déjà utilisé pour la majorité des settings
- Plus rapide à lire/écrire
- Plus simple à gérer
- Cohérent avec le reste de l'app

---

### 2. Gestion ClassCastException incomplète
**Sévérité:** MOYEN (théorique)  
**Fichier:** `OverlayPreferenceManager.kt` (`loadAdvancedSettings()`)

**Problème:**
- Seul `mouseSwipeThreshold` a une gestion `ClassCastException`
- Autres valeurs Float/Int n'ont pas de gestion d'erreur

**Impact:** Crash possible si migration de type (ex: Int → Float)

**Solution:** Ajouter try/catch pour toutes les valeurs Float/Int

---

## PLAN D'IMPLÉMENTATION

### Étape 1: Corriger le mélange de sources (RetroArch Settings Dialog)

**Actions:**
1. Déplacer VSync, Rewind, Run-Ahead de `.cfg` vers SharedPreferences
2. Mettre à jour `RetroArchSettingsDialog.kt` pour lire depuis SharedPreferences
3. Mettre à jour les callbacks pour sauvegarder dans SharedPreferences
4. Ajouter migration automatique (lire depuis `.cfg` si existe, copier vers SharedPreferences, supprimer `.cfg`)

**Fichiers à modifier:**
- `RetroArchSettingsDialog.kt` - Lire depuis SharedPreferences
- `RetroArchEmulatorActivity.kt` - Callbacks de sauvegarde
- `NativeComposeEmulatorActivity.kt` - Callbacks de sauvegarde

---

### Étape 2: Ajouter gestion ClassCastException

**Actions:**
1. Ajouter try/catch dans `getFloat()` helper
2. Ajouter try/catch dans `getInt()` helper
3. Gérer migration Int → Float pour `opacity` et `aspectAdjust`
4. Tester avec valeurs existantes

**Fichiers à modifier:**
- `OverlayPreferenceManager.kt` - Helpers `getFloat()` et `getInt()`

---

## ORDRE DE PRIORITÉ

1. **Étape 1** (Mélange de sources) - Impact plus visible
2. **Étape 2** (ClassCastException) - Impact théorique mais important pour robustesse

---

## TESTS

Après chaque correction:
1. Vérifier que les settings se chargent correctement
2. Vérifier que les settings se sauvegardent correctement
3. Vérifier que les callbacks fonctionnent
4. Tester avec valeurs existantes (migration)

