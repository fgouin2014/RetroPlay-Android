#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Trouve les ROMs qui existent sur le disque mais qui ne sont pas dans gamelist.json
Usage: python find_missing_roms.py <chemin_vers_GameLibrary-Data> [console_id]
"""

import os
import sys
import json
from pathlib import Path

# Extensions par console (identique à GamelistManager.kt)
CONSOLE_EXTENSIONS = {
    "nes": [".nes", ".fds", ".unf", ".zip"],
    "snes": [".smc", ".sfc", ".fig", ".zip"],
    "n64": [".n64", ".z64", ".v64", ".zip"],
    "gb": [".gb", ".zip"],
    "gbc": [".gbc", ".zip"],
    "gba": [".gba", ".agb", ".zip"],
    "genesis": [".md", ".gen", ".smd", ".32x", ".zip"],
    "megadrive": [".md", ".gen", ".smd", ".32x", ".zip"],
    "mastersystem": [".sms", ".zip"],
    "gamegear": [".gg", ".zip"],
    "segacd": [".iso", ".cue", ".bin", ".img", ".chd", ".gdi", ".zip"],
    "psx": [".iso", ".cue", ".bin", ".img", ".mdf", ".pbp", ".zip"],
    "psp": [".iso", ".cso", ".pbp", ".elf", ".zip"],
    "atari2600": [".a26", ".bin", ".zip"],
    "atari5200": [".a52", ".bin", ".zip"],
    "atari7800": [".a78", ".bin", ".zip", ".7z"],
    "lynx": [".lnx", ".zip"],
    "ngp": [".ngp", ".ngc", ".zip"],
    "wonderswancolor": [".ws", ".wsc", ".zip"],
    "pce": [".pce", ".sgx", ".zip"],
    "fbneo": [".zip", ".7z"],
    "arcade": [".zip", ".7z"],
    "c64": [".d64", ".d71", ".d81", ".t64", ".tap", ".prg", ".crt", ".bin", ".zip"],
    "amiga": [".adf", ".dsk", ".ipf", ".zip"],
    "zxspectrum": [".tap", ".tzx", ".z80", ".sna", ".dsk", ".trd", ".scl", ".zip"],
    "spectrum": [".tap", ".tzx", ".z80", ".sna", ".dsk", ".trd", ".scl", ".zip"],
}

# Répertoires système à ignorer
SYSTEM_DIRECTORIES = {
    "data", "emulatorjs", "vmnes", "playlists",
    "saves", "states", "cheats", "media",
    "overlays", "cores", "bios", ".cache"
}


def normalize_console_id(console_id: str) -> str:
    """Normalise l'ID de console"""
    console_id_lower = console_id.lower()
    mappings = {
        "fc": "nes",
        "famicom": "nes",
        "super nintendo": "snes",
        "sfc": "snes",
        "superfamicom": "snes",
        "sega genesis": "genesis",
        "sega megadrive": "genesis",
        "mega drive": "genesis",
        "md": "genesis",
    }
    return mappings.get(console_id_lower, console_id_lower)


def get_default_extensions(console_id: str) -> list:
    """Retourne les extensions par défaut pour une console"""
    normalized = normalize_console_id(console_id)
    return CONSOLE_EXTENSIONS.get(normalized, [".zip", ".7z", ".rom", ".bin"])


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
                dirs[:] = []  # Ne pas descendre plus profond
                continue
            
            for file in files:
                file_path = Path(root) / file
                ext = file_path.suffix.lower()
                if any(ext == e.lower() for e in extensions):
                    # Chemin relatif depuis console_dir
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
                # Nettoyer le path
                clean_path = path_str.replace("./", "").strip()
                paths.add(clean_path)
    except Exception as e:
        print(f"  Erreur lecture gamelist: {e}")
    
    return paths


def main():
    if len(sys.argv) < 2:
        print("Usage: python find_missing_roms.py <chemin_vers_GameLibrary-Data> [console_id]")
        print("\nExemples:")
        print("  python find_missing_roms.py E:/GameLibrary-Data")
        print("  python find_missing_roms.py E:/GameLibrary-Data nes")
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
                       if d.is_dir() and not d.name.startswith('.') 
                       and d.name.lower() not in SYSTEM_DIRECTORIES]
    
    if not console_dirs:
        print("Aucun répertoire de console trouvé")
        sys.exit(0)
    
    total_missing = 0
    
    # Traiter chaque console
    for console_dir in sorted(console_dirs):
        console_id = console_dir.name
        gamelist_path = console_dir / "gamelist.json"
        
        print(f"\n{'='*80}")
        print(f"Console: {console_id}")
        print(f"{'='*80}")
        
        # Trouver tous les fichiers ROM sur le disque
        rom_files = find_rom_files(console_dir, console_id)
        print(f"ROMs trouvées sur disque: {len(rom_files)}")
        
        # Extraire les chemins depuis gamelist.json
        gamelist_paths = get_gamelist_paths(gamelist_path)
        print(f"ROMs dans gamelist.json: {len(gamelist_paths)}")
        
        # Trouver les ROMs manquantes
        missing_roms = []
        for rel_path, file_path in rom_files:
            # Normaliser le chemin pour comparaison
            normalized_path = rel_path.replace("\\", "/")
            if normalized_path not in gamelist_paths:
                missing_roms.append((rel_path, file_path))
        
        if missing_roms:
            print(f"\n{len(missing_roms)} ROMs manquantes dans gamelist.json:\n")
            for i, (rel_path, file_path) in enumerate(missing_roms[:30], 1):  # Limiter à 30
                print(f"{i}. {rel_path}")
            
            if len(missing_roms) > 30:
                print(f"\n... et {len(missing_roms) - 30} autres ROMs manquantes")
            
            total_missing += len(missing_roms)
        else:
            print("\n[OK] Toutes les ROMs sont dans le gamelist.json")
    
    if total_missing > 0:
        print(f"\n{'='*80}")
        print(f"Total ROMs manquantes: {total_missing}")
        print(f"{'='*80}")


if __name__ == "__main__":
    main()


