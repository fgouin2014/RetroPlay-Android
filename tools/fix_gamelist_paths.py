#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Corrige les chemins dans gamelist.json en comparant avec les fichiers réels sur le disque
Usage: python fix_gamelist_paths.py <chemin_vers_GameLibrary-Data> [console_id]
"""

import os
import sys
import json
from pathlib import Path


def find_file_by_name(console_dir: Path, filename: str) -> str:
    """Trouve un fichier par son nom dans le répertoire (récursif)"""
    filename_lower = filename.lower()
    
    for root, dirs, files in os.walk(console_dir):
        for file in files:
            if file.lower() == filename_lower:
                file_path = Path(root) / file
                rel_path = file_path.relative_to(console_dir)
                return str(rel_path).replace("\\", "/")
    
    return None


def fix_gamelist_paths(gamelist_path: Path) -> tuple[int, int]:
    """
    Corrige les chemins dans gamelist.json en comparant avec les fichiers réels
    Retourne (nombre_modifié, total)
    """
    if not gamelist_path.exists():
        return (0, 0)
    
    console_dir = gamelist_path.parent
    
    try:
        # Charger le gamelist.json
        with open(gamelist_path, 'r', encoding='utf-8') as f:
            gamelist = json.load(f)
        
        if 'games' not in gamelist:
            return (0, 0)
        
        games = gamelist['games']
        total = len(games)
        modified = 0
        
        # Parcourir tous les jeux
        for game in games:
            if 'path' not in game:
                continue
            
            current_path = game.get('path', '')
            if not current_path:
                continue
            
            # Extraire le nom de fichier depuis le path actuel
            clean_path = current_path.replace("./", "").strip()
            filename = os.path.basename(clean_path)
            
            # Vérifier si le fichier existe à ce chemin
            file_path = console_dir / clean_path
            
            if not file_path.exists() or not file_path.is_file():
                # Le fichier n'existe pas à ce chemin, chercher par nom
                found_path = find_file_by_name(console_dir, filename)
                
                if found_path:
                    # Mettre à jour le path
                    new_path = f"./{found_path}"
                    if current_path != new_path:
                        game['path'] = new_path
                        modified += 1
        
        # Sauvegarder si des modifications ont été faites
        if modified > 0:
            # Créer une sauvegarde
            from datetime import datetime
            backup_path = gamelist_path.parent / f"gamelist_backup_paths_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
            with open(backup_path, 'w', encoding='utf-8') as f:
                json.dump(gamelist, f, indent=2, ensure_ascii=False)
            
            # Sauvegarder le fichier modifié
            with open(gamelist_path, 'w', encoding='utf-8') as f:
                json.dump(gamelist, f, indent=2, ensure_ascii=False)
        
        return (modified, total)
        
    except Exception as e:
        print(f"  Erreur: {e}")
        return (0, 0)


def main():
    if len(sys.argv) < 2:
        print("Usage: python fix_gamelist_paths.py <chemin_vers_GameLibrary-Data> [console_id]")
        print("\nExemples:")
        print("  python fix_gamelist_paths.py E:/GameLibrary-Data")
        print("  python fix_gamelist_paths.py E:/GameLibrary-Data nes")
        sys.exit(1)
    
    gamelibrary_path = Path(sys.argv[1])
    specific_console = sys.argv[2] if len(sys.argv) > 2 else None
    
    if not gamelibrary_path.exists():
        print(f"Erreur: Le répertoire {gamelibrary_path} n'existe pas")
        sys.exit(1)
    
    # Trouver les gamelist.json
    if specific_console:
        gamelist_files = [gamelibrary_path / specific_console / "gamelist.json"]
    else:
        gamelist_files = list(gamelibrary_path.glob("*/gamelist.json"))
    
    if not gamelist_files:
        print("Aucun gamelist.json trouvé")
        sys.exit(0)
    
    print(f"Correction des chemins dans: {gamelibrary_path}\n")
    
    total_modified = 0
    total_games = 0
    
    # Traiter chaque gamelist.json
    for gamelist_file in sorted(gamelist_files):
        if not gamelist_file.exists():
            continue
            
        console_name = gamelist_file.parent.name
        print(f"Traitement: {console_name}...")
        
        modified, total = fix_gamelist_paths(gamelist_file)
        
        if modified > 0:
            print(f"  [OK] {modified}/{total} chemins corrigés")
            total_modified += modified
        else:
            print(f"  [OK] Aucune correction necessaire ({total} jeux)")
        
        total_games += total
    
    print(f"\n{'='*80}")
    print(f"Total chemins corriges: {total_modified}")
    print(f"Total jeux verifies: {total_games}")
    print(f"{'='*80}")


if __name__ == "__main__":
    main()


