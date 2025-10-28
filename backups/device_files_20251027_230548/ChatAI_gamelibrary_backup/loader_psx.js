// PSX Loader Configuration
(function() {
    const serverIP = window.location.hostname;  // Use current hostname for network access
    const urlParams = new URLSearchParams(window.location.search);
    const gameUrl = urlParams.get('game');
    const gameSlug = urlParams.get('slug');
    const useDpad = urlParams.get('dpad') === 'true';  // Option D-Pad

    // Clear any existing localStorage that might interfere
    localStorage.removeItem('EJS-controlScheme');

    // Configure EmulatorJS for PSX
    window.EJS_player = "#game";
    window.EJS_core = 'pcsx_rearmed';
    window.EJS_pathtodata = `http://${serverIP}:8888/gamedata/data/`;
    window.EJS_biosUrl = `http://${serverIP}:8888/gamedata/data/bios/scph5501.bin`;
    window.EJS_skipBios = false;
    window.EJS_startOnLoaded = false;
    window.EJS_language = 'en-US';
    
    // Control script will set EJS_VirtualGamepadSettings
    console.log('[PSX] Control mode:', useDpad ? 'D-Pad ONLY (no analog sticks)' : 'Analog (DualShock with sticks)');

    // If direct game URL is provided, use it directly
    if (gameUrl) {
        window.EJS_gameUrl = gameUrl;
        
        console.log('[PSX] Loading with game URL:', gameUrl);
        
        // Choose which PSX control script to load (both served locally)
        const psxControlUrl = useDpad 
            ? `http://${serverIP}:8888/gamelibrary/psxcontroldpad.js`  // D-Pad only
            : `http://${serverIP}:8888/gamelibrary/psxcontrol.js`;     // Analog sticks (local copy)
        
        console.log('[PSX] Loading control script:', psxControlUrl);
        
        const psxScript = document.createElement('script');
        psxScript.src = psxControlUrl;
        psxScript.onload = () => {
            console.log('[PSX] Control script loaded, now loading EmulatorJS');
            
            // Load EmulatorJS AFTER control script is ready
            const loaderScript = document.createElement('script');
            loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
            loaderScript.onload = () => console.log('[PSX] EmulatorJS loaded');
            loaderScript.onerror = () => console.error('[PSX] Failed to load EmulatorJS');
            document.body.appendChild(loaderScript);
        };
        psxScript.onerror = () => {
            console.error('[PSX] Failed to load control script');
        };
        document.body.appendChild(psxScript);
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

    // Load gamelist.json and find the game
    fetch(`http://${serverIP}:8888/gamedata/psx/gamelist.json`)
        .then(response => {
            console.log('Gamelist fetch response:', response.status);
            return response.json();
        })
        .then(data => {
            console.log('Gamelist loaded');
            const games = data.games || [];
            console.log('Looking for slug:', gameSlug);
            
            const game = games.find(g => generateSlug(g.name) === gameSlug);
            
            if (!game) {
                console.error('Game not found!');
                console.log('Available games:', games.map(g => ({name: g.name, slug: generateSlug(g.name)})));
                document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Game not found</h1><p style="color: white; text-align: center;">Slug: ' + gameSlug + '</p>';
                return;
            }

            console.log('Game found:', game.name);
            
            // Set game URL (clean ./ prefix if present)
            let gameFile = game.path || game.file;
            gameFile = gameFile.replace(/^\.\//, '');
            window.EJS_gameUrl = `http://${serverIP}:8888/gamedata/psx/${gameFile}`;
            
            console.log('[PSX] Game URL:', window.EJS_gameUrl);

            // Choose which PSX control script to load (both local)
            const psxControlUrl = useDpad 
                ? `http://${serverIP}:8888/gamelibrary/psxcontroldpad.js`  // D-Pad only
                : `http://${serverIP}:8888/gamelibrary/psxcontrol.js`;     // Analog sticks (local)
            
            console.log('[PSX] Loading control script:', psxControlUrl);
            
            const psxScript = document.createElement('script');
            psxScript.src = psxControlUrl;
            psxScript.onload = () => {
                console.log('[PSX] Control script loaded, now loading EmulatorJS');
                
                // Load EmulatorJS AFTER control script is ready
                const loaderScript = document.createElement('script');
                loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
                loaderScript.onload = () => console.log('[PSX] EmulatorJS loaded');
                loaderScript.onerror = () => console.error('[PSX] Failed to load EmulatorJS');
                document.body.appendChild(loaderScript);
            };
            psxScript.onerror = () => {
                console.error('[PSX] Failed to load control script');
            };
            document.body.appendChild(psxScript);
        })
        .catch(error => {
            console.error('Error loading gamelist:', error);
            document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Error loading game</h1>';
        });
})();

