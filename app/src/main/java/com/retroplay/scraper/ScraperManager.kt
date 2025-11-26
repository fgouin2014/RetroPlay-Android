package com.retroplay.scraper

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * ScraperManager - Scraping hash-based style sselph
 * 
 * Utilise ScreenScraper.fr API pour récupérer les métadonnées des jeux
 * Identification par hash uniquement (SHA1 > MD5 > CRC32)
 * Cache local pour éviter re-scraping
 */
object ScraperManager {
    private const val TAG = "ScraperManager"
    private const val API_BASE_URL = "https://www.screenscraper.fr/api2"
    private const val CACHE_DIR = "/storage/emulated/0/RetroPlay-Data/scraper/cache"
    // Délai augmenté pour respecter les limites de l'API ScreenScraper (compte gratuit: ~1 req/10s)
    private const val RATE_LIMIT_DELAY_MS = 12000L // 12 secondes entre requêtes pour éviter de dépasser la limite
    private const val CONNECT_TIMEOUT_MS = 20000 // 20 secondes pour connexion
    private const val READ_TIMEOUT_MS = 20000 // 20 secondes pour lecture
    private const val MAX_RETRIES = 2 // 2 tentatives en cas d'échec
    
    // Credentials ScreenScraper.fr
    private const val SSID = "reaper13"
    private const val SSPASSWORD = "reaper"
    
    private var lastRequestTime = 0L
    private var rateLimitExceeded = false // Flag pour arrêter le scraping si limite dépassée
    
    // Mutex pour garantir qu'un seul thread fait des requêtes API à la fois (spécification ScreenScraper)
    private val apiMutex = Any()
    
    /**
     * Métadonnées scrappées depuis ScreenScraper.fr
     */
    data class ScrapedMetadata(
        val name: String,
        val description: String? = null,
        val genre: String? = null,
        val releaseDate: String? = null, // Format: YYYYMMDDTHHMMSS (ES style)
        val developer: String? = null,
        val publisher: String? = null,
        val players: String? = null, // Format: "1-2" ou "1"
        val rating: String? = null,
        val imageUrl: String? = null,
        val screenshotUrl: String? = null
    )
    
    /**
     * Scrape les métadonnées d'un jeu depuis ScreenScraper.fr
     * Utilise hash (SHA1 > MD5 > CRC32) pour identification
     * 
     * @param hash GameHash avec les hashes calculés
     * @param console ID de la console (normalisé, ex: "nes", "snes")
     * @return ScrapedMetadata ou null si non trouvé/erreur
     */
    fun scrapeGame(hash: HashCalculator.GameHash, console: String): ScrapedMetadata? {
        // Si la limite de taux a été dépassée, arrêter le scraping
        if (rateLimitExceeded) {
            Log.d(TAG, "Rate limit exceeded, skipping scraping (use developer account for higher limits)")
            return null
        }
        
        // Vérifier si la console est supportée par ScreenScraper AVANT de continuer
        val systemId = mapConsoleToScreenScraperId(console)
        if (systemId == null) {
            // Console non supportée (arcade, fbneo, etc.) - ne pas logger à chaque fois
            return null
        }
        
        // Vérifier le cache d'abord
        val cached = loadFromCache(hash, console)
        if (cached != null) {
            Log.d(TAG, "Cache hit for ${hash.getPrimaryHash()} ($console)")
            return cached
        }
        
        // Essayer avec SHA1 d'abord, puis MD5, puis CRC32
        val primaryHash = hash.getPrimaryHashWithType()
        if (primaryHash == null) {
            Log.w(TAG, "No hash available for scraping")
            return null
        }
        
        val (hashValue, hashType) = primaryHash
        
        // Synchroniser les appels API pour garantir 1 thread à la fois (spécification ScreenScraper)
        val metadata = synchronized(apiMutex) {
            // Respecter le rate limiting (dans le mutex pour garantir l'ordre)
            enforceRateLimit()
            fetchFromScreenScraper(hashValue, hashType, console)
        }
        
        // Sauvegarder dans le cache si trouvé
        if (metadata != null) {
            saveToCache(hash, console, metadata)
        }
        
        return metadata
    }
    
    /**
     * Fetch métadonnées depuis ScreenScraper.fr API avec retry
     */
    private fun fetchFromScreenScraper(
        hash: String,
        hashType: String,
        console: String
    ): ScrapedMetadata? {
        // Mapper console ID vers ScreenScraper system ID
        // Note: Cette vérification est déjà faite dans scrapeGame(), mais on la garde pour sécurité
        val systemId = mapConsoleToScreenScraperId(console)
        if (systemId == null) {
            // Ne pas logger ici car c'est déjà vérifié dans scrapeGame()
            return null
        }
        
        // Construire l'URL API avec authentification
        val hashParam = when (hashType.lowercase()) {
            "sha1" -> "sha1=$hash"
            "md5" -> "md5=$hash"
            "crc32" -> "crc=$hash"
            else -> return null
        }
        
        val url = "$API_BASE_URL/jeuInfos.php?output=json&$hashParam&systemeid=$systemId&ssid=$SSID&sspassword=$SSPASSWORD"
        // Log sans le mot de passe pour sécurité
        val logUrl = "$API_BASE_URL/jeuInfos.php?output=json&$hashParam&systemeid=$systemId&ssid=$SSID&sspassword=***"
        Log.d(TAG, "Fetching from ScreenScraper: $logUrl")
        
        // Retry mechanism
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "RetroPlay-Android/1.0")
                
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    // 403 = Forbidden (pas de credentials ou limite dépassée) - arrêter le scraping
                    if (responseCode == 403) {
                        rateLimitExceeded = true
                        if (attempt == 1) {
                            Log.w(TAG, "ScreenScraper API returned 403 (Forbidden) - Rate limit exceeded. Register as developer for higher limits.")
                        }
                        connection.disconnect()
                        return null // Pas de retry pour 403
                    }
                    // Autres erreurs: log seulement la première tentative
                    if (attempt == 1) {
                        Log.w(TAG, "ScreenScraper API returned code: $responseCode (attempt $attempt/$MAX_RETRIES)")
                    }
                    connection.disconnect()
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(1000) // Attendre 1 seconde avant retry
                        continue
                    }
                    return null
                }
                
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                
                // Parser la réponse JSON
                val metadata = parseScreenScraperResponse(response, console)
                if (metadata != null) {
                    return metadata
                }
                
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
                Log.w(TAG, "Timeout fetching from ScreenScraper (attempt $attempt/$MAX_RETRIES): ${e.message}")
                if (attempt < MAX_RETRIES) {
                    // Attendre plus longtemps avant retry (backoff)
                    Thread.sleep((2000 * attempt).toLong())
                }
            } catch (e: java.net.ConnectException) {
                lastException = e
                Log.w(TAG, "Connection error fetching from ScreenScraper (attempt $attempt/$MAX_RETRIES): ${e.message}")
                if (attempt < MAX_RETRIES) {
                    Thread.sleep((2000 * attempt).toLong())
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Error fetching from ScreenScraper (attempt $attempt/$MAX_RETRIES): ${e.message}")
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(1000)
                }
            }
        }
        
        // Toutes les tentatives ont échoué
        Log.e(TAG, "Failed to fetch from ScreenScraper after $MAX_RETRIES attempts: ${lastException?.message}")
        return null
    }
    
    /**
     * Parse la réponse JSON de ScreenScraper.fr
     */
    private fun parseScreenScraperResponse(json: String, console: String): ScrapedMetadata? {
        return try {
            val root = JSONObject(json)
            
            // ScreenScraper retourne: {"response": {"jeu": {...}}}
            val response = root.optJSONObject("response")
            if (response == null) {
                Log.d(TAG, "No 'response' in ScreenScraper JSON")
                return null
            }
            
            val jeu = response.optJSONObject("jeu")
            if (jeu == null) {
                Log.d(TAG, "No 'jeu' in ScreenScraper response")
                return null
            }
            
            // Extraire les métadonnées
            val name = jeu.optString("nom", "")
            if (name.isEmpty()) {
                return null
            }
            
            val description = jeu.optString("synopsis", null)
            val genre = jeu.optString("genre", null)
            
            // Date de sortie: format ScreenScraper peut varier, convertir en YYYYMMDDTHHMMSS
            val releaseDate = parseReleaseDate(jeu)
            
            val developer = jeu.optString("developpeur", null)
            val publisher = jeu.optString("editeur", null)
            
            // Nombre de joueurs
            val players = jeu.optString("joueurs", null)?.let { 
                if (it == "1") "1" else "1-$it"
            }
            
            // Note (rating)
            val rating = jeu.optString("note", null)
            
            // Images: boxart et screenshot
            val medias = jeu.optJSONObject("medias")
            val imageUrl = medias?.optJSONArray("media_box-2D")?.optJSONObject(0)?.optString("url", null)
            val screenshotUrl = medias?.optJSONArray("media_screenshot")?.optJSONObject(0)?.optString("url", null)
            
            ScrapedMetadata(
                name = name,
                description = description,
                genre = genre,
                releaseDate = releaseDate,
                developer = developer,
                publisher = publisher,
                players = players,
                rating = rating,
                imageUrl = imageUrl,
                screenshotUrl = screenshotUrl
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ScreenScraper response: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse la date de sortie depuis ScreenScraper vers format ES (YYYYMMDDTHHMMSS)
     */
    private fun parseReleaseDate(jeu: JSONObject): String? {
        // ScreenScraper peut avoir: "datesortie" ou "date"
        val dateStr = jeu.optString("datesortie", null) ?: jeu.optString("date", null)
        if (dateStr == null || dateStr.isEmpty()) {
            return null
        }
        
        // Formats possibles: "1985-09-13", "19850913", "1985", etc.
        return try {
            when {
                dateStr.length >= 8 && dateStr.contains("-") -> {
                    // Format: "1985-09-13"
                    val parts = dateStr.split("-")
                    if (parts.size >= 3) {
                        "${parts[0]}${parts[1].padStart(2, '0')}${parts[2].padStart(2, '0')}T000000"
                    } else null
                }
                dateStr.length >= 8 -> {
                    // Format: "19850913"
                    "${dateStr}T000000"
                }
                dateStr.length == 4 -> {
                    // Format: "1985"
                    "${dateStr}0101T000000"
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing release date: $dateStr", e)
            null
        }
    }
    
    /**
     * Vérifie si une console est supportée par ScreenScraper
     */
    fun isConsoleSupported(console: String): Boolean {
        return mapConsoleToScreenScraperId(console) != null
    }
    
    /**
     * Réinitialise le flag de limite de taux dépassée
     * Utile si vous avez un compte développeur ou après un délai
     */
    fun resetRateLimit() {
        rateLimitExceeded = false
        Log.d(TAG, "Rate limit flag reset")
    }
    
    /**
     * Vérifie si la limite de taux a été dépassée
     */
    fun isRateLimitExceeded(): Boolean {
        return rateLimitExceeded
    }
    
    /**
     * Mappe l'ID console vers ScreenScraper system ID
     */
    private fun mapConsoleToScreenScraperId(console: String): Int? {
        // Mapping basé sur les IDs ScreenScraper.fr
        // Note: arcade/fbneo/mame ne sont pas supportés par ScreenScraper (pas de système unique)
        return when (console.lowercase()) {
            "nes", "famicom", "fc" -> 1      // Nintendo Entertainment System
            "snes", "sfc", "superfamicom" -> 2  // Super Nintendo
            "n64" -> 3                        // Nintendo 64
            "gb", "gameboy" -> 4              // Game Boy
            "gbc", "gameboycolor" -> 5       // Game Boy Color
            "gba", "gameboyadvance" -> 6     // Game Boy Advance
            "genesis", "megadrive", "md" -> 7  // Sega Genesis
            "mastersystem", "sms" -> 8       // Master System
            "gamegear", "gg" -> 9            // Game Gear
            "32x" -> 10                      // 32X
            "psx", "ps1", "playstation" -> 11 // PlayStation
            "psp" -> 12                      // PlayStation Portable
            "lynx", "atarilynx" -> 13        // Atari Lynx
            "atari2600", "a2600" -> 26      // Atari 2600
            "atari5200", "a5200" -> 27      // Atari 5200
            "atari7800", "a7800" -> 28      // Atari 7800
            "ngp", "neogeopocket" -> 14      // Neo Geo Pocket
            "wonderswancolor", "ws", "wonderswan" -> 15    // WonderSwan
            "pce", "pcengine" -> 16          // PC Engine
            "segacd" -> 14                   // Sega CD (utilise ID 14, vérifier si correct)
            "saturn" -> 15                   // Sega Saturn (utilise ID 15, vérifier si correct)
            "c64" -> 64                      // Commodore 64
            // Arcade/MAME/FBNeo ne sont pas supportés (pas de système unique dans ScreenScraper)
            "arcade", "mame", "fbneo" -> null
            else -> null
        }
    }
    
    /**
     * Respecte le rate limiting (évite trop de requêtes)
     */
    private fun enforceRateLimit() {
        val now = System.currentTimeMillis()
        val timeSinceLastRequest = now - lastRequestTime
        if (timeSinceLastRequest < RATE_LIMIT_DELAY_MS) {
            val delay = RATE_LIMIT_DELAY_MS - timeSinceLastRequest
            Thread.sleep(delay)
        }
        lastRequestTime = System.currentTimeMillis()
    }
    
    /**
     * Charge depuis le cache local
     */
    private fun loadFromCache(hash: HashCalculator.GameHash, console: String): ScrapedMetadata? {
        return try {
            val primaryHash = hash.getPrimaryHash() ?: return null
            val cacheFile = File(CACHE_DIR, "$console-${primaryHash}.json")
            
            if (!cacheFile.exists()) {
                return null
            }
            
            val json = cacheFile.readText()
            val obj = JSONObject(json)
            
            ScrapedMetadata(
                name = obj.getString("name"),
                description = obj.optString("description", null),
                genre = obj.optString("genre", null),
                releaseDate = obj.optString("releaseDate", null),
                developer = obj.optString("developer", null),
                publisher = obj.optString("publisher", null),
                players = obj.optString("players", null),
                rating = obj.optString("rating", null),
                imageUrl = obj.optString("imageUrl", null),
                screenshotUrl = obj.optString("screenshotUrl", null)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error loading from cache: ${e.message}")
            null
        }
    }
    
    /**
     * Sauvegarde dans le cache local
     */
    private fun saveToCache(
        hash: HashCalculator.GameHash,
        console: String,
        metadata: ScrapedMetadata
    ) {
        try {
            val cacheDir = File(CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val primaryHash = hash.getPrimaryHash() ?: return
            val cacheFile = File(cacheDir, "$console-${primaryHash}.json")
            
            val json = JSONObject().apply {
                put("name", metadata.name)
                if (metadata.description != null) put("description", metadata.description)
                if (metadata.genre != null) put("genre", metadata.genre)
                if (metadata.releaseDate != null) put("releaseDate", metadata.releaseDate)
                if (metadata.developer != null) put("developer", metadata.developer)
                if (metadata.publisher != null) put("publisher", metadata.publisher)
                if (metadata.players != null) put("players", metadata.players)
                if (metadata.rating != null) put("rating", metadata.rating)
                if (metadata.imageUrl != null) put("imageUrl", metadata.imageUrl)
                if (metadata.screenshotUrl != null) put("screenshotUrl", metadata.screenshotUrl)
            }
            
            cacheFile.writeText(json.toString())
            Log.d(TAG, "Cached metadata for ${metadata.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Error saving to cache: ${e.message}")
        }
    }
}

