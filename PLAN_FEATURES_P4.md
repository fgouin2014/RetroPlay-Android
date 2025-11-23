# Plan Features P4 - Long Terme

**Date:** 2025-01-XX  
**Status:** Planification pour développement futur

---

## Features P4 Restantes

### 1. EmulationSettingsDialog

**Complexité:** Élevée  
**Impact:** Élevé  
**Estimation:** 1-2 semaines

**Description:**
UI complète pour configurer les 70+ options d'émulation RetroArch identifiées dans l'audit.

**Fonctionnalités requises:**
- Dialog Compose avec scroll
- Groupement par catégories (Video, Audio, Input, Core-specific, etc.)
- Support de tous les types d'options (bool, int, float, string, enum)
- Validation des valeurs
- Sauvegarde dans SharedPreferences
- Application via CoreVariableManager

**Fichiers à créer:**
- `EmulationSettingsDialog.kt` - Dialog principal
- `EmulationSettingsCategory.kt` - Catégories d'options
- `EmulationOptionItem.kt` - Item d'option individuel

**Références:**
- `CORE_OPTIONS_EMULATION_COMPLETE_ANALYSIS.md` - Liste complète des options
- `CoreVariableManager.kt` - Gestion des variables core

---

### 2. Custom Overlays Creator

**Complexité:** Très élevée  
**Impact:** Moyen  
**Estimation:** 2-3 semaines

**Description:**
Outil complet pour créer ses propres overlays RetroArch avec export/import et partage communautaire.

**Fonctionnalités requises:**
- UI de création d'overlay (drag & drop boutons)
- Édition des propriétés (position, taille, action, image)
- Preview en temps réel
- Export .cfg compatible RetroArch
- Import .cfg existants
- Partage communautaire (optionnel)

**Fichiers à créer:**
- `OverlayCreatorActivity.kt` - Activité principale
- `OverlayEditorComposable.kt` - Éditeur Compose
- `OverlayExportManager.kt` - Export/Import

**Références:**
- `RetroArchOverlayParser.kt` - Format .cfg
- `RetroArchOverlayRenderer.kt` - Rendu preview

---

### 3. RetroAchievements Integration

**Complexité:** Très élevée  
**Impact:** Élevé  
**Estimation:** 2-3 semaines

**Description:**
Intégration complète de RetroAchievements (succès rétro, leaderboards, rich presence).

**Fonctionnalités requises:**
- Authentification RetroAchievements
- Détection automatique des succès
- Affichage des succès débloqués
- Leaderboards
- Rich Presence (statut de jeu)
- Synchronisation cloud

**Fichiers à créer:**
- `RetroAchievementsManager.kt` - Gestion principale
- `RetroAchievementsAPI.kt` - API client
- `AchievementsDialog.kt` - UI affichage succès

**Références:**
- RetroAchievements API documentation
- Libretro achievements support

---

### 4. Support Run-Ahead

**Complexité:** Élevée (dépend APIs)  
**Impact:** Moyen  
**Estimation:** 1-2 semaines (quand APIs disponibles)

**Description:**
Support du Run-Ahead pour réduire la latence d'input.

**Fonctionnalités requises:**
- Détection support Run-Ahead par core
- Configuration frames Run-Ahead (1-8)
- Activation/désactivation
- Gestion mémoire

**Fichiers à modifier:**
- `RetroArchEmulatorActivity.kt` - Configuration Run-Ahead
- `CoreVariableManager.kt` - Variables Run-Ahead

**Note:** Nécessite que LibretroDroid expose les APIs Run-Ahead.

**Références:**
- TODO lignes 307, 1917 dans RetroArchEmulatorActivity.kt

---

## Priorisation Recommandée

### Phase 1: EmulationSettingsDialog (Priorité Haute)
- Impact utilisateur élevé
- Nécessaire pour configuration complète
- Base pour autres features

### Phase 2: Run-Ahead (Quand APIs disponibles)
- Améliore expérience de jeu
- Relativement simple si APIs disponibles

### Phase 3: RetroAchievements (Priorité Moyenne)
- Features sociales intéressantes
- Nécessite API externe

### Phase 4: Custom Overlays Creator (Priorité Basse)
- Nice-to-have
- Complexe mais utile pour communauté

---

## Notes

Ces features sont toutes des améliorations majeures qui nécessitent un développement significatif. Elles peuvent être développées selon les priorités du projet et les besoins des utilisateurs.

