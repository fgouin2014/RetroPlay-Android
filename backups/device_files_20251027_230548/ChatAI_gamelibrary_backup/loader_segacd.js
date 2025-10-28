// Sega CD / Mega CD Loader Configuration
(function() {
    const serverIP = window.location.hostname;
    const urlParams = new URLSearchParams(window.location.search);
    const gameUrl = urlParams.get('game');
    const gameSlug = urlParams.get('slug');

    window.EJS_player = "#game";
    window.EJS_core = "picodrive";
    window.EJS_pathtodata = `http://${serverIP}:8888/gamedata/data/`;
    window.EJS_biosUrl = `http://${serverIP}:8888/gamedata/data/bios/bios_CD_U.bin`;
    window.EJS_startOnLoaded = false;
    window.EJS_language = 'en-US';
    window.EJS_controlScheme = "";
    window.EJS_VirtualGamepadSettings = { enabled: true, opacity: 0.8, size: 1.0 };

    if (gameUrl) {
        window.EJS_gameUrl = gameUrl;
        
        console.log('[SEGA CD] Loading with game URL:', gameUrl);
        
        // Load Sega control script (local copy)
        const segaControlUrl = `http://${serverIP}:8888/gamelibrary/segacontrol.js`;
        console.log('[SEGA CD] Loading control script:', segaControlUrl);
        
        const segaScript = document.createElement('script');
        segaScript.src = segaControlUrl;
        segaScript.onload = () => {
            console.log('[SEGA CD] Control script loaded, now loading EmulatorJS');
            
            // Load EmulatorJS AFTER control script is ready
            const loaderScript = document.createElement('script');
            loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
            loaderScript.onload = () => console.log('[SEGA CD] EmulatorJS loaded');
            loaderScript.onerror = () => console.error('[SEGA CD] Failed to load EmulatorJS');
            document.body.appendChild(loaderScript);
        };
        segaScript.onerror = () => {
            console.error('[SEGA CD] Failed to load control script');
        };
        document.body.appendChild(segaScript);
        return;
    }

    function generateSlug(name) {
        return name.toLowerCase().replace(/['`"`]/g, '').replace(/[^a-z0-9\s-]/g, '').replace(/\s+/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
    }

    fetch(`http://${serverIP}:8888/gamedata/segacd/gamelist.json`)
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
            window.EJS_gameUrl = `http://${serverIP}:8888/gamedata/segacd/${gameFile}`;
            
            console.log('[SEGA CD] Game URL:', window.EJS_gameUrl);
            
            // Load Sega control script
            const segaControlUrl = `http://${serverIP}:8888/gamelibrary/segacontrol.js`;
            console.log('[SEGA CD] Loading control script:', segaControlUrl);
            
            const segaScript = document.createElement('script');
            segaScript.src = segaControlUrl;
            segaScript.onload = () => {
                console.log('[SEGA CD] Control script loaded, now loading EmulatorJS');
                
                const loaderScript = document.createElement('script');
                loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
                loaderScript.onload = () => console.log('[SEGA CD] EmulatorJS loaded');
                loaderScript.onerror = () => console.error('[SEGA CD] Failed to load EmulatorJS');
                document.body.appendChild(loaderScript);
            };
            segaScript.onerror = () => {
                console.error('[SEGA CD] Failed to load control script');
            };
            document.body.appendChild(segaScript);
        })
        .catch(error => { 
            console.error('Error loading game:', error); 
            document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Error loading game</h1>'; 
        });
})();
