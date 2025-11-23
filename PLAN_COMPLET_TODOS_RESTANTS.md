# Plan Complet - Todos Restants

**Date:** 2025-01-XX  
**Basé sur:** Audit "Nos Rules" Complet  
**Statut:** Récapitulatif de toutes les tâches identifiées

---

## 📊 Vue d'Ensemble

**Total identifié dans l'audit:** ~56 items (gaps critiques, importants, mineurs + améliorations)  
**Complétés (P0/P1):** 12 items  
**Complétés (Session récente):** 6 items supplémentaires  
**Total complété:** ~18 items  
**Restants:** ~38 items

---

## ✅ Items Complétés (P0/P1)

### P0 - Priorité Critique (6/6 complétés)

1. ✅ **Autoconfig System** - `AutoconfigManager.kt`
2. ✅ **Multi-touch Support** - `input.cpp` (16 pointers)
3. ✅ **Conversion Viewport** - `input.cpp`
4. ✅ **Parsing Buttons "a|b|c"** - `RetroArchOverlayParser.kt`
5. ✅ **Résolution Targets** - `RetroArchOverlayParser.kt`
6. ✅ **Exclusive Hitboxes** - `RetroArchOverlayRenderer.kt`

### P1 - Priorité Haute (6/6 complétés)

7. ✅ **Sensors Support** - `input.cpp` + `environment.cpp`
8. ✅ **Hacks Spéciaux Devices** - `DeviceHacksManager.kt`
9. ✅ **Keyboard Support** - `input.cpp`
10. ✅ **Analog Saturate PCT** - `RetroArchOverlayRenderer.kt`
11. ✅ **Hitbox Disabled** - `RetroArchOverlayRenderer.kt`
12. ✅ **Conversion Normalized vs Pixel** - `RetroArchOverlayParser.kt` (partiellement)

### Session Récente (6 items complétés)

13. ✅ **Boutons Lightgun (gun_*)** - Support 11 actions lightgun
14. ✅ **Support Movable Buttons Visuellement** - Déplacement visuel des boutons
15. ✅ **Amélioration Exclusive Hitboxes** - Zones 8-way intégrées, logs améliorés
16. ✅ **Support Hotkeys dans Overlays** - Navigation cyclique `overlay_next`/`overlay_prev`
17. ✅ **Implémentation `shader_prev`** - Cycle shader précédent
18. ✅ **Amélioration UX Messages** - Message informatif overlay non configuré

---

## ⏳ Items Restants - Par Priorité

### P2 - Priorité Moyenne (Optimisations)

#### Overlay Parsing
19. ⏳ **Auto-détection aspect ratio depuis name**
   - Détecter "portrait" → `0.5625f` (1 / 16:9)
   - Détecter "landscape" → `1.7777778f` (16:9)
   - Fichier: `RetroArchOverlayParser.kt`
   - Référence: RetroArch `task_overlay.c` lignes 1997-1998

20. ⏳ **Validation stricte parsing**
   - Vérifier `list_size >= 6` (tokens) pour overlay_desc
   - Validation des valeurs numériques (x, y, range_x, range_y)
   - Messages d'erreur explicites
   - Fichier: `RetroArchOverlayParser.kt`
   - Référence: RetroArch `task_overlay.c` lignes 368-379

21. ⏳ **Support overlayN_descN_normalized (override global)**
   - Permettre override `normalized` par bouton
   - Fichier: `RetroArchOverlayParser.kt`
   - Référence: RetroArch `task_overlay.c` lignes 368-378

#### Overlay Rendering
22. ⏳ **Multi-touch lightgun (2/3/4 doigts)**
   - Support actions configurables pour 2/3/4 doigts
   - Mapping vers actions lightgun (gun_reload, gun_aux_a/b, etc.)
   - Fichier: `RetroArchEmulatorActivity.kt`
   - Référence: RetroArch `configuration.c` lignes 2652-2654

23. ⏳ **Précalcul diagonales (bitmask)**
   - Stocker mappings 8-way comme bitmask au lieu de String
   - Optimisation performance
   - Fichier: `RetroArchOverlayRenderer.kt`
   - Référence: RetroArch `input_driver.c` lignes 2540-2543

#### Input Handling
24. ⏳ **Support POINTER_IS_OFFSCREEN**
   - Détection touch hors écran
   - Retourner `-0x8000` pour coordonnées offscreen
   - Fichier: `input.cpp`
   - Référence: RetroArch `android_input.c` lignes 1933-2051

25. ⏳ **Support POINTER_COUNT**
   - Retourner nombre de pointers actifs
   - Fichier: `input.cpp`
   - Référence: RetroArch `android_input.c` lignes 1933-2051

26. ⏳ **Support POINTER_BACK**
   - Support bouton retour (back button)
   - Fichier: `input.cpp`
   - Référence: RetroArch `android_input.c` lignes 1933-2051

27. ⏳ **Triggers séparés (L2/R2 axes)**
   - Support L2/R2 comme axes séparés (pas seulement boutons)
   - Fichier: `input.cpp`
   - Référence: RetroArch `android_input.c` lignes 1933-2051

---

### P3 - Priorité Basse (Améliorations Futures)

#### Input Handling
28. ⏳ **Quick tap detection**
   - Détection tap rapide (< 200ms)
   - Condition overlay blocking
   - Fichier: `input.cpp`
   - Référence: RetroArch `android_input.c` lignes 1933-2051

29. ⏳ **Support souris relative (Android Oreo+)**
   - Support souris relative pour jeux nécessitant mouvement relatif
   - Fichier: `input.cpp`
   - Référence: RetroArch `android_input.c` lignes 1933-2051

30. ⏳ **Gestion boutons souris multiples (LMB, RMB, MMB séparés)**
   - Support clic gauche/droit/milieu séparés
   - Fichier: `input.cpp`
   - Référence: RetroArch `android_input.c` lignes 1933-2051

#### Overlay Parsing
31. ⏳ **Support valeurs par défaut 8-way (DPAD_AREA vs ABXY_AREA)**
   - Valeurs par défaut différentes selon type de zone
   - Fichier: `RetroArchOverlayParser.kt`
   - Référence: RetroArch `task_overlay.c` lignes 138-156

#### Overlay Rendering
32. ⏳ **Sauvegarde persistante positions boutons déplaçables**
   - Sauvegarder positions finales après drag
   - Charger positions sauvegardées au démarrage
   - Fichier: `RetroArchOverlayRenderer.kt` + `OverlayPreferenceManager.kt`

33. ⏳ **Configuration per-orientation overlays**
   - Settings séparés pour landscape vs portrait
   - Fichier: `OverlayPreferenceManager.kt`

---

### P4 - Long Terme (Features Avancées)

#### N64 Extensions
34. ⏳ **Validation et tests N64 Extensions**
   - Tester avec Mupen64Plus Next
   - Tester avec ParaLLEl N64
   - Valider IDs corrects pour chaque core
   - Documenter IDs par core
   - Fichier: `RetroArchEmulatorActivity.kt`

#### Run-Ahead / Rewind
35. ⏳ **Support Run-Ahead**
   - Implémenter quand APIs disponibles
   - Fichier: `RetroArchEmulatorActivity.kt`
   - Note: TODO ligne 307, 1917

#### Core Options
36. ⏳ **EmulationSettingsDialog**
   - UI complète pour options émulation RetroArch
   - Support 70+ options identifiées
   - Fichier: Nouveau `EmulationSettingsDialog.kt`
   - Référence: `CORE_OPTIONS_EMULATION_COMPLETE_ANALYSIS.md`

#### Overlay Creator
37. ⏳ **Custom Overlays Creator**
   - UI pour créer ses propres overlays
   - Export .cfg compatible RetroArch
   - Partage communautaire
   - Fichier: Nouveau `OverlayCreatorActivity.kt`

#### RetroAchievements
38. ⏳ **RetroAchievements Integration**
   - Support des succès rétro
   - Leaderboards
   - Rich Presence
   - Fichier: Nouveau `RetroAchievementsManager.kt`

---

## 📋 Items Spécifiques par Catégorie

### Input Handling Restants

**P2:**
- Support POINTER_IS_OFFSCREEN/COUNT/BACK
- Triggers séparés (L2/R2 axes)

**P3:**
- Quick tap detection
- Support souris relative
- Gestion boutons souris multiples

### Overlay Parsing Restants

**P2:**
- Auto-détection aspect ratio depuis name
- Validation stricte parsing
- Support overlayN_descN_normalized (override)

**P3:**
- Support valeurs par défaut 8-way

### Overlay Rendering Restants

**P2:**
- Multi-touch lightgun (2/3/4 doigts)
- Précalcul diagonales (bitmask)

**P3:**
- Sauvegarde persistante positions boutons déplaçables
- Configuration per-orientation overlays

### Features Avancées

**P4:**
- Validation N64 Extensions
- Support Run-Ahead
- EmulationSettingsDialog
- Custom Overlays Creator
- RetroAchievements Integration

---

## 🎯 Recommandations par Priorité

### Immédiat (P2 - Impact Moyen)

1. **Auto-détection aspect ratio** - Simple, améliore compatibilité
2. **Validation stricte parsing** - Améliore robustesse
3. **Multi-touch lightgun** - Améliore UX lightgun

### Court Terme (P2/P3 - Optimisations)

4. **Support POINTER_IS_OFFSCREEN/COUNT/BACK** - Complète support pointer
5. **Triggers séparés** - Support axes L2/R2
6. **Sauvegarde positions boutons déplaçables** - Améliore UX

### Moyen Terme (P3/P4 - Features)

7. **Validation N64 Extensions** - Tests et documentation
8. **EmulationSettingsDialog** - UI complète options émulation
9. **Configuration per-orientation** - Améliore flexibilité

### Long Terme (P4 - Features Avancées)

10. **Custom Overlays Creator** - Outil création overlays
11. **RetroAchievements Integration** - Features sociales
12. **Support Run-Ahead** - Quand APIs disponibles

---

## 📊 Statistiques

**Total items identifiés:** ~56  
**Complétés (P0/P1):** 12  
**Complétés (Session récente):** 6  
**Total complétés:** 18 (~32%)  
**Restants (P2):** 9  
**Restants (P3):** 6  
**Restants (P4):** 5  
**Total restants:** 20 (~36%)  
**Simplifications acceptables:** 6 (~11%)  
**Non applicables/obsolètes:** ~12 (~21%)

---

## 🔄 Prochaines Étapes Recommandées

### Phase 1: P2 Items (1-2 semaines)
1. Auto-détection aspect ratio
2. Validation stricte parsing
3. Multi-touch lightgun (2/3/4 doigts)
4. Support POINTER_IS_OFFSCREEN/COUNT/BACK

### Phase 2: P3 Items (2-3 semaines)
5. Quick tap detection
6. Sauvegarde positions boutons déplaçables
7. Configuration per-orientation overlays
8. Triggers séparés

### Phase 3: P4 Items (Selon besoins)
9. Validation N64 Extensions
10. EmulationSettingsDialog
11. Custom Overlays Creator
12. RetroAchievements Integration

---

**Note:** Ce document liste tous les items identifiés dans l'audit "Nos Rules" complet. Les items P0 et P1 sont tous complétés. Les items restants sont principalement des optimisations (P2) et des features avancées (P3/P4).

