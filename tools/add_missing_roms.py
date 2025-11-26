#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ajoute les ROMs manquantes aux gamelist.json existants
Usage: python add_missing_roms.py <chemin_vers_GameLibrary-Data> [console_id]
"""

import os
import sys
import json
from pathlib import Path
from datetime import datetime

# Redéfinir les fonctions nécessaires (copiées de generate_gamelist_pc.py)
def normalize_console_id(console_id: str) -> str:
    console_id_lower = console_id.lower()
    mappings = {
        "fc": "nes", "famicom": "nes",
        "super nintendo": "snes", "sfc": "snes", "superfamicom": "snes",
        "sega genesis": "genesis", "sega megadrive": "genesis",
        "mega drive": "genesis", "md": "genesis",
    }
    return mappings.get(console_id_lower, console_id_lower)

CONSOLE_EXTENSIONS = {
    "nes": [".nes", ".fds", ".unf", ".zip"],
    "snes": [".smc", ".sfc", ".fig", ".zip"],
    "n64": [".n64", ".z64", ".v64", ".zip"],
    "gb": [".gb", ".zip"], "gbc": [".gbc", ".zip"],
    "gba": [".gba", ".agb", ".zip"],
    "genesis": [".md", ".gen", ".smd", ".32x", ".zip"],
    "megadrive": [".md", ".gen", ".smd", ".32x", ".zip"],
    "mastersystem": [".sms", ".zip"], "gamegear": [".gg", ".zip"],
    "segacd": [".iso", ".cue", ".bin", ".img", ".chd", ".gdi", ".zip"],
    "psx": [".iso", ".cue", ".bin", ".img", ".mdf", ".pbp", ".zip"],
    "psp": [".iso", ".cso", ".pbp", ".elf", ".zip"],
    "atari2600": [".a26", ".bin", ".zip"],
    "atari5200": [".a52", ".bin", ".zip"],
    "atari7800": [".a78", ".bin", ".zip", ".7z"],
    "lynx": [".lnx", ".zip"], "ngp": [".ngp", ".ngc", ".zip"],
    "wonderswancolor": [".ws", ".wsc", ".zip"],
    "pce": [".pce", ".sgx", ".zip"],
    "fbneo": [".zip", ".7z"], "arcade": [".zip", ".7z"],
    "c64": [".d64", ".d71", ".d81", ".t64", ".tap", ".prg", ".crt", ".bin", ".zip"],
    "amiga": [".adf", ".dsk", ".ipf", ".zip"],
    "zxspectrum": [".tap", ".tzx", ".z80", ".sna", ".dsk", ".trd", ".scl", ".zip"],
    "spectrum": [".tap", ".tzx", ".z80", ".sna", ".dsk", ".trd", ".scl", ".zip"],
}

def get_default_extensions(console_id: str) -> list:
    normalized = normalize_console_id(console_id)
    return CONSOLE_EXTENSIONS.get(normalized, [".zip", ".7z", ".rom", ".bin"])

def extract_filename_from_path(path_str: str) -> str:
    if not path_str: return ""
    clean_path = path_str.replace("./", "").strip()
    filename = os.path.basename(clean_path)
    return os.path.splitext(filename)[0]

def clean_file_name(file_name: str) -> str:
    base_name = extract_filename_from_path(file_name) if "." in file_name else file_name
    base_name = base_name.replace("_", " ").replace("-", " ")
    return " ".join(base_name.split())

# Importer les fonctions de hash depuis generate_gamelist_pc
import importlib.util
spec = importlib.util.spec_from_file_location("generate_gamelist_pc", Path(__file__).parent / "generate_gamelist_pc.py")
gen_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen_module)

calculate_hash_from_file = gen_module.calculate_hash_from_file
calculate_hash_from_zip = gen_module.calculate_hash_from_zip
get_primary_hash = gen_module.get_primary_hash


def find_rom_files(console_dir: Path, console_id: str, max_depth: int = 2) -> list:
    """Trouve tous les fichiers ROM dans un répertoire"""
    rom_files = []
    extensions = get_default_extensions(console_id)
    
    if not console_dir.exists() or not console_dir.is_dir():
        return rom_files
    
    try:
        for root, dirs, files in os.walk(console_dir):
            # Limiter la profondeur
            depth = root[len(str(console_dir)):].count(os.sep)
            if depth > max_depth:
                dirs[:] = []
                continue
            
            for file in files:
                file_path = Path(root) / file
                ext = file_path.suffix.lower()
                if any(ext == e.lower() for e in extensions):
                    rel_path = file_path.relative_to(console_dir)
                    rom_files.append((str(rel_path).replace("\\", "/"), file_path))
    except Exception as e:
        print(f"  Erreur scan: {e}")
    
    return rom_files


def get_gamelist_paths(gamelist_path: Path) -> set:
    """Extrait tous les chemins depuis gamelist.json"""
    paths = set()
    
    if not gamelist_path.exists():
        return paths
    
    try:
        with open(gamelist_path, 'r', encoding='utf-8') as f:
            gamelist = json.load(f)
        
        if 'games' not in gamelist:
            return paths
        
        for game in gamelist['games']:
            if 'path' in game:
                path_str = game['path']
                clean_path = path_str.replace("./", "").strip()
                paths.add(clean_path)
    except Exception as e:
        print(f"  Erreur lecture gamelist: {e}")
    
    return paths


def create_game_entry(file_path: Path, base_dir: Path, console_id: str) -> dict:
    """Crée une entrée de jeu depuis un fichier ROM (identique à generate_gamelist_pc.py)"""
    try:
        file_name = file_path.name
        base_name = clean_file_name(extract_filename_from_path(file_name))
        relative_path = file_path.relative_to(base_dir).as_posix()
        
        # Calculer les hashes
        if file_path.suffix.lower() == '.zip':
            crc32, md5, sha1 = calculate_hash_from_zip(file_path)
        else:
            # Vérifier header iNES pour .nes
            skip_bytes = 0
            if file_path.suffix.lower() == '.nes':
                with open(file_path, 'rb') as f:
                    header = f.read(4)
                    if header == b'NES\x1a':
                        skip_bytes = 16
            
            crc32, md5, sha1 = calculate_hash_from_file(file_path, skip_bytes)
        
        primary_hash = get_primary_hash(crc32, md5, sha1)
        
        if not primary_hash:
            return None
        
        # Créer l'entrée de jeu
        game_entry = {
            "id": "0",  # Sera assigné plus tard
            "name": base_name,
            "path": f"./{relative_path}",
            "desc": None,
            "image": f"./media/box2d/{base_name}.png",
            "releasedate": None,
            "developer": None,
            "publisher": None,
            "genre": None,
            "players": None,
            "hash": primary_hash,
            "rating": None
        }
        
        return game_entry
        
    except Exception as e:
        print(f"  Erreur création entrée pour {file_path.name}: {e}")
        return None


def add_missing_roms(console_dir: Path, console_id: str) -> tuple[int, int]:
    """
    Ajoute les ROMs manquantes au gamelist.json existant
    Retourne (nombre_ajouté, total_roms_manquantes)
    """
    gamelist_path = console_dir / "gamelist.json"
    
    # Charger le gamelist existant
    if gamelist_path.exists():
        with open(gamelist_path, 'r', encoding='utf-8') as f:
            gamelist = json.load(f)
    else:
        # Créer un nouveau gamelist
        gamelist = {
            "version": "1.0",
            "console": console_id,
            "games": [],
            "metadata": {
                "generatedAt": int(datetime.now().timestamp() * 1000),
                "sourceDirectories": [str(console_dir.absolute())],
                "autoGenerated": True,
                "totalGames": 0,
                "console": console_id
            }
        }
    
    # Obtenir les chemins existants
    existing_paths = get_gamelist_paths(gamelist_path)
    
    # Trouver tous les fichiers ROM
    all_rom_files = find_rom_files(console_dir, console_id)
    
    # Identifier les ROMs manquantes
    missing_roms = []
    for rel_path, file_path in all_rom_files:
        normalized_path = rel_path.replace("\\", "/")
        if normalized_path not in existing_paths:
            missing_roms.append((rel_path, file_path))
    
    if not missing_roms:
        return (0, 0)
    
    print(f"  Ajout de {len(missing_roms)} ROMs manquantes...")
    
    # Créer les entrées pour les ROMs manquantes
    new_entries = []
    for rel_path, file_path in missing_roms:
        entry = create_game_entry(file_path, console_dir, console_id)
        if entry:
            new_entries.append(entry)
        else:
            # Créer une entrée basique même sans hash (pour arcade/fbneo)
            try:
                base_name = clean_file_name(extract_filename_from_path(file_path.name))
                entry = {
                    "id": "0",
                    "name": base_name,
                    "path": f"./{rel_path}",
                    "desc": None,
                    "image": f"./media/box2d/{base_name}.png",
                    "releasedate": None,
                    "developer": None,
                    "publisher": None,
                    "genre": None,
                    "players": None,
                    "hash": None,  # Pas de hash disponible
                    "rating": None
                }
                new_entries.append(entry)
            except Exception as e:
                print(f"    Erreur création entrée basique pour {rel_path}: {e}")
    
    if not new_entries:
        return (0, len(missing_roms))
    
    # Ajouter les nouvelles entrées aux jeux existants
    existing_games = gamelist.get('games', [])
    all_games = existing_games + new_entries
    
    # Trier par nom
    all_games_sorted = sorted(all_games, key=lambda g: g['name'].lower())
    
    # Réassigner les IDs
    for index, game in enumerate(all_games_sorted, start=1):
        game['id'] = str(index)
    
    # Mettre à jour le gamelist
    gamelist['games'] = all_games_sorted
    if 'metadata' in gamelist:
        gamelist['metadata']['totalGames'] = len(all_games_sorted)
        gamelist['metadata']['generatedAt'] = int(datetime.now().timestamp() * 1000)
    
    # Créer une sauvegarde
    backup_path = console_dir / f"gamelist_backup_add_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    if gamelist_path.exists():
        with open(backup_path, 'w', encoding='utf-8') as f:
            json.dump(gamelist, f, indent=2, ensure_ascii=False)
    
    # Sauvegarder le gamelist mis à jour
    with open(gamelist_path, 'w', encoding='utf-8') as f:
        json.dump(gamelist, f, indent=2, ensure_ascii=False)
    
    return (len(new_entries), len(missing_roms))


def main():
    if len(sys.argv) < 2:
        print("Usage: python add_missing_roms.py <chemin_vers_GameLibrary-Data> [console_id]")
        print("\nExemples:")
        print("  python add_missing_roms.py E:/GameLibrary-Data")
        print("  python add_missing_roms.py E:/GameLibrary-Data arcade")
        sys.exit(1)
    
    gamelibrary_path = Path(sys.argv[1])
    specific_console = sys.argv[2] if len(sys.argv) > 2 else None
    
    if not gamelibrary_path.exists():
        print(f"Erreur: Le répertoire {gamelibrary_path} n'existe pas")
        sys.exit(1)
    
    # Trouver les répertoires de consoles
    if specific_console:
        console_dirs = [gamelibrary_path / specific_console]
    else:
        console_dirs = [d for d in gamelibrary_path.iterdir() 
                       if d.is_dir() and not d.name.startswith('.')]
    
    if not console_dirs:
        print("Aucun répertoire de console trouvé")
        sys.exit(0)
    
    print(f"Ajout des ROMs manquantes dans: {gamelibrary_path}\n")
    
    total_added = 0
    total_missing = 0
    
    # Traiter chaque console
    for console_dir in sorted(console_dirs):
        console_id = console_dir.name
        print(f"Traitement: {console_id}...")
        
        added, missing = add_missing_roms(console_dir, console_id)
        
        if added > 0:
            print(f"  [OK] {added}/{missing} ROMs ajoutées")
            total_added += added
        else:
            if missing > 0:
                print(f"  [WARN] {missing} ROMs manquantes mais impossible de les ajouter (hash failed)")
            else:
                print(f"  [OK] Aucune ROM manquante")
        
        total_missing += missing
    
    print(f"\n{'='*80}")
    print(f"Total ROMs ajoutées: {total_added}")
    print(f"Total ROMs manquantes: {total_missing}")
    print(f"{'='*80}")


if __name__ == "__main__":
    main()

