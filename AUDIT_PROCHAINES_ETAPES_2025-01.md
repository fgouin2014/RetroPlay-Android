# Audit - Prochaines Étapes RetroPlay

**Date:** 2025-01-XX  
**Contexte:** Après complétion de la suppression de jeux du gamelist  
**Status Global:** ~68% complété (26/38 items)

---

## 📊 RÉSUMÉ EXÉCUTIF

### ✅ Complétions Récentes
- ✅ **Suppression de jeux du gamelist** - Bouton DELETE dans Edit Game Metadata avec rafraîchissement automatique
- ✅ **Auto-hide QuickActionsBar** - Chevron pour afficher/masquer (Native + RetroArch)
- ✅ **Zapper Native Mode** - Intégration complète avec correction viewport et support Famicom/Chiller
- ✅ **EmulationSettingsDialog** - Dialog complet 70+ options avec catégorisation
- ✅ **Overlays RetroArch** - 7/8 phases complètes (Phase 8: Sync ChatAI en attente)

### ⏳ Tâches Restantes
- **P3 (Priorité Basse):** 1 item
- **P4 (Long Terme):** 3 items
- **Phase 8 Overlays:** Sync ChatAI
- **Améliorations UX:** Plusieurs options
- **N64 Configuration:** Corrections nécessaires

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
- **Complexité:** Élevée (30-40h pour fork, 15-20h pour "Best Effort")
- **Impact:** Moyen-Haut (réduction latence input)
- **Status:** ⏳ Pending (APIs manquantes dans LibretroDroid)
- **Description:**
  - Support du Run-Ahead pour réduire la latence d'input
  - Détection support Run-Ahead par core
  - Configuration frames Run-Ahead (1-8)
  - Activation/désactivation
  - Gestion mémoire
- **APIs Manquantes dans LibretroDroid:**
  - ❌ Hook pre-frame (intercepter avant `LibretroDroid.step()`)
  - ❌ Disable audio/video (suspendre pendant ahead frames)
  - ❌ Contrôle du game loop timing
- **Options Disponibles:**
  - **A. Fork LibretroDroid** (30-40h, 100% bénéfice, maintenance élevée)
  - **B. Implémentation "Best Effort"** (15-20h, 50-70% bénéfice, timing imparfait)
  - **C. PR Upstream** (40-60h, 100% bénéfice, bénéfice communauté)
- **Fichiers à modifier:**
  - `RetroArchEmulatorActivity.kt` - Configuration Run-Ahead (TODO lignes 315, 2502)
  - `CoreVariableManager.kt` - Variables Run-Ahead
  - `libretrodroid/` - Si fork (ajout hooks)
- **Temps estimé:** 
  - Fork: 30-40h initial + maintenance
  - Best Effort: 15-20h
  - PR Upstream: 40-60h

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

## 🔧 CORRECTIONS N64 (Priorité Moyenne)

### Problèmes Identifiés

1. **Incohérence SharedPreferences**
   - `ConsoleConfigActivity` utilise `console_config`
   - Activités Compose utilisent `PreferenceManager.getDefaultSharedPreferences()`
   - **Impact:** Settings N64 non synchronisés

2. **Mapping valeurs spinner incorrect**
   - Positions spinner ne correspondent pas aux IDs Libretro
   - **Impact:** Extensions N64 mal configurées

3. **Application extensions N64 non fonctionnelle**
   - Valeurs sauvegardées mais pas appliquées
   - **Impact:** Controller Pak/Rumble Pak/Transfer Pak ne fonctionnent pas

4. **Options core N64 non appliquées**
   - Resolution, Anti-Aliasing, Bilinear non appliquées
   - **Impact:** Configuration N64 incomplète

**Fichiers concernés:**
- `ConsoleConfigActivity.java` (lignes 313-317, 391-558)
- `NativeComposeEmulatorActivity.kt` (TODO ligne 1148)
- `activity_console_config.xml`

**Temps estimé:** 1-2 jours

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
   - **Fichier:** `RetroArchEmulatorActivity.kt` (TODO ligne 5033)
   - **Temps estimé:** 2-3 jours

2. **N64 Extensions Configuration** ⚠️ (Correction nécessaire)
   - Configurer les extensions contrôleur N64
   - **Fichier:** `NativeComposeEmulatorActivity.kt` (TODO ligne 1148)
   - **Note:** Fait partie des corrections N64 ci-dessus
   - **Temps estimé:** Inclus dans corrections N64

3. **Intégration Autoconfig**
   - Intégrer avec input.cpp pour appliquer les mappings
   - **Fichier:** `AutoconfigManager.kt` (TODO lignes 454, 464)
   - **Temps estimé:** 2-3 jours

---

## 📋 TODOS DANS LE CODE

### RetroArchEmulatorActivity.kt
- **Ligne 315:** `// TODO: Apply config to emulator once APIs are available`
- **Ligne 2502:** `// TODO: Apply config to running emulator (requires Run-Ahead/Rewind APIs)`
- **Ligne 5033:** `// TODO: Create RetroArchSettingsDialog for RetroArch-specific overlay configuration`

### NativeComposeEmulatorActivity.kt
- **Ligne 1148:** `// TODO: Configurer les extensions contrôleur N64` ⚠️ (Correction nécessaire)

### OverlayModels.kt
- **Ligne 528:** `// TODO P3: Configuration per-orientation - Les settings avancés (dpadDiagonalSensitivity, opacity, etc.)`

### AutoconfigManager.kt
- **Ligne 454:** `// TODO: Intégrer avec input.cpp pour appliquer les mappings`
- **Ligne 464:** `// TODO: Intégrer avec input.cpp pour appliquer les mappings`

### ConsoleConfigActivity.java
- **Lignes 313-317:** Chargement settings N64 (incohérence SharedPreferences)
- **Lignes 391-558:** Mapping valeurs spinner (incorrect)

---

## 🎯 RECOMMANDATIONS PAR PRIORITÉ

### Immédiat (Court Terme - 1-2 semaines)

1. **Corrections N64** ⭐ (Priorité)
   - Fix incohérence SharedPreferences
   - Fix mapping valeurs spinner
   - Fix application extensions N64
   - Fix options core N64
   - **Impact:** Élevé (support N64 complet)

2. **Phase 8: Sync ChatAI** ⭐
   - Port overlays vers ChatAI-Android
   - Tests et validation
   - **Impact:** Élevé (complétion feature majeure)

3. **Configuration per-orientation overlays**
   - Compléter implémentation partielle
   - **Impact:** Moyen (améliore UX)

### Moyen Terme (1-2 mois)

4. **RetroArchSettingsDialog**
   - Dialog spécifique RetroArch
   - **Impact:** Moyen (améliore configuration)

5. **Preview Overlay**
   - Aperçu avant sélection
   - **Impact:** Moyen (améliore UX)

6. **Intégration Autoconfig**
   - Intégrer avec input.cpp
   - **Impact:** Moyen (améliore support contrôleurs)

7. **Support souris relative** (si nécessaire)
   - Implémenter si jeux spécifiques le nécessitent
   - **Impact:** Moyen (jeux nécessitant mouvement relatif)

### Long Terme (Selon besoins)

8. **Custom Overlays Creator**
   - Outil création overlays
   - **Impact:** Moyen (outil création)

9. **RetroAchievements Integration**
   - Features sociales
   - **Impact:** Élevé (features sociales)

10. **Support Run-Ahead**
    - **Option A:** Fork LibretroDroid (30-40h, 100% bénéfice)
    - **Option B:** Implémentation "Best Effort" (15-20h, 50-70% bénéfice)
    - **Option C:** PR Upstream (40-60h, 100% bénéfice + communauté)
    - **Impact:** Moyen-Haut (réduction latence)

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

### Corrections Nécessaires
- **N64 Configuration:** 4 problèmes identifiés
- **Priorité:** Moyenne-Élevée
- **Temps estimé:** 1-2 jours

---

## 🔄 PROCHAINES ÉTAPES RECOMMANDÉES

### Option 1: Corrections + Complétion Overlays (Recommandé)
1. Corrections N64 (1-2 jours) ⭐
2. Phase 8: Sync ChatAI (1 semaine)
3. Configuration per-orientation (2-4 heures)
4. Preview Overlay (1-2 jours)

### Option 2: Améliorations UX
1. RetroArchSettingsDialog (2-3 jours)
2. Intégration Autoconfig (2-3 jours)
3. Support souris relative (2-4 heures, si nécessaire)

### Option 3: Features Avancées
1. Custom Overlays Creator (2-3 semaines)
2. RetroAchievements Integration (2-3 semaines)
3. Support Run-Ahead (selon option choisie)

### Option 4: Tests et Validation
1. Tests EmulationSettingsDialog avec différents cores
2. Validation N64 Extensions avec vrais jeux
3. Tests d'intégration complets

---

## 💬 DISCUSSION

### Questions à considérer

1. **Corrections N64:**
   - Priorité immédiate ou peut attendre?
   - Impact sur utilisateurs N64?

2. **Phase 8 Overlays:**
   - Quand souhaitez-vous faire le port vers ChatAI?
   - Y a-t-il des différences d'architecture à considérer?

3. **Support souris relative:**
   - Y a-t-il des jeux spécifiques qui nécessitent ce support?
   - Ou peut-on le reporter?

4. **Custom Overlays Creator:**
   - Est-ce une priorité pour les utilisateurs?
   - Ou peut-on le reporter?

5. **RetroAchievements:**
   - Intérêt pour les features sociales?
   - Priorité élevée ou peut attendre?

6. **Run-Ahead:**
   - Quelle option préférez-vous?
     - **A. Fork LibretroDroid** (effort élevé, résultat parfait)
     - **B. Best Effort** (effort moyen, résultat partiel)
     - **C. PR Upstream** (effort très élevé, bénéfice communauté)
   - Priorité immédiate ou peut attendre?

7. **Améliorations UX:**
   - Quelles améliorations sont les plus importantes pour vous?
   - Configuration per-orientation vs Preview vs Éditeur?

---

## 📝 NOTES

- La majorité des tâches critiques (P0/P1/P2) sont complétées
- Les overlays RetroArch sont **Production Ready** (7/8 phases)
- Les tâches restantes sont principalement des optimisations (P3) et des features avancées (P4)
- **Corrections N64 identifiées** - Priorité recommandée
- Plusieurs améliorations UX sont optionnelles et peuvent être développées selon les priorités
- **Run-Ahead nécessite décision** sur l'approche (Fork vs Best Effort vs PR)

---

## 🔗 RÉFÉRENCES

### Documents Clés
- `RUNAHEAD_RESEARCH_COMPLETE.md` - Analyse complète Run-Ahead
- `AUDIT_CONSOLE_CONFIG_N64.md` - Audit configuration N64
- `ETAT_ACTUEL_OVERLAYS_RETROARCH.md` - Status overlays
- `TACHES_RESTANTES_RESUME.md` - Résumé tâches restantes

### Code Sources
- `c:\repos\RetroArch-master\` - Source de vérité RetroArch
- `c:\repos\common-overlays-master\` - Overlays officiels
- `libretrodroid/` - APIs LibretroDroid (limitations identifiées)

---

**Dernière mise à jour:** 2025-01-XX
