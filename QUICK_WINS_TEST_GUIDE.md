# 🧪 GUIDE DE TEST - Quick Wins #1 & #2

**Date:** 31 octobre 2025  
**Features testées:** Fast Forward Toggle + Audio Mute Toggle  
**Durée estimée:** 15-20 minutes

---

## 📋 FEATURES IMPLÉMENTÉES

### Quick Win #1: Fast Forward Toggle ⚡
- **Hotkey:** `toggle_fast_forward` (mappé aux overlays RetroArch)
- **UI:** Bouton dans QuickMenu
- **Speed:** 2x par défaut (configurable)
- **Persistance:** État sauvegardé dans SharedPreferences

### Quick Win #2: Audio Mute Toggle 🔇
- **Hotkey:** `audio_mute_toggle` (mappé aux overlays RetroArch)
- **UI:** Bouton dans QuickMenu
- **Persistance:** État sauvegardé + appliqué au démarrage
- **Visual:** Bouton rouge si muted, vert si ON

---

## 🔧 COMPILATION

### Étape 1: Compiler l'APK
```powershell
cd C:\androidProject\ChatAI-Android-beta\RetroPlay-Android
.\gradlew assembleDebug
```

### Étape 2: Installer sur device
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Durée:** ~2-3 minutes

---

## 🎮 TESTS À EFFECTUER

### TEST 1: Fast Forward Toggle (5 min)

#### 1.1 - Test Hotkey
1. Lancer un jeu NES/SNES (Super Mario Bros, Street Fighter II)
2. Appuyer sur le hotkey Fast Forward (vérifier mapping overlay)
3. **Vérifier:** Toast "Fast Forward: 2x" apparaît
4. **Vérifier:** Jeu accélère à 2x (visible sur animations)
5. Ré-appuyer sur le hotkey
6. **Vérifier:** Toast "Normal Speed" apparaît
7. **Vérifier:** Jeu revient à vitesse normale

**Résultat attendu:** ✅ Accélération visible + Toasts corrects

#### 1.2 - Test QuickMenu
1. Appuyer sur **BACK** pour ouvrir QuickMenu
2. **Vérifier:** Bouton "FAST FORWARD: OFF" visible (couleur marron)
3. Appuyer sur le bouton
4. **Vérifier:** QuickMenu se ferme + Toast "Fast Forward: 2x"
5. **Vérifier:** Jeu accélère immédiatement
6. Rouvrir QuickMenu (BACK)
7. **Vérifier:** Bouton affiche "FAST FORWARD: ON (2x)" (couleur rouge/orange)
8. Appuyer à nouveau
9. **Vérifier:** Jeu revient à vitesse normale

**Résultat attendu:** ✅ Bouton toggle + Couleur change + Vitesse change

#### 1.3 - Test Persistance
1. Activer Fast Forward
2. **Quitter le jeu** (bouton Quit)
3. **Relancer le même jeu**
4. **Vérifier:** Fast Forward est toujours OFF (comportement normal)
   - NOTE: L'état actif n'est PAS persisté volontairement (retour normal au lancement)
   - Seul le **ratio** (2x/3x/4x) est persisté

**Résultat attendu:** ✅ Fast Forward OFF au lancement (normal)

---

### TEST 2: Audio Mute Toggle (5 min)

#### 2.1 - Test Hotkey
1. Lancer un jeu avec musique (Super Mario Bros, Sonic)
2. **Vérifier:** Audio joue normalement
3. Appuyer sur le hotkey Audio Mute (vérifier mapping overlay)
4. **Vérifier:** Toast "Audio Muted" apparaît
5. **Vérifier:** Audio coupe immédiatement (silence total)
6. Ré-appuyer sur le hotkey
7. **Vérifier:** Toast "Audio Unmuted" apparaît
8. **Vérifier:** Audio revient immédiatement

**Résultat attendu:** ✅ Audio mute/unmute instantané + Toasts corrects

#### 2.2 - Test QuickMenu
1. Appuyer sur **BACK** pour ouvrir QuickMenu
2. **Vérifier:** Bouton "AUDIO: ON" visible (couleur verte)
3. Appuyer sur le bouton
4. **Vérifier:** QuickMenu reste ouvert (pas de fermeture)
5. **Vérifier:** Audio coupe immédiatement
6. **Vérifier:** Bouton affiche "AUDIO: MUTED" (couleur rouge)
7. Appuyer à nouveau sur le bouton
8. **Vérifier:** Audio revient + Bouton redevient vert "AUDIO: ON"

**Résultat attendu:** ✅ Bouton toggle + Couleur change + Audio change

#### 2.3 - Test Persistance (CRITIQUE)
1. Mute l'audio (bouton ou hotkey)
2. **Vérifier:** Toast "Audio Muted" + Silence
3. **Quitter le jeu** (bouton Quit)
4. **Relancer le MÊME jeu**
5. **Vérifier:** Audio est toujours MUTED au démarrage (silence)
6. Ouvrir QuickMenu
7. **Vérifier:** Bouton affiche "AUDIO: MUTED" (rouge)
8. Unmute l'audio
9. **Quitter le jeu**
10. **Relancer le jeu**
11. **Vérifier:** Audio joue normalement au démarrage

**Résultat attendu:** ✅ État audio persisté entre sessions

---

### TEST 3: Combinaisons & Edge Cases (5 min)

#### 3.1 - Fast Forward + Audio Mute simultané
1. Activer Fast Forward (2x)
2. Mute l'audio
3. **Vérifier:** Jeu à 2x + silence
4. Unmute l'audio
5. **Vérifier:** Jeu à 2x + audio accéléré (pitch normal ou high pitch selon core)
6. Désactiver Fast Forward
7. **Vérifier:** Jeu normal + audio normal

**Résultat attendu:** ✅ Les deux features fonctionnent indépendamment

#### 3.2 - Rotation device (Portrait → Landscape)
1. Activer Fast Forward + Mute audio
2. **Tourner le device** (portrait → landscape ou inverse)
3. **Vérifier:** Fast Forward actif (jeu à 2x)
4. **Vérifier:** Audio muted (silence)
5. Ouvrir QuickMenu
6. **Vérifier:** Boutons affichent états corrects (FF ON, AUDIO MUTED)

**Résultat attendu:** ✅ États préservés après rotation

#### 3.3 - Lancer jeu différent
1. Lancer Super Mario Bros (NES)
2. Mute l'audio
3. **Quitter**
4. Lancer Sonic (Genesis)
5. **Vérifier:** Audio joue normalement (état mute NON partagé entre jeux)
6. Mute l'audio dans Sonic
7. **Quitter**
8. **Relancer Super Mario Bros**
9. **Vérifier:** Audio muted (état persisté PAR JEU)

**Résultat attendu:** ⚠️ Vérifier si persistance est globale ou par-jeu
- **Comportement actuel:** Probablement global (même SharedPreferences)
- **Comportement souhaité:** TBD (à décider)

---

## 📊 LOGS À VÉRIFIER

### Logs Fast Forward
```powershell
adb logcat | Select-String "FAST_FORWARD"
```

**Attendu:**
```
I/RetroArchEmulator: [FAST_FORWARD] ENABLED (2x)
I/RetroArchEmulator: [FAST_FORWARD] DISABLED (1x)
```

### Logs Audio Mute
```powershell
adb logcat | Select-String "AUDIO"
```

**Attendu:**
```
I/RetroArchEmulator: [AUDIO] MUTED
I/RetroArchEmulator: [AUDIO] UNMUTED
```

### Logs SharedPreferences
```powershell
adb logcat | Select-String "emulation_"
```

**Attendu:**
```
(Pas de logs explicites, mais pas d'erreurs non plus)
```

---

## ✅ CHECKLIST FINALE

### Fast Forward ⚡
- [ ] Hotkey fonctionne (toggle ON/OFF)
- [ ] Bouton QuickMenu fonctionne
- [ ] Bouton change de couleur (marron → rouge)
- [ ] Vitesse change visiblement (2x)
- [ ] Toast informatif apparaît
- [ ] Logs "[FAST_FORWARD]" présents

### Audio Mute 🔇
- [ ] Hotkey fonctionne (toggle ON/OFF)
- [ ] Bouton QuickMenu fonctionne
- [ ] Bouton change de couleur (vert → rouge)
- [ ] Audio mute/unmute instantané
- [ ] Toast informatif apparaît
- [ ] État persisté entre sessions
- [ ] Logs "[AUDIO]" présents

### Edge Cases
- [ ] FF + Mute simultané OK
- [ ] Rotation préserve états
- [ ] Pas de crash/freeze
- [ ] QuickMenu affiche états corrects

---

## 🐛 BUGS POTENTIELS À SURVEILLER

### Bug #1: Audio ne s'applique pas au démarrage
**Symptôme:** Audio joue même si état "muted" sauvegardé

**Cause possible:** `retroView.audioEnabled = !audioMuted` appelé trop tôt

**Fix:** Déplacer après `lifecycle.addObserver(retroView)` ✅ (déjà fait)

### Bug #2: Fast Forward reste actif après crash
**Symptôme:** Jeu démarre en mode 2x après un crash

**Cause possible:** État `isFastForwardActive` persisté

**Fix:** Ne persister QUE le ratio, pas l'état actif ✅ (déjà fait)

### Bug #3: QuickMenu ne reflète pas l'état
**Symptôme:** Bouton dit "OFF" mais feature est ON

**Cause possible:** États `isFastForwardActive`/`audioMuted` pas passés au Dialog

**Fix:** Passer les states en paramètres ✅ (déjà fait)

---

## 📝 RAPPORT DE TEST

**Après les tests, remplir:**

| Feature | Test | Résultat | Notes |
|---------|------|----------|-------|
| FF Hotkey | 1.1 | ⬜ PASS / ❌ FAIL | |
| FF QuickMenu | 1.2 | ⬜ PASS / ❌ FAIL | |
| FF Persistance | 1.3 | ⬜ PASS / ❌ FAIL | |
| Audio Hotkey | 2.1 | ⬜ PASS / ❌ FAIL | |
| Audio QuickMenu | 2.2 | ⬜ PASS / ❌ FAIL | |
| Audio Persistance | 2.3 | ⬜ PASS / ❌ FAIL | |
| Combinaison FF+Audio | 3.1 | ⬜ PASS / ❌ FAIL | |
| Rotation | 3.2 | ⬜ PASS / ❌ FAIL | |
| Multi-jeux | 3.3 | ⬜ PASS / ❌ FAIL | |

---

## 🎯 PRÊT À COMPILER

**Commande rapide:**
```powershell
cd C:\androidProject\ChatAI-Android-beta\RetroPlay-Android
.\gradlew assembleDebug && adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Lancer les tests et revenez avec les résultats !** 🚀

