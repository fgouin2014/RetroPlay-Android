// FBNeo Arcade Loader Configuration - OPTIMIZED
(function() {
    const serverIP = window.location.hostname;
    const urlParams = new URLSearchParams(window.location.search);
    const gameUrl = urlParams.get('game');
    const gameSlug = urlParams.get('slug');
    const gameConsole = urlParams.get('console') || 'fbneo';

    window.EJS_player = "#game";
    
    // Sélection automatique du core selon la sous-console
    let selectedCore = "fbneo"; // Core par défaut
    const consoleLower = gameConsole.toLowerCase();
    
    if (consoleLower.includes('cps1') || consoleLower.includes('cps-i')) {
        selectedCore = "fbalpha2012_cps1";
        console.log('Using optimized CPS1 core:', selectedCore);
    } else if (consoleLower.includes('cps2') || consoleLower.includes('cps-ii')) {
        selectedCore = "fbalpha2012_cps2";
        console.log('Using optimized CPS2 core:', selectedCore);
    } else if (consoleLower.includes('cps3') || consoleLower.includes('cps-iii') || consoleLower.includes('cpiii')) {
        selectedCore = "fbneo"; // CPS3 utilise FBNeo standard
        console.log('Using FBNeo for CPS3:', selectedCore);
    } else if (consoleLower.includes('sega') || consoleLower.includes('taito') || consoleLower.includes('konami')) {
        selectedCore = "fbneo"; // Manufacturer-specific subconsoles use FBNeo
        console.log('Using FBNeo for manufacturer-specific games:', selectedCore);
    }
    
    window.EJS_core = selectedCore;
    window.EJS_pathtodata = `http://${serverIP}:8888/gamedata/data/`;
    window.EJS_startOnLoaded = false;
    window.EJS_language = 'en-US';
    window.EJS_controlScheme = "";
    window.EJS_VirtualGamepadSettings = { enabled: true, opacity: 0.8, size: 1.0 };

    if (gameUrl) {
        window.EJS_gameUrl = gameUrl;
        const loaderScript = document.createElement('script');
        loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
        document.body.appendChild(loaderScript);
        return;
    }

    function generateSlug(name) {
        return name.toLowerCase().replace(/['`"`]/g, '').replace(/[^a-z0-9\s-]/g, '').replace(/\s+/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
    }

    // Supporter les sous-consoles (fbneo, fbneo/sega, fbneo/taito, etc.)
    const consolePath = gameConsole;
    console.log('Loading FBNeo games from:', consolePath, 'with core:', selectedCore);

    fetch(`http://${serverIP}:8888/gamedata/${consolePath}/gamelist.json`)
        .then(response => response.json())
        .then(data => {
            const games = data.games || [];
            const game = games.find(g => generateSlug(g.name) === gameSlug);
            if (!game) { document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Game not found</h1>'; return; }
            let gameFile = game.path || game.file;
            gameFile = gameFile.replace(/^\.\//, '');
            window.EJS_gameUrl = `http://${serverIP}:8888/gamedata/${consolePath}/${gameFile}`;
            console.log('Game URL:', window.EJS_gameUrl);
            const loaderScript = document.createElement('script');
            loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
            document.body.appendChild(loaderScript);
        })
        .catch(error => { console.error('Error loading game:', error); document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Error loading game</h1>'; });
})();


