# Audit Système Gamelist - RetroPlay

**Date:** 23 novembre 2025  
**Objectif:** Audit complet du système gamelist pour compatibilité RetroPie/EmulationStation

---

## 📋 ÉTAT ACTUEL

### 1. Génération Initiale (`GamelistManager.generateGamelist`)

**Quand:** Lors du scan automatique (GamelistScanner) ou génération manuelle

**Processus:**
1. ✅ Scan des ROMs dans le répertoire
2. ✅ Calcul CRC32 (ou Serial pour PSX)
3. ✅ **Interrogation BD UNE FOIS** → enrichit name, genre, description, releaseDate, players
4. ✅ Création GameEntry avec métadonnées BD
5. ✅ Sauvegarde dans `gamelist.json`

**Problème actuel:** ✅ CORRECT - BD interrogée une seule fois lors de la génération

---

### 2. Format GameEntry Actuel

```kotlin
data class GameEntry(
    val id: String,              // ✅ Compatible
    val name: String,            // ✅ Compatible (depuis BD)
    val path: String,            // ✅ Compatible
    val description: String? = null,  // ✅ Compatible (depuis BD)
    val genre: String? = null,        // ✅ Compatible (depuis BD)
    val releaseDate: String? = null,   // ✅ Compatible (depuis BD)
    val players: String? = null,      // ✅ Compatible (depuis BD)
    val crc32: String? = null,        // ❌ Pas dans RetroPie (mais utile)
    val size: Long? = null,           // ❌ Pas dans RetroPie (mais utile)
    val serial: String? = null,       // ❌ Pas dans RetroPie (mais utile)
    val md5: String? = null,          // ❌ Pas dans RetroPie
    val sha1: String? = null,         // ❌ Pas dans RetroPie
    val corePath: String? = null,    // ❌ Pas dans RetroPie
    val coreName: String? = null      // ❌ Pas dans RetroPie
)
```

**Champs RetroPie manquants:**
- ❌ `rating` (0.0-1.0)
- ❌ `favorite` (true/false)
- ❌ `playcount` (int)
- ❌ `lastplayed` (date)
- ❌ `image` (path)
- ❌ `thumbnail` (path)
- ❌ `video` (path)
- ❌ `marquee` (path)

---

### 3. Import XML Actuel (`ConsoleManagerActivity.importXmlMetadataSync`)

**Quand:** Import manuel depuis menu

**Processus actuel:**
1. ✅ Parse `gamelist.xml`
2. ❌ **PROBLÈME:** Enrichit name, desc, genre, etc. (redondant avec BD)
3. ✅ Enrichit rating, favorite, playcount
4. ✅ Sauvegarde dans `gamelist.json`

**Problèmes:**
- ❌ Enrichit des champs déjà dans la BD (redondant)
- ❌ Ne vérifie pas si le ROM existe sur le device
- ❌ Ne gère pas tous les champs RetroPie (image, video, marquee, etc.)

---

## 🎯 OBJECTIFS

### 1. Génération Initiale
- ✅ BD interrogée UNE SEULE FOIS → déjà fait
- ✅ Métadonnées BD sauvegardées dans `gamelist.json` → déjà fait

### 2. Import XML Optionnel
- ✅ Import depuis `gamelist.xml` RetroPie/EmulationStation
- ✅ **Vérifier que le ROM existe sur le device** (par path)
- ✅ Enrichir SEULEMENT les champs non-BD:
  - `rating` (0.0-1.0)
  - `favorite` (true/false)
  - `playcount` (int)
  - `lastplayed` (date)
  - `image` (path)
  - `thumbnail` (path)
  - `video` (path)
  - `marquee` (path)

### 3. Format Compatible RetroPie
- ✅ Format JSON (pas XML)
- ✅ Tous les champs RetroPie supportés
- ✅ Champs supplémentaires RetroPlay (crc32, size, serial) conservés

---

## 🔧 PROPOSITIONS

### 1. Étendre GameEntry

```kotlin
data class GameEntry(
    // Champs de base (compatibles RetroPie)
    val id: String,
    val name: String,
    val path: String,
    val description: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val players: String? = null,
    
    // Champs RetroPie (depuis XML)
    val rating: String? = null,        // "0.85"
    val favorite: String? = null,      // "true"/"false"
    val playcount: String? = null,     // "5"
    val lastplayed: String? = null,    // "2025-11-23T10:30:00"
    val image: String? = null,          // "./media/box2d/game.png"
    val thumbnail: String? = null,     // "./media/box2d/game.png"
    val video: String? = null,         // "./media/video/game.mp4"
    val marquee: String? = null,       // "./media/marquee/game.png"
    
    // Champs RetroPlay (supplémentaires)
    val crc32: String? = null,
    val size: Long? = null,
    val serial: String? = null,
    val md5: String? = null,
    val sha1: String? = null,
    val corePath: String? = null,
    val coreName: String? = null
)
```

### 2. Refactorer Import XML

**Nouvelle fonction:** `importXmlMetadataForExistingGames()`

**Processus:**
1. Charger `gamelist.json` existant
2. Parser `gamelist.xml`
3. Pour chaque jeu dans JSON:
   - Vérifier que le ROM existe (par path)
   - Si existe, enrichir SEULEMENT les champs non-BD depuis XML:
     - rating, favorite, playcount, lastplayed
     - image, thumbnail, video, marquee
4. Sauvegarder `gamelist.json` enrichi

**Ne PAS enrichir:** name, description, genre, releaseDate, players (viennent de la BD)

### 3. Parser XML Complet

**Champs XML à parser:**
- `<rating>` → `rating`
- `<favorite>` → `favorite`
- `<playcount>` → `playcount`
- `<lastplayed>` → `lastplayed`
- `<image>` → `image`
- `<thumbnail>` → `thumbnail`
- `<video>` → `video`
- `<marquee>` → `marquee`

**Champs XML à IGNORER (viennent de BD):**
- `<name>` → ignoré
- `<desc>` → ignoré
- `<genre>` → ignoré
- `<releasedate>` → ignoré
- `<players>` → ignoré
- `<developer>` → ignoré
- `<publisher>` → ignoré

---

## 📝 PLAN D'ACTION

1. ✅ Étendre `GameEntry` avec champs RetroPie
2. ✅ Modifier `gameToJson()` pour inclure tous les champs
3. ✅ Modifier `parseGameEntry()` pour lire tous les champs
4. ✅ Refactorer `importXmlMetadataSync()` pour:
   - Vérifier existence ROM
   - Enrichir SEULEMENT champs non-BD
5. ✅ Étendre `XmlGameData` avec tous les champs RetroPie
6. ✅ Modifier `parseXmlGamelist()` pour parser tous les champs

---

## ✅ RÉSULTAT ATTENDU

**Format `gamelist.json` compatible RetroPie:**
```json
{
  "version": "1.0",
  "console": "nes",
  "games": [
    {
      "id": "1",
      "name": "Super Mario Bros",
      "path": "./Super Mario Bros.nes",
      "description": "Developer: Nintendo",
      "genre": "Platformer",
      "releaseDate": "1985-09-13",
      "players": "1-2",
      "rating": "0.95",
      "favorite": "true",
      "playcount": "42",
      "lastplayed": "2025-11-23T10:30:00",
      "image": "./media/box2d/Super Mario Bros.png",
      "thumbnail": "./media/box2d/Super Mario Bros.png",
      "video": "./media/video/Super Mario Bros.mp4",
      "marquee": "./media/marquee/Super Mario Bros.png",
      "crc32": "a1b2c3d4",
      "size": 40960
    }
  ]
}
```

**Flux:**
1. Génération initiale → BD enrichit name, genre, etc. → sauvegarde JSON
2. Import XML optionnel → enrichit rating, favorite, playcount, media → sauvegarde JSON
3. Format compatible RetroPie mais en JSON

---

## ❓ QUESTIONS

1. Faut-il migrer les `gamelist.json` existants pour ajouter les nouveaux champs ?
2. Faut-il supprimer les champs RetroPlay (crc32, size, serial) ou les garder ?
3. Faut-il créer une fonction de conversion XML → JSON pour migration complète ?

