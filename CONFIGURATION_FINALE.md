# 🎮 RetroPlay-Android - Configuration Finale

**Date:** 20 octobre 2025  
**Version:** 1.0 Final  
**Statut:** ✅ OPÉRATIONNEL ET CONFIGURÉ

---

## 📋 Configuration Actuelle

### Ports Réseau
- **ChatAI-Android:** Port **8888** (WebServer) + 8081 (WebSocket)
- **GameLibrary-Android:** Port **9999** (WebServer)
- **RetroPlay-Android:** Port **7777** (WebServer) ✅

**Pourquoi 7777 ?**
- ✅ Pas bloqué par les navigateurs (contrairement à 6666)
- ✅ Pas de conflit avec ChatAI (8888/8081) ou GameLibrary (9999)
- ✅ Facile à retenir
- ✅ Port standard pour apps web custom

### Chemins de Stockage

#### Répertoire Principal
```
/storage/emulated/0/RetroPlay-Files/
└── sites/
    └── gamelibrary/
        ├── index.html      (Liste des jeux - EmulatorJS)
        └── emulator.html   (Émulateur WASM - EmulatorJS)
```

**Note:** RetroPlay utilise maintenant son propre répertoire **RetroPlay-Files** (au lieu de ChatAI-Files ou GameLibrary-Files).

#### ROMs et Données (Partagées avec GameLibrary)
```
/storage/emulated/0/GameLibrary-Data/
├── {console}/              (ROMs par console)
│   ├── nes/
│   ├── snes/
│   ├── megadrive/
│   ├── psx/
│   └── ...
├── data/
│   └── bios/              (BIOS pour PSX, PSP, etc.)
├── saves/                 (Save states)
├── cheats/                (Fichiers .cht)
└── .cache/                (Cache d'extraction ZIP/7Z)
```

---

## 🌐 URLs d'Accès

**Depuis un navigateur sur le même réseau WiFi :**

Remplacez `[DEVICE_IP]` par l'adresse IP de votre device (ex: `172.26.22.217`)

### Pages Web
- **Page principale:** `http://[DEVICE_IP]:7777/`
- **Liste des jeux EmulatorJS:** `http://[DEVICE_IP]:7777/gamelibrary/index.html`
- **Émulateur WASM:** `http://[DEVICE_IP]:7777/gamelibrary/emulator.html`

### API / GameData
- **ROMs NES:** `http://[DEVICE_IP]:7777/gamedata/nes/`
- **ROMs Genesis:** `http://[DEVICE_IP]:7777/gamedata/megadrive/`
- **Gamelist JSON:** `http://[DEVICE_IP]:7777/gamedata/{console}/gamelist.json`
- **Auto-scan:** `http://[DEVICE_IP]:7777/gamelibrary/api/consoles`

---

## 🔧 Routes du WebServer

### Route `/gamelibrary/`
Sert les fichiers HTML d'EmulatorJS depuis :
```
/storage/emulated/0/RetroPlay-Files/sites/gamelibrary/
```

### Route `/gamedata/`
Sert les ROMs et données depuis :
```
/storage/emulated/0/GameLibrary-Data/
```

### Route `/`
Page d'accueil avec listing des dossiers disponibles.

---

## 📂 Structure Complète sur le Device

```
/storage/emulated/0/
├── RetroPlay-Files/           (Nouveau - Propre à RetroPlay)
│   └── sites/
│       └── gamelibrary/
│           ├── index.html
│           └── emulator.html
│
├── GameLibrary-Data/          (Partagé avec GameLibrary-Android)
│   ├── nes/
│   ├── snes/
│   ├── n64/
│   ├── gb/
│   ├── gbc/
│   ├── gba/
│   ├── psx/
│   ├── psp/
│   ├── megadrive/
│   ├── segacd/
│   ├── mastersystem/
│   ├── gamegear/
│   ├── sega32x/
│   ├── atari2600/
│   ├── atari5200/
│   ├── atari7800/
│   ├── atarilynx/
│   ├── ngp/
│   ├── wonderswan/
│   ├── pce/
│   ├── mame/
│   ├── fbneo/
│   ├── data/
│   │   └── bios/
│   ├── saves/
│   ├── cheats/
│   └── .cache/
│
├── ChatAI-Files/              (ChatAI uniquement)
│   └── sites/
│       └── gamelibrary/
│
└── GameLibrary-Files/         (GameLibrary uniquement)
    └── sites/
        └── gamelibrary/
```

---

## 🔄 Différences entre les Apps

| Aspect | ChatAI | GameLibrary | RetroPlay |
|--------|--------|-------------|-----------|
| **Port WebServer** | 8888 | 9999 | **7777** |
| **Fichiers Web** | ChatAI-Files | GameLibrary-Files | **RetroPlay-Files** |
| **ROMs** | GameLibrary-Data | GameLibrary-Data | GameLibrary-Data |
| **Fonction principale** | Chat AI + Jeux | Jeux uniquement | Jeux uniquement |
| **Package** | com.chatai | com.gamelibrary | **com.retroplay** |
| **Launcher** | MainActivity | GameListActivity | GameListActivity |

---

## ✅ Avantages de la Configuration Actuelle

### Isolation des Fichiers Web
- ✅ Chaque app a son propre répertoire `/sites/`
- ✅ Pas de conflit entre les versions HTML personnalisées
- ✅ Possibilité de personnaliser indépendamment

### Partage des ROMs
- ✅ Un seul répertoire `GameLibrary-Data` pour toutes les ROMs
- ✅ Pas de duplication (économise de l'espace)
- ✅ ROMs accessibles par les 3 apps

### Ports Réseau
- ✅ Chaque app a son port unique
- ✅ Pas de conflit réseau
- ✅ Toutes les apps peuvent tourner simultanément

---

## 🚀 Comment Utiliser

### Option 1 : Via l'App RetroPlay
1. Ouvrir RetroPlay sur le device
2. Choisir un jeu
3. Appuyer sur **"PLAY NATIVE"** (émulation native) ou **"PLAY WASM"** (émulation web)

### Option 2 : Via Navigateur Web
1. **Sur un PC/téléphone sur le même WiFi**
2. Trouver l'IP du device : `adb shell ip addr show wlan0`
3. Ouvrir : `http://[DEVICE_IP]:7777/gamelibrary/index.html`
4. Jouer directement dans le navigateur

---

## 📝 Commandes Utiles

### Trouver l'IP du Device
```bash
adb shell ip addr show wlan0 | findstr "inet "
```

### Tester le WebServer
```bash
curl http://[DEVICE_IP]:7777/
curl http://[DEVICE_IP]:7777/gamelibrary/index.html
curl http://[DEVICE_IP]:7777/gamedata/nes/
```

### Copier des Fichiers sur le Device
```bash
# Copier index.html personnalisé
adb push index.html /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/

# Copier emulator.html personnalisé
adb push emulator.html /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/

# Copier des ROMs
adb push game.nes /storage/emulated/0/GameLibrary-Data/nes/
```

### Vérifier les Logs
```powershell
adb logcat WebServer:I GameListActivity:I "*:E"
```

---

## 🔧 Fichiers Modifiés (Changelog)

### Mise à Jour du Port (6666 → 7777)
- ✅ `WebServer.java` : `PORT = 7777`
- ✅ `WebViewActivity.java` : `WEBSERVER_PORT = 7777`
- ✅ `GameListActivity.java` : URLs localhost:7777
- ✅ `GameDetailsActivity.java` : URLs localhost:7777
- ✅ `Game.java` : URLs localhost:7777
- ✅ `strings.xml` : "Port 7777"

### Mise à Jour des Chemins (ChatAI-Files/GameLibrary-Files → RetroPlay-Files)
- ✅ `WebServer.java` : `SITES_DIR = "/storage/emulated/0/RetroPlay-Files/sites"`
- ✅ `GameListActivity.java` : `basePath = "/storage/emulated/0/RetroPlay-Files/sites/"`
- ✅ `FileServer.java` : Toutes références
- ✅ `HttpServer.java` : Toutes références
- ✅ `ConsoleConfigActivity.java` : Toutes références
- ✅ Fichiers Kotlin dans `activities/` : Toutes références

### Ajout de la Gestion des Permissions
- ✅ `GameListActivity.java` : `checkStoragePermissions()`, `requestStoragePermissions()`

### Ajout du Démarrage du WebServer
- ✅ `GameListActivity.java` : `startWebServer()`, `onDestroy()` pour arrêt propre

---

## ⚠️ Important : Préparation du Device

### 1. Créer le Répertoire RetroPlay-Files
```bash
adb shell mkdir -p /storage/emulated/0/RetroPlay-Files/sites/gamelibrary
```

### 2. Copier les Fichiers HTML
```bash
# Option A : Copier depuis ChatAI-Files
adb shell cp -r /storage/emulated/0/ChatAI-Files/sites/gamelibrary/* /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/

# Option B : Copier depuis GameLibrary-Files
adb shell cp -r /storage/emulated/0/GameLibrary-Files/sites/gamelibrary/* /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/

# Option C : L'app les copiera automatiquement depuis les assets au premier lancement
```

### 3. Vérifier la Structure
```bash
adb shell ls -la /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/
```

Vous devriez voir :
```
index.html
emulator.html
```

---

## 🎯 Tests de Validation

### Test 1 : WebServer Démarre
```powershell
adb logcat -c
# Ouvrir RetroPlay sur le device
adb logcat WebServer:I -d
```

Vous devriez voir :
```
I WebServer: Serveur web prêt sur http://localhost:7777
```

### Test 2 : Accès Web Externe
```bash
curl -I http://[DEVICE_IP]:7777/
```

Réponse attendue :
```
HTTP/1.1 200 OK
Content-Type: text/html; charset=utf-8
```

### Test 3 : EmulatorJS Accessible
```bash
curl -I http://[DEVICE_IP]:7777/gamelibrary/index.html
```

Réponse attendue :
```
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8
```

### Test 4 : GameData Accessible
```bash
curl -I http://[DEVICE_IP]:7777/gamedata/nes/
```

Réponse attendue :
```
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8
Access-Control-Allow-Origin: *
```

---

## 📊 Récapitulatif Final

### ✅ Ce qui Fonctionne
- ✅ WebServer démarre sur port 7777
- ✅ Permissions de stockage demandées au lancement
- ✅ Émulation native (19 cores)
- ✅ Émulation WASM (EmulatorJS)
- ✅ Système de cheats
- ✅ Save states
- ✅ Cache automatique ZIP/7Z
- ✅ Accès web externe (navigateur)
- ✅ Utilise RetroPlay-Files (propre et isolé)
- ✅ Partage GameLibrary-Data (économise de l'espace)

### 🔧 Configuration Réseau
```
Port 7777 ✅
├── Pas bloqué par les navigateurs
├── Pas de conflit avec ChatAI (8888/8081)
└── Pas de conflit avec GameLibrary (9999)
```

### 📁 Chemins Configurés
```
Sites Web : /storage/emulated/0/RetroPlay-Files/sites/ ✅
ROMs      : /storage/emulated/0/GameLibrary-Data/ ✅
```

---

**🎮 RetroPlay-Android est maintenant configuré de manière optimale et isolée ! ✅**

---

**Créé le:** 20 octobre 2025  
**Dernière mise à jour:** 20 octobre 2025  
**Version:** 1.0 Final

