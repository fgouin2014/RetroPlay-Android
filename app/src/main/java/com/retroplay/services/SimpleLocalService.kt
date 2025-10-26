package com.retroplay.services

/**
 * Service local simplifié pour KITT
 * Fournit des réponses basiques sans dépendances externes
 */
class SimpleLocalService {
    
    private val responses = mapOf(
        "bonjour" to "Bonjour, je suis KITT. Comment puis-je vous aider ?",
        "salut" to "Salut ! Je suis prêt à vous assister.",
        "comment ça va" to "Tous mes systèmes fonctionnent parfaitement. Et vous ?",
        "qui es-tu" to "Je suis KITT, Knight Industries Two Thousand, votre assistant personnel.",
        "aide" to "Je peux vous aider avec la navigation, la communication, et bien plus encore.",
        "merci" to "De rien, c'est un plaisir de vous aider.",
        "au revoir" to "Au revoir ! N'hésitez pas à me rappeler si vous avez besoin d'aide.",
        "kitt" to "Oui, je suis là. Que puis-je faire pour vous ?",
        "système" to "Tous mes systèmes sont opérationnels et prêts.",
        "statut" to "Statut : Tous les systèmes fonctionnent normalement.",
        "scanner" to "Scanner activé. Surveillance de l'environnement en cours.",
        "gps" to "Système GPS activé. Navigation prête.",
        "communication" to "Système de communication ouvert. Fréquence établie.",
        "urgence" to "Mode urgence activé. Tous les systèmes en alerte.",
        "turbo" to "Mode turbo activé. Performance maximale engagée."
    )
    
    fun processUserInput(input: String): String {
        val normalizedInput = input.lowercase().trim()
        
        // Recherche exacte d'abord
        responses[normalizedInput]?.let { return it }
        
        // Recherche par mots-clés
        for ((keyword, response) in responses) {
            if (normalizedInput.contains(keyword)) {
                return response
            }
        }
        
        // Réponses par défaut selon le contexte
        return when {
            normalizedInput.contains("comment") -> "Je fonctionne parfaitement, merci de demander."
            normalizedInput.contains("quoi") -> "Je suis votre assistant KITT, prêt à vous aider."
            normalizedInput.contains("pourquoi") -> "C'est ma fonction de vous assister dans toutes vos tâches."
            normalizedInput.contains("où") -> "Je peux vous aider avec la navigation et le GPS."
            normalizedInput.contains("quand") -> "Je suis disponible 24h/24 pour vous assister."
            normalizedInput.contains("qui") -> "Je suis KITT, votre assistant personnel."
            else -> "Je comprends votre demande. Laissez-moi traiter cette information."
        }
    }
}
