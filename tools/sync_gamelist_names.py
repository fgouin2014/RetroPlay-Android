#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Synchronise les noms dans gamelist.json avec les noms de fichiers dans "path"
Usage: python sync_gamelist_names.py <chemin_vers_GameLibrary-Data>
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


def sync_gamelist_names(gamelist_path: Path) -> tuple[int, int]:
    """
    Synchronise les noms dans un gamelist.json avec les noms de fichiers dans "path"
    Retourne (nombre_modifié, total)
    """
    if not gamelist_path.exists():
        print(f"  Fichier non trouvé: {gamelist_path}")
        return (0, 0)
    
    try:
        # Charger le gamelist.json
        with open(gamelist_path, 'r', encoding='utf-8') as f:
            gamelist = json.load(f)
        
        if 'games' not in gamelist:
            print(f"  Format invalide: pas de champ 'games'")
            return (0, 0)
        
        games = gamelist['games']
        total = len(games)
        modified = 0
        
        # Parcourir tous les jeux
        for game in games:
            if 'path' not in game:
                continue
            
            # Extraire le nom du fichier depuis le path
            filename_from_path = extract_filename_from_path(game.get('path', ''))
            
            if not filename_from_path:
                continue
            
            # Comparer avec le nom actuel
            current_name = game.get('name', '')
            
            if current_name != filename_from_path:
                # Mettre à jour le nom
                game['name'] = filename_from_path
                modified += 1
        
        # Sauvegarder si des modifications ont été faites
        if modified > 0:
            # Créer une sauvegarde
            backup_path = gamelist_path.parent / f"gamelist_backup_names_{gamelist_path.stem}.json"
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
        print("Usage: python sync_gamelist_names.py <chemin_vers_GameLibrary-Data>")
        print("\nExemples:")
        print("  python sync_gamelist_names.py E:/GameLibrary-Data")
        sys.exit(1)
    
    gamelibrary_path = Path(sys.argv[1])
    
    if not gamelibrary_path.exists():
        print(f"Erreur: Le répertoire {gamelibrary_path} n'existe pas")
        sys.exit(1)
    
    if not gamelibrary_path.is_dir():
        print(f"Erreur: {gamelibrary_path} n'est pas un répertoire")
        sys.exit(1)
    
    print(f"Synchronisation des noms dans: {gamelibrary_path}\n")
    
    # Trouver tous les gamelist.json
    gamelist_files = list(gamelibrary_path.glob("*/gamelist.json"))
    
    if not gamelist_files:
        print("Aucun gamelist.json trouvé")
        sys.exit(0)
    
    print(f"{len(gamelist_files)} gamelist.json trouvé(s)\n")
    
    total_modified = 0
    total_games = 0
    
    # Traiter chaque gamelist.json
    for gamelist_file in sorted(gamelist_files):
        console_name = gamelist_file.parent.name
        print(f"Traitement: {console_name}...")
        
        modified, total = sync_gamelist_names(gamelist_file)
        
        if modified > 0:
            print(f"  ✓ {modified}/{total} noms synchronisés")
            total_modified += modified
        else:
            print(f"  ✓ Aucune modification nécessaire ({total} jeux)")
        
        total_games += total
    
    print(f"\n✓ Synchronisation terminée:")
    print(f"  - {total_modified} noms modifiés sur {total_games} jeux")
    print(f"  - {len(gamelist_files)} gamelist.json traités")


if __name__ == "__main__":
    main()


