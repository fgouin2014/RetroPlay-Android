# Guide de Test Rapide - Extensions N64

## Étapes Rapides

1. **Configurer les extensions dans l'app:**
   - Ouvrir RetroPlay → Paramètres N64
   - Configurer Port 1 = "Controller Pak"
   - Sauvegarder

2. **Lancer un jeu N64:**
   - Choisir un jeu N64 compatible (ex: Super Mario 64)
   - Observer les notifications Toast

3. **Vérifier les logs:**
   ```powershell
   adb logcat -s RetroArchEmulatorActivity:* | Select-String -Pattern "N64"
   ```

## Logs Attendus

✅ **Succès:**
```
[N64] Configuring controller extensions...
[N64] Available controllers for each port:
[N64] Extension configured for port 1: Controller Pak (id=1) via setControllerType()
```

❌ **Erreur:**
```
[N64] Failed to set extension for port 1 via setControllerType(): [erreur]
```

## Tests à Faire

- [ ] Test avec ParaLLEl N64
- [ ] Test avec Mupen64Plus Next
- [ ] Vérifier Controller Pak fonctionne
- [ ] Vérifier Rumble Pak fonctionne
- [ ] Vérifier Transfer Pak fonctionne (si applicable)

