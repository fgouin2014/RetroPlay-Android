# 🔍 AUDIT - CoreMetadataManager.fetchCoresMetadata()

**Date:** 20 novembre 2025  
**Objectif:** Vérifier la conformité avec les règles du projet concernant la détection automatique des cores

---

## ✅ ÉTAT ACTUEL

### 1. **Implémentation CoreMetadataManager**
- ✅ **Fichier:** `CoreMetadataManager.kt`
- ✅ **Fonctionnalités:**
  - Détection automatique depuis `https://buildbot.libretro.com/nightly/android/latest/cores.json`
  - Cache local (24h) pour performance
  - Support NIGHTLY et STABLE builds
  - Parsing complet des métadonnées (version, license, firmware, etc.)
  - Conversion en `CoreInfo` pour compatibilité

### 2. **Méthode Recommandée**
- ✅ **Fichier:** `CoreDownloader.kt`
- ✅ **Méthode:** `getAvailableCoresFromMetadata()`
  - Utilise `CoreMetadataManager.fetchCoresMetadata()`
  - Fallback sur liste hardcodée en cas d'erreur réseau
  - Suspend function (coroutines)

### 3. **Méthode Dépréciée**
- ⚠️ **Fichier:** `CoreDownloader.kt`
- ⚠️ **Méthode:** `getAvailableCores()` (marquée `@Deprecated`)
  - Liste hardcodée de cores
  - Ne détecte pas automatiquement les nouveaux cores
  - Nécessite mise à jour manuelle du code

---

## ❌ PROBLÈMES IDENTIFIÉS

### 1. **CoreManagerActivity utilise la méthode dépréciée**
- **Fichier:** `CoreManagerActivity.kt`
- **Ligne 24:** `private val cores = CoreDownloader.getAvailableCores()`
- **Problème:** Utilise la liste hardcodée au lieu de la détection automatique
- **Impact:** Les nouveaux cores (ex: VICE pour C64) ne sont pas visibles dans la liste

### 2. **Fallback dans getAvailableCoresFromMetadata**
- **Fichier:** `CoreDownloader.kt`
- **Ligne 109:** `return@withContext getAvailableCores()`
- **Problème:** Fallback sur méthode dépréciée (acceptable pour compatibilité)
- **Impact:** Si erreur réseau, utilise liste hardcodée (limite mais acceptable)

---

## ✅ CORRECTIONS NÉCESSAIRES

### 1. **Migrer CoreManagerActivity vers CoreMetadataManager**

**Avant:**
```kotlin
private val cores = CoreDownloader.getAvailableCores()
```

**Après:**
```kotlin
private var cores: List<CoreDownloader.CoreInfo> = emptyList()

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_core_manager)
    
    // Charger les cores depuis CoreMetadataManager
    loadCoresFromMetadata()
    
    // ... reste du code
}

private fun loadCoresFromMetadata() {
    val progressDialog = ProgressDialog(this)
    progressDialog.setTitle("Loading Cores")
    progressDialog.setMessage("Fetching cores from buildbot...")
    progressDialog.setCancelable(false)
    progressDialog.show()
    
    lifecycleScope.launch {
        try {
            cores = CoreDownloader.getAvailableCoresFromMetadata(
                this@CoreManagerActivity,
                CoreMetadataManager.BuildType.NIGHTLY
            )
            
            runOnUiThread {
                progressDialog.dismiss()
                adapter = CoreAdapter(cores)
                recyclerView.adapter = adapter
                Log.i(TAG, "Loaded ${cores.size} cores from metadata")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cores from metadata: ${e.message}", e)
            runOnUiThread {
                progressDialog.dismiss()
                Toast.makeText(
                    this@CoreManagerActivity,
                    "Error loading cores. Using fallback list.",
                    Toast.LENGTH_LONG
                ).show()
                // Fallback sur liste hardcodée
                cores = CoreDownloader.getAvailableCores()
                adapter = CoreAdapter(cores)
                recyclerView.adapter = adapter
            }
        }
    }
}
```

**Dépendances nécessaires:**
- `androidx.lifecycle:lifecycle-runtime-ktx` pour `lifecycleScope`

---

## 📋 RÈGLES DU PROJET

### ✅ Règle 1: Utiliser CoreMetadataManager pour détection automatique
- **Statut:** ⚠️ Partiellement respecté
- **Action:** Migrer `CoreManagerActivity` vers `getAvailableCoresFromMetadata()`

### ✅ Règle 2: Fallback sur liste hardcodée en cas d'erreur
- **Statut:** ✅ Respecté
- **Implémentation:** `getAvailableCoresFromMetadata()` fait déjà le fallback

### ✅ Règle 3: Cache local pour performance
- **Statut:** ✅ Respecté
- **Implémentation:** `CoreMetadataManager` utilise un cache de 24h

### ✅ Règle 4: Support NIGHTLY et STABLE builds
- **Statut:** ✅ Respecté
- **Implémentation:** `CoreMetadataManager.BuildType` enum

---

## 🎯 PLAN D'ACTION

1. ✅ **Ajouter dépendance lifecycle-runtime-ktx** (si pas déjà présente)
2. ⏳ **Migrer CoreManagerActivity** vers `getAvailableCoresFromMetadata()`
3. ✅ **Tester avec connexion réseau** (détection automatique)
4. ✅ **Tester sans connexion réseau** (fallback sur cache local)
5. ✅ **Tester sans cache** (fallback sur liste hardcodée)

---

## 📝 NOTES

- La liste hardcodée dans `getAvailableCores()` reste nécessaire comme fallback
- Les nouveaux cores ajoutés manuellement (ex: VICE) doivent rester dans la liste hardcodée pour le fallback
- La détection automatique permet d'avoir TOUS les cores disponibles sans modification du code

---

## 🔗 RÉFÉRENCES

- **Buildbot Libretro:** https://buildbot.libretro.com/nightly/android/latest/cores.json
- **Documentation:** `IMPLEMENTATION_CORES_GAMELIST_2025-11-19.md`
- **CoreMetadataManager:** `app/src/main/java/com/retroplay/CoreMetadataManager.kt`








