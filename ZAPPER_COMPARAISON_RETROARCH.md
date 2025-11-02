# 🔍 Test Comparatif - Zapper RetroPlay vs RetroArch Officiel

**Date:** 2025-11-01 04:18  
**Objectif:** Déterminer si le Zapper fonctionne dans RetroArch officiel  
**Raison:** RetroPlay ne tire pas malgré tous les fixes appliqués

---

## 🎯 POURQUOI CE TEST?

### Ce que nous avons confirmé dans RetroPlay:
- ✅ Coordonnées transmises (X, Y changent avec le mouvement du doigt)
- ✅ PRESSED=1 actif pendant le touch
- ✅ FCEUmm reçoit les valeurs (logs NATIVE POINTER)
- ✅ Core options configurées (`fceumm_zapper_mode = touchscreen`)
- ✅ Device type configuré (`setControllerType(1, 258)` = RETRO_DEVICE_ZAPPER)
- ✅ Fix `mousedata[3] = 0` compilé et installé
- ❌ **MAIS les canards ne tombent JAMAIS**

### Questions non résolues:
1. Est-ce un bug de FCEUmm?
2. Est-ce un bug de LibretroDroid?
3. Est-ce un bug de Duck Hunt (ROM)?
4. Est-ce quelque chose de spécifique à notre implémentation?

**→ Tester avec RetroArch officiel répondra à ces questions!**

---

## 📋 PROCÉDURE DE TEST

### Étape 1: Installer RetroArch officiel

**Depuis Google Play Store:**
1. Chercher "RetroArch"
2. Installer (développeur: Libretro)
3. Lancer l'app

**Ou télécharger l'APK:**
- https://buildbot.libretro.com/stable/1.19.1/android/RetroArch-aarch64.apk

### Étape 2: Télécharger le core FCEUmm

1. Dans RetroArch: **Main Menu** → **Online Updater** → **Core Downloader**
2. Chercher **"Nintendo - NES / Famicom (FCEUmm)"**
3. Télécharger et installer

### Étape 3: Configurer les core options

1. **Main Menu** → **Settings** → **Core**
2. Ou charger Duck Hunt directement et:
   - **Quick Menu** (pendant le jeu) → **Options**
   - Configurer:
     ```
     fceumm_zapper_mode = touchscreen
     fceumm_show_crosshair = enabled
     ```

### Étape 4: Tester Duck Hunt

1. Charger Duck Hunt
2. **Toucher l'écran**
3. Observer:
   - ❓ Crosshair visible?
   - ❓ Flash blanc?
   - ❓ Son de coup de feu?
   - ❓ Canards tombent?

---

## 🎯 RÉSULTATS ATTENDUS

### Scénario A: Zapper fonctionne dans RetroArch officiel ✅

**Conclusion:** Le problème est dans **RetroPlay** ou **LibretroDroid**

**Prochaines étapes:**
- Comparer la configuration RetroArch vs RetroPlay
- Vérifier les différences d'implémentation
- Possiblement forker/modifier LibretroDroid

### Scénario B: Zapper NE fonctionne PAS dans RetroArch officiel ❌

**Conclusion:** Le problème est dans **FCEUmm** ou **Duck Hunt**

**Prochaines étapes:**
- Reporter le bug à l'équipe LibRetro/FCEUmm
- Essayer d'autres jeux Zapper (Hogan's Alley, Wild Gunman)
- Essayer d'autres cores NES (Mesen, Nestopia)

### Scénario C: Zapper fonctionne PARTIELLEMENT

**Conclusion:** Différences de configuration ou de timing

**Prochaines étapes:**
- Analyser les settings RetroArch en détail
- Comparer les core options actives
- Vérifier les différences de performance/timing

---

## 📝 INFORMATIONS À NOTER

Pendant le test RetroArch, noter:

### Configuration:
- Version RetroArch: `?`
- Version FCEUmm: `?`
- Core options actives: `?`

### Comportement:
- Crosshair visible: ❓ Oui / Non
- Touch détecté: ❓ Oui / Non
- Flash blanc: ❓ Oui / Non
- Son de tir: ❓ Oui / Non
- Canards tombent: ❓ Oui / Non

### Logs (optionnel):
Si possible, capturer les logs RetroArch:
```bash
adb logcat | Select-String "RetroArch|fceumm|zapper"
```

---

## 🔗 LIENS UTILES

- **RetroArch Android:** https://play.google.com/store/apps/details?id=com.retroarch
- **Buildbot APK:** https://buildbot.libretro.com/stable/
- **Documentation Zapper:** https://docs.libretro.com/library/fceumm/#zapper-emulation
- **Forum LibRetro:** https://forums.libretro.com/

---

## 📊 APRÈS LE TEST

Selon les résultats, nous pourrons:

1. **Si ça marche dans RetroArch:**
   - Comparer leur implémentation avec la nôtre
   - Identifier ce qui manque dans RetroPlay

2. **Si ça ne marche pas dans RetroArch:**
   - Documenter le bug
   - Reporter à LibRetro/FCEUmm
   - Passer à autre chose (Zapper = feature avancée optionnelle)

---

**Date:** 2025-11-01 04:18  
**Statut:** 🔬 Test comparatif recommandé avant d'aller plus loin

