# Fix: Affichage du nom du shader dans QuickActionsBar

**Date:** 2025-10-31  
**Problème:** L'utilisateur ne voyait pas quel shader était actif  
**Solution:** Affichage du nom du shader dans la QuickActionsBar

---

## Problème identifié

L'utilisateur a rapporté ne pas voir les shaders. Le problème était en fait:
- ✅ Les **shaders sont intégrés dans LibretroDroid** (pas de fichiers à copier)
- ❌ Le **nom du shader actif n'était pas affiché** dans la QuickActionsBar
- ❌ Impossible de savoir quel shader est appliqué sans ouvrir le menu

---

## Solution implémentée

### 1. Affichage du shader dans le mode EXPAND (barre complète)

**Fichier:** `app/src/main/java/com/retroplay/ui/QuickActionsBar.kt`

```kotlin
// Shader Cycle (Quick Win #4) - Afficher le nom du shader
ActionButton(
    icon = "🎨",
    label = currentShaderName.take(8),  // 8 premiers caractères max
    isActive = currentShaderName != "None (Fast)",
    activeColor = Color(0xFF9C27B0),  // Purple pour shader actif
    onClick = onCycleShader
)
```

**Avant:** Le label était vide `""`  
**Après:** Affiche les 8 premiers caractères du nom du shader  
**Couleur:** Purple `#9C27B0` quand un shader est actif

---

### 2. Affichage du shader dans le mode COMPACT (barre réduite)

**Ajout d'un paramètre:**
```kotlin
private fun CompactBar(
    // ...
    currentShaderName: String = "None"
) {
```

**Affichage du shader actif:**
```kotlin
// Shader actif (si différent de None)
if (currentShaderName != "None (Fast)") {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🎨",
            fontSize = 16.sp,
            color = Color(0xFF9C27B0)
        )
        Text(
            text = currentShaderName.take(6),  // 6 caractères max en mode compact
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9C27B0)
        )
    }
}
```

**Avant:** Aucun affichage du shader en mode compact  
**Après:** Affiche l'icône 🎨 + les 6 premiers caractères du nom du shader

---

## Shaders disponibles

Les **7 shaders intégrés dans LibretroDroid** sont:

| Shader | Nom affiché | Effet visuel |
|--------|-------------|--------------|
| `DEFAULT` | None (Fast) | Aucun effet - Rendu natif |
| `CRT` | CRT (Scanlines) | Lignes de balayage TV cathodique |
| `LCD` | LCD (Handheld) | Grille LCD style Game Boy |
| `SHARP` | Sharp (Pixels) | Pixels nets sans anti-aliasing |
| `CUT` | Upscale (Low) | Upscale intelligent faible |
| `CUT2` | Upscale (Med) | Upscale intelligent moyen |
| `CUT3` | Upscale (High) | Upscale intelligent max qualité |

---

## Comportement

### Mode Compact (barre réduite)
- Si **aucun shader**: Affiche "RetroPlay"
- Si **shader actif**: Affiche 🎨 + nom (6 caractères max)
- Couleur: **Purple** `#9C27B0`

### Mode Expand (barre complète)
- Bouton dédié avec icône 🎨
- Label: Nom du shader (8 caractères max)
- Couleur: **Blanc** si None, **Purple** si actif
- Animation de couleur lors du toggle

---

## Test des shaders

### 1. Lancer un jeu PSX
```
1. Lancer Crash Bandicoot ou autre jeu PSX
2. Regarder la QuickActionsBar en haut
3. Par défaut: "None (Fast)" devrait être affiché
```

### 2. Cycler les shaders
```
1. Cliquer sur le bouton [⋮] pour ouvrir la barre complète
2. Cliquer sur l'icône 🎨 (Shader)
3. Le nom du shader devrait changer et s'afficher
4. Toast de confirmation devrait apparaître
```

### 3. Vérifier les effets visuels

**CRT (Scanlines):**
- Devrait afficher des lignes horizontales sur l'écran
- Effet "TV cathodique" visible

**LCD (Handheld):**
- Devrait afficher une grille de pixels
- Effet "Game Boy" visible

**Sharp (Pixels):**
- Pixels plus nets, moins flous
- Idéal pour les jeux 2D pixelisés

**NOTES:**
- Certains shaders sont **subtils** et difficiles à voir sur certains jeux
- Les shaders **CUT/CUT2/CUT3** (upscale) sont surtout visibles sur les jeux basse résolution
- Le shader **DEFAULT** n'a aucun effet (rendu natif)

---

## Compilation et installation

```bash
cd C:\androidProject\ChatAI-Android-beta\RetroPlay-Android
.\gradlew assembleDebug --no-daemon
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Statut:** ✅ Compilé et installé avec succès le 2025-10-31

---

## Fichiers modifiés

- `RetroPlay-Android/app/src/main/java/com/retroplay/ui/QuickActionsBar.kt`

---

## Capture d'écran attendue

### Mode Compact
```
┌─────────────────────────────────────────────┐
│  🎨 CRT (S                             [⋮]  │
└─────────────────────────────────────────────┘
```

### Mode Expand
```
┌─────────────────────────────────────────────┐
│  ⚡   🔊   💾   📂   🎨   ⚙️   ✕           │
│  2x                   CRT (S                 │
└─────────────────────────────────────────────┘
```

---

## À vérifier

✅ Le nom du shader s'affiche en mode Compact  
✅ Le nom du shader s'affiche en mode Expand  
✅ La couleur change en purple quand un shader est actif  
❓ Les effets visuels des shaders sont-ils visibles?  
❓ Le toast de confirmation apparaît-il?

