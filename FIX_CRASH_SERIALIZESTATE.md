# Fix Crash serializeState() - SIGSEGV

**Date:** 2025-01-XX  
**Problème:** Crash SIGSEGV dans `libretrodroid::LibretroDroid::serializeState()`  
**Cause:** Appel à `serializeState()` avant que le core soit chargé ou après destruction

---

## 🔍 ANALYSE DU CRASH

### Stack Trace
```
Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x50
#00 pc 0000000000103430  liblibretrodroid.so (libretrodroid::LibretroDroid::serializeState()+48)
#01 pc 00000000000f0ab4  liblibretrodroid.so (Java_com_swordfish_libretrodroid_LibretroDroid_serializeState+48)
```

**Problème:** Accès à l'adresse mémoire invalide (0x50 = 80 bytes depuis un pointeur null), indiquant que le core n'est pas initialisé.

---

## ✅ SOLUTION IMPLÉMENTÉE

### 1. Protection dans GLRetroView.kt

**Ajout de vérifications dans `serializeState()`:**
```kotlin
fun serializeState(): ByteArray = runOnGLThread {
    if (!isGameLoaded || isAborted) {
        Log.w("GLRetroView", "serializeState called but game not loaded or aborted")
        return@runOnGLThread ByteArray(0)
    }
    try {
        LibretroDroid.serializeState()
    } catch (e: Exception) {
        Log.e("GLRetroView", "serializeState failed: ${e.message}", e)
        ByteArray(0)
    }
}

fun isGameLoaded(): Boolean = isGameLoaded && !isAborted
```

**Avantages:**
- Vérification de l'état avant l'appel natif
- Try-catch pour capturer les erreurs
- Retourne `ByteArray(0)` si le core n'est pas prêt
- Méthode publique `isGameLoaded()` pour vérifier l'état

### 2. Protection dans NativeComposeEmulatorActivity.kt

**Ajout de vérification dans `saveGameState()`:**
```kotlin
private fun saveGameState(slot: Int) {
    try {
        // Vérifier que le core est chargé avant de sauvegarder
        if (!retroView.isGameLoaded()) {
            Log.w(TAG, "[$console] Cannot save state: game not loaded yet")
            runOnUiThread {
                Toast.makeText(this, "Game not ready for saving", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        val stateData = retroView.serializeState()
        
        // Vérifier que les données sont valides
        if (stateData.isEmpty()) {
            Log.w(TAG, "[$console] serializeState returned empty data")
            runOnUiThread {
                Toast.makeText(this, "Save failed: empty state", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        saveFile.writeBytes(stateData)
        // ...
    }
}
```

### 3. Protection dans RetroArchEmulatorActivity.kt

**Même protection que NativeComposeEmulatorActivity.kt**

### 4. Protection dans RewindManager.kt

**Ajout de vérifications dans `captureState()` et `ensureSupport()`:**
```kotlin
private suspend fun captureState() {
    // Vérifier que le core est chargé avant de capturer
    if (!retroView.isGameLoaded()) {
        Log.d(TAG, "Skipping state capture: game not loaded")
        return
    }
    
    val state = try {
        retroView.serializeState()
    } catch (e: Exception) {
        Log.e(TAG, "serializeState failed during capture", e)
        ByteArray(0)
    }
    // ...
}

suspend fun ensureSupport(): Boolean {
    // ...
    // Vérifier que le core est chargé avant de tester
    if (!retroView.isGameLoaded()) {
        Log.d(TAG, "Skipping support check: game not loaded")
        _isSupported.value = false
        return false
    }
    // ...
}
```

### 5. Protection dans RunAheadManager.kt

**Ajout de vérifications dans `ensureInitialized()` et `onBeforeFrame()`:**
```kotlin
private fun ensureInitialized(force: Boolean = false): Boolean {
    // ...
    // Vérifier que le core est chargé avant d'initialiser
    if (!retroView.isGameLoaded()) {
        Log.d(TAG, "Skipping run-ahead initialization: game not loaded")
        initialized = false
        supported = false
        return false
    }
    // ...
}

override fun onBeforeFrame(): Boolean {
    // ...
    if (index == 0) {
        // Vérifier que le core est chargé avant de capturer
        if (!retroView.isGameLoaded()) {
            Log.w(TAG, "Run-ahead: game not loaded, disabling feature")
            supported = false
            return true // avoid stepping twice
        }
        // ...
    }
}
```

---

## 📋 FICHIERS MODIFIÉS

1. **GLRetroView.kt**
   - Ajout vérification `isGameLoaded` et `isAborted` dans `serializeState()`
   - Ajout méthode publique `isGameLoaded()`
   - Ajout try-catch pour sécurité

2. **NativeComposeEmulatorActivity.kt**
   - Vérification avant `saveGameState()`
   - Vérification données vides après `serializeState()`

3. **RetroArchEmulatorActivity.kt**
   - Même protection que NativeComposeEmulatorActivity.kt

4. **RewindManager.kt**
   - Vérification dans `captureState()`
   - Vérification dans `ensureSupport()`

5. **RunAheadManager.kt**
   - Vérification dans `ensureInitialized()`
   - Vérification dans `onBeforeFrame()`

---

## 🧪 TESTS RECOMMANDÉS

1. **Test sauvegarde immédiate:**
   - Lancer un jeu
   - Sauvegarder immédiatement (avant chargement complet)
   - Vérifier qu'aucun crash ne se produit

2. **Test sauvegarde après destruction:**
   - Lancer un jeu
   - Fermer l'activité rapidement
   - Vérifier qu'aucun crash ne se produit

3. **Test Rewind:**
   - Lancer un jeu
   - Activer Rewind immédiatement
   - Vérifier qu'aucun crash ne se produit

4. **Test Run-Ahead:**
   - Lancer un jeu
   - Activer Run-Ahead immédiatement
   - Vérifier qu'aucun crash ne se produit

---

## 📝 NOTES

- **Double protection:** Vérification à la fois dans `GLRetroView.serializeState()` et dans les appels
- **Graceful degradation:** Retourne `ByteArray(0)` au lieu de crasher
- **User feedback:** Messages Toast pour informer l'utilisateur
- **Logs détaillés:** Logs pour debugging

---

**Dernière mise à jour:** 2025-01-XX

