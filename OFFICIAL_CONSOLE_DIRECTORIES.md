# 📂 Liste Officielle des Répertoires de Consoles

**Source:** Standards Libretro/RetroArch  
**Date:** 20 novembre 2025  
**Référence:** libretro-database, RetroArch documentation

---

## 🎯 Nomenclature Officielle Libretro

Basé sur les noms utilisés dans `libretro-database` et les conventions RetroArch.

---

## 📋 LISTE COMPLÈTE DES RÉPERTOIRES OFFICIELS

### 🎮 Nintendo

| Nom Officiel Libretro | Répertoire Recommandé | Aliases Supportés | Status |
|----------------------|----------------------|-------------------|--------|
| `Nintendo - Nintendo Entertainment System` | `nes` | `fc`, `famicom` | ✅ Standard |
| `Nintendo - Super Nintendo Entertainment System` | `snes` | `sfc`, `superfamicom` | ✅ Standard |
| `Nintendo - Nintendo 64` | `n64` | `nintendo64` | ✅ Standard |
| `Nintendo - Game Boy` | `gb` | `gameboy` | ✅ Standard |
| `Nintendo - Game Boy Color` | `gbc` | `gameboycolor` | ✅ Standard |
| `Nintendo - Game Boy Advance` | `gba` | `gameboyadvance` | ✅ Standard |
| `Nintendo - Nintendo DS` | `nds` | `ds`, `nintendods` | ✅ Standard |
| `Nintendo - Virtual Boy` | `vb` | `virtualboy` | ✅ Standard |

### 🎮 Sega

| Nom Officiel Libretro | Répertoire Recommandé | Aliases Supportés | Status |
|----------------------|----------------------|-------------------|--------|
| `Sega - Mega Drive - Genesis` | `genesis` ou `megadrive` | `md`, `sega genesis` | ✅ Standard |
| `Sega - Master System - Mark III` | `mastersystem` | `sms` | ✅ Standard |
| `Sega - Game Gear` | `gamegear` | `gg` | ✅ Standard |
| `Sega - 32X` | `32x` | `sega32x` | ✅ Standard |
| `Sega - Mega-CD - Sega CD` | `segacd` | `megacd`, `sega cd` | ✅ Standard |
| `Sega - Saturn` | `saturn` | `segasaturn` | ✅ Standard |
| `Sega - Dreamcast` | `dreamcast` | `dc`, `segadreamcast` | ✅ Standard |

### 🎮 Sony

| Nom Officiel Libretro | Répertoire Recommandé | Aliases Supportés | Status |
|----------------------|----------------------|-------------------|--------|
| `Sony - PlayStation` | `psx` | `ps1`, `playstation` | ✅ Standard |
| `Sony - PlayStation Portable` | `psp` | `playstationportable` | ✅ Standard |

### 🎮 Atari

| Nom Officiel Libretro | Répertoire Recommandé | Aliases Supportés | Status |
|----------------------|----------------------|-------------------|--------|
| `Atari - 2600` | `atari2600` | `2600`, `a2600`, `atari` | ✅ Standard |
| `Atari - 5200` | `atari5200` | `5200`, `a5200` | ✅ Standard |
| `Atari - 7800` | `atari7800` | `7800`, `a7800` | ✅ Standard |
| `Atari - Lynx` | `lynx` | `atarilynx` | ✅ Standard |
| `Atari - Jaguar` | `jaguar` | `atarijaguar` | ✅ Standard |

### 🎮 Arcade

| Nom Officiel Libretro | Répertoire Recommandé | Aliases Supportés | Status |
|----------------------|----------------------|-------------------|--------|
| `Arcade` | `arcade` | (FBNeo par défaut) | ✅ Standard |
| `MAME` | `mame` | (MAME2010 par défaut) | ✅ Standard |
| `FBNeo` | `fbneo` | `neogeo` | ✅ Standard |

**Note:** `arcade`, `mame`, et `fbneo` sont **séparés** car ils ont des configurations différentes (cores, ROM sets).

### 🎮 Autres Consoles

| Nom Officiel Libretro | Répertoire Recommandé | Aliases Supportés | Status |
|----------------------|----------------------|-------------------|--------|
| `SNK - Neo Geo Pocket` | `ngp` | `ngc`, `neogeopocket` | ✅ Standard |
| `Bandai - WonderSwan Color` | `wonderswancolor` | `ws`, `wsc`, `wonderswan` | ✅ Standard |
| `NEC - PC Engine - TurboGrafx-16` | `pce` | `pcengine`, `turbografx` | ✅ Standard |
| `Commodore - 64` | `c64` | `commodore64` | ✅ Standard |
| `Commodore - Amiga` | `amiga` | `commodoreamiga` | ✅ Standard |
| `DOS` | `dos` | - | ✅ Standard |
| `3DO Interactive Multiplayer` | `3do` | - | ✅ Standard |

---

## 🔍 RÉPERTOIRES ACTUELS DANS NOTRE CODEBASE

### ✅ Répertoires Confirmés (Avec ROMs)

```
✅ nes/              (Nintendo Entertainment System)
✅ snes/             (Super Nintendo)
✅ n64/              (Nintendo 64)
✅ gb/               (Game Boy)
✅ gbc/              (Game Boy Color)
✅ gba/              (Game Boy Advance)
✅ megadrive/        (Sega Genesis/Mega Drive)
✅ segacd/           (Sega CD)
✅ psx/              (PlayStation)
✅ psp/              (PlayStation Portable)
✅ atari2600/        (Atari 2600)
✅ atari5200/        (Atari 5200)
✅ atari7800/        (Atari 7800)
✅ atarilynx/        (Atari Lynx)
```

### ⚠️ Répertoires avec Aliases (Doublons Gérés)

**Note:** Ces répertoires peuvent coexister car notre code gère les aliases via `ConsoleNameMapper`. Les deux noms pointent vers la même configuration mais peuvent avoir des répertoires séparés sur le device.

```
⚠️ genesis/          (alias de megadrive/ - normalisé vers "genesis")
⚠️ lynx/             (alias de atarilynx/ - normalisé vers "lynx")
⚠️ sms/              (alias de mastersystem/ - normalisé vers "mastersystem")
⚠️ arcade/           (séparé de mame/ et fbneo/ - configurations différentes)
```

**Gestion:** Le scan automatique détectera les deux répertoires s'ils existent et générera un gamelist.json pour chacun. `ConsoleNameMapper` normalise les IDs pour la configuration (cores, extensions) mais conserve le nom réel du répertoire.

### 📋 Répertoires Officiels (Tous Supportés)

**Note:** Ces répertoires sont **tous supportés** par le code. "Non testés" signifie simplement qu'ils n'ont pas été vérifiés manuellement sur le device, mais le scan automatique les détectera et générera leur gamelist.json automatiquement.

```
📋 nds/              (Nintendo DS) - Supporté dans ConsoleNameMapper
📋 vb/               (Virtual Boy) - Supporté dans ConsoleNameMapper
📋 virtualboy/       (Virtual Boy - alias de vb/)
📋 mastersystem/     (Sega Master System) - Supporté dans ConsoleNameMapper
📋 sms/              (Sega Master System - alias de mastersystem/)
📋 gamegear/         (Sega Game Gear) - Supporté dans ConsoleNameMapper
📋 gg/               (Sega Game Gear - alias de gamegear/)
📋 32x/              (Sega 32X) - Supporté dans ConsoleNameMapper
📋 saturn/           (Sega Saturn) - Supporté dans ConsoleNameMapper
📋 dreamcast/        (Sega Dreamcast) - Supporté dans ConsoleNameMapper
📋 jaguar/           (Atari Jaguar) - Supporté dans ConsoleNameMapper
📋 mame/             (MAME) - Supporté dans ConsoleNameMapper
📋 fbneo/            (FBNeo) - Supporté dans ConsoleNameMapper
📋 ngp/              (Neo Geo Pocket) - Supporté dans ConsoleNameMapper
📋 wonderswancolor/  (WonderSwan Color) - Supporté dans ConsoleNameMapper
📋 ws/               (WonderSwan - alias de wonderswancolor/)
📋 pce/              (PC Engine) - Supporté dans ConsoleNameMapper
📋 c64/              (Commodore 64) - Supporté dans ConsoleNameMapper
📋 amiga/            (Commodore Amiga) - Supporté dans ConsoleNameMapper
📋 dos/              (DOS) - Supporté dans ConsoleNameMapper
📋 3do/              (3DO) - Supporté dans ConsoleNameMapper
```

**Important:** Avec le scan automatique au démarrage, **TOUS** ces répertoires seront automatiquement détectés et traités s'ils existent sur le device. Il n'y a pas de limitation technique - c'est juste une question de vérification manuelle.

---

## 📊 MAPPING ACTUEL DANS ConsoleNameMapper.java

### ✅ Couvert (Tous les aliases mappés)

- ✅ Nintendo : nes, snes, n64, gb, gbc, gba, nds
- ✅ Sega : genesis/megadrive, mastersystem/sms, gamegear/gg, 32x, segacd, saturn, dreamcast
- ✅ Sony : psx/ps1, psp
- ✅ Atari : atari2600, atari5200, atari7800, lynx/atarilynx, jaguar
- ✅ Arcade : arcade, mame, fbneo (séparés)
- ✅ Autres : ngp, wonderswancolor, pce, c64, amiga, dos

### ✅ Gestion des Aliases

**Tous les aliases sont gérés automatiquement :**

- ✅ Virtual Boy : `vb` et `virtualboy` → Normalisés vers `vb` (ID canonique)
- ✅ Master System : `mastersystem` ou `sms` → Normalisés vers `mastersystem` (ID canonique)
- ✅ Game Gear : `gamegear` ou `gg` → Normalisés vers `gamegear` (ID canonique)

**Fonctionnement :**
- Le scan automatique détecte **tous** les répertoires existants
- `ConsoleNameMapper.normalizeToCanonical()` convertit les aliases vers l'ID canonique
- Le nom **réel** du répertoire est conservé dans `ConsoleInfo.directory`
- La configuration (cores, extensions) utilise l'ID canonique
- Les deux répertoires peuvent coexister (ex: `nes/` et `fc/` peuvent tous deux exister)

---

## 🎯 RECOMMANDATIONS

### 1. **Répertoires Officiels à Utiliser**

Basé sur les standards Libretro, voici les répertoires **recommandés** :

```
nes/
snes/
n64/
gb/
gbc/
gba/
nds/
vb/                    (Virtual Boy)
genesis/               (ou megadrive/ - les deux sont valides)
mastersystem/          (ou sms/ - les deux sont valides)
gamegear/              (ou gg/ - les deux sont valides)
32x/
segacd/
saturn/
dreamcast/
psx/
psp/
atari2600/
atari5200/
atari7800/
lynx/                  (ou atarilynx/ - les deux sont valides)
jaguar/
arcade/                (FBNeo par défaut)
mame/                  (MAME séparé)
fbneo/                 (FBNeo séparé)
ngp/
wonderswancolor/
pce/
c64/
amiga/
dos/
3do/
```

### 2. **Gestion des Doublons**

**Stratégie actuelle :**
- `ConsoleNameMapper` normalise les aliases vers un ID canonique
- Le nom du répertoire réel est conservé dans `ConsoleInfo.directory`
- Les deux peuvent coexister (ex: `nes/` et `fc/`)

**Recommandation :**
- ✅ **Garder la stratégie actuelle** : Permettre les doublons si l'utilisateur les crée
- ✅ **Normaliser pour la configuration** : Utiliser l'ID canonique pour cores/extensions
- ✅ **Conserver le nom réel** : Utiliser `directory` pour les chemins de fichiers

### 3. **Scan Automatique**

Lors du scan automatique au démarrage :
1. Scanner **tous** les répertoires dans `/storage/emulated/0/GameLibrary-Data/`
2. Ignorer les répertoires système : `data/`, `cores/`, `bios/`, `cheats/`, `saves/`, `states/`, `media/`, `overlays/`, `playlists/`, `.cache/`
3. Pour chaque répertoire :
   - Normaliser avec `ConsoleNameMapper.normalizeToCanonical()`
   - Générer `gamelist.json` si absent ou obsolète
   - Conserver le nom original du répertoire dans `directory`

---

## 📚 RÉFÉRENCES

### Sources Officielles

1. **libretro-database** : `https://github.com/libretro/libretro-database`
   - Structure : `cht/{System Name}/`
   - Exemples : `cht/Nintendo - Nintendo Entertainment System/`, `cht/Sega - Mega Drive - Genesis/`

2. **RetroArch Documentation** : `https://docs.libretro.com/`
   - Nomenclature des systèmes
   - Formats de ROMs supportés

3. **Cores.json** : `https://buildbot.libretro.com/nightly/android/latest/cores.json`
   - Liste des cores disponibles
   - `system_id` et `system_name` pour chaque core

### Fichiers dans notre Codebase

- **ConsoleNameMapper.java** : Tous les mappings alias → ID canonique
- **WebServer.java** : Détection automatique des répertoires
- **GameListActivity.java** : Utilisation des répertoires réels
- **GamelistManager.kt** : Génération de gamelist par répertoire

---

## ✅ CHECKLIST POUR SCAN AUTOMATIQUE

Lors de l'implémentation du scan automatique, vérifier :

- [ ] Tous les répertoires officiels sont reconnus
- [ ] Les aliases sont correctement mappés
- [ ] Les doublons (nes/fc, genesis/megadrive) sont gérés
- [ ] Les répertoires système sont ignorés
- [ ] Les sous-répertoires (fbneo/sega) sont gérés
- [ ] Le nom réel du répertoire est conservé
- [ ] L'ID canonique est utilisé pour la configuration

---

## 🔄 PROCHAINES ÉTAPES

1. ✅ **Vérifier les répertoires réels** sur le device après suppression des gamelist.json
2. ✅ **Implémenter le scan automatique** qui reconnaît tous les répertoires officiels
3. ✅ **Tester avec différents noms** (nes vs fc, genesis vs megadrive, etc.)
4. ✅ **Documenter les répertoires confirmés** après le premier scan

