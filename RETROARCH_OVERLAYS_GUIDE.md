# Guide des Overlays RetroArch - RetroPlay

## Table des matières
1. [Vue d'ensemble](#vue-densemble)
2. [Navigation](#navigation)
3. [Overlays disponibles](#overlays-disponibles)
4. [Configuration par console](#configuration-par-console)
5. [Modes de contrôle](#modes-de-contrôle)
6. [Menu Rapide](#menu-rapide)
7. [Dépannage](#dépannage)

---

## Vue d'ensemble

RetroPlay supporte les overlays officiels RetroArch avec :
- **24 overlays** disponibles (flat-*, retropad, etc.)
- **Rechargement à chaud** (pas besoin de redémarrer le jeu)
- **Modes multiples** : Digital, Analog, Menu pour certaines consoles
- **Rotation automatique** : Basculer entre layouts portrait/landscape
- **Debug mode** : Visualiser les zones de détection (hitboxes)

---

## Navigation

### Menu Principal
**Accès :** Bouton système sur l'overlay OU Bouton **Back** Android

**Options disponibles :**
```
[Menu Rapide]
├── RESUME           → Reprendre le jeu
├── HIDE OVERLAY     → Cacher/afficher les boutons
├── SAVE STATE       → Sauvegarde rapide (Slot 1)
├── LOAD STATE       → Chargement rapide (Slot 1)
├── SETTINGS         → Menu complet
└── QUIT GAME        → Quitter l'émulation
```

### Menu Settings Complet
**Accès :** Menu Rapide → SETTINGS

**Options :**
1. **Save State** (Slots 1-5)
2. **Load State** (Slots 1-5)
3. **GamePad Settings** ← Configuration des overlays
4. **Cheat Codes**

---

## Overlays disponibles

### Overlays universels (toutes consoles)
| Overlay | Layouts | Description |
|---------|---------|-------------|
| `flat-retropad` | 14 layouts, 263 boutons | Overlay universel optimisé, design plat |
| `retropad` | 10 layouts | Overlay RetroArch original |

### Overlays spécifiques par console

#### Consoles 8-bit
| Console | Overlays | Layouts | Notes |
|---------|----------|---------|-------|
| NES | `flat-nes` | 5 layouts, 89 boutons | D-pad + A/B |
| Sega Master System | `flat-sms` | Similar à NES | D-pad + 1/2 |
| Game Boy | `flat-gb`, `flat-gbc` | 5 layouts | D-pad + A/B + Start/Select |

#### Consoles 16-bit
| Console | Overlays | Layouts | Notes |
|---------|----------|---------|-------|
| SNES | `flat-snes` | 5 layouts, 89 boutons | D-pad + A/B/X/Y + L/R |
| Genesis/Mega Drive | `flat-genesis` | Similar à SNES | 6 boutons mode |
| Game Boy Advance | `flat-gba` | 5 layouts | Similar à SNES |

#### Consoles 32-bit+
| Console | Overlays | Layouts | Notes |
|---------|----------|---------|-------|
| **PlayStation** | `flat-psx` | **11 layouts** | **Digital + Analog modes** |
| N64 | `flat-n64` | Complex | Analog stick + C-buttons |
| Dreamcast | `flat-dreamcast` | Similar à PSX | Analog + triggers |

#### Arcade
| System | Overlays | Layouts | Notes |
|--------|----------|---------|-------|
| Arcade | `flat-arcade` | Multiple | 6-button layouts |
| Neo Geo | `flat-neogeo` | 4-button | Arcade style |
| FBNeo | `retropad` | Universal | Compatible avec tous les jeux arcade |

---

## Configuration par console

### Accès aux paramètres
1. Lancer un jeu
2. Appuyer sur **Back** (ou bouton menu de l'overlay)
3. Sélectionner **SETTINGS** → **GamePad Settings**

### Options disponibles

#### 1. Mode Gamepad
```
• Default (Lemuroid)  → Gamepad natif de l'app
• Compact             → Version compacte
• RetroArch           → Overlays RetroArch (recommandé)
```

#### 2. Overlay Package (si RetroArch sélectionné)
```
Exemple pour SNES :
• flat-retropad       → Universel (recommandé)
• retropad            → Universel original
• flat-snes           → Spécifique SNES
```

#### 3. Layouts par orientation

**Pour PSX (flat-psx) :**

**Landscape Layout :**
- `Digital` → D-pad classique (pas d'analog)
- `Left Analog` → Stick analogique gauche actif
- `Right Analog` → Stick analogique droit actif
- `Both Analog` → Les deux sticks actifs
- `B` → Layout alternatif B
- `Left Analog B` → Layout B avec analog gauche

**Portrait Layout :**
- `Digital` → D-pad classique
- `Analog` → Mode analog

**Pour retropad :**

**Landscape Layout :**
- `Digital` → Mode classique
- `Analog` → Stick analogique
- `Menu` → Avec bouton menu visible
- `Analog Menu` → Analog + menu

**Portrait Layout :**
- `Digital` → Mode classique
- `Analog` → Stick analogique
- `Menu` → Avec bouton menu
- `Analog Menu` → Analog + menu

#### 4. Options supplémentaires
- **Auto-switch on rotation** : Basculer automatiquement entre layouts portrait/landscape
- **DEBUG: Show hitboxes** : Afficher les zones de détection (cercles rouges/rectangles bleus)

#### 5. Reset
- **RESET TO DEFAULT** : Retour au mode Lemuroid (gamepad par défaut)
- **DONE** : Sauvegarder et fermer

---

## Modes de contrôle

### Mode Digital (D-pad)
**Idéal pour :**
- Jeux 2D (platformers, RPG, shoot'em up)
- Jeux NES/SNES/Genesis classiques
- Jeux n'utilisant pas d'analog

**Avantages :**
- Précision maximale pour les directions
- Diagonales parfaites pour fighting games
- Moins d'espace écran occupé

### Mode Analog (Stick analogique)
**Idéal pour :**
- Jeux 3D (PSX, N64, Dreamcast)
- FPS, racing games, adventure games
- Jeux nécessitant un contrôle progressif

**Caractéristiques :**
- Contrôle progressif de la vitesse
- Mouvements fluides de caméra
- Essential pour les jeux PSX modernes

### Mode Both Analog (PSX uniquement)
**Idéal pour :**
- Jeux PSX utilisant le DualShock
- FPS avec mouvement + caméra
- Jeux nécessitant les deux sticks

**Exemples :**
- Medal of Honor
- Ape Escape
- Alien Resurrection

---

## Menu Rapide

### Bouton Back Android
**Comportement :**
- **1er appui** : Ouvre Menu Rapide (pause automatique)
- **2ème appui** : Ferme Menu Rapide (resume automatique)
- **Cooldown** : 800ms après fermeture pour éviter réouverture accidentelle

### Options du Menu Rapide

#### RESUME 🟢
- Fermer le menu et reprendre le jeu
- **Raccourci** : Bouton Back

#### HIDE OVERLAY 🔵
- Basculer visibilité des boutons
- Utile pour captures d'écran ou voir le jeu mieux
- Le jeu continue de tourner

#### SAVE STATE 🟠
- Sauvegarde rapide dans le Slot 1
- Le menu se ferme automatiquement après sauvegarde

#### LOAD STATE 🟣
- Chargement rapide depuis le Slot 1
- Le menu se ferme automatiquement après chargement

#### SETTINGS ⚙️
- Ouvre le menu complet
- Accès à GamePad Settings, Cheats, etc.

#### QUIT GAME 🔴
- Quitter l'émulation
- Retour à la liste des jeux

---

## Dépannage

### Problème : Pas de boutons visibles
**Solution :**
1. Vérifier que le mode RetroArch est sélectionné
2. Vérifier qu'un overlay est sélectionné
3. Vérifier que "HIDE OVERLAY" n'est pas activé
4. Utiliser le bouton Back pour ouvrir le Menu Rapide
5. Si toujours bloqué : Advanced Config → "RESET TO NATIVE GAMEPAD"

### Problème : Overlay ne change pas
**Solution :**
- Le rechargement est maintenant instantané (à chaud)
- Si problème persiste : Force Stop l'app et relancer

### Problème : Hitboxes ne correspondent pas
**Solution :**
1. Activer le mode DEBUG dans GamePad Settings
2. Vérifier que les cercles rouges/rectangles bleus correspondent aux boutons
3. Si décalage : Signaler le problème (bug overlay RetroArch)

### Problème : Analog ne fonctionne pas
**Vérifications :**
1. S'assurer que le layout "Analog" est sélectionné
2. Certains jeux PSX nécessitent d'activer l'analog dans leurs options
3. Vérifier dans les options du jeu (ex: "Analog Controller: ON")

### Problème : Doigts cachent l'écran
**Solution :**
- L'écran est automatiquement décalé de 20% vers le haut en mode portrait
- En landscape, les boutons sont sur les côtés pour ne pas gêner

### Problème : Menu Rapide se rouvre tout seul
**Cause :** Geste back répété trop rapidement
**Solution :**
- Attendre 800ms (cooldown) après avoir fermé le menu
- Le système ignore les back répétés pendant ce délai

---

## Conseils d'utilisation

### Pour les jeux 2D classiques
```
Overlay recommandé : flat-retropad
Layout : Digital (landscape ou portrait)
Auto-rotate : ON
```

### Pour les jeux PSX 3D
```
Overlay recommandé : flat-psx
Layout Landscape : Both Analog (ou Left/Right selon le jeu)
Layout Portrait : Analog
Auto-rotate : ON
```

### Pour les jeux Arcade
```
Overlay recommandé : retropad
Layout : Digital
Auto-rotate : OFF (toujours landscape)
```

### Pour les jeux N64
```
Overlay recommandé : flat-n64 ou retropad
Layout : Analog
Note : C-buttons mapped sur boutons droits
```

---

## Tableau récapitulatif des overlays

| Overlay | Consoles compatibles | Modes disponibles | Layouts count |
|---------|---------------------|-------------------|---------------|
| `flat-retropad` | TOUTES | Digital, Analog | 14 |
| `retropad` | TOUTES | Digital, Analog, Menu | 10 |
| `flat-psx` | PSX, Dreamcast | Digital, Analog (L/R/Both) | 11 |
| `flat-n64` | N64 | Analog + C-buttons | Multiple |
| `flat-snes` | SNES, GBA | Digital | 5 |
| `flat-nes` | NES, SMS | Digital | 5 |
| `flat-genesis` | Genesis/MD | Digital (6-button) | Multiple |
| `flat-gb/gbc` | Game Boy | Digital | 5 |
| `flat-arcade` | Arcade, FBNeo | Digital (multi-button) | Multiple |

---

## Historique des fonctionnalités

**Version actuelle :**
- ✅ 24 overlays disponibles
- ✅ Rechargement à chaud (sans redémarrer)
- ✅ Menu Rapide (bouton Back)
- ✅ Tous les layouts visibles (plus de limite)
- ✅ Labels clairs (Digital/Analog/Menu/etc.)
- ✅ Cooldown pour éviter réouverture accidentelle
- ✅ Offset vertical en portrait (20%)
- ✅ Hide/Show overlays
- ✅ Debug mode (hitboxes)
- ✅ Reset to default

**Améliorations futures possibles :**
- Multiple save state slots dans Quick Menu
- Configuration de l'opacité des overlays
- Taille/position personnalisée des boutons
- Import d'overlays personnalisés
- Vibration sur appui des boutons

---

## Support

**En cas de problème :**
1. Consulter la section [Dépannage](#dépannage)
2. Vérifier les logs (adb logcat | Select-String "ComposeEmulator")
3. Utiliser "RESET TO NATIVE GAMEPAD" si bloqué
4. Force Stop l'app et relancer

**Fichiers de configuration :**
- Overlays : `/storage/emulated/0/RetroPlay-Data/overlays/`
- Préférences : `SharedPreferences` ("compose_gamepad_settings")
- Clés : `overlay_[console]_*`, `gamepad_[console]_variant`

---

**Dernière mise à jour :** 27 octobre 2024
**Version RetroPlay :** Beta avec support RetroArch complet



