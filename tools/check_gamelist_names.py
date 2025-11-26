#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Vérifie les différences entre "name" et le nom de fichier dans "path"
Usage: python check_gamelist_names.py <chemin_vers_GameLibrary-Data> [console_id]
"""

import os
import sys
import json
from pathlib import Path


def extract_filename_from_path(path_str: str) -> str:
    """Extrait le nom de fichier depuis le path et enlève l'extension"""
    if not path_str:
        return ""
    
    # Enlever "./" du début si présent
    clean_path = path_str.replace("./", "").strip()
    
    # Extraire le nom de fichier (dernier élément du path)
    filename = os.path.basename(clean_path)
    
    # Enlever l'extension
    base_name = os.path.splitext(filename)[0]
    
    return base_name


def check_gamelist_names(gamelist_path: Path, show_all: bool = False) -> list:
    """
    Vérifie les différences entre "name" et le nom de fichier dans "path"
    Retourne une liste de tuples (name, path, filename_from_path)
    """
    mismatches = []
    
    if not gamelist_path.exists():
        return mismatches
    
    try:
        # Charger le gamelist.json
        with open(gamelist_path, 'r', encoding='utf-8') as f:
            gamelist = json.load(f)
        
        if 'games' not in gamelist:
            return mismatches
        
        games = gamelist['games']
        
        # Parcourir tous les jeux
        for game in games:
            if 'path' not in game or 'name' not in game:
                continue
            
            # Extraire le nom du fichier depuis le path
            filename_from_path = extract_filename_from_path(game.get('path', ''))
            current_name = game.get('name', '')
            
            if not filename_from_path:
                continue
            
            # Comparer (normaliser les espaces pour la comparaison)
            if current_name.strip() != filename_from_path.strip():
                mismatches.append((current_name, game.get('path', ''), filename_from_path))
        
        return mismatches
        
    except Exception as e:
        print(f"  Erreur: {e}")
        return mismatches


def main():
    if len(sys.argv) < 2:
        print("Usage: python check_gamelist_names.py <chemin_vers_GameLibrary-Data> [console_id]")
        print("\nExemples:")
        print("  python check_gamelist_names.py E:/GameLibrary-Data")
        print("  python check_gamelist_names.py E:/GameLibrary-Data nes")
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
    
    total_mismatches = 0
    
    # Traiter chaque gamelist.json
    for gamelist_file in sorted(gamelist_files):
        if not gamelist_file.exists():
            continue
            
        console_name = gamelist_file.parent.name
        print(f"\n{'='*80}")
        print(f"Console: {console_name}")
        print(f"{'='*80}")
        
        mismatches = check_gamelist_names(gamelist_file, show_all=True)
        
        if mismatches:
            print(f"\n{len(mismatches)} différences trouvées:\n")
            for i, (name, path, filename_from_path) in enumerate(mismatches[:50], 1):  # Limiter à 50 pour l'affichage
                print(f"{i}. name: '{name}'")
                print(f"   path: '{path}'")
                print(f"   → devrait être: '{filename_from_path}'")
                print()
            
            if len(mismatches) > 50:
                print(f"... et {len(mismatches) - 50} autres différences\n")
            
            total_mismatches += len(mismatches)
        else:
            print("[OK] Tous les noms sont synchronises")
    
    if total_mismatches > 0:
        print(f"\n{'='*80}")
        print(f"Total: {total_mismatches} différences trouvées")
        print(f"{'='*80}")


if __name__ == "__main__":
    main()

