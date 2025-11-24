# Plan d'Optimisation - Lancement de l'App

**Date:** 23 novembre 2025  
**Problèmes identifiés:**
1. Splashscreen se ferme avant la completion des listes
2. Gamelist continuent à être générés après le lancement
3. Gamelist lus avant le bon moment
4. Chargement d'images non optimisé

---

## 🔍 PROBLÈMES IDENTIFIÉS

### 1. SplashActivity
- ❌ Timeout de 30s peut être trop court
- ❌ Se ferme même si scan non terminé
- ❌ Délais inutiles (100ms + 300ms = 400ms)
- ❌ Ne précharge pas les gamelist.json existants

### 2. GameListActivity
- ❌ Charge gamelist.json immédiatement au lancement
- ❌ Charge toutes les images en même temps (pas de lazy loading)
- ❌ Pas de cache pour les images
- ❌ Pas de préchargement

### 3. GamelistScanner
- ❌ Scan peut prendre du temps (CRC32, BD)
- ❌ Ne précharge pas les gamelist.json existants
- ❌ Continue après le lancement de MainActivity

---

## 🎯 SOLUTIONS PROPOSÉES

### 1. Optimiser SplashActivity

**Objectifs:**
- ✅ Attendre que TOUS les gamelist.json soient prêts (générés OU chargés)
- ✅ Précharger les gamelist.json existants en parallèle du scan
- ✅ Augmenter le timeout si nécessaire
- ✅ Supprimer les délais inutiles

**Modifications:**
1. Précharger les gamelist.json existants pendant le scan
2. Attendre que le scan ET le préchargement soient terminés
3. Augmenter le timeout à 60s pour les gros scans
4. Supprimer les délais inutiles (100ms + 300ms)

### 2. Optimiser GameListActivity

**Objectifs:**
- ✅ Lazy loading des images (charger seulement celles visibles)
- ✅ Cache des images (Glide avec cache)
- ✅ Préchargement des images en arrière-plan
- ✅ Ne pas bloquer l'UI pendant le chargement

**Modifications:**
1. Utiliser RecyclerView avec lazy loading
2. Configurer Glide avec cache optimisé
3. Précharger les images en arrière-plan
4. Afficher placeholder pendant le chargement

### 3. Optimiser GamelistScanner

**Objectifs:**
- ✅ Précharger les gamelist.json existants en parallèle
- ✅ Ne pas bloquer le lancement si scan en cours
- ✅ Prioriser les consoles populaires

**Modifications:**
1. Précharger les gamelist.json existants pendant le scan
2. Scanner les consoles par priorité (populaires en premier)
3. Permettre le lancement même si scan non terminé (mais avec préchargement)

---

## 📋 PLAN D'IMPLÉMENTATION

### Phase 1: Optimiser SplashActivity (PRIORITÉ)

1. **Précharger les gamelist.json existants**
   - Pendant le scan, charger les gamelist.json existants en parallèle
   - Stocker en cache mémoire pour accès rapide

2. **Attendre la completion**
   - Attendre que scan ET préchargement soient terminés
   - Augmenter timeout à 60s
   - Supprimer délais inutiles

3. **Feedback utilisateur**
   - Afficher progression du scan
   - Afficher progression du préchargement
   - Message clair quand tout est prêt

### Phase 2: Optimiser GameListActivity

1. **Lazy loading des images**
   - Utiliser RecyclerView avec ViewHolder
   - Charger images seulement quand visibles
   - Placeholder pendant chargement

2. **Cache des images**
   - Configurer Glide avec cache optimisé
   - Précharger images en arrière-plan
   - Réutiliser images chargées

3. **Optimiser parseAndDisplayGames**
   - Ne pas bloquer l'UI
   - Afficher progressivement
   - Cache des GameEntry

### Phase 3: Optimiser GamelistScanner

1. **Préchargement parallèle**
   - Charger gamelist.json existants pendant scan
   - Stocker en cache mémoire

2. **Priorisation**
   - Scanner consoles populaires en premier
   - Permettre lancement même si scan non terminé

---

## ✅ RÉSULTAT ATTENDU

**Flux optimisé:**
1. SplashActivity démarre
2. Scan des consoles (si nécessaire)
3. Préchargement des gamelist.json existants (en parallèle)
4. Préchargement des images (en arrière-plan)
5. Splashscreen reste ouvert jusqu'à completion
6. MainActivity lance avec tout prêt
7. GameListActivity affiche instantanément (cache)

**Performance:**
- ⚡ Lancement plus rapide
- ⚡ Affichage instantané des listes
- ⚡ Images chargées progressivement
- ⚡ Pas de blocage UI

