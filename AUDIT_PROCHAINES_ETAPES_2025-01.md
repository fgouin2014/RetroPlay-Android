# Audit - Prochaines Étapes RetroPlay

**Date:** 2025-01-XX  
**Contexte:** Après complétion de la suppression de jeux du gamelist  
**Status Global:** ~68% complété (26/38 items)

---

## 📊 RÉSUMÉ EXÉCUTIF

### ✅ Complétions Récentes
- ✅ **Suppression de jeux du gamelist** - Bouton DELETE dans Edit Game Metadata
- ✅ **Auto-hide QuickActionsBar** - Chevron pour afficher/masquer
- ✅ **Zapper Native Mode** - Intégration complète avec correction viewport
- ✅ **EmulationSettingsDialog** - Dialog complet 70+ options
- ✅ **Overlays RetroArch** - 7/8 phases complètes (Phase 8: Sync ChatAI en attente)

### ⏳ Tâches Restantes
- **P3 (Priorité Basse):** 1 item
- **P4 (Long Terme):** 3 items
- **Phase 8 Overlays:** Sync ChatAI
- **Améliorations UX:** Plusieurs options

---

## 🎯 TÂCHES PAR PRIORITÉ

### P3 - Priorité Basse (1 restante)

#### 1. Support souris relative (Android Oreo+)
- **Complexité:** Moyenne-Élevée
- **Impact:** Moyen (jeux nécessitant mouvement relatif)
- **Status:** ⏳ Pending
- **Note:** Coordonnées absolues fonctionnent pour la plupart des cas
- **Fichiers à modifier:**
  - `libretrodroid/src/main/cpp/input.cpp`
  - `libretrodroid/src/main/cpp/input.h`
- **Référence:** RetroArch `android_input.c` - Détection `AINPUT_SOURCE_MOUSE_RELATIVE` et calcul deltas
- **Temps estimé:** 2-4 heures

---

### P4 - Long Terme (3 restantes)

#### 2. Custom Overlays Creator
- **Complexité:** Très élevée (2-3 semaines)
- **Impact:** Moyen (outil création overlays)
- **Status:** ⏳ Pending
- **Description:**
  - UI complète pour créer ses propres overlays RetroArch
  - Drag & drop boutons, édition propriétés, preview temps réel
  - Export/import .cfg compatible RetroArch
  - Partage communautaire (optionnel)
- **Fichiers à créer:**
  - `OverlayCreatorActivity.kt` - Activité principale
  - `OverlayEditorComposable.kt` - Éditeur Compose
  - `OverlayExportManager.kt` - Export/Import
- **Temps estimé:** 2-3 semaines

#### 3. RetroAchievements Integration
- **Complexité:** Très élevée (2-3 semaines)
- **Impact:** Élevé (features sociales)
- **Status:** ⏳ Pending
- **Description:**
  - Authentification RetroAchievements
  - Détection automatique des succès
  - Affichage des succès débloqués
  - Leaderboards
  - Rich Presence (statut de jeu)
  - Synchronisation cloud
- **Fichiers à créer:**
  - `RetroAchievementsManager.kt` - Gestion principale
  - `RetroAchievementsAPI.kt` - API client
  - `AchievementsDialog.kt` - UI affichage succès
- **Temps estimé:** 2-3 semaines

#### 4. Support Run-Ahead
- **Complexité:** Élevée (1-2 semaines)
- **Impact:** Moyen
- **Status:** ⏳ Pending (quand APIs disponibles)
- **Description:**
  - Support du Run-Ahead pour réduire la latence d'input
  - Détection support Run-Ahead par core
  - Configuration frames Run-Ahead (1-8)
  - Activation/désactivation
  - Gestion mémoire
- **Note:** Nécessite que LibretroDroid expose les APIs Run-Ahead
- **Fichiers à modifier:**
  - `RetroArchEmulatorActivity.kt` - Configuration Run-Ahead (TODO lignes 307, 1917)
  - `CoreVariableManager.kt` - Variables Run-Ahead
- **Temps estimé:** 1-2 semaines (quand APIs disponibles)

---

## 🔄 PHASE 8 - SYNC CHATAI (Overlays)

### Status: ⏳ En attente

#### Tâches
1. **Port vers ChatAI-Android**
   - Copier code overlay depuis RetroPlay
   - Adapter chemins et intégration
   - Tests complets

2. **Tests ChatAI**
   - Validation toutes consoles
   - Tests performance
   - Validation UX

3. **Validation finale**
   - Tests utilisateur
   - Documentation mise à jour

**Temps estimé:** 1 semaine

---

## 🎨 AMÉLIORATIONS UX (Optionnelles)

### Overlays RetroArch

1. **Configuration per-orientation**
   - Settings avancés séparés landscape/portrait
   - Actuellement: settings globaux
   - **Fichier:** `OverlayModels.kt` (TODO ligne 528)
   - **Temps estimé:** 2-4 heures

2. **Preview Overlay**
   - Aperçu avant sélection
   - Visualisation hitboxes en mode preview
   - **Temps estimé:** 1-2 jours

3. **Éditeur de Layout**
   - Ajuster positions boutons
   - Sauvegarder layouts custom
   - **Temps estimé:** 1 semaine

4. **Support Overlays Animés**
   - `arcade-anim/` (animations)
   - Frames multiples
   - **Temps estimé:** 2-3 jours

5. **Import Custom**
   - Depuis URL
   - Depuis fichier local
   - Validation format
   - **Temps estimé:** 2-3 jours

6. **Synchronisation Cloud**
   - Préférences overlay
   - Layouts custom
   - **Temps estimé:** 1 semaine

### Autres Améliorations

1. **RetroArchSettingsDialog**
   - Dialog spécifique RetroArch pour configuration overlays
   - **Fichier:** `RetroArchEmulatorActivity.kt` (TODO ligne 4466)
   - **Temps estimé:** 2-3 jours

2. **N64 Extensions Configuration**
   - Configurer les extensions contrôleur N64
   - **Fichier:** `NativeComposeEmulatorActivity.kt` (TODO ligne 1148)
   - **Temps estimé:** 1-2 jours

3. **Intégration Autoconfig**
   - Intégrer avec input.cpp pour appliquer les mappings
   - **Fichier:** `AutoconfigManager.kt` (TODO lignes 454, 464)
   - **Temps estimé:** 2-3 jours

---

## 📋 TODOS DANS LE CODE

### RetroArchEmulatorActivity.kt
- **Ligne 313:** `// TODO: Apply config to emulator once APIs are available`
- **Ligne 2041:** `// TODO: Apply config to running emulator (requires Run-Ahead/Rewind APIs)`
- **Ligne 4466:** `// TODO: Create RetroArchSettingsDialog for RetroArch-specific overlay configuration`

### NativeComposeEmulatorActivity.kt
- **Ligne 1148:** `// TODO: Configurer les extensions contrôleur N64`

### OverlayModels.kt
- **Ligne 528:** `// TODO P3: Configuration per-orientation - Les settings avancés (dpadDiagonalSensitivity, opacity, etc.)`

### AutoconfigManager.kt
- **Ligne 454:** `// TODO: Intégrer avec input.cpp pour appliquer les mappings`
- **Ligne 464:** `// TODO: Intégrer avec input.cpp pour appliquer les mappings`

---

## 🎯 RECOMMANDATIONS PAR PRIORITÉ

### Immédiat (Court Terme - 1-2 semaines)

1. **Phase 8: Sync ChatAI** ⭐
   - Port overlays vers ChatAI-Android
   - Tests et validation
   - **Impact:** Élevé (complétion feature majeure)

2. **Configuration per-orientation overlays**
   - Compléter implémentation partielle
   - **Impact:** Moyen (améliore UX)

3. **Support souris relative** (si nécessaire)
   - Implémenter si jeux spécifiques le nécessitent
   - **Impact:** Moyen (jeux nécessitant mouvement relatif)

### Moyen Terme (1-2 mois)

4. **RetroArchSettingsDialog**
   - Dialog spécifique RetroArch
   - **Impact:** Moyen (améliore configuration)

5. **Preview Overlay**
   - Aperçu avant sélection
   - **Impact:** Moyen (améliore UX)

6. **N64 Extensions Configuration**
   - Configurer extensions contrôleur
   - **Impact:** Moyen (support N64 complet)

### Long Terme (Selon besoins)

7. **Custom Overlays Creator**
   - Outil création overlays
   - **Impact:** Moyen (outil création)

8. **RetroAchievements Integration**
   - Features sociales
   - **Impact:** Élevé (features sociales)

9. **Support Run-Ahead**
   - Quand APIs LibretroDroid disponibles
   - **Impact:** Moyen (réduction latence)

---

## 📊 STATISTIQUES

### Complétion Globale
- **Total identifié:** ~38 items
- **Complétés:** 26 items (~68%)
- **Restants P3:** 1 item (~3%)
- **Restants P4:** 3 items (~8%)
- **Simplifications acceptables:** ~8 items (~21%)

### Par Catégorie
- **P0/P1 (Critique):** 12/12 complétés (100%)
- **P2 (Important):** 5/5 complétés (100%)
- **P3 (Basse):** 5/6 complétés (~83%)
- **P4 (Long Terme):** 1/5 complétés (20%)

### Overlays RetroArch
- **Phases complétées:** 7/8 (87.5%)
- **Status:** Production Ready
- **Phase 8:** En attente (Sync ChatAI)

---

## 🔄 PROCHAINES ÉTAPES RECOMMANDÉES

### Option 1: Compléter Overlays (Recommandé)
1. Phase 8: Sync ChatAI (1 semaine)
2. Configuration per-orientation (2-4 heures)
3. Preview Overlay (1-2 jours)

### Option 2: Améliorations UX
1. RetroArchSettingsDialog (2-3 jours)
2. N64 Extensions Configuration (1-2 jours)
3. Intégration Autoconfig (2-3 jours)

### Option 3: Features Avancées
1. Custom Overlays Creator (2-3 semaines)
2. RetroAchievements Integration (2-3 semaines)
3. Support Run-Ahead (1-2 semaines, quand APIs disponibles)

### Option 4: Tests et Validation
1. Tests EmulationSettingsDialog avec différents cores
2. Validation N64 Extensions avec vrais jeux
3. Tests d'intégration complets

---

## 💬 DISCUSSION

### Questions à considérer

1. **Phase 8 Overlays:**
   - Quand souhaitez-vous faire le port vers ChatAI?
   - Y a-t-il des différences d'architecture à considérer?

2. **Support souris relative:**
   - Y a-t-il des jeux spécifiques qui nécessitent ce support?
   - Ou peut-on le reporter?

3. **Custom Overlays Creator:**
   - Est-ce une priorité pour les utilisateurs?
   - Ou peut-on le reporter?

4. **RetroAchievements:**
   - Intérêt pour les features sociales?
   - Priorité élevée ou peut attendre?

5. **Run-Ahead:**
   - Les APIs LibretroDroid sont-elles disponibles?
   - Ou attendre leur disponibilité?

6. **Améliorations UX:**
   - Quelles améliorations sont les plus importantes pour vous?
   - Configuration per-orientation vs Preview vs Éditeur?

---

## 📝 NOTES

- La majorité des tâches critiques (P0/P1/P2) sont complétées
- Les overlays RetroArch sont **Production Ready** (7/8 phases)
- Les tâches restantes sont principalement des optimisations (P3) et des features avancées (P4)
- Plusieurs améliorations UX sont optionnelles et peuvent être développées selon les priorités

---

**Dernière mise à jour:** 2025-01-XX

