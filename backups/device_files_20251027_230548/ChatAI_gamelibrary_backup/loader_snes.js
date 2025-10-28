// SNES Loader Configuration
(function() {
    const serverIP = window.location.hostname;
    const urlParams = new URLSearchParams(window.location.search);
    const gameUrl = urlParams.get('game');
    const gameSlug = urlParams.get('slug');

    window.EJS_player = "#game";
    window.EJS_core = "snes9x";
    window.EJS_pathtodata = `http://${serverIP}:8888/gamedata/data/`;
    window.EJS_startOnLoaded = false;
    window.EJS_language = 'en-US';

    if (gameUrl) {
        window.EJS_gameUrl = gameUrl;
        
        const loaderScript = document.createElement('script');
        loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
        document.body.appendChild(loaderScript);
        return;
    }

    function generateSlug(name) {
        return name.toLowerCase().replace(/['"`]/g, '').replace(/[^a-z0-9\s-]/g, '').replace(/\s+/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
    }

    fetch(`http://${serverIP}:8888/gamedata/snes/gamelist.json`)
        .then(response => response.json())
        .then(data => {
            const games = data.games || [];
            const game = games.find(g => generateSlug(g.name) === gameSlug);
            if (!game) { document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Game not found</h1>'; return; }
            let gameFile = game.path || game.file;
            gameFile = gameFile.replace(/^\.\//, '');
            // Encoder le nom de fichier pour gérer les caractères spéciaux (+, &, %, etc.)
            const encodedGameFile = encodeURIComponent(gameFile);
            window.EJS_gameUrl = `http://${serverIP}:8888/gamedata/snes/${encodedGameFile}`;
            const loaderScript = document.createElement('script');
            loaderScript.src = `http://${serverIP}:8888/gamedata/data/loader.js`;
            document.body.appendChild(loaderScript);
        })
        .catch(error => { console.error('Error loading game:', error); document.body.innerHTML = '<h1 style="color: white; text-align: center; padding-top: 50px;">Error loading game</h1>'; });
})();

