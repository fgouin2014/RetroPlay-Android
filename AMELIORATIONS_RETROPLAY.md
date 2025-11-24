# Améliorations RetroPlay - Plan d'Action

**Date:** 2025-01-XX  
**Méthodologie:** Nos Rules - Basé sur l'audit exhaustif des sources officielles RetroArch  
**Documents de référence:**
- `AUDIT_CONTROLLEURS_PHYSIQUES.md`
- `AUDIT_OVERLAYS_COMPLET.md`
- `AUDIT_CODE_ANALYSIS.md`

---

## Partie 1 : Gaps fonctionnels identifiés

### 1.1 Gaps majeurs (priorité haute)

#### Gap #1 : Système d'autoconfiguration non implémenté

**Description:**
- RetroPlay n'a pas de système d'autoconfiguration RetroArch
- Les gamepads physiques nécessitent une configuration manuelle
- 209 fichiers .cfg Android disponibles dans `c:\repos\retroarch-joypad-autoconfig-master\android\` non utilisés

**Impact:**
- Configuration manuelle requise pour chaque gamepad
- Pas de détection automatique des gamepads connectés
- Expérience utilisateur dégradée (vs RetroArch officiel)

**Solution proposée:**
1. Intégrer le système d'autoconfig RetroArch dans RetroPlay
2. Implémenter la détection automatique des gamepads via `InputDevice.getDeviceIds()`
3. Charger les fichiers .cfg correspondants (VID/PID/Device Index)
4. Appliquer automatiquement la configuration si disponible
5. Fallback vers mapping par défaut si autoconfig non trouvé

**Estimation:** 2-3 jours de développement

**Fichiers à modifier:**
- Créer `RetroPlay-Android/app/src/main/java/com/retroplay/input/AutoconfigManager.kt`
- Modifier `RetroArchEmulatorActivity.kt` pour détecter/charger autoconfig
- Ajouter assets autoconfig dans `app/src/main/assets/autoconfig/`

---

#### Gap #2 : Hacks spéciaux devices non implémentés

**Description:**
- RetroArch Android contient des hacks spéciaux pour devices connus (Shield, Xperia Play, GPD XD, etc.)
- Ces devices nécessitent une configuration spéciale (groupement de plusieurs HID devices, etc.)
- RetroPlay traite tous les devices de manière générique

**Impact:**
- Devices spéciaux (Shield, Xperia Play) ne fonctionnent pas correctement
- Configuration manuelle requise pour ces devices
- Expérience utilisateur dégradée pour ces devices

**Solution proposée:**
1. Implémenter les hacks spéciaux RetroArch pour devices connus
2. Détection automatique du device model (`Build.MODEL`)
3. Groupement de plusieurs HID devices si nécessaire (ex: Xperia Play)
4. Mapping spécial pour devices avec boutons supplémentaires (ex: Shield Guide button)

**Estimation:** 1-2 jours de développement

**Fichiers à modifier:**
- Créer `RetroPlay-Android/app/src/main/java/com/retroplay/input/DeviceHacksManager.kt`
- Modifier `RetroArchEmulatorActivity.kt` pour appliquer hacks

**Devices prioritaires:**
- NVIDIA Shield (TV, Portable, Gamepad)
- Xperia Play
- GPD XD
- Archos Gamepad

---

#### Gap #3 : N64 Extensions non implémentées

**Description:**
- Les extensions N64 (Controller Pak, Rumble Pak, Transfer Pak) sont loggées mais jamais configurées dans le core
- Les valeurs sont sauvegardées dans les préférences mais aucune méthode n'est appelée

**Impact:**
- Les jeux N64 nécessitant des extensions ne fonctionnent pas correctement
- Configuration des extensions non fonctionnelle

**Solution proposée:**
1. Investiguer l'API Libretro pour configurer les extensions N64
2. Utiliser `retro_set_controller_port_device()` avec les IDs d'extensions ou core options spécifiques
3. Appliquer la configuration après chargement du core

**Estimation:** 1 jour de développement + investigation

**Fichiers à modifier:**
- `RetroArchEmulatorActivity.kt` (lignes 1406-1442) - Compléter implémentation TODO

---

### 1.2 Gaps mineurs (priorité moyenne)

#### Gap #4 : Boutons lightgun (gun_*) non supportés

**Description:**
- Les actions `gun_trigger`, `gun_reload`, `gun_aux_a/b` ne sont pas reconnues par le parser RetroPlay
- Les overlays lightgun officiels RetroArch ne fonctionnent pas complètement

**Impact:**
- Overlays lightgun officiels non fonctionnels
- Support lightgun limité aux overlays custom

**Solution proposée:**
1. Ajouter support des bindings lightgun dans `RetroArchOverlayParser.kt`
2. Mapper `gun_trigger`, `gun_reload`, `gun_aux_a/b` vers actions RetroArch correspondantes

**Estimation:** 0.5 jour de développement

**Fichiers à modifier:**
- `RetroArchOverlayParser.kt` - Ajouter parsing actions lightgun
- `RetroArchOverlayRenderer.kt` - Ajouter gestion actions lightgun

---

#### Gap #5 : Support movable buttons non implémenté visuellement

**Description:**
- Le flag `movable = true` est parsé mais l'image ne suit pas le doigt dans RetroPlay
- Comportement attendu: l'image du bouton suit le mouvement du doigt dans sa zone (pour analog sticks)

**Impact:**
- Analog sticks ne bougent pas visuellement (comportement attendu pour meilleure UX)
- Expérience utilisateur dégradée pour overlays avec analog sticks

**Solution proposée:**
1. Implémenter le suivi visuel de l'image quand `movable = true`
2. Calculer offset visuel basé sur position doigt relative au centre
3. Appliquer offset à l'image du bouton

**Estimation:** 1 jour de développement

**Fichiers à modifier:**
- `RetroArchOverlayRenderer.kt` - Ajouter calcul offset visuel pour movable buttons

---

#### Gap #6 : Support clavier complet non implémenté

**Description:**
- RetroPlay gère seulement les gamepad buttons, pas les claviers physiques
- Pas de support modifiers (Alt, Ctrl, Shift) ni keymaps

**Impact:**
- Claviers physiques non supportés (rare sur Android, mais utile pour certains devices)

**Solution proposée:**
1. Ajouter support clavier complet via `input_keymaps_translate_keysym_to_rk()`
2. Gérer modifiers (Alt, Ctrl, Shift)
3. Support keymaps RetroArch

**Estimation:** 1-2 jours de développement

**Priorité:** Basse (rare sur Android)

---

### 1.3 Gaps très mineurs (priorité basse)

#### Gap #7 : Multi-touch natif limité

**Description:**
- RetroPlay supporte 1 pointer max par port (suffisant pour la plupart des cas)
- RetroArch supporte MAX_TOUCH = 64 pointeurs

**Impact:**
- Support multi-touch limité (rarement nécessaire)

**Solution proposée:**
- Améliorer support multi-touch si nécessaire (MAX_TOUCH = 64)

**Estimation:** 1 jour de développement

**Priorité:** Très basse (suffisant actuellement)

---

#### Gap #8 : Conversion viewport simplifiée

**Description:**
- Pas de support souris relative (Oreo+) ni AXIS_RELATIVE (Nougat+)
- Conversion viewport simplifiée (coordonnées normalisées directement)

**Impact:**
- Support souris limité (rare sur Android)

**Solution proposée:**
- Ajouter support souris relative si nécessaire

**Estimation:** 0.5 jour de développement

**Priorité:** Très basse (suffisant actuellement)

---

## Partie 2 : Améliorations potentielles

### 2.1 Améliorations UX (priorité haute)

#### Amélioration #1 : Détection automatique gamepad

**Description:**
- Détecter automatiquement les gamepads connectés via `InputDevice.getDeviceIds()`
- Appliquer l'autoconfig correspondant si disponible
- Fallback vers mapping par défaut si autoconfig non trouvé

**Bénéfice:**
- Expérience utilisateur améliorée (configuration automatique)
- Compatibilité avec 209 gamepads Android officiels RetroArch

**Estimation:** Inclus dans Gap #1 (Système d'autoconfiguration)

---

#### Amélioration #2 : UI configuration gamepad par console

**Description:**
- Interface pour sélectionner le type de contrôleur par console
- Sauvegarde des préférences par console
- Application automatique au chargement du jeu

**Bénéfice:**
- Configuration flexible par console
- Expérience utilisateur améliorée

**Estimation:** 2 jours de développement

**Fichiers à créer:**
- `RetroPlay-Android/app/src/main/java/com/retroplay/settings/ControllerSettingsFragment.kt`

---

#### Amélioration #3 : Support complet des hotkeys RetroArch

**Description:**
- Ajouter tous les hotkeys disponibles (save_state, load_state, fast_forward, rewind, etc.)
- Permettre mapping des hotkeys depuis les overlays
- Documentation complète des hotkeys supportés

**Bénéfice:**
- Fonctionnalités RetroArch complètes
- Expérience utilisateur améliorée

**Estimation:** 2-3 jours de développement

**Fichiers à modifier:**
- `RetroArchEmulatorActivity.kt` - Ajouter gestion hotkeys
- `RetroArchOverlayRenderer.kt` - Ajouter support hotkeys dans overlays

---

### 2.2 Améliorations fonctionnelles (priorité moyenne)

#### Amélioration #4 : Support exclusif hitboxes avancé

**Description:**
- Implémenter `exclusive` et `range_mod_exclusive` correctement
- Tester avec overlays officiels ayant des hitboxes chevauchantes
- Optimiser la détection de priorité

**Bénéfice:**
- Compatibilité 100% avec overlays officiels RetroArch
- Expérience utilisateur améliorée (pas de conflits hitboxes)

**Estimation:** 1 jour de développement

**Fichiers à modifier:**
- `RetroArchOverlayRenderer.kt` - Améliorer gestion hitboxes exclusives

---

#### Amélioration #5 : Support saturate_pct pour analog sticks

**Description:**
- Implémenter la zone de saturation personnalisée
- Permettre configuration depuis l'UI
- Documentation de l'effet sur la sensibilité analogique

**Bénéfice:**
- Contrôle fin de la sensibilité analogique
- Expérience utilisateur améliorée

**Estimation:** 0.5 jour de développement

**Fichiers à modifier:**
- `RetroArchOverlayRenderer.kt` - Implémenter saturate_pct

---

#### Amélioration #6 : Support complet des sous-classes de devices

**Description:**
- Documenter complètement tous les types disponibles par core
- Interface pour changer le type de device en temps réel
- Support des sous-classes (ex: Super Scope, Justifier)

**Bénéfice:**
- Compatibilité avec tous les devices Libretro
- Expérience utilisateur améliorée

**Estimation:** 1 jour de développement

---

## Partie 3 : Roadmap d'implémentation

### Phase 1 : Gaps majeurs (2-4 semaines)

**Sprint 1 : Système d'autoconfiguration (2-3 jours)**
- [ ] Créer `AutoconfigManager.kt`
- [ ] Intégrer détection automatique gamepads
- [ ] Charger fichiers .cfg autoconfig
- [ ] Appliquer configuration automatique
- [ ] Fallback mapping par défaut

**Sprint 2 : Hacks spéciaux devices (1-2 jours)**
- [ ] Créer `DeviceHacksManager.kt`
- [ ] Implémenter hacks Shield
- [ ] Implémenter hacks Xperia Play
- [ ] Implémenter hacks GPD XD
- [ ] Implémenter hacks Archos Gamepad

**Sprint 3 : N64 Extensions (1 jour + investigation)**
- [ ] Investiguer API Libretro extensions N64
- [ ] Implémenter configuration Controller Pak
- [ ] Implémenter configuration Rumble Pak
- [ ] Implémenter configuration Transfer Pak

---

### Phase 2 : Améliorations UX (1-2 semaines)

**Sprint 4 : UI configuration gamepad (2 jours)**
- [ ] Créer `ControllerSettingsFragment.kt`
- [ ] Interface sélection contrôleur par console
- [ ] Sauvegarde préférences par console
- [ ] Application automatique au chargement

**Sprint 5 : Hotkeys RetroArch (2-3 jours)**
- [ ] Ajouter gestion hotkeys dans `RetroArchEmulatorActivity.kt`
- [ ] Support hotkeys dans overlays
- [ ] Documentation hotkeys supportés

---

### Phase 3 : Améliorations fonctionnelles (1 semaine)

**Sprint 6 : Overlays avancés (1-2 jours)**
- [ ] Boutons lightgun (gun_*)
- [ ] Support movable buttons visuellement
- [ ] Support exclusif hitboxes avancé
- [ ] Support saturate_pct analog sticks

---

## Partie 4 : Recommandations

### Priorités recommandées

**Priorité 1 (Immédiat):**
1. ✅ Système d'autoconfiguration (Gap #1)
2. ✅ Détection automatique gamepad (Amélioration #1)
3. ✅ UI configuration gamepad par console (Amélioration #2)

**Priorité 2 (Court terme):**
4. ⚠️ Hacks spéciaux devices (Gap #2)
5. ⚠️ N64 Extensions (Gap #3)
6. ⚠️ Support hotkeys RetroArch (Amélioration #3)

**Priorité 3 (Moyen terme):**
7. 📝 Boutons lightgun (Gap #4)
8. 📝 Support movable buttons visuellement (Gap #5)
9. 📝 Support exclusif hitboxes avancé (Amélioration #4)

**Priorité 4 (Long terme):**
10. 💡 Support clavier complet (Gap #6)
11. 💡 Multi-touch natif amélioré (Gap #7)
12. 💡 Conversion viewport améliorée (Gap #8)

---

## Partie 5 : Métriques de succès

### Objectifs Phase 1

- ✅ **Autoconfiguration:** 100% des gamepads Android officiels RetroArch supportés (209 fichiers .cfg)
- ✅ **Devices spéciaux:** 100% des devices prioritaires supportés (Shield, Xperia Play, GPD XD, Archos)
- ✅ **N64 Extensions:** 100% des extensions N64 fonctionnelles (Controller Pak, Rumble Pak, Transfer Pak)

### Objectifs Phase 2

- ✅ **UX:** Configuration gamepad automatique pour 100% des gamepads connectés
- ✅ **Hotkeys:** 100% des hotkeys RetroArch essentiels supportés (save/load state, fast forward, rewind)

### Objectifs Phase 3

- ✅ **Overlays:** 100% compatibilité avec overlays officiels RetroArch (30+ packages)
- ✅ **Features avancées:** 100% des features overlay avancées fonctionnelles (movable, exclusive, saturate_pct)

---

## Conclusion

Ce document propose un plan d'action complet pour améliorer RetroPlay basé sur l'audit exhaustif "Nos Rules" des sources officielles RetroArch.

**Principales recommandations:**
1. **Priorité 1:** Système d'autoconfiguration (impact majeur sur UX)
2. **Priorité 2:** Hacks spéciaux devices + N64 Extensions (compatibilité)
3. **Priorité 3:** Améliorations overlays avancées (features)

**Estimation totale:** 4-6 semaines de développement pour toutes les améliorations prioritaires.


