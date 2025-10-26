// Nintendo DS Loader Configuration
(function() {
    const serverIP = window.location.hostname;
    const urlParams = new URLSearchParams(window.location.search);
    const gameUrl = urlParams.get('game');
    const gameSlug = urlParams.get('slug');

    window.EJS_player = "#game";
    window.EJS_core = "melonds";
    window.EJS_pathtodata = `http://${serverIP}:7777/gamedata/data/`;
    window.EJS_startOnLoaded = false;
    window.EJS_language = 'en-US';
    // BIOS required for Nintendo DS
    window.EJS_biosUrl = `http://${serverIP}:7777/gamedata/data/bios/bios7.bin`;
    window.EJS_bios9Url = `http://${serverIP}:7777/gamedata/data/bios/bios9.bin`;
    window.EJS_firmwareUrl = `http://${serverIP}:7777/gamedata/data/bios/firmware.bin`;
    window.EJS_controlScheme = "";
    window.EJS_VirtualGamepadSettings = { enabled: true, opacity: 0.8, size: 1.0 };

    if (gameUrl) {
        window.EJS_gameUrl = gameUrl;
        const loaderScript = document.createElement('script');
        loaderScript.src = `http://${serverIP}:7777/gamedata/data/loader.js`;
        document.body.appendChild(loaderScript);
        return;
    }

    function generateSlug(name) {
        return name.toLowerCase().replace(/['`"`]/g, '').replace(/[^a-z0-9\s-]/g, '').replace(/\s+/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
    }

    fetch(`http://${serverIP}:7777/gamedata/nds/gamelist.json`)
        .then(response => response.json())
        .then(data => {
            const games = data.games || [];
            const game = games.find(g => generateSlug(g.name) === gameSlug);
            if (!game) { document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Game not found</h1>'; return; }
            let gameFile = game.path || game.file;
            gameFile = gameFile.replace(/^\.\//, '');
            window.EJS_gameUrl = `http://${serverIP}:7777/gamedata/nds/${gameFile}`;
            const loaderScript = document.createElement('script');
            loaderScript.src = `http://${serverIP}:7777/gamedata/data/loader.js`;
            document.body.appendChild(loaderScript);
        })
        .catch(error => { console.error('Error loading game:', error); document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Error loading game</h1>'; });
})();
