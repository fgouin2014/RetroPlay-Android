#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Importe les métadonnées depuis gamelist.xml vers gamelist.json en utilisant le path comme clé
Usage: python import_xml_to_json.py <chemin_vers_GameLibrary-Data> [console_id]
"""

import os
import sys
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from datetime import datetime


def normalize_path(path_str: str) -> str:
    """Normalise un chemin pour la comparaison"""
    if not path_str:
        return ""
    # Enlever "./" du début, trim, normaliser les espaces multiples
    clean_path = path_str.replace("./", "").strip()
    return " ".join(clean_path.split())


def get_filename_from_path(path_str: str) -> str:
    """Extrait le nom de fichier depuis un chemin"""
    if not path_str:
        return ""
    clean_path = path_str.replace("./", "").strip()
    filename = os.path.basename(clean_path)
    return filename.strip()


def parse_xml_gamelist(xml_path: Path) -> dict:
    """Parse un gamelist.xml et retourne un dict {path: game_data}"""
    xml_games = {}
    
    if not xml_path.exists():
        return xml_games
    
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        
        for game in root.findall('game'):
            path_elem = game.find('path')
            if path_elem is None or path_elem.text is None:
                continue
            
            path = normalize_path(path_elem.text)
            
            # Extraire toutes les métadonnées
            name = game.findtext('name', '').strip()
            desc = game.findtext('desc', '').strip()
            releasedate = game.findtext('releasedate', '').strip()
            developer = game.findtext('developer', '').strip()
            publisher = game.findtext('publisher', '').strip()
            genre = game.findtext('genre', '').strip()
            players = game.findtext('players', '').strip()
            rating = game.findtext('rating', '').strip()
            
            xml_games[path] = {
                'name': name,
                'desc': desc,
                'releasedate': releasedate,
                'developer': developer,
                'publisher': publisher,
                'genre': genre,
                'players': players,
                'rating': rating
            }
    
    except Exception as e:
        print(f"  Erreur parsing XML: {e}")
    
    return xml_games


def import_xml_metadata(console_dir: Path, console_id: str) -> tuple[int, int]:
    """Importe les métadonnées XML dans le gamelist.json"""
    xml_path = console_dir / "gamelist.xml"
    json_path = console_dir / "gamelist.json"
    
    if not xml_path.exists():
        return (0, 0)
    
    if not json_path.exists():
        return (0, 0)
    
    # Parser le XML
    xml_games = parse_xml_gamelist(xml_path)
    if not xml_games:
        return (0, 0)
    
    print(f"  {len(xml_games)} jeux trouvés dans XML")
    
    # Charger le JSON
    with open(json_path, 'r', encoding='utf-8') as f:
        gamelist = json.load(f)
    
    if 'games' not in gamelist:
        return (0, 0)
    
    games = gamelist['games']
    total_games = len(games)
    enriched_count = 0
    
    # Créer un index de fallback par nom de fichier
    xml_by_filename = {}
    for path, data in xml_games.items():
        filename = get_filename_from_path(path)
        if filename and filename not in xml_by_filename:
            xml_by_filename[filename] = (path, data)
    
    # Parcourir les jeux JSON et enrichir avec les données XML
    for game in games:
        json_path_str = game.get('path', '')
        if not json_path_str:
            continue
        
        normalized_json_path = normalize_path(json_path_str)
        
        # Chercher dans XML par path exact d'abord
        xml_data = xml_games.get(normalized_json_path)
        
        # Si pas trouvé, chercher par nom de fichier
        if xml_data is None:
            json_filename = get_filename_from_path(json_path_str)
            if json_filename in xml_by_filename:
                xml_path_key, xml_data = xml_by_filename[json_filename]
        
        if xml_data:
            # XML prioritaire: remplacer les métadonnées même si elles existent déjà
            was_enriched = False
            
            if xml_data['name']:
                game['name'] = xml_data['name']
                was_enriched = True
            
            if xml_data['desc']:
                game['desc'] = xml_data['desc']
                was_enriched = True
            
            if xml_data['releasedate']:
                game['releasedate'] = xml_data['releasedate']
                was_enriched = True
            
            if xml_data['developer']:
                game['developer'] = xml_data['developer']
                was_enriched = True
            
            if xml_data['publisher']:
                game['publisher'] = xml_data['publisher']
                was_enriched = True
            
            if xml_data['genre']:
                game['genre'] = xml_data['genre']
                was_enriched = True
            
            if xml_data['players']:
                game['players'] = xml_data['players']
                was_enriched = True
            
            if xml_data['rating']:
                game['rating'] = xml_data['rating']
                was_enriched = True
            
            if was_enriched:
                enriched_count += 1
    
    if enriched_count > 0:
        # Créer une sauvegarde
        backup_path = console_dir / f"gamelist_backup_xml_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(backup_path, 'w', encoding='utf-8') as f:
            json.dump(gamelist, f, indent=2, ensure_ascii=False)
        
        # Sauvegarder le JSON modifié
        with open(json_path, 'w', encoding='utf-8') as f:
            json.dump(gamelist, f, indent=2, ensure_ascii=False)
    
    return (enriched_count, total_games)


def main():
    if len(sys.argv) < 2:
        print("Usage: python import_xml_to_json.py <chemin_vers_GameLibrary-Data> [console_id]")
        print("\nExemples:")
        print("  python import_xml_to_json.py E:/GameLibrary-Data")
        print("  python import_xml_to_json.py E:/GameLibrary-Data nes")
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
    
    print(f"Import des métadonnées XML vers JSON dans: {gamelibrary_path}\n")
    
    total_enriched = 0
    total_games = 0
    
    # Traiter chaque console
    for console_dir in sorted(console_dirs):
        console_id = console_dir.name
        xml_path = console_dir / "gamelist.xml"
        
        if not xml_path.exists():
            continue
        
        print(f"Traitement: {console_id}...")
        
        enriched, total = import_xml_metadata(console_dir, console_id)
        
        if enriched > 0:
            print(f"  [OK] {enriched}/{total} jeux enrichis depuis XML")
            total_enriched += enriched
        else:
            print(f"  [OK] Aucun enrichissement ({total} jeux)")
        
        total_games += total
    
    print(f"\n{'='*80}")
    print(f"Total jeux enrichis: {total_enriched}")
    print(f"Total jeux verifies: {total_games}")
    print(f"{'='*80}")


if __name__ == "__main__":
    main()


