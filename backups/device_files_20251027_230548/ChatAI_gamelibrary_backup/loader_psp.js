// PSP Loader Configuration - Smart threads detection + Custom Controls
(function() {
    const serverIP = window.location.hostname;
    const urlParams = new URLSearchParams(window.location.search);
    const gameUrl = urlParams.get('game');
    const gameSlug = urlParams.get('slug');
    const useDpad = urlParams.get('dpad') === 'true';  // Option D-Pad

    // Smart detection: activer threads seulement si on accède via localhost
    // (Chrome refuse COEP/COOP sur les IPs, donc SharedArrayBuffer ne fonctionne pas)
    const isLocalhost = (serverIP === 'localhost' || serverIP === '127.0.0.1');
    const enableThreads = isLocalhost;

    console.log('[PSP] Server IP:', serverIP);
    console.log('[PSP] Threads enabled:', enableThreads, '(localhost only)');
    console.log('[PSP] Control mode:', useDpad ? 'D-Pad ONLY (no analog sticks)' : 'Analog (DualShock with sticks)');

    // Clear any existing localStorage that might interfere
    localStorage.removeItem('EJS-controlScheme');

    // Configure EmulatorJS for PSP
    window.EJS_player = "#game";
    window.EJS_core = 'ppsspp';
    window.EJS_pathtodata = `http://${serverIP}:8888/gamedata/data/`;
    window.EJS_startOnLoaded = false;
    window.EJS_language = 'en-US';
    window.EJS_threads = enableThreads;  // Conditionnel selon l'accès

    if (!enableThreads) {
        console.warn('[PSP] ⚠️ Threads désactivés (accès réseau). Performance réduite.');
        console.warn('[PSP] Pour activer threads, accédez via l\'app ou http://localhost:8888');
    }

    // If direct game URL is provided, use it directly
    if (gameUrl) {
        window.EJS_gameUrl = gameUrl;
        
        console.log('[PSP] Loading with game URL:', gameUrl);
        
        // Choose which PSP control script to load (both served locally)
        const pspControlUrl = useDpad 
            ? `http://${serverIP}:8888/gamelibrary/pspcontroldpad.js`  // D-Pad only
            : `http://${serverIP}:8888/gamelibrary/pspcontrol.js`;     // Analog sticks
        
        console.log('[PSP] Loading control script:', pspControlUrl);
        
        const pspScript = document.createElement('script');
        pspScript.src = pspControlUrl;
        pspScript.onload = () => {
            console.log('[PSP] Control script loaded, now loading EmulatorJS');
            
            // Load EmulatorJS AFTER control script is ready
            const loaderScript = document.createElement('script');
            loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
            loaderScript.onload = () => console.log('[PSP] EmulatorJS loaded');
            loaderScript.onerror = () => console.error('[PSP] Failed to load EmulatorJS');
            document.body.appendChild(loaderScript);
        };
        pspScript.onerror = () => {
            console.error('[PSP] Failed to load control script');
            // Fallback to loader.js without custom controls
            const loaderScript = document.createElement('script');
            loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
            document.body.appendChild(loaderScript);
        };
        document.body.appendChild(pspScript);
        return;
    }

    // Otherwise, load from gamelist.json using slug
    function generateSlug(name) {
        return name.toLowerCase()
            .replace(/['"`]/g, '')
            .replace(/[^a-z0-9\s-]/g, '')
            .replace(/\s+/g, '-')
            .replace(/-+/g, '-')
            .replace(/^-|-$/g, '');
    }

    fetch(`http://${serverIP}:8888/gamedata/psp/gamelist.json`)
        .then(response => response.json())
        .then(data => {
            const games = data.games || [];
            const game = games.find(g => generateSlug(g.name) === gameSlug);

            if (!game) {
                document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Game not found</h1>';        
                return;
            }

            let gameFile = game.path || game.file;
            gameFile = gameFile.replace(/^\.\//, '');
            // Encoder le nom de fichier pour gérer les caractères spéciaux (+, &, %, etc.)
            const encodedGameFile = encodeURIComponent(gameFile);
            window.EJS_gameUrl = `http://${serverIP}:8888/gamedata/psp/${encodedGameFile}`;
            
            // Choose which PSP control script to load
            const pspControlUrl = useDpad 
                ? `http://${serverIP}:8888/gamelibrary/pspcontroldpad.js`  // D-Pad only
                : `http://${serverIP}:8888/gamelibrary/pspcontrol.js`;     // Analog sticks
            
            console.log('[PSP] Loading control script:', pspControlUrl);
            
            const pspScript = document.createElement('script');
            pspScript.src = pspControlUrl;
            pspScript.onload = () => {
                console.log('[PSP] Control script loaded, now loading EmulatorJS');
                
                // Load EmulatorJS AFTER control script is ready
                const loaderScript = document.createElement('script');
                loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
                loaderScript.onload = () => console.log('[PSP] EmulatorJS loaded');
                loaderScript.onerror = () => console.error('[PSP] Failed to load EmulatorJS');
                document.body.appendChild(loaderScript);
            };
            pspScript.onerror = () => {
                console.error('[PSP] Failed to load control script');
                // Fallback to loader.js without custom controls
                const loaderScript = document.createElement('script');
                loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
                document.body.appendChild(loaderScript);
            };
            document.body.appendChild(pspScript);
        })
        .catch(error => {
            console.error('Error loading game:', error);
            document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Error loading game</h1>';        
        });
})();

