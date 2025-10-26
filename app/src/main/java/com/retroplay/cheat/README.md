# 🎮 CHEAT SYSTEM MODULE

**Package** : `com.chatai.cheat`  
**Version** : 1.0  
**Status** : ✅ Production Ready

---

## 📦 COMPOSANTS

### CheatManager.kt
**Responsabilité** : Gestion des fichiers .cht et validation

**Fonctions principales** :
```kotlin
// Charger codes depuis fichier
fun loadRetroArchCheats(chtFile: File): List<Cheat>

// Trouver fichier pour un jeu
fun findCheatFile(console: String, gameName: String): File?

// Sauvegarder codes activés
fun saveEnabledCheats(console: String, gameName: String, cheats: List<Cheat>)

// Créer code personnalisé
fun createCustomCheat(console, gameName, description, code, type): Cheat

// Valider syntaxe
fun validateCheatCode(code: String, type: CheatType): Boolean
```

**Data classes** :
```kotlin
data class Cheat(
    val description: String,
    val code: String,
    val enabled: Boolean = false,
    val type: CheatType
)

enum class CheatType {
    RETROARCH, GAMESHARK, GAME_GENIE, ACTION_REPLAY, CUSTOM
}
```

---

### CheatApplier.kt
**Responsabilité** : Application des codes au core LibretroDroid

**Fonctions principales** :
```kotlin
// Appliquer liste de codes
fun applyCheatsList(cheats: List<CheatManager.Cheat>): Boolean

// Supprimer tous les codes
fun clearAllCheats()

// Toggle un code spécifique
fun toggleCheat(index: Int, enabled: Boolean, cheat: CheatManager.Cheat)
```

**Note** : Nécessite fork LibretroDroid pour application réelle (voir `FORK_LIBRETRODROID_GUIDE.md`)

---

### CheatActivity.kt
**Responsabilité** : Activity Compose principale

**Utilisation** :
```kotlin
val intent = Intent(context, CheatActivity::class.java)
intent.putExtra("console", "psx")
intent.putExtra("gameName", "Resident Evil")
startActivity(intent)
```

---

### CheatSelectionDialog.kt
**Responsabilité** : Interface Compose (dialogs)

**Composables** :
```kotlin
@Composable
fun CheatSelectionDialog(
    gameName: String,
    console: String,
    cheats: List<CheatManager.Cheat>,
    onDismiss: () -> Unit,
    onCheatsChanged: (List<CheatManager.Cheat>) -> Unit,
    onAddCustomCheat: () -> Unit
)

@Composable
fun AddCustomCheatDialog(
    console: String,
    onDismiss: () -> Unit,
    onAdd: (String, String, CheatManager.CheatType) -> Unit
)
```

---

## 📂 STRUCTURE DE STOCKAGE

```
/storage/emulated/0/GameLibrary-Data/
└── cheats/
    ├── retroarch/          # Codes officiels RetroArch
    │   ├── psx/
    │   │   └── Resident Evil.cht
    │   └── n64/
    │       └── Super Mario 64.cht
    │
    └── custom/             # Codes personnalisés
        ├── psx/
        │   └── Final Fantasy VII.cht
        └── snes/
            └── Chrono Trigger.cht
```

---

## 📝 FORMAT .CHT

```ini
cheats = 2

cheat0_desc = "Infinite Health"
cheat0_code = "8009C6E4+03E7"
cheat0_enable = false

cheat1_desc = "Max Money"
cheat1_code = "8009C8A0+FFFF"
cheat1_enable = true
```

---

## 🔧 UTILISATION

### 1. Depuis GameDetailsActivity
```java
// GameDetailsActivity.java
cheatButton.setOnClickListener(v -> {
    Intent intent = new Intent(this, CheatActivity.class);
    intent.putExtra("console", game.getConsole());
    intent.putExtra("gameName", game.getName());
    startActivity(intent);
});
```

### 2. Depuis NativeComposeEmulatorActivity
```kotlin
// NativeComposeEmulatorActivity.kt
val cheatManager = CheatManager(context)
val cheats = cheatManager.loadCheatsForGame(console, gameName)

CheatSelectionDialog(
    gameName = gameName,
    console = console,
    cheats = cheats,
    onDismiss = { /* ... */ },
    onCheatsChanged = { updatedCheats ->
        cheatManager.saveEnabledCheats(console, gameName, updatedCheats)
        cheatApplier.applyCheatsList(updatedCheats)
    },
    onAddCustomCheat = { /* ... */ }
)
```

---

## ✅ VALIDATION

### Formats supportés
```kotlin
// RetroArch : 8009C6E4+03E7
validateCheatCode("8009C6E4+03E7", CheatType.RETROARCH) // true

// GameShark : 8009C6E4 03E7
validateCheatCode("8009C6E4 03E7", CheatType.GAMESHARK) // true

// Game Genie : SXIOPO
validateCheatCode("SXIOPO", CheatType.GAME_GENIE) // true

// Action Replay : 12345678 ABCDEF01
validateCheatCode("12345678 ABCDEF01", CheatType.ACTION_REPLAY) // true
```

---

## 🐛 TROUBLESHOOTING

### Codes non appliqués
**Problème** : Logs montrent "Prepared cheat" mais pas d'effet

**Solution** : Fork LibretroDroid pour rendre `runOnGLThread()` public

**Guide** : `../../../FORK_LIBRETRODROID_GUIDE.md`

### Fichier .cht non détecté
**Problème** : `findCheatFile()` retourne null

**Vérifier** :
```bash
adb shell ls -la /storage/emulated/0/GameLibrary-Data/cheats/custom/psx/
# Le nom du fichier doit correspondre EXACTEMENT au nom du jeu
```

### Validation échoue
**Problème** : `validateCheatCode()` retourne false

**Solution** : Vérifier le format exact :
```kotlin
// ✅ Correct
"8009C6E4+03E7"  // Pas d'espaces autour du +

// ❌ Incorrect
"8009C6E4 + 03E7"  // Espaces autour du +
```

---

## 📚 DOCUMENTATION COMPLÈTE

Voir les fichiers à la racine du projet :
- `CHEAT_SYSTEM.md` - Architecture et utilisation
- `CHEAT_EXAMPLES.md` - Exemples de codes
- `CHEAT_SYSTEM_STATUS.md` - État actuel
- `FORK_LIBRETRODROID_GUIDE.md` - Activation complète

---

## 🚀 QUICK START

```kotlin
// 1. Créer le manager
val cheatManager = CheatManager(context)

// 2. Charger les codes
val cheats = cheatManager.loadCheatsForGame("psx", "Resident Evil")

// 3. Créer l'applier
val cheatApplier = CheatApplier(retroView)

// 4. Appliquer
cheatApplier.applyCheatsList(cheats)

// 5. Toggle
val updatedCheats = cheats.map { 
    if (it.description == "Infinite Health") 
        it.copy(enabled = true) 
    else 
        it 
}
cheatApplier.applyCheatsList(updatedCheats)
```

---

**🎮 Happy Cheating! ✨**

