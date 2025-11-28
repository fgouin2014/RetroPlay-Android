# Explication : Pourquoi un WebServer dans RetroPlay ?

## 🔍 RAISONS TECHNIQUES

### 1. **Restrictions de sécurité Android pour WebView**

Android bloque l'accès direct aux fichiers locaux via `file://` dans les WebViews pour des raisons de sécurité :

```java
// ❌ NE FONCTIONNE PAS dans WebView
webView.loadUrl("file:///storage/emulated/0/GameLibrary-Data/nes/gamelist.json");

// ✅ FONCTIONNE avec WebServer
webView.loadUrl("http://localhost:7777/gamedata/nes/gamelist.json");
```

**Problème :** Les WebViews Android ne peuvent pas charger directement des fichiers locaux avec `file://` à cause des restrictions CORS et de sécurité.

### 2. **Jeux WASM / EmulatorJS**

Les jeux WASM (WebAssembly) et EmulatorJS nécessitent d'être servis via HTTP avec des headers spécifiques :

- **SharedArrayBuffer** : Nécessite les headers `Cross-Origin-Embedder-Policy` (COEP) et `Cross-Origin-Opener-Policy` (COOP)
- **CORS** : Les fichiers doivent être servis avec les bons headers CORS
- **MIME types** : Les fichiers doivent avoir les bons types MIME (application/wasm, etc.)

```java
// Headers nécessaires pour SharedArrayBuffer (jeux WASM)
"Cross-Origin-Embedder-Policy: require-corp\r\n" +
"Cross-Origin-Opener-Policy: same-origin\r\n"
```

### 3. **API REST pour lister les consoles**

Le WebServer fournit une API REST pour lister les consoles disponibles :

```
GET http://localhost:7777/gamelibrary/api/consoles
```

**Réponse JSON :**
```json
{
  "consoles": [
    {
      "id": "nes",
      "name": "NES",
      "fullName": "Nintendo Entertainment System",
      "directory": "nes",
      "enabled": true
    }
  ]
}
```

Cette API permet à `GameListActivity` de charger dynamiquement la liste des consoles.

### 4. **Auto-génération de gamelist.json**

Le WebServer peut générer automatiquement un `gamelist.json` si le fichier n'existe pas :

```
GET http://localhost:7777/gamedata/nes/gamelist.json
```

Si le fichier n'existe pas, le serveur scanne automatiquement les ROMs et génère le JSON.

### 5. **Accès réseau (optionnel)**

Le WebServer peut être configuré pour être accessible sur le réseau local :

```
http://192.168.1.100:7777/gamedata/nes/gamelist.json
```

Cela permet d'accéder aux jeux depuis d'autres appareils sur le même réseau.

## 📋 UTILISATIONS CONCRÈTES

### 1. **Chargement des jeux WASM**

```java
// Dans WebViewActivity
webView.loadUrl("http://localhost:7777/gamelibrary/index.html?console=nes&game=SuperMario.nes");
```

Le WebServer sert les fichiers HTML/JS/WASM nécessaires pour les jeux.

### 2. **Chargement des images**

```java
// Dans Game.java
this.imagePath = "http://localhost:7777/gamedata/" + consoleId + "/media/box2d/" + baseName + ".png";
```

Les images sont servies via HTTP pour être chargées dans les ImageViews.

### 3. **Chargement des gamelists**

```java
// Dans GameListActivity
java.net.URL url = new java.net.URL("http://localhost:7777/gamelibrary/api/consoles");
```

L'API REST permet de lister les consoles disponibles.

### 4. **Streaming des ROMs**

Pour les grandes ROMs (ex: PSX 450+ MB), le WebServer permet le streaming :

```java
// Dans WebServer.java
FileInputStream fileInputStream = new FileInputStream(file);
byte[] buffer = new byte[65536]; // 64KB pour performance
int bytesRead;
while ((bytesRead = fileInputStream.read(buffer)) != -1) {
    outputStream.write(buffer, 0, bytesRead);
}
```

## ⚡ ALTERNATIVES (et pourquoi elles ne fonctionnent pas)

### Alternative 1 : Accès direct aux fichiers
```java
// ❌ NE FONCTIONNE PAS
File file = new File("/storage/emulated/0/GameLibrary-Data/nes/gamelist.json");
String content = Files.readString(file.toPath());
webView.loadData(content, "application/json", "UTF-8");
```
**Problème :** Les WebViews ne peuvent pas charger des fichiers locaux directement.

### Alternative 2 : Assets Android
```java
// ❌ LIMITÉ
InputStream is = getAssets().open("gamelist.json");
```
**Problème :** Les assets sont compilés dans l'APK, pas modifiables dynamiquement.

### Alternative 3 : ContentProvider
```java
// ⚠️ COMPLEXE
// Nécessite de créer un ContentProvider personnalisé
```
**Problème :** Plus complexe à implémenter, moins flexible.

## 🎯 AVANTAGES DU WEBSERVER

1. **Flexibilité** : Permet de servir n'importe quel fichier dynamiquement
2. **Standards HTTP** : Compatible avec tous les navigateurs/WebViews
3. **Headers personnalisés** : Permet d'ajouter les headers nécessaires (COEP/COOP)
4. **API REST** : Permet de créer des endpoints API
5. **Streaming** : Permet de streamer les gros fichiers efficacement
6. **Accès réseau** : Optionnel pour accès depuis d'autres appareils

## 📊 ROUTES DU WEBSERVER

- `/gamelibrary/` → Pages HTML/JS pour la bibliothèque de jeux
- `/gamedata/{console}/` → ROMs, gamelist.json, images pour une console
- `/gamelibrary/api/consoles` → API REST pour lister les consoles
- `/relax/` → Émulateur Relax (jeux WASM)

## 🔒 SÉCURITÉ

Le WebServer écoute uniquement sur `localhost:7777` par défaut, donc :
- ✅ Accessible uniquement depuis l'appareil local
- ✅ Pas accessible depuis Internet
- ✅ Option réseau désactivée par défaut





