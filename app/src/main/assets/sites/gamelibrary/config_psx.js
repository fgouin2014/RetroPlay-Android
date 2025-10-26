// Configuration spécifique pour PlayStation (PSX)
(function() {
    const serverIP = window.location.hostname;
    
    // Vider le cache pour forcer les contrôles PSX
    localStorage.removeItem('EJS-controlScheme');
    localStorage.removeItem('EJS-settings');
    
    // Core PSX
    window.EJS_core = "pcsx_rearmed";
    
    // BIOS PSX
    window.EJS_biosUrl = `http://${serverIP}:7777/gamedata/data/bios/scph5501.bin`;
    window.EJS_skipBios = false; // Afficher l'animation BIOS
    
    // Contrôles PSX complets (Triangle, Circle, X, Square, L1/L2/R1/R2)
    window.EJS_VirtualGamepadSettings = {
        enabled: true,
        opacity: 0.8,
        size: 1.0,
        scheme: 'psx'
    };
    
    // Charger psxcontrol.js pour avoir TOUS les boutons PSX (L1, L2, R1, R2)
    const psxScript = document.createElement('script');
    psxScript.src = 'https://www.lemon-web.net/includes/psxcontrol.js';
    psxScript.async = false; // Charger de façon synchrone
    document.head.appendChild(psxScript);
    
    console.log('PSX configuration initialized');
})();

