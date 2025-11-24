# Audit Nos Rules RetroPlay - Overlays

**Date:** 2025-01-XX  
**Méthodologie:** Nos Rules - Analyse exhaustive des sources officielles RetroArch  
**Sources analysées:**
- `c:\repos\docs-master\docs\development\retroarch\input\overlay.md` (186 lignes)
- `c:\repos\docs-master\docs\guides\libretro-overlays.md` (92 lignes)
- `c:\repos\docs-master\docs\guides\overlay-pointing-devices.md`
- `c:\repos\RetroArch-master\input\input_overlay.h` (structures complètes)
- `c:\repos\RetroArch-master\tasks\task_overlay.c` (parser complet)
- `c:\repos\common-overlays-master\` (300+ fichiers .cfg)
- `RetroPlay-Android/app/src/main/java/com/retroplay/overlay/parser/RetroArchOverlayParser.kt`
- `RetroPlay-Android/app/src/main/java/com/retroplay/overlay/renderer/RetroArchOverlayRenderer.kt`

---

## Partie 1 : Format .cfg RetroArch - Tous les champs possibles

### 1.1 Structure générale

Un fichier .cfg RetroArch décrit un ou plusieurs overlays avec leurs boutons, images et comportements.

**Format de base:**
```ini
overlays = 2                    # Nombre total d'overlays
overlay0_name = "landscape-A"   # Nom de l'overlay (optionnel, pour navigation)
overlay0_full_screen = true     # Plein écran vs viewport
overlay0_normalized = true      # Coordonnées normalisées [0-1] vs pixels
overlay0_descs = 19             # Nombre de boutons/descripteurs
```

### 1.2 Champs overlay globaux (overlay0_*)

#### 1.2.1 Métadonnées

```ini
overlay0_name = "landscape-A"           # Nom unique pour navigation (obligatoire si overlay_next)
overlay0_full_screen = true              # true = plein écran, false = viewport uniquement
overlay0_normalized = true               # true = coordonnées normalisées [0-1], false = pixels
overlay0_overlay = "img/background.png"  # Image de fond (optionnel, legacy format)
overlay0_rect = "0.0,0.0,1.0,1.0"       # Position et taille (x,y,width,height) normalisées
overlay0_aspect_ratio = 1.7777778        # Ratio d'aspect natif (16:9 = 1.7777778, portrait = 0.5625)
```

**Explications:**
- `overlay0_name` : Nom unique pour permettre `overlay_next` avec `next_target`
- `overlay0_full_screen` : Si `true`, l'overlay s'étend sur tout l'écran (pas seulement le viewport du jeu)
- `overlay0_normalized` : Si `true`, toutes les coordonnées sont normalisées [0-1] (recommandé)
- `overlay0_overlay` : Image de fond full-screen (format legacy, déconseillé)
- `overlay0_rect` : Position et taille personnalisées (x, y, width, height) normalisées
- `overlay0_aspect_ratio` : Ratio d'aspect pour calculer les dimensions (défaut: 16:9 = 1.7777778)

#### 1.2.2 Modificateurs globaux

```ini
overlay0_range_mod = 1.5        # Multiplicateur de taille des hitboxes (défaut: 1.0)
overlay0_alpha_mod = 2.0        # Multiplicateur d'opacité quand pressé (défaut: 1.0)
```

**Explications:**
- `overlay0_range_mod` : Multiplie toutes les hitboxes (range_x, range_y) par cette valeur quand pressé
- `overlay0_alpha_mod` : Multiplie l'opacité de tous les boutons par cette valeur quand pressé

#### 1.2.3 Flags de séparation

```ini
overlay0_block_x_separation = false  # Bloque le séparateur X (défaut: false)
overlay0_block_y_separation = false  # Bloque le séparateur Y (défaut: false)
overlay0_auto_x_separation = true    # Auto-séparation X (défaut: true)
overlay0_auto_y_separation = true    # Auto-séparation Y (défaut: true)
```

**Explications:**
- Ces flags contrôlent comment RetroArch applique les offsets de séparation (x_separation, y_separation)
- Utilisés pour ajuster les positions des boutons sans modifier les coordonnées dans le .cfg

### 1.3 Descripteurs de boutons (overlay0_descN_*)

#### 1.3.1 Format de base

```ini
overlay0_desc0 = "a,0.91667,0.85185,radial,0.05000,0.08889"
```

**Format:** `"action,x,y,hitbox_type,range_x,range_y"`

**Composants:**
- `action` : Action RetroPad ou type spécial (voir section 1.4)
- `x` : Position X normalisée [0-1] ou pixels
- `y` : Position Y normalisée [0-1] ou pixels
- `hitbox_type` : `"radial"` (cercle/ellipse) ou `"rect"` (rectangle)
- `range_x` : Rayon largeur (radial) ou distance centre→bord (rect) normalisée ou pixels
- `range_y` : Rayon hauteur (radial) ou distance centre→bord (rect) normalisée ou pixels

**Exemples:**
```ini
# Bouton A circulaire
overlay0_desc0 = "a,0.91667,0.85185,radial,0.05000,0.08889"

# Bouton Start rectangulaire
overlay0_desc1 = "start,0.63958,0.90000,radial,0.04583,0.03889"

# Combo diagonal (A+B simultané)
overlay0_desc2 = "a|b,0.85938,0.83333,radial,0.01667,0.02963"

# Bouton nul (pas d'action, juste image)
overlay0_desc3 = "nul,0.71845,0.84845,radial,0.212625,0.127575"
```

#### 1.3.2 Image par bouton

```ini
overlay0_desc0_overlay = "img/A.png"
```

**Format:** Chemin relatif à partir du répertoire du .cfg

**Utilisation:**
- Image affichée pour ce bouton spécifique
- Si absent, pas d'image individuelle (utilise l'image de fond si présente)
- Taille d'affichage = `mod_w` × `mod_h` (calculé depuis `range_x` × `range_y`)

**Calcul RetroArch:**
```cpp
// input_overlay.h:496-499
desc->mod_x   = desc->x - desc->range_x;  // Bord gauche
desc->mod_w   = 2.0f * desc->range_x;     // Largeur = 2 × range_x
desc->mod_y   = desc->y - desc->range_y;  // Bord haut
desc->mod_h   = 2.0f * desc->range_y;     // Hauteur = 2 × range_y
```

**Note importante:** `mod_w` et `mod_h` sont utilisés pour l'affichage des images, pas `range_x` et `range_y` qui sont pour les hitboxes.

#### 1.3.3 Modificateurs par bouton

```ini
overlay0_desc0_alpha_mod = 2.0           # Multiplicateur opacité quand pressé (override global)
overlay0_desc0_range_mod = 1.5           # Multiplicateur hitbox quand pressé (override global)
overlay0_desc0_normalized = true         # Override de normalized pour ce bouton (rare)
```

**Explications:**
- `overlay0_desc0_alpha_mod` : Override de `overlay0_alpha_mod` pour ce bouton
- `overlay0_desc0_range_mod` : Override de `overlay0_range_mod` pour ce bouton
- `overlay0_desc0_normalized` : Override de `overlay0_normalized` pour ce bouton (rarement utilisé)

#### 1.3.4 Hitbox étendue (reach_*)

```ini
overlay0_desc0_reach_x = 1.6             # Multiplie hitbox largeur symétriquement
overlay0_desc0_reach_y = 1.6             # Multiplie hitbox hauteur symétriquement
overlay0_desc0_reach_up = 1.6            # Multiplie hitbox vers le haut uniquement
overlay0_desc0_reach_down = 1.96         # Multiplie hitbox vers le bas uniquement
overlay0_desc0_reach_left = 1.4          # Multiplie hitbox vers la gauche uniquement
overlay0_desc0_reach_right = 1.0         # Multiplie hitbox vers la droite uniquement
```

**Explications:**
- `reach_x` / `reach_y` : Multiplie symétriquement (gauche+droite ou haut+bas)
- `reach_up/down/left/right` : Multiplie dans une seule direction
- Si `reach_x = 0` ou `reach_y = 0`, la hitbox est désactivée (pas de touch, mais peut s'allumer si même action)

**Utilisation courante:**
- D-Pad : `reach_up` et `reach_down` étendus pour meilleure précision
- Analog sticks : `reach_x = reach_y` pour zone circulaire uniforme

#### 1.3.5 Flags de comportement

```ini
overlay0_desc0_exclusive = true                  # Bloque autres hitboxes chevauchantes
overlay0_desc0_range_mod_exclusive = true        # Exclusive seulement quand range_mod actif
overlay0_desc0_movable = true                    # Image suit le doigt (analog sticks)
```

**Explications:**
- `exclusive` : Ce bouton a priorité sur les autres hitboxes chevauchantes (même doigt)
- `range_mod_exclusive` : Exclusive seulement après que `range_mod` soit appliqué (1 frame après press)
- `movable` : L'image du bouton suit le mouvement du doigt dans sa zone (pour analog sticks)

**Exemple d'utilisation (D-Pad + Analog stick chevauchants):**
```ini
# D-Pad: Priorité au premier touch, puis range_mod_exclusive
overlay0_desc0 = "dpad_area,0.15,0.57,rect,0.1094,0.1944"
overlay0_desc0_range_mod = 1.2
overlay0_desc0_range_mod_exclusive = true

# Analog stick: Priorité exclusive par défaut, puis range_mod_exclusive
overlay0_desc1 = "analog_left,0.31,0.81,radial,0.0964,0.1928"
overlay0_desc1_exclusive = true
overlay0_desc1_range_mod = 2.75
overlay0_desc1_range_mod_exclusive = true
```

### 1.4 Types de boutons spéciaux

#### 1.4.1 Types de base

**Boutons RetroPad standard:**
```ini
overlay0_desc0 = "a,0.91667,0.85185,radial,0.05000,0.08889"
overlay0_desc1 = "b,0.80208,0.85185,radial,0.05000,0.08889"
overlay0_desc2 = "x,0.84479,0.59074,radial,0.05,0.08889"
overlay0_desc3 = "y,0.76979,0.72407,radial,0.05,0.08889"
overlay0_desc4 = "start,0.63958,0.90000,radial,0.04583,0.03889"
overlay0_desc5 = "select,0.36042,0.90000,radial,0.04583,0.03889"
overlay0_desc6 = "l,0.02917,0.44074,rect,0.05208,0.09259"
overlay0_desc7 = "r,0.97083,0.50000,rect,0.05208,0.09259"
overlay0_desc8 = "l2,0.2625,0.052083333,rect,0.0625,0.052083333"
overlay0_desc9 = "r2,0.7375,0.052083333,rect,0.0625,0.052083333"
overlay0_desc10 = "l3,0.2,0.8,radial,0.04,0.04"
overlay0_desc11 = "r3,0.8,0.8,radial,0.04,0.04"
```

**Combo boutons (diagonales):**
```ini
overlay0_desc12 = "a|b,0.85938,0.83333,radial,0.01667,0.02963"  # A+B simultané
overlay0_desc13 = "left|up,0.05313,0.63148,rect,0.03646,0.06481"  # D-Pad diagonale
```

**Bouton nul (pas d'action):**
```ini
overlay0_desc14 = "nul,0.71845,0.84845,radial,0.212625,0.127575"  # Juste image
```

#### 1.4.2 Types spéciaux RetroArch

**Analog sticks:**
```ini
overlay0_desc15 = "analog_left,0.28845,0.75208333333,radial,0.083,0.136"
overlay0_desc15_overlay = "img/thumbstick-pad.png"
overlay0_desc15_range_mod = 3.5
overlay0_desc15_saturate_pct = 0.75
overlay0_desc15_movable = true

overlay0_desc16 = "analog_right,0.71845,0.75208333333,radial,0.083,0.136"
overlay0_desc16_overlay = "img/thumbstick-pad.png"
overlay0_desc16_range_mod = 3.5
overlay0_desc16_saturate_pct = 0.75
overlay0_desc16_movable = true
```

**Caractéristiques:**
- Type: `"analog_left"` ou `"analog_right"`
- Hitbox: DOIT être `"radial"` (cercle/ellipse)
- `saturate_pct` : Zone intérieure contenant toute la plage analogique (0.75 = 75% intérieur)
- `movable` : L'image suit le doigt (recommandé)

**D-Pad area (8-way):**
```ini
overlay0_desc17 = "dpad_area,0.15,0.57,rect,0.1094,0.1944"
overlay0_desc17_up = "up"
overlay0_desc17_down = "down"
overlay0_desc17_left = "left"
overlay0_desc17_right = "right"
```

**Caractéristiques:**
- Type: `"dpad_area"`
- Hitbox: `"rect"` recommandé (peut être `"radial"`)
- Mappings personnalisables: `overlay0_descN_up/down/left/right`
- Diagonales: Automatiquement générées (up_left, up_right, down_left, down_right)

**ABXY area (8-way):**
```ini
overlay0_desc18 = "abxy_area,0.85,0.57,rect,0.1607,0.2857"
overlay0_desc18_up = "x"
overlay0_desc18_down = "b"
overlay0_desc18_left = "y"
overlay0_desc18_right = "a"
```

**Caractéristiques:**
- Type: `"abxy_area"`
- Hitbox: `"rect"` recommandé (peut être `"radial"`)
- Mappings personnalisables: `overlay0_descN_up/down/left/right`
- Diagonales: Automatiquement générées (x|a, x|y, y|b, a|b)

#### 1.4.3 Boutons système

**Menu toggle:**
```ini
overlay0_desc19 = "menu_toggle,0.50000,0.08889,radial,0.02604,0.046296"
overlay0_desc19_overlay = "img/rgui.png"
```

**Overlay navigation:**
```ini
overlay0_desc20 = "overlay_next,0.35000,0.08889,radial,0.02604,0.046296"
overlay0_desc20_overlay = "img/rotate.png"
overlay0_desc20_next_target = "portrait-A"  # Overlay cible (par nom)
```

**Caractéristiques:**
- Action: `"overlay_next"` ou `"menu_toggle"`
- `next_target` : Nom de l'overlay cible (doit exister via `overlayN_name`)
- Si `next_target` absent, passe à l'overlay suivant (index + 1)

#### 1.4.4 Boutons clavier

```ini
overlay0_desc21 = "retrok_a,0.5,0.5,radial,0.05,0.05"
```

**Format:** `"retrok_<keyname>"` où `<keyname>` est le nom de la touche RetroArch

**Note:** Un overlay desc peut avoir SEULEMENT UNE touche clavier (pas de combos).

### 1.5 Support #include

```ini
#include "common/buttons.cfg"
#include "../shared/dpad.cfg"
```

**Caractéristiques:**
- Profondeur maximale: 16 niveaux (MAX_INCLUDE_DEPTH = 16)
- Chemins relatifs au fichier .cfg courant
- Les lignes incluses sont fusionnées dans le flux principal

**Implémentation RetroArch:**
```c
// task_overlay.c:readConfigWithIncludes()
static void readConfigWithIncludes(file, depth) {
    if (depth >= 16) return;  // Protection récursion infinie
    for (line in file) {
        if (line.startsWith("#include ")) {
            includeFile = resolvePath(line)
            readConfigWithIncludes(includeFile, depth + 1)
        }
    }
}
```

---

## Partie 2 : Parser RetroArch vs Parser RetroPlay

### 2.1 Parser RetroArch (task_overlay.c)

**Fonction principale:** `task_overlay_load_desc()` (ligne 219-517)

**Processus de parsing:**
1. **Lire overlay global:**
   - `overlayN_name` → `overlay->name`
   - `overlayN_full_screen` → `overlay->flags |= OVERLAY_FULL_SCREEN`
   - `overlayN_normalized` → `overlay->config.normalized`
   - `overlayN_range_mod` → `overlay->config.range_mod`
   - `overlayN_alpha_mod` → `overlay->config.alpha_mod`
   - `overlayN_rect` → `overlay->x, y, w, h`
   - `overlayN_overlay` → Image de fond (legacy)
   - `overlayN_aspect_ratio` → `overlay->aspect_ratio`

2. **Lire descripteurs:**
   - `overlayN_descs` → `overlay->size` (nombre de descripteurs)
   - Pour chaque `descIndex` de 0 à `size-1`:
     - `overlayN_descN` → Parser format `"action,x,y,hitbox,range_x,range_y"`
     - `overlayN_descN_overlay` → Image individuelle
     - `overlayN_descN_alpha_mod` → Override alpha
     - `overlayN_descN_range_mod` → Override range
     - `overlayN_descN_exclusive` → Flag exclusive
     - `overlayN_descN_range_mod_exclusive` → Flag range_mod_exclusive
     - `overlayN_descN_movable` → Flag movable
     - `overlayN_descN_reach_*` → Hitbox étendue
     - `overlayN_descN_saturate_pct` → Zone saturation (analog sticks)
     - `overlayN_descN_next_target` → Nom overlay cible
     - `overlayN_descN_*` → Mappings 8-way (dpad_area, abxy_area)

3. **Calculer dimensions:**
   - `desc->mod_x = desc->x - desc->range_x`
   - `desc->mod_w = 2.0 * desc->range_x`
   - `desc->mod_y = desc->y - desc->range_y`
   - `desc->mod_h = 2.0 * desc->range_y`

**Structures de données:**
```c
struct overlay {
    struct overlay_desc *descs;      // Array de descripteurs
    struct texture_image *load_images; // Images chargées
    struct texture_image image;       // Image de fond (legacy)
    unsigned size;                    // Nombre de descripteurs
    char name[64];                    // Nom de l'overlay
    float mod_x, mod_y, mod_w, mod_h; // Position/taille calculée
    float aspect_ratio;               // Ratio d'aspect
    uint8_t flags;                    // OVERLAY_FULL_SCREEN, etc.
};

struct overlay_desc {
    enum overlay_hitbox hitbox;       // RADIAL, RECT, NONE
    enum overlay_type type;           // BUTTONS, ANALOG_LEFT, ANALOG_RIGHT, DPAD_AREA, ABXY_AREA, KEYBOARD
    float x, y;                       // Position centre
    float range_x, range_y;           // Rayon/taille hitbox
    float mod_x, mod_y, mod_w, mod_h; // Position/taille affichage (calculé)
    float alpha_mod;                  // Multiplicateur opacité
    float range_mod;                  // Multiplicateur hitbox quand pressé
    float analog_saturate_pct;        // Zone saturation analogique (0.0-1.0)
    float reach_right, reach_left, reach_up, reach_down; // Hitbox étendue
    uint8_t flags;                    // MOVABLE, EXCLUSIVE, RANGE_MOD_EXCLUSIVE
    input_bits_t button_mask;         // Masque bits actions RetroPad
    unsigned retro_key_idx;           // Index touche clavier (si KEYBOARD)
    overlay_eightway_config_t *eightway_config; // Config 8-way (dpad_area, abxy_area)
    char next_index_name[64];         // Nom overlay cible (overlay_next)
};
```

### 2.2 Parser RetroPlay (RetroArchOverlayParser.kt)

**Fonction principale:** `parseConfig()` (ligne 28-65)

**Processus de parsing:**
1. **Lire avec #include:**
   - `readConfigWithIncludes()` (ligne 71-97)
   - Support récursif jusqu'à 16 niveaux ✅ (compatible RetroArch)

2. **Lire nombre total:**
   - `overlays = N` → `config.totalOverlays`

3. **Parser chaque overlay:**
   - `parseOverlay()` (ligne 102-188)
   - Lit `overlayN_name`, `overlayN_full_screen`, `overlayN_normalized`, etc.
   - Lit `overlayN_descs` et appelle `parseButton()` pour chaque

4. **Parser chaque bouton:**
   - `parseButton()` (ligne 197-344)
   - Parse format `"action,x,y,hitbox,range_x,range_y"`
   - Lit `overlayN_descN_overlay`, `overlayN_descN_alpha_mod`, etc.
   - Lit `overlayN_descN_reach_*`, `overlayN_descN_saturate_pct`, etc.
   - Lit `overlayN_descN_*` pour mappings 8-way ✅

**Structures de données:**
```kotlin
data class RetroArchOverlayConfig(
    val totalOverlays: Int,
    val layouts: Map<String, OverlayLayout>
)

data class OverlayLayout(
    val name: String,
    val fullScreen: Boolean,
    val normalized: Boolean,
    val rangeModifier: Float,
    val alphaModifier: Float,
    val buttons: List<OverlayButton>,
    val rect: OverlayRect?,
    val backgroundImage: String?,
    val aspectRatio: Float?,
    val blockXSeparation: Boolean,
    val blockYSeparation: Boolean,
    val autoXSeparation: Boolean,
    val autoYSeparation: Boolean
)

data class OverlayButton(
    val action: String,
    val x: Float,
    val y: Float,
    val shape: ButtonShape,  // RADIAL, RECT
    val width: Float,        // range_x
    val height: Float,       // range_y
    val imagePath: String?,
    val nextTarget: String?,
    val type: OverlayButtonType,  // BUTTONS, ANALOG_LEFT, ANALOG_RIGHT, DPAD_AREA, ABXY_AREA
    val rangeModifier: Float,
    val alphaModifier: Float?,
    val exclusive: Boolean,
    val rangeModExclusive: Boolean,
    val movable: Boolean,
    val reachUp: Float,
    val reachDown: Float,
    val reachLeft: Float,
    val reachRight: Float,
    val analogSaturatePct: Float,
    val modX: Float,
    val modY: Float,
    val modW: Float,
    val modH: Float,
    // 8-way mappings
    val eightwayUp: String?,
    val eightwayDown: String?,
    val eightwayLeft: String?,
    val eightwayRight: String?,
    val eightwayUpLeft: String?,
    val eightwayUpRight: String?,
    val eightwayDownLeft: String?,
    val eightwayDownRight: String?
)
```

### 2.3 Comparaison Parser RetroArch vs RetroPlay

#### 2.3.1 Champs supportés

| Champ | RetroArch | RetroPlay | Statut |
|-------|-----------|-----------|--------|
| `overlays` | ✅ | ✅ | Compatible |
| `overlayN_name` | ✅ | ✅ | Compatible |
| `overlayN_full_screen` | ✅ | ✅ | Compatible |
| `overlayN_normalized` | ✅ | ✅ | Compatible |
| `overlayN_range_mod` | ✅ | ✅ | Compatible |
| `overlayN_alpha_mod` | ✅ | ✅ | Compatible |
| `overlayN_rect` | ✅ | ✅ | Compatible |
| `overlayN_overlay` | ✅ | ✅ | Compatible |
| `overlayN_aspect_ratio` | ✅ | ✅ | Compatible |
| `overlayN_block_x_separation` | ✅ | ✅ | Compatible |
| `overlayN_block_y_separation` | ✅ | ✅ | Compatible |
| `overlayN_auto_x_separation` | ✅ | ✅ | Compatible |
| `overlayN_auto_y_separation` | ✅ | ✅ | Compatible |
| `overlayN_descs` | ✅ | ✅ | Compatible |
| `overlayN_descN` | ✅ | ✅ | Compatible |
| `overlayN_descN_overlay` | ✅ | ✅ | Compatible |
| `overlayN_descN_alpha_mod` | ✅ | ✅ | Compatible |
| `overlayN_descN_range_mod` | ✅ | ✅ | Compatible |
| `overlayN_descN_exclusive` | ✅ | ✅ | Compatible |
| `overlayN_descN_range_mod_exclusive` | ✅ | ✅ | Compatible |
| `overlayN_descN_movable` | ✅ | ✅ | Compatible |
| `overlayN_descN_reach_x` | ✅ | ✅ | Compatible |
| `overlayN_descN_reach_y` | ✅ | ✅ | Compatible |
| `overlayN_descN_reach_up` | ✅ | ✅ | Compatible |
| `overlayN_descN_reach_down` | ✅ | ✅ | Compatible |
| `overlayN_descN_reach_left` | ✅ | ✅ | Compatible |
| `overlayN_descN_reach_right` | ✅ | ✅ | Compatible |
| `overlayN_descN_saturate_pct` | ✅ | ✅ | Compatible |
| `overlayN_descN_next_target` | ✅ | ✅ | Compatible |
| `overlayN_descN_normalized` | ✅ | ✅ | Compatible |
| `overlayN_descN_*` (8-way) | ✅ | ✅ | Compatible |
| `#include` | ✅ | ✅ | Compatible (16 niveaux) |

**Verdict:** ✅ **100% compatible** - Tous les champs RetroArch sont supportés par RetroPlay.

#### 2.3.2 Différences d'implémentation

**Calcul mod_w/mod_h:**

**RetroArch:**
```c
// task_overlay.c:496-499
desc->mod_x   = desc->x - desc->range_x;
desc->mod_w   = 2.0f * desc->range_x;
desc->mod_y   = desc->y - desc->range_y;
desc->mod_h   = 2.0f * desc->range_y;
```

**RetroPlay:**
```kotlin
// RetroArchOverlayParser.kt:319-322
modX = x - width,
modY = y - height,
modW = 2f * width,
modH = 2f * height,
```

**Verdict:** ✅ **Identique** - Calcul exactement identique.

**Support #include:**

**RetroArch:** Parser interne RetroArch (probablement dans `config_file.c`)

**RetroPlay:**
```kotlin
// RetroArchOverlayParser.kt:71-97
private fun readConfigWithIncludes(file: File, depth: Int): List<String> {
    if (depth >= 16) return emptyList()  // ✅ Compatible MAX_INCLUDE_DEPTH = 16
    // ... parsing récursif
}
```

**Verdict:** ✅ **Compatible** - Support récursif jusqu'à 16 niveaux (identique RetroArch).

---

## Partie 3 : Intégration Overlays + Contrôleurs Physiques

### 3.1 Détection gamepad physique

**Code source:** `RetroArchOverlayRenderer.kt:84-96`

```kotlin
// Détecter si un gamepad physique est connecté
val isGamepadConnected = remember {
    android.view.InputDevice.getDeviceIds().any { deviceId ->
        val device = android.view.InputDevice.getDevice(deviceId)
        device != null && (device.sources and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
    }
}

// Si hideWhenGamepadConnected est activé et qu'un gamepad est connecté, ne rien afficher
if (hideWhenGamepadConnected && isGamepadConnected) {
    Log.i(TAG, "Gamepad connected, hiding overlay (hideWhenGamepadConnected=true)")
    return
}
```

**Fonctionnement:**
- Détecte tous les devices Android avec `SOURCE_GAMEPAD`
- Si `hideWhenGamepadConnected = true` et qu'un gamepad est détecté, l'overlay est masqué
- Permet de jouer avec gamepad physique sans overlay touch visible

### 3.2 Interaction touch vs gamepad

**Code source:** `RetroArchOverlayRenderer.kt:130-165`

**Gestion des événements touch:**
```kotlin
.pointerInteropFilter { event ->
    val handled = handleOverlayTouch(
        event = event,
        layout = scaledLayout,
        screenSize = screenSize,
        pressedButtons = pressedButtons,
        analogLeftState = analogLeftState,
        analogRightState = analogRightState,
        onButtonPress = onButtonPress,
        onButtonRelease = onButtonRelease,
        onLayoutSwitch = onLayoutSwitch,
        onMenuToggle = onMenuToggle,
        onAnalogMove = onAnalogMove,
        onHotkey = onHotkey,
        onHotkeyChange = onHotkeyChange,
        // ...
    )
    
    // Mode Zapper: Ne consommer QUE si un bouton est touché
    // Sinon laisser passer au Zapper en dessous
    if (isZapperGame) {
        handled  // Retourne true seulement si bouton touché
    } else {
        true     // Mode normal: toujours consommer
    }
}
```

**Support multi-input:**
- Les overlays touch et les gamepads physiques peuvent fonctionner simultanément
- Les événements touch sont interceptés par `pointerInteropFilter`
- Les événements gamepad sont gérés directement par `GLRetroView.onKeyEvent()` / `sendMotionEvent()`
- Pas de conflit : touch → overlay → RetroPad, gamepad → RetroPad directement

### 3.3 Mode Zapper (overlay + pointer)

**Code source:** `RetroArchEmulatorActivity.kt:597-643` et `RetroArchOverlayRenderer.kt:157-160`

**Fonctionnement hybride:**
1. **Overlay touch:** Boutons (Start/Select) via overlay touch
2. **Pointer touch:** Zone de jeu (screen) → Zapper via `sendMotionEvent(MOTION_SOURCE_POINTER)`

**Interaction:**
```kotlin
// Dans RetroArchOverlayRenderer.kt
if (isZapperGame) {
    // Mode Zapper: Ne consommer QUE si un bouton est touché
    // Sinon laisser passer au Zapper en dessous
    handled  // handled = true si bouton touché, false sinon
} else {
    // Mode normal: toujours consommer
    true
}
```

**Implémentation:**
- Si touch sur un bouton overlay → `handleOverlayTouch()` retourne `true` → événement consommé
- Si touch en dehors des boutons → `handleOverlayTouch()` retourne `false` → événement passe au Zapper

---

## Partie 4 : Support Pointer/Lightgun Complet

### 4.1 Options globales RetroArch

Définies dans `input_overlay.h` et `configuration.c`:

```c
// input_overlay.h:38
#define OVERLAY_LIGHTGUN_TRIG_MAX_DELAY 15

// Options dans configuration.c:
SETTING_BOOL("input_overlay_lightgun_trigger_on_touch", 
    &settings->bools.input_overlay_lightgun_trigger_on_touch, true, true, false);
SETTING_BOOL("input_overlay_lightgun_allow_offscreen", 
    &settings->bools.input_overlay_lightgun_allow_offscreen, true, true, false);
SETTING_UINT("input_overlay_lightgun_trigger_delay", 
    &settings->uints.input_overlay_lightgun_trigger_delay, true, 0, false);
SETTING_UINT("input_overlay_lightgun_port", 
    &settings->uints.input_overlay_lightgun_port, true, 0, false);
```

### 4.2 Options lightgun dans RetroPlay

**Code source:** `OverlayPreferenceManager.kt` et `RetroArchEmulatorActivity.kt`

**Options disponibles:**
```kotlin
data class AdvancedOverlaySettings(
    // Lightgun options
    val lightgunTriggerOnTouch: Boolean = true,      // Tir instantané au touch (défaut: true)
    val lightgunAllowOffscreen: Boolean = true,      // Permet tir hors zone (défaut: true)
    val lightgunTriggerDelay: Int = 0,               // Délai avant tir (0-15 frames, défaut: 0)
    val lightgunPort: Int = 1,                       // Port lightgun (0-3, défaut: 1 = port 2)
    val lightgunTwoTouchInput: Int = 0,              // Action multi-touch 2 doigts (0 = none)
    val lightgunThreeTouchInput: Int = 0,            // Action multi-touch 3 doigts (0 = none)
    val lightgunFourTouchInput: Int = 0              // Action multi-touch 4 doigts (0 = none)
)
```

### 4.3 Implémentation trigger_on_touch

**Code source:** `RetroArchEmulatorActivity.kt:735-758`

```kotlin
when (event.actionMasked) {
    ACTION_DOWN, ACTION_MOVE -> {
        // Envoyer position POINTER
        retroView.sendMotionEvent(MOTION_SOURCE_POINTER, relativeX, relativeY, lightgunPort)
        
        if (event.actionMasked == ACTION_DOWN) {
            if (triggerOnTouch) {
                // BRANCHER triggerOnTouch: Si true, envoyer trigger immédiatement au DOWN
                sendLightgunTrigger(lightgunPort, triggerDelay)
            } else {
                Log.i(TAG, "[ZAPPER] Touch DOWN registered, waiting for UP to trigger (triggerOnTouch=false)")
            }
        }
    }
    
    ACTION_UP -> {
        if (!triggerOnTouch) {
            // BRANCHER triggerOnTouch: Si false, envoyer trigger au UP (release)
            sendLightgunTrigger(lightgunPort, triggerDelay)
        }
    }
}
```

**Comportement:**
- `triggerOnTouch = true` (défaut RetroArch) → Tir **instantané** au touch DOWN
- `triggerOnTouch = false` → Tir au **release** (touch UP)

### 4.4 Implémentation allow_offscreen

**Code source:** `RetroArchEmulatorActivity.kt:619-627`

```kotlin
val isInGameArea = touchX >= bounds.left && touchX <= bounds.right &&
                  touchY >= bounds.top && touchY <= bounds.bottom

if (!isInGameArea) {
    if (!allowOffscreen) {
        Log.d(TAG, "[ZAPPER] Touch OUTSIDE game area and allowOffscreen=false - ignored")
        return false
    }
    // Si allowOffscreen=true, clamp aux bounds
    Log.d(TAG, "[ZAPPER] Touch OUTSIDE game area, clamping to bounds")
}
```

**Comportement:**
- `allowOffscreen = true` (défaut) → Permet tir hors zone de jeu (clampé aux bords)
- `allowOffscreen = false` → Ignore les touches hors zone de jeu

### 4.5 Implémentation trigger_delay

**Code source:** `RetroArchEmulatorActivity.kt:506-522`

```kotlin
private fun sendLightgunTrigger(port: Int, delayMs: Int) {
    val sendTrigger = Runnable {
        retroView.sendKeyEvent(ACTION_DOWN, KEYCODE_BUTTON_A, port)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            retroView.sendKeyEvent(ACTION_UP, KEYCODE_BUTTON_A, port)
        }, 50)  // 50ms pulse
    }
    
    if (delayMs > 0) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(sendTrigger, delayMs.toLong())
    } else {
        sendTrigger.run()
    }
}
```

**Comportement:**
- `triggerDelay = 0` (défaut) → Tir instantané
- `triggerDelay = 1-15` frames → Délai avant tir (pour multi-touch ou cores lents)

**Note:** RetroArch utilise des frames (0-15), RetroPlay utilise des millisecondes. Conversion requise si nécessaire.

### 4.6 Boutons lightgun dans .cfg

**Format RetroArch:**
```ini
overlay1_full_screen = true

# Boutons lightgun visibles:
overlay1_desc0 = "gun_start,0.06,0.948,rect,0.06,0.052"    # Start button
overlay1_desc1 = "gun_aux_a,0.06,0.25,rect,0.06,0.052"     # Aux button A
overlay1_desc2 = "gun_aux_b,0.94,0.25,rect,0.06,0.052"     # Aux button B
overlay1_desc3 = "gun_reload,0.94,0.948,rect,0.06,0.052"   # Reload
overlay1_desc4 = "gun_trigger,0.5,0.5,rect,0.9,0.9"        # Full screen trigger
```

**Actions lightgun:**
- `gun_trigger` : Trigger principal (full screen ou bouton)
- `gun_reload` : Reload (force off-screen shot)
- `gun_aux_a` : Bouton auxiliaire A
- `gun_aux_b` : Bouton auxiliaire B
- `gun_start` : Start button
- `gun_select` : Select button
- `gun_dpad_up/down/left/right` : D-Pad directions

**Note:** Ces actions sont des bindings RetroArch spéciaux, pas des actions RetroPad standard.

### 4.7 Support multi-touch lightgun

**Code source:** `RetroArchEmulatorActivity.kt:529-546`

```kotlin
private fun handleMultiTouchActions(event: android.view.MotionEvent) {
    val lightgunSettings = OverlayPreferenceManager.loadAdvancedSettings(prefs, console)
    val fingerCount = event.pointerCount
    
    if (fingerCount > 1) {
        val actionId = when (fingerCount) {
            2 -> lightgunSettings.lightgunTwoTouchInput
            3 -> lightgunSettings.lightgunThreeTouchInput
            4 -> lightgunSettings.lightgunFourTouchInput
            else -> 0
        }
        
        if (actionId > 0) {
            sendLightgunAction(actionId, lightgunSettings.lightgunPort)
        }
    }
}
```

**Fonctionnement:**
- Détecte le nombre de doigts (2, 3, ou 4)
- Envoie l'action configurée correspondante
- Permet d'assigner différentes actions lightgun aux multi-touches

**Utilisation:** Exemple : 2 doigts = Reload, 3 doigts = Aux A, 4 doigts = Aux B

---

## Partie 5 : Gaps identifiés & Améliorations possibles

### 5.1 Gaps fonctionnels

1. **Boutons lightgun (gun_*) non supportés**
   - **Problème:** Les actions `gun_trigger`, `gun_reload`, `gun_aux_a/b` ne sont pas reconnues par le parser RetroPlay
   - **Impact:** Les overlays lightgun officiels RetroArch ne fonctionnent pas complètement
   - **Solution requise:** Ajouter support des bindings lightgun dans `RetroArchOverlayParser.kt`

2. **Trigger delay en frames vs millisecondes**
   - **Problème:** RetroArch utilise des frames (0-15), RetroPlay utilise des millisecondes
   - **Impact:** Comportement légèrement différent si conversion requise
   - **Solution requise:** Convertir frames → millisecondes basé sur FPS (60 FPS = 1 frame = 16.67ms)

3. **Support movable buttons non implémenté visuellement**
   - **Problème:** Le flag `movable = true` est parsé mais l'image ne suit pas le doigt dans RetroPlay
   - **Impact:** Les analog sticks ne bougent pas visuellement (comportement attendu pour meilleure UX)
   - **Solution requise:** Implémenter le suivi visuel de l'image quand `movable = true`

### 5.2 Améliorations potentielles

1. **Support complet des hotkeys RetroArch**
   - Ajouter tous les hotkeys disponibles (save_state, load_state, fast_forward, rewind, etc.)
   - Permettre mapping des hotkeys depuis les overlays
   - Documentation complète des hotkeys supportés

2. **Support exclusif hitboxes avancé**
   - Implémenter `exclusive` et `range_mod_exclusive` correctement
   - Tester avec overlays officiels ayant des hitboxes chevauchantes
   - Optimiser la détection de priorité

3. **Support saturate_pct pour analog sticks**
   - Implémenter la zone de saturation personnalisée
   - Permettre configuration depuis l'UI
   - Documentation de l'effet sur la sensibilité analogique

---

## Conclusion

Ce document d'audit "Nos Rules" fournit une documentation exhaustive du système d'overlays dans RetroPlay, basée sur l'analyse approfondie des sources officielles RetroArch et de l'implémentation actuelle de RetroPlay.

**Principales découvertes:**
- ✅ Format .cfg 100% documenté (tous les champs possibles)
- ✅ Parser RetroPlay 100% compatible avec RetroArch
- ✅ Support #include avec 16 niveaux de profondeur
- ✅ Intégration overlays + contrôleurs physiques fonctionnelle
- ✅ Support pointer/lightgun avec toutes les options RetroArch
- ⚠️ Boutons lightgun (gun_*) non supportés (gap identifié)
- ⚠️ Support movable buttons non implémenté visuellement (amélioration possible)

**Prochaines étapes:**
1. Ajouter support des bindings lightgun (gun_trigger, gun_reload, etc.)
2. Implémenter support movable buttons visuellement
3. Documenter tous les hotkeys RetroArch disponibles
4. Optimiser support exclusif hitboxes


