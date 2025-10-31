# 🎨 STATUS BAR OVERLAY - MOCKUP VISUEL

**Date:** 31 octobre 2025  
**Objectif:** Prototype visuel avant implémentation  
**Contexte:** Remplacement du Quick Menu par une barre overlay permanente

---

## 📱 VARIANTE A: BARRE HORIZONTALE COMPLÈTE

```
┌─────────────────────────────────────────────────┐
│                                                 │
│                                                 │
│              ÉCRAN DE JEU (GLRetroView)         │
│                                                 │
│                                                 │
├─────────────────────────────────────────────────┤
│  ⚡2x  🔇  💾  📂  ⚙️                           │ ← 48dp height
├─────────────────────────────────────────────────┤
│                                                 │
│              CONTRÔLES OVERLAY                  │
│          (D-Pad, A, B, Start, Select)           │
│                                                 │
└─────────────────────────────────────────────────┘
```

**Caractéristiques:**
- Position: Entre écran et contrôles (fixe)
- Hauteur: 48dp (~10% écran vertical)
- Background: Noir 50% transparent (#80000000)
- Icônes: 40dp, espacées uniformément
- État visible: Fast Forward "2x" affiché, audio muted en rouge

**Pros:**
- ✅ Toujours visible
- ✅ État clair (couleurs, textes)
- ✅ Accès rapide

**Cons:**
- ❌ Réduit l'espace de jeu vertical (~10%)
- ❌ Pas d'auto-hide

---

## 📱 VARIANTE B: MINI-BAR AUTO-HIDE

```
┌─────────────────────────────────────────────────┐
│                                                 │
│                                                 │
│              ÉCRAN DE JEU (GLRetroView)         │
│                                                 │
│  ⚡🔇💾📂⚙️  ← Mini-bar (32dp, auto-hide)        │
├─────────────────────────────────────────────────┤
│                                                 │
│              CONTRÔLES OVERLAY                  │
│          (D-Pad, A, B, Start, Select)           │
│                                                 │
└─────────────────────────────────────────────────┘
```

**Comportement:**
- Affichée pendant 3 secondes après un tap
- Fade out progressif (500ms)
- Tap sur le haut de l'écran pour réafficher
- Icônes 32dp (plus petites)

**Pros:**
- ✅ Écran dégagé (99% du temps)
- ✅ Moins intrusive
- ✅ Apparaît quand on en a besoin

**Cons:**
- ❌ Faut tapper pour voir l'état
- ❌ Peut se cacher au mauvais moment

---

## 📱 VARIANTE C: BARRE VERTICALE LATÉRALE (GAUCHE)

```
┌─┬───────────────────────────────────────────────┐
│⚡│                                               │
│ │                                               │
│🔇│          ÉCRAN DE JEU (GLRetroView)           │
│ │                                               │
│💾│                                               │
│ │                                               │
│📂│                                               │
│ │                                               │
│⚙️│                                               │
└─┴───────────────────────────────────────────────┘
│                                                 │
│              CONTRÔLES OVERLAY                  │
│          (D-Pad, A, B, Start, Select)           │
│                                                 │
└─────────────────────────────────────────────────┘
```

**Caractéristiques:**
- Position: Bord gauche (ou droit au choix)
- Largeur: 40dp (~5% écran horizontal)
- Icônes: 32dp, empilées verticalement
- Semi-transparent (#80000000)

**Pros:**
- ✅ Ne réduit pas la hauteur de jeu
- ✅ Toujours visible
- ✅ Compact

**Cons:**
- ❌ Peut gêner le D-Pad
- ❌ Moins accessible en mode landscape

---

## 📱 VARIANTE D: FLOATING BUBBLE (Comme Messenger)

```
┌─────────────────────────────────────────────────┐
│                                                 │
│                                                 │
│              ÉCRAN DE JEU                       │
│                                            [⚡]  │ ← Bubble draggable
│                                                 │
├─────────────────────────────────────────────────┤
│              CONTRÔLES OVERLAY                  │
└─────────────────────────────────────────────────┘

TAP sur bubble → Expand:
┌─────────────────────────────────────────────────┐
│                                    ┌─────────┐  │
│              ÉCRAN DE JEU          │ ⚡2x     │  │
│                                    │ 🔇      │  │
│                                    │ 💾      │  │
│                                    │ 📂      │  │
│                                    │ ⚙️      │  │
│                                    └─────────┘  │
├─────────────────────────────────────────────────┤
```

**Comportement:**
- Bubble flottante déplaçable (64dp)
- Tap → Expand vertical menu (200ms animation)
- Auto-collapse après 3s sans interaction
- Position sauvegardée

**Pros:**
- ✅ 0% d'espace perdu
- ✅ Positionnable partout
- ✅ Élégant

**Cons:**
- ❌ Plus complexe à implémenter
- ❌ Peut masquer l'action

---

## 📱 VARIANTE E: MINI-ICONS OVERLAY (Discret)

```
┌─────────────────────────────────────────────────┐
│  ⚡🔇                                            │ ← Top-left, 24dp
│                                                 │
│              ÉCRAN DE JEU                       │
│                                                 │
│                                                 │
├─────────────────────────────────────────────────┤
│              CONTRÔLES OVERLAY                  │
└─────────────────────────────────────────────────┘
```

**Caractéristiques:**
- Position: Coin haut-gauche
- Icônes: 24dp (mini)
- Seulement les états actifs affichés:
  - ⚡ si Fast Forward ON
  - 🔇 si Audio Muted
  - Sinon: caché
- Long press → Menu complet

**Pros:**
- ✅ Ultra-discret (presque invisible)
- ✅ Info visuelle instantanée
- ✅ 0% d'espace si inactif

**Cons:**
- ❌ Pas d'accès direct aux actions
- ❌ Moins intuitif

---

## 📱 VARIANTE F: HYBRID (Recommandée)

```
NORMAL (idle):
┌─────────────────────────────────────────────────┐
│  ⚡🔇                                 [⋮]        │ ← Mini-états + menu icon
│                                                 │
│              ÉCRAN DE JEU                       │
│                                                 │
│                                                 │
├─────────────────────────────────────────────────┤

TAP sur [⋮] → EXPAND:
┌─────────────────────────────────────────────────┐
│  ⚡2x  🔇  💾  📂  ⚙️                           │ ← Barre complète
├─────────────────────────────────────────────────┤
│              ÉCRAN DE JEU                       │
│                                                 │
│                                                 │
├─────────────────────────────────────────────────┤
```

**Comportement:**
- **Mode idle:** Seulement états actifs + icône menu (24dp)
- **Tap menu [⋮]:** Expand barre complète (48dp)
- **Auto-collapse:** Après 3s sans interaction
- **Quick toggle:** Tap direct sur ⚡ ou 🔇 sans expand

**Pros:**
- ✅ Meilleur des deux mondes
- ✅ Discret par défaut
- ✅ Complet si besoin
- ✅ Quick actions possibles

**Cons:**
- ❌ Légèrement plus complexe

---

## 🎨 DÉTAILS VISUELS

### Icônes et Couleurs

**Fast Forward:**
- OFF: ⏩ Blanc (50% opacity)
- ON: ⚡2x Rouge/Orange (#FF5722)

**Audio:**
- ON: 🔊 Blanc
- MUTED: 🔇 Rouge (#F44336)

**Save/Load:**
- 💾 Blanc (Save)
- 📂 Blanc (Load)

**Settings:**
- ⚙️ Blanc

### States Visuels
```
[⚡]    Fast Forward OFF (gris)
[⚡2x]  Fast Forward ON (rouge pulsant)
[🔊]    Audio ON (blanc)
[🔇]    Audio MUTED (rouge)
```

---

## 🎯 COMPARAISON VARIANTES

| Variante | Espace | Accessibilité | Discrétion | Complexité |
|----------|--------|---------------|------------|------------|
| **A - Barre Complète** | ❌ -10% | ⭐⭐⭐⭐⭐ | ❌ | ⭐⭐ |
| **B - Auto-Hide** | ✅ 0% | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| **C - Verticale** | ⚠️ -5% | ⭐⭐⭐ | ⭐⭐ | ⭐⭐ |
| **D - Bubble** | ✅ 0% | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **E - Mini-Icons** | ✅ 0% | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **F - Hybrid** | ✅ 0% | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |

---

## 💡 RECOMMANDATION

**Variante F (Hybrid)** est le meilleur compromis :
- Discret par défaut (mini-icons)
- Accessible en 1 tap ([⋮])
- Visual feedback constant (états visibles)
- Quick toggles possibles sans expand

**Alternative:** Variante A si vous préférez tout voir tout le temps.

---

## 🎬 ANIMATIONS PROPOSÉES

### Expand/Collapse
```kotlin
animateContentSize(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
)
```

### Icon State Change
```kotlin
animateColorAsState(
    targetValue = if (active) Color.Red else Color.White,
    animationSpec = tween(300)
)
```

### Pulse Effect (FF actif)
```kotlin
val infiniteTransition = rememberInfiniteTransition()
val alpha by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 0.5f,
    animationSpec = infiniteRepeatable(
        animation = tween(800),
        repeatMode = RepeatMode.Reverse
    )
)
```

---

## 🗳️ VOTRE CHOIX ?

**Quelle variante préférez-vous ?**
- A: Barre complète toujours visible
- B: Mini-bar auto-hide
- C: Barre verticale latérale
- D: Floating bubble (comme Messenger)
- E: Mini-icons discrets
- F: Hybrid (recommandé)

**OU proposez des modifications !** 🎨

