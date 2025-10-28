# 🎮 RetroPlay-Android

**Application d'émulation rétro standalone pour Android**

**Package:** `com.retroplay`  
**Version:** 1.0  
**Statut:** ✅ Actif - Développement continu

---

## 📋 DESCRIPTION

RetroPlay est une application Android autonome dédiée à l'émulation de jeux rétro, dérivée de ChatAI-Android. Elle combine l'émulation native (LibretroDroid) avec l'émulation web (EmulatorJS) pour offrir une expérience complète.

### Fonctionnalités Principales

- 🎮 **27 cores natifs** LibretroDroid (ARM64)
- 🌐 **EmulatorJS** (émulation WASM/web)
- 💾 **Save states** (5 slots par jeu)
- 🎯 **Système de cheats** RetroArch (.cht)
- 🕹️ **Gamepads virtuels** Lemuroid Touch Input
- 🖼️ **RetroArch overlays** authentiques
- 📦 **Cache automatique** ZIP/7Z
- 🌐 **WebServer intégré** (port 7777)
- 📱 **Interface Compose** moderne

---

## 🏗️ ARCHITECTURE

RetroPlay fait partie d'un écosystème de 2 apps actives:

1. **ChatAI-Android** (port 8888) - App principale avec AI + émulation
2. **RetroPlay-Android** (port 7777) - App émulation pure ← **Cette app**

Les deux apps partagent le répertoire `/storage/emulated/0/GameLibrary-Data/` pour les ROMs et assets.

**Voir:** `ARCHITECTURE_ACTUELLE.md` pour plus de détails

---

## 📦 INSTALLATION

### Prérequis

- Android 7.0+ (API 24)
- Android Studio ou ligne de commande Gradle
- Device Android ou émulateur

### Compilation

```bash
cd RetroPlay-Android
.\gradlew clean assembleDebug
```

### Installation

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Premier Lancement

1. L'app demande les permissions de stockage (obligatoire)
2. Le WebServer démarre automatiquement (port 7777)
3. Les overlays RetroArch s'installent
4. La liste des jeux s'affiche

---

## 🎮 CONSOLES SUPPORTÉES

### Émulation Native (27 cores)

**Nintendo:** NES, SNES, N64, GB, GBC, GBA  
**Sony:** PSX, PSP  
**Sega:** Genesis/Megadrive, Sega CD, Master System, Game Gear, 32X  
**Atari:** 2600, 5200, 7800, Lynx  
**Autres:** Neo Geo Pocket, WonderSwan, PC Engine  
**Arcade:** MAME 2003/Plus, FBNeo, FBA CPS1/CPS2  
**Bonus:** Dreamcast (Flycast)

### Émulation Web (EmulatorJS)

Toutes les consoles ci-dessus + Virtual Boy, Jaguar, Saturn, etc.

---

## 📂 STRUCTURE DES DONNÉES

### Sur le Device

```
/storage/emulated/0/
├── RetroPlay-Files/              # Fichiers propres à RetroPlay
│   └── sites/gamelibrary/
│       ├── index.html
│       ├── emulator.html
│       └── loader_*.js
│
└── GameLibrary-Data/             # Partagé avec ChatAI
    ├── data/                     # EmulatorJS
    │   ├── cores/
    │   ├── bios/
    │   └── loader.js
    ├── nes/                      # ROMs NES
    ├── snes/                     # ROMs SNES
    ├── psx/                      # ROMs PSX
    └── ...
```

---

## 🔧 CONFIGURATION

### Ports Réseau

- **WebServer:** 7777
- **Accès local:** `http://localhost:7777/`
- **Accès réseau:** `http://[DEVICE_IP]:7777/`

### Chemins Importants

- **ROMs:** `/storage/emulated/0/GameLibrary-Data/{console}/`
- **BIOS:** `/storage/emulated/0/GameLibrary-Data/data/bios/`
- **Saves:** `/storage/emulated/0/GameLibrary-Data/saves/{console}/`
- **Cheats:** `/storage/emulated/0/GameLibrary-Data/cheats/`
- **Overlays:** Installés automatiquement au premier lancement

---

## 📚 DOCUMENTATION

### Guides Principaux

- `AUDIT_COMPLET_RETROPLAY.md` - Audit technique complet
- `MODIFICATION_PAGES_EXTERNES_SECURITE.md` - Procédures de sécurité
- `RETROPLAY_FINAL.md` - Documentation fonctionnelle
- `CONFIGURATION_FINALE.md` - Configuration réseau et chemins

### Scripts Utiles

- `backup_pages_externes.ps1` - Backup automatique
- `restore_pages_externes.ps1` - Restauration sécurisée
- `download_arcade_cores.ps1` - Téléchargement cores arcade
- `download_n64_cores.ps1` - Téléchargement cores N64

### Architecture

- `ARCHITECTURE_ACTUELLE.md` - Architecture globale (ChatAI + RetroPlay)
- `GAMELIBRARY_OBSOLETE.md` - Info sur GameLibrary (obsolète)

---

## 🛠️ DÉVELOPPEMENT

### Structure du Projet

```
RetroPlay-Android/
├── app/
│   ├── src/main/
│   │   ├── java/com/retroplay/
│   │   │   ├── GameListActivity.java
│   │   │   ├── GameDetailsActivity.java
│   │   │   ├── NativeComposeEmulatorActivity.kt
│   │   │   ├── WebViewActivity.java
│   │   │   ├── WebServer.java
│   │   │   └── ...
│   │   ├── jniLibs/arm64-v8a/        # 27 cores .so
│   │   ├── assets/                    # Overlays, BIOS, etc.
│   │   └── res/
│   └── build.gradle
├── lemuroid-touchinput/              # Gamepads virtuels
├── retrograde-util/                  # Utilitaires Libretro
└── build.gradle
```

### Technologies

- **Kotlin** 2.0.21 + **Java** 8
- **Jetpack Compose** (UI moderne)
- **LibretroDroid** 0.13.0 (émulation native)
- **Material 3** Design
- **OkHttp** 4.9.3 (HTTP client)
- **Glide** 4.16.0 (images)

---

## 🐛 DÉBOGAGE

### Logs

```powershell
# Logs généraux
adb logcat GameListActivity:I WebServer:I NativeComposeEmulator:I "*:E"

# Logs LibretroDroid
adb logcat libretrodroid:* "Libretro Core:*" "*:E"
```

### Problèmes Courants

**ROMs ne se chargent pas:**
```bash
# Vérifier permissions
adb shell dumpsys package com.retroplay | findstr MANAGE_EXTERNAL_STORAGE

# Vérifier que les ROMs existent
adb shell ls /storage/emulated/0/GameLibrary-Data/nes/
```

**WebServer ne démarre pas:**
```bash
# Vérifier le port
adb shell netstat -tulpn | findstr 7777

# Vérifier les logs
adb logcat WebServer:* "*:E"
```

---

## 🔄 SYNCHRONISATION AVEC CHATAI

RetroPlay partage certaines fonctionnalités avec ChatAI-Android:

- **WebServer.java** - Synchronisé depuis ChatAI
- **Loaders JavaScript** - Partagés et adaptés
- **GameLibrary-Data/** - Répertoire commun

**Workflow:**
1. Développement sur ChatAI (source)
2. Adaptation pour RetroPlay (ports, chemins, packages)
3. Tests sur les 2 apps
4. Commit

---

## 📄 LICENCE

Ce projet est dérivé de ChatAI-Android et utilise:
- LibretroDroid (divers cores avec licences open source)
- Lemuroid Touch Input (Apache 2.0)
- EmulatorJS (GPL-3.0)

---

## 🎯 OBJECTIFS FUTURS

- [ ] Support Android TV
- [ ] Multiplayer local
- [ ] Cloud saves
- [ ] Achievement system
- [ ] Traductions multilingues
- [ ] Thèmes personnalisables

---

## 🤝 CONTRIBUTION

Ce projet est en développement actif. Les contributions sont bienvenues!

---

## 📞 SUPPORT

**Documentation:** Voir les fichiers `.md` dans le répertoire du projet  
**Architecture:** `ARCHITECTURE_ACTUELLE.md`  
**Sécurité:** `MODIFICATION_PAGES_EXTERNES_SECURITE.md`

---

**Créé le:** 20 octobre 2025  
**Dernière mise à jour:** 27 octobre 2025  
**Version:** 1.0  

**🎮 Bon gaming sur RetroPlay! 🚀**

