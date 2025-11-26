#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Outil PC pour générer les gamelist.json avec la même logique que l'app Android
Usage: python generate_gamelist_pc.py <chemin_vers_GameLibrary-Data>
"""

import os
import sys
import json
import hashlib
import zipfile
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Optional, Tuple

# Mapping des consoles vers leurs extensions (identique à GamelistManager.kt)
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

GAMELIST_VERSION = "1.0"


def normalize_console_id(console_id: str) -> str:
    """Normalise l'ID de console (même logique que ConsoleNameMapper)"""
    console_id_lower = console_id.lower()
    
    # Mapping des variantes vers IDs canoniques
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


def get_default_extensions(console_id: str) -> List[str]:
    """Retourne les extensions par défaut pour une console"""
    normalized = normalize_console_id(console_id)
    return CONSOLE_EXTENSIONS.get(normalized, [".zip", ".7z", ".rom", ".bin"])


def calculate_crc32(data: bytes) -> str:
    """Calcule le CRC32 d'un buffer"""
    import zlib
    crc = zlib.crc32(data) & 0xffffffff
    return format(crc, '08x').upper()


def calculate_hash_from_file(file_path: Path, skip_bytes: int = 0) -> Tuple[Optional[str], Optional[str], Optional[str]]:
    """
    Calcule CRC32, MD5 et SHA1 d'un fichier
    Retourne (crc32, md5, sha1)
    Identique à HashCalculator.calculateHashFromFile() dans l'app Android
    """
    try:
        import zlib
        crc = 0
        md5_hash = hashlib.md5()
        sha1_hash = hashlib.sha1()
        
        with open(file_path, 'rb') as f:
            if skip_bytes > 0:
                f.read(skip_bytes)
            
            while True:
                chunk = f.read(8192)
                if not chunk:
                    break
                
                crc = zlib.crc32(chunk, crc) & 0xffffffff
                md5_hash.update(chunk)
                sha1_hash.update(chunk)
        
        crc32_str = format(crc, '08x').upper()
        md5_str = md5_hash.hexdigest()
        sha1_str = sha1_hash.hexdigest()
        
        return (crc32_str, md5_str, sha1_str)
    except Exception as e:
        print(f"  Erreur calcul hash pour {file_path.name}: {e}")
        return (None, None, None)


def calculate_hash_from_zip(zip_path: Path) -> Tuple[Optional[str], Optional[str], Optional[str]]:
    """
    Calcule les hashes depuis un fichier ROM dans une archive ZIP
    Identique à HashCalculator.calculateHashFromArchive() dans l'app Android
    """
    try:
        import zlib
        with zipfile.ZipFile(zip_path, 'r') as zip_file:
            # Trouver le premier fichier ROM dans l'archive (même logique que l'app)
            rom_extensions = ['.nes', '.unh', '.unf', '.sfc', '.smc', '.gb', '.gbc', '.gba',
                             '.bin', '.gen', '.md', '.iso', '.cue', '.img', '.mdf', '.pbp',
                             '.cso', '.elf', '.lnx', '.a26', '.a52', '.a78', '.ws', '.wsc',
                             '.pce', '.sgx']
            
            rom_entry = None
            for entry_name in zip_file.namelist():
                entry = zip_file.getinfo(entry_name)
                if entry.is_dir():
                    continue
                if any(entry_name.lower().endswith(ext) for ext in rom_extensions):
                    rom_entry = entry_name
                    break
            
            if rom_entry is None:
                return (None, None, None)
            
            # Calculer les hashes en streaming (comme l'app Android)
            crc = 0
            md5_hash = hashlib.md5()
            sha1_hash = hashlib.sha1()
            skip_bytes = 0
            
            with zip_file.open(rom_entry) as rom_stream:
                # Vérifier header iNES pour .nes
                if rom_entry.lower().endswith('.nes'):
                    header = rom_stream.read(4)
                    if len(header) == 4 and header == b'NES\x1a':
                        rom_stream.read(12)  # Skip les 12 bytes restants du header
                        skip_bytes = 16
                    else:
                        # Remettre les 4 bytes dans le calcul si ce n'est pas un header iNES
                        crc = zlib.crc32(header, crc) & 0xffffffff
                        md5_hash.update(header)
                        sha1_hash.update(header)
                
                # Lire le reste du fichier
                while True:
                    chunk = rom_stream.read(8192)
                    if not chunk:
                        break
                    crc = zlib.crc32(chunk, crc) & 0xffffffff
                    md5_hash.update(chunk)
                    sha1_hash.update(chunk)
            
            crc32_str = format(crc, '08x').upper()
            md5_str = md5_hash.hexdigest()
            sha1_str = sha1_hash.hexdigest()
            
            return (crc32_str, md5_str, sha1_str)
    except Exception as e:
        print(f"  Erreur calcul hash ZIP pour {zip_path.name}: {e}")
        return (None, None, None)


def get_primary_hash(crc32: Optional[str], md5: Optional[str], sha1: Optional[str]) -> Optional[str]:
    """Retourne le hash principal (SHA1 > MD5 > CRC32)"""
    if sha1:
        return sha1
    elif md5:
        return md5
    elif crc32:
        return crc32
    return None


def clean_file_name(file_name: str) -> str:
    """Nettoie le nom de fichier pour en faire un nom de jeu"""
    # Enlever l'extension
    base_name = os.path.splitext(file_name)[0]
    # Remplacer underscores et tirets par espaces
    base_name = base_name.replace('_', ' ').replace('-', ' ')
    # Normaliser les espaces multiples
    base_name = ' '.join(base_name.split())
    return base_name


def create_game_entry(file_path: Path, base_dir: Path, console_id: str) -> Optional[Dict]:
    """Crée une entrée de jeu depuis un fichier ROM"""
    try:
        file_name = file_path.name
        base_name = clean_file_name(file_name)
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
            print(f"  Avertissement: Impossible de calculer hash pour {file_name}")
            return None
        
        # Créer l'entrée de jeu (format identique à GameEntry)
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


def scan_directory(dir_path: Path, console_id: str, extensions: List[str], base_dir: Path, games: List[Dict], max_depth: int = 2, current_depth: int = 0):
    """Scanne récursivement un répertoire pour trouver des ROMs"""
    if current_depth > max_depth:
        return
    
    if not dir_path.exists() or not dir_path.is_dir():
        return
    
    try:
        for item in dir_path.iterdir():
            if item.is_dir():
                scan_directory(item, console_id, extensions, base_dir, games, max_depth, current_depth + 1)
            elif item.is_file():
                file_ext = item.suffix.lower()
                if any(file_ext == ext.lower() for ext in extensions):
                    game_entry = create_game_entry(item, base_dir, console_id)
                    if game_entry:
                        games.append(game_entry)
    except Exception as e:
        print(f"  Erreur scan répertoire {dir_path}: {e}")


def generate_gamelist(console_dir: Path, console_id: str) -> Optional[Dict]:
    """Génère un gamelist.json pour un répertoire de console"""
    print(f"\nGénération gamelist pour: {console_dir.name} (ID: {console_id})")
    
    # Normaliser l'ID de console
    normalized_id = normalize_console_id(console_id)
    extensions = get_default_extensions(normalized_id)
    
    print(f"  Extensions: {', '.join(extensions)}")
    
    # Scanner le répertoire
    games = []
    scan_directory(console_dir, normalized_id, extensions, console_dir, games)
    
    if not games:
        print(f"  Aucune ROM trouvée dans {console_dir.name}")
        return None
    
    print(f"  {len(games)} ROMs trouvées")
    
    # Trier par nom
    games_sorted = sorted(games, key=lambda g: g["name"].lower())
    
    # Assigner des IDs séquentiels
    for index, game in enumerate(games_sorted, start=1):
        game["id"] = str(index)
    
    # Créer le gamelist (format identique à Gamelist)
    gamelist = {
        "version": GAMELIST_VERSION,
        "console": console_id,
        "games": games_sorted,
        "metadata": {
            "generatedAt": int(datetime.now().timestamp() * 1000),
            "sourceDirectories": [str(console_dir.absolute())],
            "autoGenerated": True,
            "totalGames": len(games_sorted),
            "console": console_id
        }
    }
    
    return gamelist


def main():
    if len(sys.argv) < 2:
        print("Usage: python generate_gamelist_pc.py <chemin_vers_GameLibrary-Data> [console_id]")
        print("\nExemples:")
        print("  python generate_gamelist_pc.py C:/GameLibrary-Data")
        print("  python generate_gamelist_pc.py C:/GameLibrary-Data nes")
        sys.exit(1)
    
    gamelibrary_path = Path(sys.argv[1])
    specific_console = sys.argv[2] if len(sys.argv) > 2 else None
    
    if not gamelibrary_path.exists():
        print(f"Erreur: Le répertoire {gamelibrary_path} n'existe pas")
        sys.exit(1)
    
    if not gamelibrary_path.is_dir():
        print(f"Erreur: {gamelibrary_path} n'est pas un répertoire")
        sys.exit(1)
    
    print(f"Scan de: {gamelibrary_path}")
    
    # Trouver les répertoires de consoles
    console_dirs = []
    for item in gamelibrary_path.iterdir():
        if item.is_dir():
            dir_name = item.name
            if not dir_name.startswith('.') and dir_name.lower() not in SYSTEM_DIRECTORIES:
                if specific_console is None or dir_name.lower() == specific_console.lower():
                    console_dirs.append(item)
    
    if not console_dirs:
        print("Aucun répertoire de console trouvé")
        sys.exit(0)
    
    print(f"{len(console_dirs)} répertoire(s) de console trouvé(s)\n")
    
    # Générer les gamelist.json
    generated_count = 0
    for console_dir in console_dirs:
        console_id = console_dir.name
        gamelist = generate_gamelist(console_dir, console_id)
        
        if gamelist:
            # Sauvegarder le gamelist.json
            gamelist_file = console_dir / "gamelist.json"
            
            # Créer une sauvegarde si le fichier existe déjà
            if gamelist_file.exists():
                backup_file = console_dir / f"gamelist_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
                print(f"  Sauvegarde de l'ancien gamelist.json vers {backup_file.name}")
                gamelist_file.rename(backup_file)
            
            # Écrire le nouveau gamelist.json
            with open(gamelist_file, 'w', encoding='utf-8') as f:
                json.dump(gamelist, f, indent=2, ensure_ascii=False)
            
            print(f"  ✓ gamelist.json généré: {gamelist_file}")
            generated_count += 1
    
    print(f"\n✓ {generated_count} gamelist.json généré(s)")


if __name__ == "__main__":
    main()

