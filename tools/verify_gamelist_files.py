#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Vérifie que tous les fichiers listés dans gamelist.json existent réellement
Usage: python verify_gamelist_files.py <chemin_vers_GameLibrary-Data> [console_id]
"""

import os
import sys
import json
from pathlib import Path


def verify_gamelist_files(gamelist_path: Path) -> tuple[list, list]:
    """
    Vérifie que tous les fichiers listés dans gamelist.json existent
    Retourne (fichiers_manquants, fichiers_existants)
    """
    missing_files = []
    existing_files = []
    
    if not gamelist_path.exists():
        return (missing_files, existing_files)
    
    console_dir = gamelist_path.parent
    
    try:
        # Charger le gamelist.json
        with open(gamelist_path, 'r', encoding='utf-8') as f:
            gamelist = json.load(f)
        
        if 'games' not in gamelist:
            return (missing_files, existing_files)
        
        games = gamelist['games']
        
        # Parcourir tous les jeux
        for game in games:
            if 'path' not in game:
                continue
            
            # Extraire le chemin du fichier
            path_str = game.get('path', '')
            if not path_str:
                continue
            
            # Nettoyer le path (enlever "./")
            clean_path = path_str.replace("./", "").strip()
            
            # Construire le chemin absolu
            file_path = console_dir / clean_path
            
            # Vérifier si le fichier existe
            if file_path.exists() and file_path.is_file():
                existing_files.append((game.get('name', ''), path_str))
            else:
                missing_files.append((game.get('name', ''), path_str, str(file_path)))
        
        return (missing_files, existing_files)
        
    except Exception as e:
        print(f"  Erreur: {e}")
        return (missing_files, existing_files)


def main():
    if len(sys.argv) < 2:
        print("Usage: python verify_gamelist_files.py <chemin_vers_GameLibrary-Data> [console_id]")
        print("\nExemples:")
        print("  python verify_gamelist_files.py E:/GameLibrary-Data")
        print("  python verify_gamelist_files.py E:/GameLibrary-Data nes")
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
    
    total_missing = 0
    total_existing = 0
    
    # Traiter chaque gamelist.json
    for gamelist_file in sorted(gamelist_files):
        if not gamelist_file.exists():
            continue
            
        console_name = gamelist_file.parent.name
        print(f"\n{'='*80}")
        print(f"Console: {console_name}")
        print(f"{'='*80}")
        
        missing_files, existing_files = verify_gamelist_files(gamelist_file)
        
        if missing_files:
            print(f"\n{len(missing_files)} fichiers manquants:\n")
            for i, (name, path, full_path) in enumerate(missing_files[:20], 1):  # Limiter à 20 pour l'affichage
                print(f"{i}. {name}")
                print(f"   path: {path}")
                print(f"   fichier: {full_path}")
                print()
            
            if len(missing_files) > 20:
                print(f"... et {len(missing_files) - 20} autres fichiers manquants\n")
        
        print(f"Fichiers existants: {len(existing_files)}")
        print(f"Fichiers manquants: {len(missing_files)}")
        
        total_missing += len(missing_files)
        total_existing += len(existing_files)
    
    print(f"\n{'='*80}")
    print(f"Total fichiers existants: {total_existing}")
    print(f"Total fichiers manquants: {total_missing}")
    print(f"{'='*80}")


if __name__ == "__main__":
    main()


