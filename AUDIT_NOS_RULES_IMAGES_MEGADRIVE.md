# Audit Nos Rules - Images Megadrive non trouvées

## 🔍 PROBLÈME IDENTIFIÉ

**Symptôme :** Les images de la liste de jeux Megadrive ne sont pas trouvées lors du scan même si elles existent.

## 🔬 ANALYSE DU PROBLÈME

### Flux actuel

1. **`loadGames()`** (ligne 659-668) : Trouve le `realConsoleDirectory` (nom réel du répertoire) depuis `availableConsoles`
   - Exemple : `currentConsole = "genesis"` (ID canonique) → `realConsoleDirectory = "megadrive"` (nom réel du répertoire)

2. **`parseAndDisplayGames()`** (ligne 784) : Définit la console du jeu avec `currentConsole` (ID canonique)
   ```java
   game.setConsole(currentConsole);  // "genesis" au lieu de "megadrive"
   ```

3. **`Game.initializePaths()`** (ligne 158) : Construit le chemin d'image avec `consoleId` (qui est `currentConsole`)
   ```java
   this.imagePath = "http://localhost:7777/gamedata/" + consoleId + "/media/box2d/" + baseName + ".png";
   // Cherche dans: /gamedata/genesis/media/box2d/...
   // Mais les images sont dans: /gamedata/megadrive/media/box2d/...
   ```

4. **`ArtworkResolver.resolveBoxArt()`** (ligne 38) : Utilise `game.getConsole()` qui retourne l'ID canonique
   ```java
   String console = sanitizeConsole(game.getConsole());  // "genesis"
   // Cherche dans: /storage/emulated/0/GameLibrary-Data/genesis/media/...
   // Mais les images sont dans: /storage/emulated/0/GameLibrary-Data/megadrive/media/...
   ```

### Problème

Le code utilise l'**ID canonique** (`currentConsole = "genesis"`) au lieu du **nom réel du répertoire** (`realConsoleDirectory = "megadrive"`) pour construire les chemins d'images.

### Exemple concret

- Répertoire réel : `/storage/emulated/0/GameLibrary-Data/megadrive/`
- ID canonique : `"genesis"` (normalisé par `ConsoleNameMapper`)
- Images existantes : `/storage/emulated/0/GameLibrary-Data/megadrive/media/box2d/Sonic.png`
- Chemin cherché : `/storage/emulated/0/GameLibrary-Data/genesis/media/box2d/Sonic.png` ❌

## 🎯 SOLUTION PROPOSÉE (Nos Rules)

### Principe Nos Rules

1. **Utiliser le nom réel du répertoire** pour les chemins de fichiers (images, ROMs, etc.)
2. **Utiliser l'ID canonique** uniquement pour la configuration (cores, extensions, etc.)
3. **Passer `realConsoleDirectory` à `parseAndDisplayGames()`** pour qu'il puisse l'utiliser

### Implémentation

1. **Modifier `loadGames()`** pour passer `realConsoleDirectory` à `parseAndDisplayGames()`
2. **Modifier `parseAndDisplayGames()`** pour accepter `realConsoleDirectory` en paramètre
3. **Utiliser `realConsoleDirectory`** au lieu de `currentConsole` pour `game.setConsole()`

## ✅ SOLUTION IMPLÉMENTÉE

### Modifications apportées

1. **Modification de `parseAndDisplayGames()`** pour accepter `realConsoleDirectory` en paramètre
2. **Utilisation de `realConsoleDirectory`** pour `game.setConsole()` au lieu de `currentConsole`
3. **Ajout de logs** pour diagnostiquer le problème (ligne 762)
4. **Mise à jour de tous les appels** à `parseAndDisplayGames()` pour passer `realConsoleDirectory`

### Code modifié

**Avant :**
```java
game.setConsole(currentConsole);  // "genesis" (ID canonique)
// Cherche les images dans: /gamedata/genesis/media/...
```

**Après :**
```java
String consoleForImages = realConsoleDirectory != null ? realConsoleDirectory : currentConsole;
game.setConsole(consoleForImages);  // "megadrive" (nom réel du répertoire)
// Cherche les images dans: /gamedata/megadrive/media/...
```

### Flux corrigé

1. `loadGames()` trouve `realConsoleDirectory` depuis `availableConsoles`
2. `loadGames()` passe `realConsoleDirectory` à `parseAndDisplayGames()`
3. `parseAndDisplayGames()` utilise `realConsoleDirectory` pour `game.setConsole()`
4. `Game.initializePaths()` construit les chemins avec le nom réel du répertoire
5. `ArtworkResolver` cherche les images dans le bon répertoire

### Avantages

- ✅ Les images sont trouvées dans le bon répertoire (ex: `megadrive/` au lieu de `genesis/`)
- ✅ Fonctionne avec tous les aliases (genesis/megadrive, nes/fc, etc.)
- ✅ Les logs permettent de diagnostiquer les problèmes
- ✅ Compatible avec la recherche multi-consoles (utilise `console.id` qui est correct pour la recherche)

## 📋 VÉRIFICATIONS À FAIRE

1. ✅ Modifier la signature de `parseAndDisplayGames()` pour accepter `realConsoleDirectory` - FAIT
2. ✅ Utiliser `realConsoleDirectory` pour `game.setConsole()` au lieu de `currentConsole` - FAIT
3. ✅ Ajouter des logs pour diagnostiquer le problème - FAIT
4. ⏳ Tester avec Megadrive et Genesis pour vérifier que les images sont trouvées - À TESTER

