package com.retroplay.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.retroplay.R

/**
 * Fragment drawer pour le menu des commandes KITT
 * Affiche toutes les commandes disponibles avec des boutons
 */
class KittDrawerFragment : Fragment() {
    
    private var commandListener: CommandListener? = null
    private lateinit var sharedPreferences: SharedPreferences
    
    interface CommandListener {
        fun onCommandSelected(command: String)
        fun onCloseDrawer()
        fun onConfigurationCenterRequested() // Centre de configuration
        fun onWebServerRequested() // Serveur web local
        fun onWebServerConfigRequested() // Configuration WebServer (port 7777)
        fun onEndpointsListRequested() // Liste des endpoints API
        fun onHtmlExplorerRequested() // Explorateur HTML
        fun onThemeChanged(theme: String) // Changement de thème
        fun onButtonPressed(buttonName: String) // Annonce vocale du bouton pressé
        fun onAnimationModeChanged(mode: String) // Changement de mode d'animation VU-meter
    }
    
    fun setCommandListener(listener: CommandListener) {
        this.commandListener = listener
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_kitt_drawer, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = requireContext().getSharedPreferences("kitt_prefs", Context.MODE_PRIVATE)
        setupButtons(view)
        updateThemeButtons(view)
        applySelectedTheme(view)
        
        // Intercepter le bouton back pour fermer le drawer au lieu de l'app
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                commandListener?.onCloseDrawer()
                true
            } else {
                false
            }
        }
    }
    
    // Méthode publique pour forcer la mise à jour du thème
    fun refreshTheme() {
        view?.let { 
            updateThemeButtons(it)
            applySelectedTheme(it) 
        }
    }
    
    fun updateAnimationModeButtons(originalSelected: Boolean) {
        view?.let { view ->
            val originalButton = view.findViewById<MaterialButton>(R.id.animationOriginalButton)
            val dualButton = view.findViewById<MaterialButton>(R.id.animationDualButton)
            val currentTheme = getCurrentTheme()
            
            // Couleurs selon le thème actuel
            val (primaryColor, primaryAlpha, textColor) = when (currentTheme) {
                "red" -> Triple(R.color.kitt_red, R.color.kitt_red_alpha, R.color.kitt_red)
                "dark" -> Triple(R.color.dark_gray_light, R.color.dark_gray_medium, R.color.dark_white)
                "amber" -> Triple(R.color.amber_primary, R.color.amber_primary_light, R.color.amber_primary)
                else -> Triple(R.color.kitt_red, R.color.kitt_red_alpha, R.color.kitt_red)
            }
            
            // Réinitialiser les deux boutons au style de base
            originalButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
            originalButton.setTextColor(ContextCompat.getColor(requireContext(), textColor))
            originalButton.setStrokeColorResource(primaryColor)
            
            dualButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
            dualButton.setTextColor(ContextCompat.getColor(requireContext(), textColor))
            dualButton.setStrokeColorResource(primaryColor)
            
            if (originalSelected) {
                // ORIGINAL sélectionné - ajouter une couche transparente plus foncée
                originalButton.setBackgroundColor(ContextCompat.getColor(requireContext(), primaryAlpha))
                originalButton.text = "✓ ANIMATION\nORIGINAL"
                dualButton.text = "ANIMATION\nDUAL"
            } else {
                // DUAL sélectionné - ajouter une couche transparente plus foncée
                dualButton.setBackgroundColor(ContextCompat.getColor(requireContext(), primaryAlpha))
                originalButton.text = "ANIMATION\nORIGINAL"
                dualButton.text = "✓ ANIMATION\nDUAL"
            }
        }
    }
    
    
    private fun setupButtons(view: View) {
        // Bouton fermer
        view.findViewById<MaterialButton>(R.id.closeDrawerButton).setOnClickListener {
            commandListener?.onCloseDrawer()
        }
        
        // Commandes de base
        view.findViewById<MaterialButton>(R.id.activateKittButton).setOnClickListener {
            commandListener?.onButtonPressed("Activation de KITT")
            commandListener?.onCommandSelected("ACTIVATE_KITT")
        }
        
        view.findViewById<MaterialButton>(R.id.systemStatusButton).setOnClickListener {
            commandListener?.onButtonPressed("Statut du système")
            commandListener?.onCommandSelected("SYSTEM_STATUS")
        }
        
        view.findViewById<MaterialButton>(R.id.activateScannerButton).setOnClickListener {
            commandListener?.onButtonPressed("Activation du scanner")
            commandListener?.onCommandSelected("ACTIVATE_SCANNER")
        }
        
        // Mode affichage - Animation VU-meter
        view.findViewById<MaterialButton>(R.id.animationOriginalButton).setOnClickListener {
            commandListener?.onButtonPressed("Animation VU-meter originale")
            commandListener?.onAnimationModeChanged("ORIGINAL")
            updateAnimationModeButtons(true)
        }
        
        view.findViewById<MaterialButton>(R.id.animationDualButton).setOnClickListener {
            commandListener?.onButtonPressed("Animation VU-meter dual")
            commandListener?.onAnimationModeChanged("DUAL")
            updateAnimationModeButtons(false)
        }
        
        // Analyse et surveillance
        view.findViewById<MaterialButton>(R.id.environmentalAnalysisButton).setOnClickListener {
            commandListener?.onButtonPressed("Analyse environnementale")
            commandListener?.onCommandSelected("ENVIRONMENTAL_ANALYSIS")
        }
        
        view.findViewById<MaterialButton>(R.id.surveillanceModeButton).setOnClickListener {
            commandListener?.onButtonPressed("Mode surveillance")
            commandListener?.onCommandSelected("SURVEILLANCE_MODE")
        }
        
        view.findViewById<MaterialButton>(R.id.emergencyModeButton).setOnClickListener {
            commandListener?.onButtonPressed("Mode urgence")
            commandListener?.onCommandSelected("EMERGENCY_MODE")
        }
        
        // Navigation
        view.findViewById<MaterialButton>(R.id.gpsActivationButton).setOnClickListener {
            commandListener?.onButtonPressed("Activation GPS")
            commandListener?.onCommandSelected("GPS_ACTIVATION")
        }
        
        view.findViewById<MaterialButton>(R.id.calculateRouteButton).setOnClickListener {
            commandListener?.onButtonPressed("Calcul de route")
            commandListener?.onCommandSelected("CALCULATE_ROUTE")
        }
        
        view.findViewById<MaterialButton>(R.id.setDestinationButton).setOnClickListener {
            commandListener?.onButtonPressed("Définition de destination")
            commandListener?.onCommandSelected("SET_DESTINATION")
        }
        
        // Communication
        view.findViewById<MaterialButton>(R.id.openCommunicationButton).setOnClickListener {
            commandListener?.onButtonPressed("Ouverture de communication")
            commandListener?.onCommandSelected("OPEN_COMMUNICATION")
        }
        
        view.findViewById<MaterialButton>(R.id.setFrequencyButton).setOnClickListener {
            commandListener?.onButtonPressed("Définition de fréquence")
            commandListener?.onCommandSelected("SET_FREQUENCY")
        }
        
        view.findViewById<MaterialButton>(R.id.transmitMessageButton).setOnClickListener {
            commandListener?.onButtonPressed("Transmission de message")
            commandListener?.onCommandSelected("TRANSMIT_MESSAGE")
        }
        
        // Performance
        view.findViewById<MaterialButton>(R.id.turboBoostButton).setOnClickListener {
            commandListener?.onButtonPressed("Turbo boost")
            commandListener?.onCommandSelected("TURBO_BOOST")
        }
        
        view.findViewById<MaterialButton>(R.id.pursuitModeButton).setOnClickListener {
            commandListener?.onButtonPressed("Mode poursuite")
            commandListener?.onCommandSelected("PURSUIT_MODE")
        }
        
        view.findViewById<MaterialButton>(R.id.deactivateKittButton).setOnClickListener {
            commandListener?.onButtonPressed("Désactivation de KITT")
            commandListener?.onCommandSelected("DEACTIVATE_KITT")
        }
        
            // AI Configuration button
            view.findViewById<MaterialButton>(R.id.btnAIConfig).setOnClickListener {
                commandListener?.onButtonPressed("Configuration IA")
                commandListener?.onConfigurationCenterRequested()
            }
            
            // Music button
            view.findViewById<MaterialButton>(R.id.musicButton).setOnClickListener {
                android.util.Log.d("Music", "Bouton musique cliqué dans le drawer")
                commandListener?.onButtonPressed("Musique")
                commandListener?.onCommandSelected("TOGGLE_MUSIC")
                android.util.Log.d("Music", "Commande TOGGLE_MUSIC envoyée")
            }
            
            // Games button
            view.findViewById<MaterialButton>(R.id.gamesButton).setOnClickListener {
                android.util.Log.d("Games", "Bouton jeux cliqué dans le drawer")
                commandListener?.onButtonPressed("Jeux")
                // Ouvrir la WebView RelaxWebViewActivity
                val intent = android.content.Intent(requireContext(), com.retroplay.activities.RelaxWebViewActivity::class.java)
                startActivity(intent)
                android.util.Log.d("Games", "Ouverture de RelaxWebViewActivity")
            }
            
            // Games button 2
            view.findViewById<MaterialButton>(R.id.gamesLibraryButton).setOnClickListener {
                android.util.Log.d("Games", "Bouton bibliothèque NES cliqué dans le drawer")
                commandListener?.onButtonPressed("Bibliothèque NES")
                // Ouvrir la bibliothèque de jeux NES
                val intent = android.content.Intent(requireContext(), com.retroplay.GameListActivity::class.java)
                startActivity(intent)
                android.util.Log.d("Games", "Ouverture de GameListActivity")
            }
            
            // Web Server Configuration button
            view.findViewById<MaterialButton>(R.id.btnWebServer).setOnClickListener {
                commandListener?.onButtonPressed("Configuration serveur web")
                commandListener?.onWebServerRequested()
            }
            
            // WebServer Configuration button (port 7777)
            view.findViewById<MaterialButton>(R.id.btnWebServerConfig).setOnClickListener {
                commandListener?.onButtonPressed("Configuration WebServer")
                commandListener?.onWebServerConfigRequested()
            }
            
            // Endpoints List button
            view.findViewById<MaterialButton>(R.id.btnEndpointsList).setOnClickListener {
                commandListener?.onButtonPressed("Liste des endpoints API")
                commandListener?.onEndpointsListRequested()
            }
            
            // HTML Explorer button
            view.findViewById<MaterialButton>(R.id.btnHtmlExplorer).setOnClickListener {
                commandListener?.onButtonPressed("Explorateur HTML")
                commandListener?.onHtmlExplorerRequested()
            }
            
            // Theme toggle buttons
            view.findViewById<MaterialButton>(R.id.btnThemeRed).setOnClickListener {
                commandListener?.onButtonPressed("Thème rouge activé")
                saveTheme("red")
                commandListener?.onThemeChanged("red")
                updateThemeButtons(view)
            }
            
            view.findViewById<MaterialButton>(R.id.btnThemeDark).setOnClickListener {
                commandListener?.onButtonPressed("Thème sombre activé")
                saveTheme("dark")
                commandListener?.onThemeChanged("dark")
                updateThemeButtons(view)
            }
            
            view.findViewById<MaterialButton>(R.id.btnThemeAmber).setOnClickListener {
                commandListener?.onButtonPressed("Thème ambre activé")
                saveTheme("amber")
                commandListener?.onThemeChanged("amber")
                updateThemeButtons(view)
            }
    }
    
    private fun saveTheme(theme: String) {
        sharedPreferences.edit().putString("kitt_theme", theme).apply()
    }
    
    private fun getCurrentTheme(): String {
        return sharedPreferences.getString("kitt_theme", "red") ?: "red"
    }
    
    private fun updateThemeButtons(view: View) {
        val currentTheme = getCurrentTheme()
        
        // Réinitialiser tous les boutons
        val redButton = view.findViewById<MaterialButton>(R.id.btnThemeRed)
        val darkButton = view.findViewById<MaterialButton>(R.id.btnThemeDark)
        val amberButton = view.findViewById<MaterialButton>(R.id.btnThemeAmber)
        
        // Styles par défaut (non sélectionnés) - chaque bouton garde ses couleurs de thème
        redButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        redButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        redButton.setStrokeColorResource(R.color.kitt_red)
        redButton.text = "🔴 ROUGE"
        
        darkButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        darkButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        darkButton.setStrokeColorResource(R.color.dark_gray_light)
        darkButton.text = "⚫ SOMBRE"
        
        amberButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        amberButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        amberButton.setStrokeColorResource(R.color.amber_primary)
        amberButton.text = "🟠 AMBRE"
        
        // Activer le bouton correspondant au thème actuel - ajouter une couche transparente
        when (currentTheme) {
            "red" -> {
                redButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_red_alpha))
                redButton.text = "✓ 🔴 ROUGE"
            }
            "dark" -> {
                darkButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dark_gray_medium))
                darkButton.text = "✓ ⚫ SOMBRE"
            }
            "amber" -> {
                amberButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.amber_primary_light))
                amberButton.text = "✓ 🟠 AMBRE"
            }
        }
    }
    
    
    private fun applySelectedTheme(view: View) {
        val selectedTheme = getCurrentTheme()
        
        when (selectedTheme) {
            "red" -> applyRedTheme(view)
            "dark" -> applyDarkTheme(view)
            "amber" -> applyAmberTheme(view)
        }
    }
    
    private fun applyRedTheme(view: View) {
        // Appliquer le thème rouge au drawer
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        
        // Appliquer aux textes
        val headerLayout = view.findViewById<LinearLayout>(R.id.drawerHeader)
        val headerText = headerLayout?.getChildAt(0) as? TextView
        headerText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        
        // Appliquer aux boutons
        val allButtons = listOf(
            R.id.closeDrawerButton, R.id.activateKittButton, R.id.systemStatusButton,
            R.id.activateScannerButton, R.id.environmentalAnalysisButton, R.id.surveillanceModeButton,
            R.id.emergencyModeButton, R.id.gpsActivationButton, R.id.calculateRouteButton,
            R.id.setDestinationButton, R.id.openCommunicationButton, R.id.setFrequencyButton,
            R.id.transmitMessageButton, R.id.turboBoostButton, R.id.pursuitModeButton,
            R.id.deactivateKittButton, R.id.btnAIConfig, R.id.musicButton, R.id.btnWebServer, 
            R.id.btnWebServerConfig, R.id.btnEndpointsList, R.id.btnHtmlExplorer
        )
        
        allButtons.forEach { buttonId ->
            val button = view.findViewById<MaterialButton>(buttonId)
            button?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
            button?.setStrokeColorResource(R.color.kitt_red)
        }
        
        // Appliquer le thème rouge aux boutons
        updateThemeButtons(view)
        // Mettre à jour les boutons de mode d'affichage avec le nouveau thème
        updateAnimationModeButtons(true) // ORIGINAL par défaut
    }
    
    private fun applyDarkTheme(view: View) {
        // Appliquer le thème sombre au drawer
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dark_black))
        
        // Appliquer aux textes
        val headerLayout = view.findViewById<LinearLayout>(R.id.drawerHeader)
        val headerText = headerLayout?.getChildAt(0) as? TextView
        headerText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        
        // Appliquer aux boutons
        val allButtons = listOf(
            R.id.closeDrawerButton, R.id.activateKittButton, R.id.systemStatusButton,
            R.id.activateScannerButton, R.id.environmentalAnalysisButton, R.id.surveillanceModeButton,
            R.id.emergencyModeButton, R.id.gpsActivationButton, R.id.calculateRouteButton,
            R.id.setDestinationButton, R.id.openCommunicationButton, R.id.setFrequencyButton,
            R.id.transmitMessageButton, R.id.turboBoostButton, R.id.pursuitModeButton,
            R.id.deactivateKittButton, R.id.btnAIConfig, R.id.musicButton, R.id.btnWebServer, 
            R.id.btnWebServerConfig, R.id.btnEndpointsList, R.id.btnHtmlExplorer
        )
        
        allButtons.forEach { buttonId ->
            val button = view.findViewById<MaterialButton>(buttonId)
            button?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
            button?.setStrokeColorResource(R.color.dark_gray_light)
        }
        
        // Appliquer le thème sombre aux boutons
        updateThemeButtons(view)
        // Mettre à jour les boutons de mode d'affichage avec le nouveau thème
        updateAnimationModeButtons(true) // ORIGINAL par défaut
    }
    
    private fun applyAmberTheme(view: View) {
        // Appliquer le thème ambre au drawer
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.amber_surface))
        
        // Appliquer aux textes
        val headerLayout = view.findViewById<LinearLayout>(R.id.drawerHeader)
        val headerText = headerLayout?.getChildAt(0) as? TextView
        headerText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_on_surface))
        
        // Appliquer aux boutons
        val allButtons = listOf(
            R.id.closeDrawerButton, R.id.activateKittButton, R.id.systemStatusButton,
            R.id.activateScannerButton, R.id.environmentalAnalysisButton, R.id.surveillanceModeButton,
            R.id.emergencyModeButton, R.id.gpsActivationButton, R.id.calculateRouteButton,
            R.id.setDestinationButton, R.id.openCommunicationButton, R.id.setFrequencyButton,
            R.id.transmitMessageButton, R.id.turboBoostButton, R.id.pursuitModeButton,
            R.id.deactivateKittButton, R.id.btnAIConfig, R.id.musicButton, R.id.btnWebServer, 
            R.id.btnWebServerConfig, R.id.btnEndpointsList, R.id.btnHtmlExplorer
        )
        
        allButtons.forEach { buttonId ->
            val button = view.findViewById<MaterialButton>(buttonId)
            button?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
            button?.setStrokeColorResource(R.color.amber_primary)
        }
        
        // Appliquer le thème ambre aux boutons
        updateThemeButtons(view)
        // Mettre à jour les boutons de mode d'affichage avec le nouveau thème
        updateAnimationModeButtons(true) // ORIGINAL par défaut
    }
}
