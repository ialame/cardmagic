package com.pcagrad.magic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcagrad.magic.model.MtgCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ScryfallService {

    private static final Logger logger = LoggerFactory.getLogger(ScryfallService.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ScryfallService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }


    // AJOUTER cette méthode dans ScryfallService.java

    /**
     * Récupère TOUTES les cartes d'une extension depuis Scryfall avec pagination
     */
    public Mono<List<MtgCard>> getCardsFromScryfall(String setCode) {
        logger.info("🔮 Récupération COMPLÈTE des cartes de {} depuis Scryfall", setCode);

        return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
            try {
                List<MtgCard> allCards = getAllCardsWithPagination(setCode.toLowerCase());
                logger.info("✅ {} cartes TOTALES récupérées depuis Scryfall pour {}", allCards.size(), setCode);
                return allCards;
            } catch (Exception e) {
                logger.error("❌ Erreur Scryfall pour {} : {}", setCode, e.getMessage());
                return Collections.<MtgCard>emptyList();
            }
        }));
    }

    // Remplacez la méthode getAllCardsWithPagination dans votre ScryfallService par cette version corrigée

    /**
     * VERSION CORRIGÉE - Récupère toutes les cartes avec pagination automatique
     */
    private List<MtgCard> getAllCardsWithPagination(String setCode) {
        List<MtgCard> allCards = new ArrayList<>();
        int page = 1;
        boolean hasMore = true;

        while (hasMore && page <= 10) { // Limite sécurité
            // CORRECTION: Construire l'URL de page manuellement au lieu d'utiliser next_page
            String currentUrl = String.format(
                    "https://api.scryfall.com/cards/search?q=set:%s&format=json&order=name&page=%d",
                    setCode, page
            );

            logger.info("📄 Récupération page {} pour {} - URL: {}", page, setCode, currentUrl);

            try {
                String response = restTemplate.getForObject(currentUrl, String.class);

                if (response == null) {
                    logger.warn("⚠️ Réponse nulle pour page {} de {}", page, setCode);
                    break;
                }

                JsonNode root = objectMapper.readTree(response);

                // Vérifier s'il y a une erreur
                if (root.has("type") && "error".equals(root.get("type").asText())) {
                    String errorMessage = root.has("details") ? root.get("details").asText() : "Erreur inconnue";

                    // Si erreur 404 et qu'on a déjà des cartes, c'est normal (fin de pagination)
                    if (errorMessage.contains("didn't match any cards") && !allCards.isEmpty()) {
                        logger.info("🏁 Fin de pagination normale pour {} - {} cartes au total", setCode, allCards.size());
                        break;
                    } else {
                        logger.error("❌ Erreur Scryfall API page {}: {}", page, errorMessage);
                        break;
                    }
                }

                // Parser les cartes de cette page
                JsonNode dataNode = root.get("data");
                if (dataNode != null && dataNode.isArray()) {
                    List<MtgCard> pageCards = parseCardsFromPage(dataNode, setCode);
                    allCards.addAll(pageCards);

                    logger.info("✅ Page {} : {} cartes ajoutées (Total: {})",
                            page, pageCards.size(), allCards.size());

                    // Si cette page a moins de 175 cartes, c'est probablement la dernière
                    if (pageCards.size() < 175) {
                        logger.info("🏁 Page {} a moins de 175 cartes - probablement la dernière page", page);
                        hasMore = false;
                    }
                } else {
                    logger.warn("⚠️ Pas de données dans la page {} pour {}", page, setCode);
                    break;
                }

                // CORRECTION: Vérifier has_more mais ne PAS utiliser next_page
                if (root.has("has_more")) {
                    hasMore = root.get("has_more").asBoolean();
                    if (!hasMore) {
                        logger.info("🏁 has_more=false - dernière page atteinte pour {} page {}", setCode, page);
                    }
                } else {
                    // Si pas de champ has_more, continuer jusqu'à avoir une erreur ou moins de 175 cartes
                    hasMore = dataNode != null && dataNode.isArray() && dataNode.size() >= 175;
                }

                page++;

                // Délai pour respecter les limites de l'API Scryfall
                if (hasMore) {
                    Thread.sleep(150);
                }

            } catch (Exception e) {
                // Si erreur 404 et qu'on a déjà des cartes, c'est normal
                if (e.getMessage().contains("404") && !allCards.isEmpty()) {
                    logger.info("🏁 Erreur 404 normale - fin de pagination pour {} après {} cartes", setCode, allCards.size());
                    break;
                } else {
                    logger.error("❌ Erreur page {} pour {} : {}", page, setCode, e.getMessage());
                    break;
                }
            }
        }

        logger.info("🎉 Pagination terminée pour {} : {} cartes au total sur {} page(s)",
                setCode, allCards.size(), page - 1);
        return allCards;
    }
    /**
     * Parse les cartes d'une page
     */
    private List<MtgCard> parseCardsFromPage(JsonNode dataNode, String setCode) {
        List<MtgCard> cards = new ArrayList<>();

        for (JsonNode cardNode : dataNode) {
            try {
                MtgCard card = parseScryfallCard(cardNode);
                cards.add(card);
            } catch (Exception e) {
                String cardName = cardNode.has("name") ? cardNode.get("name").asText() : "Carte inconnue";
                logger.warn("⚠️ Erreur parsing carte '{}' dans extension {}: {}",
                        cardName, setCode, e.getMessage());
            }
        }

        return cards;
    }

    /**
     * Vérifie si une extension existe sur Scryfall ET compte le nombre total de cartes
     */
    public Mono<SetInfo> getSetInfo(String setCode) {
        return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://api.scryfall.com/sets/" + setCode.toLowerCase();
                String response = restTemplate.getForObject(url, String.class);

                if (response == null) {
                    return new SetInfo(false, setCode, 0, null);
                }

                JsonNode root = objectMapper.readTree(response);

                String name = root.has("name") ? root.get("name").asText() : setCode;
                int cardCount = root.has("card_count") ? root.get("card_count").asInt() : 0;
                String releaseDate = root.has("released_at") ? root.get("released_at").asText() : null;

                logger.info("🎯 Extension {} trouvée : {} - {} cartes attendues", setCode, name, cardCount);

                return new SetInfo(true, name, cardCount, releaseDate);

            } catch (Exception e) {
                logger.warn("⚠️ Extension {} non trouvée : {}", setCode, e.getMessage());
                return new SetInfo(false, setCode, 0, null);
            }
        }));
    }

    /**
     * Vérifie si une extension existe (méthode simplifiée)
     */
    public Mono<Boolean> setExistsOnScryfall(String setCode) {
        return getSetInfo(setCode).map(SetInfo::exists);
    }

    /**
     * Extrait l'URL d'image avec gestion des cartes double-face
     */
    private String extractImageUrl(JsonNode cardNode) {
        // Image normale
        if (cardNode.has("image_uris") && cardNode.get("image_uris").has("normal")) {
            return cardNode.get("image_uris").get("normal").asText();
        }

        // Cartes double-face (première face)
        if (cardNode.has("card_faces") && cardNode.get("card_faces").isArray()
                && cardNode.get("card_faces").size() > 0) {
            JsonNode firstFace = cardNode.get("card_faces").get(0);
            if (firstFace.has("image_uris") && firstFace.get("image_uris").has("normal")) {
                return firstFace.get("image_uris").get("normal").asText();
            }
        }

        return null;
    }

    /**
     * Convertit la rareté Scryfall vers format MTG API
     */
    private String convertRarity(JsonNode cardNode) {
        if (!cardNode.has("rarity")) return "Common";

        String scryfallRarity = cardNode.get("rarity").asText();
        return switch (scryfallRarity) {
            case "mythic" -> "Mythic Rare";
            case "rare" -> "Rare";
            case "uncommon" -> "Uncommon";
            case "common" -> "Common";
            case "special" -> "Special";
            case "bonus" -> "Special";
            default -> scryfallRarity;
        };
    }

    /**
     * Parse les couleurs d'un node JSON
     */
    private List<String> parseColors(JsonNode colorsNode) {
        if (colorsNode == null || !colorsNode.isArray() || colorsNode.size() == 0) {
            return null;
        }

        List<String> colors = new ArrayList<>();
        for (JsonNode colorNode : colorsNode) {
            colors.add(colorNode.asText());
        }
        return colors;
    }

    /**
     * Parse les supertypes depuis la type line
     */
    private List<String> parseSupertypes(String typeLine) {
        if (typeLine == null) return null;

        List<String> supertypes = new ArrayList<>();
        String[] supertypeKeywords = {"Legendary", "Basic", "Snow", "World", "Ongoing"};

        for (String keyword : supertypeKeywords) {
            if (typeLine.contains(keyword)) {
                supertypes.add(keyword);
            }
        }

        return supertypes.isEmpty() ? null : supertypes;
    }

    /**
     * Parse les types principaux depuis la type line
     */
    private List<String> parseTypes(String typeLine) {
        if (typeLine == null) return null;

        List<String> types = new ArrayList<>();
        String[] parts = typeLine.split(" — ");

        if (parts.length > 0) {
            String mainTypes = parts[0].trim();
            String[] typeWords = mainTypes.split(" ");

            for (String word : typeWords) {
                if (!word.equals("Legendary") && !word.equals("Basic") && !word.equals("Snow")
                        && !word.equals("World") && !word.equals("Ongoing")) {
                    types.add(word);
                }
            }
        }

        return types.isEmpty() ? null : types;
    }

    /**
     * Parse les sous-types depuis la type line
     */
    private List<String> parseSubtypes(String typeLine) {
        if (typeLine == null || !typeLine.contains(" — ")) {
            return null;
        }

        String[] parts = typeLine.split(" — ");
        if (parts.length < 2) return null;

        String subtypesStr = parts[1].trim();
        if (subtypesStr.isEmpty()) return null;

        List<String> subtypes = List.of(subtypesStr.split(" "));
        return subtypes.isEmpty() ? null : subtypes;
    }

    /**
     * Record pour les informations d'extension
     */
    public record SetInfo(boolean exists, String name, int expectedCardCount, String releaseDate) {}

// 1. CORRECTION dans ScryfallService.java - Méthode fetchAllCardsFromSet

    /**
     * Récupère TOUTES les cartes d'une extension avec pagination complète
     */
    public List<MtgCard> fetchAllCardsFromSet(String setCode) {
        logger.info("🔮 Récupération COMPLÈTE des cartes de {} depuis Scryfall", setCode);

        List<MtgCard> allCards = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        int page = 1;
        boolean hasMore = true;

        // OBJECTIF SPÉCIAL POUR FIN
        int expectedCards = "FIN".equalsIgnoreCase(setCode) ? 586 : 0;

        while (hasMore && page <= 20) { // Augmenté de 10 à 20 pages max
            String currentUrl = String.format(
                    "https://api.scryfall.com/cards/search?q=set:%s&unique=prints&format=json&order=name&page=%d",
                    setCode.toLowerCase(), page
            );

            logger.info("📄 Récupération page {} pour {} - URL: {}", page, setCode, currentUrl);

            try {
                String response = restTemplate.getForObject(currentUrl, String.class);

                if (response == null) {
                    logger.warn("⚠️ Réponse nulle pour page {} de {}", page, setCode);
                    break;
                }

                JsonNode root = objectMapper.readTree(response);

                // Vérifier erreurs API
                if (root.has("type") && "error".equals(root.get("type").asText())) {
                    String errorMessage = root.has("details") ? root.get("details").asText() : "Erreur inconnue";

                    if (errorMessage.contains("didn't match any cards") && !allCards.isEmpty()) {
                        logger.info("🏁 Fin naturelle pagination pour {} - {} cartes totales", setCode, allCards.size());
                        break;
                    } else {
                        logger.error("❌ Erreur Scryfall API page {}: {}", page, errorMessage);
                        break;
                    }
                }

                // Parser les cartes
                JsonNode dataNode = root.get("data");
                if (dataNode != null && dataNode.isArray()) {
                    List<MtgCard> pageCards = parseCardsFromPage(dataNode, setCode);
                    allCards.addAll(pageCards);

                    logger.info("✅ Page {} : {} cartes ajoutées (Total: {} / {})",
                            page, pageCards.size(), allCards.size(),
                            expectedCards > 0 ? expectedCards : "?");

                    // NOUVELLE LOGIQUE : Ne s'arrêter QUE si has_more = false
                    if (root.has("has_more")) {
                        hasMore = root.get("has_more").asBoolean();
                        if (!hasMore) {
                            logger.info("🏁 has_more=false - pagination terminée pour {} page {}", setCode, page);
                        }
                    } else {
                        // Si pas de has_more, continuer tant qu'on a des cartes
                        hasMore = pageCards.size() > 0;
                        if (!hasMore) {
                            logger.info("🏁 Page vide - pagination terminée pour {} page {}", setCode, page);
                        }
                    }

                    // POUR FIN : Vérifier si on a atteint l'objectif
                    if ("FIN".equalsIgnoreCase(setCode) && allCards.size() >= expectedCards) {
                        logger.info("🎯 Objectif FIN atteint : {} cartes récupérées !", allCards.size());
                        break;
                    }

                } else {
                    logger.warn("⚠️ Pas de données dans la page {} pour {}", page, setCode);
                    hasMore = false;
                }

                page++;

                // Délai respectueux pour l'API
                if (hasMore) {
                    Thread.sleep(150);
                }

            } catch (Exception e) {
                if (e.getMessage().contains("404") && !allCards.isEmpty()) {
                    logger.info("🏁 Erreur 404 - fin pagination pour {} après {} cartes", setCode, allCards.size());
                    break;
                } else {
                    logger.error("❌ Erreur page {} pour {} : {}", page, setCode, e.getMessage());

                    // Pour FIN, essayer de continuer malgré l'erreur
                    if (!"FIN".equalsIgnoreCase(setCode)) {
                        break;
                    }
                    page++; // Essayer la page suivante
                }
            }
        }

        logger.info("🎉 Pagination terminée pour {} : {} cartes au total sur {} page(s)",
                setCode, allCards.size(), page - 1);

        // ALERTE SPÉCIALE POUR FIN
        if ("FIN".equalsIgnoreCase(setCode) && allCards.size() < expectedCards) {
            logger.warn("⚠️ FIN INCOMPLET : {} cartes récupérées sur {} attendues",
                    allCards.size(), expectedCards);
        }

        return allCards;
    }
// MÉTHODE CORRIGÉE dans ScryfallService.java - Compatible avec le record MtgCard

    /**
     * Parse une carte depuis un JsonNode Scryfall
     */
    public MtgCard parseScryfallCard(JsonNode cardNode) {
        try {
            // Extraction des données de base
            String id = cardNode.has("id") ? cardNode.get("id").asText() : null;
            String name = cardNode.has("name") ? cardNode.get("name").asText() : "Unknown";
            String manaCost = cardNode.has("mana_cost") ? cardNode.get("mana_cost").asText() : null;
            String typeLine = cardNode.has("type_line") ? cardNode.get("type_line").asText() : null;
            String rarity = cardNode.has("rarity") ? cardNode.get("rarity").asText() : null;
            String setCode = cardNode.has("set") ? cardNode.get("set").asText().toUpperCase() : null;
            String artist = cardNode.has("artist") ? cardNode.get("artist").asText() : null;
            String text = cardNode.has("oracle_text") ? cardNode.get("oracle_text").asText() : null;
            String power = cardNode.has("power") ? cardNode.get("power").asText() : null;
            String toughness = cardNode.has("toughness") ? cardNode.get("toughness").asText() : null;
            String loyalty = cardNode.has("loyalty") ? cardNode.get("loyalty").asText() : null;
            String number = cardNode.has("collector_number") ? cardNode.get("collector_number").asText() : null;
            String layout = cardNode.has("layout") ? cardNode.get("layout").asText() : "normal";

            // CMC (Converted Mana Cost)
            Integer cmc = cardNode.has("cmc") ? cardNode.get("cmc").asInt() : null;

            // MultiverseId (peut ne pas exister pour toutes les cartes)
            Integer multiverseId = null;
            if (cardNode.has("multiverse_ids") && cardNode.get("multiverse_ids").isArray() &&
                    cardNode.get("multiverse_ids").size() > 0) {
                multiverseId = cardNode.get("multiverse_ids").get(0).asInt();
            }

            // URL d'image
            String imageUrl = null;
            if (cardNode.has("image_uris")) {
                JsonNode imageUris = cardNode.get("image_uris");
                if (imageUris.has("normal")) {
                    imageUrl = imageUris.get("normal").asText();
                } else if (imageUris.has("large")) {
                    imageUrl = imageUris.get("large").asText();
                } else if (imageUris.has("small")) {
                    imageUrl = imageUris.get("small").asText();
                }
            }

            // Pour les cartes double-face, prendre la première face
            if (imageUrl == null && cardNode.has("card_faces") && cardNode.get("card_faces").isArray()) {
                JsonNode firstFace = cardNode.get("card_faces").get(0);
                if (firstFace.has("image_uris")) {
                    JsonNode faceImageUris = firstFace.get("image_uris");
                    if (faceImageUris.has("normal")) {
                        imageUrl = faceImageUris.get("normal").asText();
                    }
                }
            }

            // Couleurs
            List<String> colors = new ArrayList<>();
            if (cardNode.has("colors") && cardNode.get("colors").isArray()) {
                for (JsonNode colorNode : cardNode.get("colors")) {
                    colors.add(colorNode.asText());
                }
            }

            // Identité de couleur
            List<String> colorIdentity = new ArrayList<>();
            if (cardNode.has("color_identity") && cardNode.get("color_identity").isArray()) {
                for (JsonNode colorNode : cardNode.get("color_identity")) {
                    colorIdentity.add(colorNode.asText());
                }
            }

            // Types, Supertypes, Subtypes
            List<String> supertypes = new ArrayList<>();
            List<String> types = new ArrayList<>();
            List<String> subtypes = new ArrayList<>();

            if (cardNode.has("type_line")) {
                String typeLineStr = cardNode.get("type_line").asText();

                // Parser la ligne de type (ex: "Legendary Creature — Human Warrior")
                if (typeLineStr.contains("—")) {
                    String[] parts = typeLineStr.split("—");
                    String leftPart = parts[0].trim();
                    String rightPart = parts.length > 1 ? parts[1].trim() : "";

                    // Partie gauche: supertypes + types
                    String[] leftWords = leftPart.split("\\s+");
                    for (String word : leftWords) {
                        word = word.trim();
                        if (isSupertype(word)) {
                            supertypes.add(word);
                        } else if (isType(word)) {
                            types.add(word);
                        }
                    }

                    // Partie droite: subtypes
                    if (!rightPart.isEmpty()) {
                        String[] rightWords = rightPart.split("\\s+");
                        for (String word : rightWords) {
                            subtypes.add(word.trim());
                        }
                    }
                } else {
                    // Pas de subtypes, seulement types et supertypes
                    String[] words = typeLineStr.split("\\s+");
                    for (String word : words) {
                        word = word.trim();
                        if (isSupertype(word)) {
                            supertypes.add(word);
                        } else if (isType(word)) {
                            types.add(word);
                        }
                    }
                }
            }

            // Nom du set - essayer de récupérer depuis l'API ou utiliser le code
            String setName = setCode; // Fallback
            if (cardNode.has("set_name")) {
                setName = cardNode.get("set_name").asText();
            }

            // Création du record MtgCard avec TOUS les paramètres requis
            return new MtgCard(
                    id,                // String id
                    name,              // String name
                    manaCost,          // String manaCost
                    cmc,               // Integer cmc
                    colors,            // List<String> colors
                    colorIdentity,     // List<String> colorIdentity
                    typeLine,          // String type
                    supertypes,        // List<String> supertypes
                    types,             // List<String> types
                    subtypes,          // List<String> subtypes
                    rarity,            // String rarity
                    setCode,           // String set
                    setName,           // String setName
                    text,              // String text
                    artist,            // String artist
                    number,            // String number
                    power,             // String power
                    toughness,         // String toughness
                    layout,            // String layout
                    multiverseId,      // Integer multiverseid
                    imageUrl           // String imageUrl
            );

        } catch (Exception e) {
            logger.error("❌ Erreur parsing carte Scryfall: {}", e.getMessage());
            // Retourner une carte minimale en cas d'erreur
            String name = cardNode.has("name") ? cardNode.get("name").asText() : "Unknown Card";
            String setCode = cardNode.has("set") ? cardNode.get("set").asText().toUpperCase() : "UNK";

            return new MtgCard(
                    null, name, null, null, new ArrayList<>(), new ArrayList<>(),
                    null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                    null, setCode, setCode, null, null, null,
                    null, null, "normal", null, null
            );
        }
    }

    // Méthodes utilitaires pour parser les types
    private boolean isSupertype(String word) {
        return List.of("Legendary", "Basic", "Snow", "World", "Ongoing").contains(word);
    }

    private boolean isType(String word) {
        return List.of("Artifact", "Creature", "Enchantment", "Instant", "Land",
                "Planeswalker", "Sorcery", "Tribal", "Conspiracy", "Phenomenon",
                "Plane", "Scheme", "Vanguard", "Battle").contains(word);
    }
}