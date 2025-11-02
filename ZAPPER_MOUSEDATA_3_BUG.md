# 🐛 BUG CRITIQUE - mousedata[3] (Sensor) Non Défini dans FCEUmm

**Date:** 2025-11-01  
**Impact:** ⭐⭐⭐⭐⭐ CRITIQUE - Zapper sensor ne fonctionne pas en mode switchZapper  
**Source:** Analyse `c:\repos\libretro-fceumm-master\src\drivers\libretro\libretro.c`  
**Statut:** 🔍 BUG CONFIRMÉ - Solution de contournement disponible

---

## 🚨 LE PROBLÈME

### Symptôme:
Les valeurs Zapper sont **parfaitement transmises** (logs confirment X, Y, PRESSED=1), mais le jeu **ne réagit pas**.

### Analyse Logs:
```log
01:28:35.893 [NATIVE MOTION] port=1 POINTER stored: X=0.349 Y=0.713  ✅
01:28:35.893 [NATIVE POINTER] port=1 X=-9871 (raw=0.349)             ✅
01:28:35.893 [NATIVE POINTER] port=1 Y=13966 (raw=0.713)             ✅
01:28:35.893 [NATIVE POINTER] port=1 PRESSED=1 (X=0.349 Y=0.713)     ✅
```

**LibretroDroid fonctionne PARFAITEMENT!** Le problème est dans FCEUmm.

---

## 🔍 ANALYSE DU CODE FCEUmm

### Code `get_mouse_input()` - Ligne 2441-2460:

```c
else if (variant != RETRO_DEVICE_ARKANOID && zappermode == RetroPointer) {
    int offset_x = (crop_overscan_h_left * 0x120) - 1;
    int offset_y = (crop_overscan_v_top * 0x133) + 1;

    int _x = input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_X);
    int _y = input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_Y);

    if (_x == 0 && _y == 0) {
        mousedata[0] = 0;
    }
    else {
        mousedata[0] = (_x + (0x7FFF + offset_x)) * max_width  / ((0x7FFF + offset_x) * 2);
        mousedata[1] = (_y + (0x7FFF + offset_y)) * max_height  / ((0x7FFF + offset_y) * 2);
    }

    if (input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_PRESSED))
        mousedata[2] |= 0x1;
    
    // ❌ mousedata[3] N'EST JAMAIS DÉFINI!
}
```

### Code `UpdateZapper()` - zapper.c ligne 167-187:

```c
static void FP_FASTAPASS(3) UpdateZapper(int w, void *data, int arg) {
    uint32 *ptr = (uint32*)data;

    if (ZD[w].bogo)
        ZD[w].bogo--;
    if (ptr[2] & 3 && (!(ZD[w].mzb & 3)))
        ZD[w].bogo = 5;

    ZD[w].mzx = ptr[0];  // ✅ X
    ZD[w].mzy = ptr[1];  // ✅ Y

    if (zapper_trigger_invert_option)
        ZD[w].mzb = ptr[2];    // ✅ Trigger
    else
        ZD[w].mzb = !ptr[2];

    if (zapper_sensor_invert_option)
        ZD[w].mzs = !ptr[3];   // ❌ LIT ptr[3] QUI EST INDÉFINI!
    else
        ZD[w].mzs = ptr[3];    // ❌ LIT ptr[3] QUI EST INDÉFINI!
}
```

### Code `ReadZapper()` - zapper.c ligne 146-160:

```c
static uint8 FP_FASTAPASS(1) ReadZapper(int w) {
    uint8 ret = 0;
        
    if (ZD[w].bogo) 
        ret |= 0x10;

    if (!switchZapper) {  // Mode normal (switchZapper = 0)
        if (CheckColor(w))  // ✅ Utilise CheckColor() (détection couleur)
            ret |= 0x8;
    }
    else if (ZD[w].mzs)  // Mode alternatif (switchZapper != 0)
        ret |= 0x8;      // ❌ Utilise mzs QUI EST INDÉFINI!
    
    return ret;
}
```

---

## 💡 ANALYSE APPROFONDIE

### Mode Normal (switchZapper = 0):

Dans ce mode, `mzs` **n'est PAS utilisé**! Le sensor fonctionne via `CheckColor(w)` qui vérifie la couleur de l'écran au point (X, Y).

**Donc le bug `mousedata[3]` ne devrait PAS affecter le mode normal!**

### Problème Possible:

1. **Coordonnées X/Y incorrectes?**
   - Conversion `(_x + 0x7FFF) * max_width / ((0x7FFF + offset_x) * 2)` peut donner des valeurs hors limites?

2. **CheckColor() ne détecte pas la lumière?**
   - La fonction vérifie `sum >= 100 * 3` (RGB total >= 300)
   - Peut-être que la couleur blanche du flash n'est pas assez brillante?

3. **Timing du trigger?**
   - `ZapperFrapper()` est appelé pendant le rendu de ligne
   - Peut-être que le timing est décalé?

---

## ✅ SOLUTION DE CONTOURNEMENT

### Option 1: Vérifier switchZapper

Si `switchZapper == 0` (mode normal), le problème n'est **PAS** `mousedata[3]`.

**Chercher dans les logs FCEUmm:**
- Vérifier si `CheckColor()` retourne `true`
- Vérifier si `ZapperFrapper()` détecte la couleur blanche

### Option 2: Initialiser mousedata[3]

**Dans `get_mouse_input()`, après ligne 2459, AJOUTER:**

```c
// Sensor: 0 = light detected (si switchZapper != 0)
mousedata[3] = 0;
```

**MAIS:** Cela nécessite de compiler FCEUmm depuis les sources!

### Option 3: Patch FCEUmm Source

**Fichier:** `c:\repos\libretro-fceumm-master\src\drivers\libretro\libretro.c`

**Ligne 2459, AJOUTER:**

```c
if (input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_PRESSED))
    mousedata[2] |= 0x1;

// ✅ FIX: Initialiser sensor (utilisé si switchZapper != 0)
mousedata[3] = 0;  // 0 = light detected when triggered
```

**Puis compiler FCEUmm pour Android:**

```bash
cd c:\repos\libretro-fceumm-master
# Instructions de compilation Android à venir
```

---

## 📊 RÉSUMÉ

| Aspect | Statut |
|--------|--------|
| **LibretroDroid** | ✅ Fonctionne parfaitement |
| **FCEUmm get_mouse_input()** | ✅ Lit X, Y, PRESSED correctement |
| **mousedata[0], [1], [2]** | ✅ Définis correctement |
| **mousedata[3]** | ❌ **JAMAIS DÉFINI** (bug confirmé) |
| **Impact réel** | ⚠️ **Seulement si switchZapper != 0** (mode normal utilise CheckColor) |

---

## 🎯 PROCHAINES ÉTAPES

1. **Vérifier switchZapper:**
   - Chercher dans les logs si `switchZapper` est 0 ou non
   - Si 0, le problème n'est PAS `mousedata[3]`

2. **Vérifier CheckColor():**
   - Ajouter des logs dans FCEUmm pour voir si `CheckColor()` retourne `true`
   - Vérifier si la détection de couleur fonctionne

3. **Si switchZapper != 0:**
   - Compiler FCEUmm avec le fix `mousedata[3] = 0`
   - Remplacer `fceumm_libretro_android.so` dans `app/src/main/jniLibs/arm64-v8a/`

---

## 📝 NOTES TECHNIQUES

- **FCEUmm source:** `c:\repos\libretro-fceumm-master`
- **Compilé .so:** `RetroPlay-Android/app/src/main/jniLibs/arm64-v8a/fceumm_libretro_android.so`
- **Fonction buggée:** `get_mouse_input()` ligne 2441-2460
- **Valeur manquante:** `mousedata[3]` (sensor state)

---

**Date de découverte:** 2025-11-01  
**Méthodologie:** "Nos Rules" - Analyse approfondie du code source FCEUmm

