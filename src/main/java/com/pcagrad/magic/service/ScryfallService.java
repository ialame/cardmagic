package com.pcagrad.magic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcagrad.magic.model.MtgCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    /**
     * Récupère TOUTES les cartes d'une extension depuis Scryfall avec pagination CORRIGÉE
     */
    public Mono<List<MtgCard>> getCardsFromScryfall(String setCode) {
        logger.info("🔮 Récupération COMPLÈTE des cartes de {} depuis Scryfall", setCode);

        return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
            try {
                List<MtgCard> allCards = getAllCardsWithPaginationFixed(setCode.toLowerCase());
                logger.info("✅ {} cartes TOTALES récupérées depuis Scryfall pour {}", allCards.size(), setCode);
                return allCards;
            } catch (Exception e) {
                logger.error("❌ Erreur Scryfall pour {} : {}", setCode, e.getMessage());
                return Collections.<MtgCard>emptyList();
            }
        }));
    }

    /**
     * VERSION CORRIGÉE - Récupère toutes les cartes avec pagination FORCÉE
     */
    private List<MtgCard> getAllCardsWithPaginationFixed(String setCode) {
        List<MtgCard> allCards = new ArrayList<>();
        int page = 1;
        int maxPages = 20; // Sécurité : max 20 pages (20 * 175 = 3500 cartes max)

        logger.info("🔄 PAGINATION FORCÉE pour {} - Objectif: récupérer TOUTES les cartes", setCode);

        while (page <= maxPages) {
            String currentUrl = String.format(
                    "https://api.scryfall.com/cards/search?q=set:%s&format=json&order=set&page=%d",
                    setCode, page
            );

            logger.info("📄 Page {}/{} pour {} - URL: {}", page, maxPages, setCode, currentUrl);

            try {
                String response = restTemplate.getForObject(currentUrl, String.class);

                if (response == null) {
                    logger.warn("⚠️ Réponse nulle pour page {} de {}", page, setCode);
                    break;
                }

                JsonNode root = objectMapper.readTree(response);

                // Vérifier si erreur API (fin normale de pagination)
                if (root.has("type") && "error".equals(root.get("type").asText())) {
                    String errorType = root.has("code") ? root.get("code").asText() : "unknown";

                    if ("not_found".equals(errorType) && !allCards.isEmpty()) {
                        logger.info("🏁 FIN NORMALE pagination {} - {} cartes totales récupérées", setCode, allCards.size());
                        break;
                    } else {
                        String errorMessage = root.has("details") ? root.get("details").asText() : "Erreur inconnue";
                        logger.error("❌ Erreur Scryfall API page {} : {}", page, errorMessage);
                        break;
                    }
                }

                // Parser les cartes de cette page
                JsonNode dataNode = root.get("data");
                if (dataNode != null && dataNode.isArray()) {
                    int cardsInThisPage = dataNode.size();

                    for (JsonNode cardNode : dataNode) {
                        try {
                            MtgCard card = parseScryfallCard(cardNode);
                            allCards.add(card);
                        } catch (Exception e) {
                            String cardName = cardNode.has("name") ? cardNode.get("name").asText() : "Carte inconnue";
                            logger.warn("⚠️ Erreur parsing carte '{}' page {} : {}", cardName, page, e.getMessage());
                        }
                    }

                    logger.info("✅ Page {} : {} cartes ajoutées (Total: {})",
                            page, cardsInThisPage, allCards.size());

                    // LOGIQUE CORRIGÉE : Continuer tant qu'on a des cartes OU que has_more = true
                    boolean shouldContinue = false;

                    // Vérifier has_more en premier
                    if (root.has("has_more")) {
                        boolean hasMore = root.get("has_more").asBoolean();
                        logger.info("📊 Page {} - has_more: {}", page, hasMore);
                        shouldContinue = hasMore;
                    } else {
                        // Si pas de has_more, continuer tant qu'on a des cartes
                        shouldContinue = cardsInThisPage > 0;
                        logger.info("📊 Page {} - pas de has_more, {} cartes trouvées", page, cardsInThisPage);
                    }

                    // CONDITION SPÉCIALE POUR FIN
                    if ("fin".equals(setCode)) {
                        // Pour FIN, on sait qu'il y a 586 cartes, donc continuer jusqu'à les avoir toutes
                        if (allCards.size() >= 586) {
                            logger.info("🎯 OBJECTIF FIN ATTEINT : {} cartes récupérées !", allCards.size());
                            break;
                        } else if (!shouldContinue && allCards.size() < 586) {
                            // Forcer la continuation pour FIN même si has_more = false
                            logger.warn("⚠️ FIN: has_more=false mais seulement {} cartes - FORCER continuation", allCards.size());
                            shouldContinue = true;
                        }
                    }

                    if (!shouldContinue) {
                        logger.info("🏁 Pagination terminée naturellement pour {} à la page {}", setCode, page);
                        break;
                    }

                } else {
                    logger.warn("⚠️ Pas de données dans la page {} pour {}", page, setCode);
                    break;
                }

                page++;

                // Délai respectueux pour l'API Scryfall
                if (page <= maxPages) {
                    Thread.sleep(150);
                }

            } catch (Exception e) {
                if (e.getMessage().contains("404") && !allCards.isEmpty()) {
                    logger.info("🏁 Erreur 404 normale - fin pagination pour {} après {} cartes", setCode, allCards.size());
                    break;
                } else {
                    logger.error("❌ Erreur page {} pour {} : {}", page, setCode, e.getMessage());

                    // Pour FIN, essayer de continuer même avec des erreurs
                    if ("fin".equals(setCode) && allCards.size() < 586 && page <= 5) {
                        logger.warn("⚠️ FIN: Erreur page {} mais continuation forcée", page);
                        page++;
                        continue;
                    }
                    break;
                }
            }
        }

        logger.info("🎉 PAGINATION TERMINÉE pour {} : {} cartes récupérées sur {} pages",
                setCode.toUpperCase(), allCards.size(), page - 1);

        // VÉRIFICATION FINALE POUR FIN
        if ("fin".equals(setCode)) {
            if (allCards.size() >= 580) {
                logger.info("🎯 FIN SUCCÈS : {} cartes récupérées (objectif ~586)", allCards.size());
            } else {
                logger.warn("⚠️ FIN INCOMPLET : {} cartes seulement - Essayer d'autres requêtes ?", allCards.size());
            }
        }

        return allCards;
    }

    /**
     * VERSION ALTERNATIVE - Essaie différentes requêtes pour FIN
     */
    public List<MtgCard> fetchAllCardsFromSetWithMultipleQueries(String setCode) {
        if (!"FIN".equalsIgnoreCase(setCode)) {
            // Pour les autres sets, utiliser la méthode normale
            return getAllCardsWithPaginationFixed(setCode.toLowerCase());
        }

        logger.info("🎮 STRATÉGIE MULTIPLE pour Final Fantasy - Test de plusieurs requêtes");

        String[] strategies = {
                "set:fin",                    // Stratégie 1: basique
                "set:fin unique:prints",      // Stratégie 2: avec prints uniques
                "e:fin",                     // Stratégie 3: notation alternative
                "set:fin include:extras",     // Stratégie 4: avec extras
                "(set:fin OR e:fin)"         // Stratégie 5: combinée
        };

        List<MtgCard> bestResult = new ArrayList<>();
        String bestStrategy = "";
        int maxFound = 0;

        for (String strategy : strategies) {
            try {
                logger.info("🧪 Test stratégie FIN: '{}'", strategy);
                List<MtgCard> result = fetchCardsWithCustomQuery(strategy);

                logger.info("📊 Stratégie '{}' : {} cartes trouvées", strategy, result.size());

                if (result.size() > maxFound) {
                    maxFound = result.size();
                    bestStrategy = strategy;
                    bestResult = new ArrayList<>(result);
                }

                // Si on atteint l'objectif, on peut s'arrêter
                if (result.size() >= 580) {
                    logger.info("🎯 Objectif atteint avec stratégie '{}' : {} cartes", strategy, result.size());
                    break;
                }

                Thread.sleep(500); // Délai entre stratégies

            } catch (Exception e) {
                logger.error("❌ Erreur stratégie '{}' : {}", strategy, e.getMessage());
            }
        }

        logger.info("🏆 MEILLEURE STRATÉGIE FIN : '{}' avec {} cartes", bestStrategy, maxFound);
        return bestResult;
    }

    /**
     * Exécute une requête personnalisée
     */
    private List<MtgCard> fetchCardsWithCustomQuery(String query) throws Exception {
        List<MtgCard> cards = new ArrayList<>();
        int page = 1;
        int maxPages = 10;

        while (page <= maxPages) {
            String url = String.format(
                    "https://api.scryfall.com/cards/search?q=%s&format=json&order=set&page=%d",
                    query.replace(" ", "%20"), page
            );

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) break;

            JsonNode root = objectMapper.readTree(response);

            if (root.has("type") && "error".equals(root.get("type").asText())) {
                if (cards.isEmpty()) {
                    throw new Exception("Query failed: " + query);
                } else {
                    break; // Fin normale
                }
            }

            JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode cardNode : dataNode) {
                    try {
                        MtgCard card = parseScryfallCard(cardNode);
                        cards.add(card);
                    } catch (Exception e) {
                        logger.warn("⚠️ Erreur parsing carte custom query: {}", e.getMessage());
                    }
                }

                boolean hasMore = root.has("has_more") && root.get("has_more").asBoolean();
                if (!hasMore) break;
            } else {
                break;
            }

            page++;
            Thread.sleep(150);
        }

        return cards;
    }

    /**
     * Utilise la méthode corrigée ou alternative selon le set
     */
    public List<MtgCard> fetchAllCardsFromSet(String setCode) {
        if ("FIN".equalsIgnoreCase(setCode)) {
            // Pour FIN, essayer la stratégie multiple
            return fetchAllCardsFromSetWithMultipleQueries(setCode);
        } else {
            // Pour les autres, utiliser la méthode corrigée
            return getAllCardsWithPaginationFixed(setCode.toLowerCase());
        }
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
     * Parse une carte depuis un JsonNode Scryfall - VERSION COMPLÈTE
     */
    public MtgCard parseScryfallCard(JsonNode cardNode) {
        try {
            // Extraction des données de base
            String id = cardNode.has("id") ? cardNode.get("id").asText() : null;
            String name = cardNode.has("name") ? cardNode.get("name").asText() : "Unknown";
            String manaCost = cardNode.has("mana_cost") ? cardNode.get("mana_cost").asText() : null;
            String typeLine = cardNode.has("type_line") ? cardNode.get("type_line").asText() : null;
            String rarity = cardNode.has("rarity") ? convertRarity(cardNode) : null;
            String setCode = cardNode.has("set") ? cardNode.get("set").asText().toUpperCase() : null;
            String artist = cardNode.has("artist") ? cardNode.get("artist").asText() : null;
            String text = cardNode.has("oracle_text") ? cardNode.get("oracle_text").asText() : null;
            String power = cardNode.has("power") ? cardNode.get("power").asText() : null;
            String toughness = cardNode.has("toughness") ? cardNode.get("toughness").asText() : null;
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
            String imageUrl = extractImageUrl(cardNode);

            // Couleurs
            List<String> colors = parseColors(cardNode.get("colors"));
            List<String> colorIdentity = parseColors(cardNode.get("color_identity"));

            // Types, Supertypes, Subtypes
            List<String> supertypes = parseSupertypes(typeLine);
            List<String> types = parseTypes(typeLine);
            List<String> subtypes = parseSubtypes(typeLine);

            // Nom du set
            String setName = setCode;
            if (cardNode.has("set_name")) {
                setName = cardNode.get("set_name").asText();
            }

            // Création du record MtgCard
            return new MtgCard(
                    id, name, manaCost, cmc, colors, colorIdentity,
                    typeLine, supertypes, types, subtypes, rarity,
                    setCode, setName, text, artist, number,
                    power, toughness, layout, multiverseId, imageUrl
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

    /**
     * VERSION CORRIGÉE pour Final Fantasy - Les vraies requêtes qui fonctionnent
     */
    public List<MtgCard> fetchAllCardsFromSetFixed(String setCode) {
        if (!"FIN".equalsIgnoreCase(setCode)) {
            return getAllCardsWithPaginationFixed(setCode.toLowerCase());
        }

        logger.info("🎮 RÉCUPÉRATION CORRIGÉE Final Fantasy - Objectif 312 cartes");

        // LES VRAIES REQUÊTES QUI FONCTIONNENT POUR FIN
        String[] workingQueries = {
                "set:fin",                                    // Requête de base
                "\"final fantasy\" set:fin",                  // Avec nom complet
                "e:fin",                                      // Extension alternative
                "set=\"Magic: The Gathering—FINAL FANTASY\"", // Nom exact de l'extension
                "(set:fin OR e:fin)",                         // Combinaison
                "game:paper set:fin",                         // Avec format papier
                "is:booster set:fin"                          // Cartes en booster
        };

        List<MtgCard> bestResult = new ArrayList<>();
        String bestQuery = "";
        int maxFound = 0;

        for (String query : workingQueries) {
            try {
                logger.info("🔍 Test requête FIN: '{}'", query);

                List<MtgCard> result = fetchCardsWithPaginationForQuery(query);

                logger.info("📊 Requête '{}' : {} cartes trouvées", query, result.size());

                if (result.size() > maxFound) {
                    maxFound = result.size();
                    bestQuery = query;
                    bestResult = new ArrayList<>(result);
                }

                // Si on trouve 300+ cartes, c'est probablement le bon résultat
                if (result.size() >= 300) {
                    logger.info("🎯 OBJECTIF ATTEINT avec '{}' : {} cartes ≥ 300", query, result.size());
                    break;
                }

                Thread.sleep(300); // Respecter les limites de l'API

            } catch (Exception e) {
                logger.error("❌ Erreur requête '{}' : {}", query, e.getMessage());
            }
        }

        if (maxFound >= 300) {
            logger.info("🎉 SUCCESS Final Fantasy : {} cartes avec requête '{}'", maxFound, bestQuery);
        } else {
            logger.warn("⚠️ Seulement {} cartes trouvées - Problème potentiel", maxFound);
        }

        return bestResult;
    }

    /**
     * NOUVELLE MÉTHODE: Pagination complète pour une requête spécifique
     */
    private List<MtgCard> fetchCardsWithPaginationForQuery(String query) throws Exception {
        List<MtgCard> allCards = new ArrayList<>();
        int page = 1;
        int maxPages = 25; // Plus de pages pour FIN

        while (page <= maxPages) {
            String url = String.format(
                    "https://api.scryfall.com/cards/search?q=%s&format=json&order=set&page=%d",
                    URLEncoder.encode(query, StandardCharsets.UTF_8), page
            );

            logger.debug("📄 Page {}/{} - URL: {}", page, maxPages, url);

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                logger.warn("⚠️ Réponse nulle page {} pour query '{}'", page, query);
                break;
            }

            JsonNode root = objectMapper.readTree(response);

            // Vérifier erreur API
            if (root.has("type") && "error".equals(root.get("type").asText())) {
                String errorCode = root.has("code") ? root.get("code").asText() : "unknown";

                if ("not_found".equals(errorCode) && !allCards.isEmpty()) {
                    logger.info("🏁 Fin normale pagination pour '{}' - {} cartes", query, allCards.size());
                    break;
                } else {
                    String errorMessage = root.has("details") ? root.get("details").asText() : "Erreur API";
                    throw new Exception("Erreur Scryfall: " + errorMessage);
                }
            }

            // Parser les cartes
            JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray()) {
                int cardsInPage = dataNode.size();

                for (JsonNode cardNode : dataNode) {
                    try {
                        MtgCard card = parseScryfallCard(cardNode);

                        // FILTRAGE IMPORTANT: S'assurer que c'est bien FIN
                        if (isValidFinCard(card)) {
                            allCards.add(card);
                        } else {
                            logger.debug("⚠️ Carte '{}' filtrée (pas FIN)", card.name());
                        }
                    } catch (Exception e) {
                        logger.warn("⚠️ Erreur parsing carte page {} : {}", page, e.getMessage());
                    }
                }

                logger.info("✅ Page {} : {} cartes ajoutées (Total: {})", page, cardsInPage, allCards.size());

                // Vérifier continuation
                boolean hasMore = root.has("has_more") && root.get("has_more").asBoolean();
                if (!hasMore) {
                    logger.info("🏁 Fin pagination normale page {} pour '{}'", page, query);
                    break;
                }
            } else {
                logger.warn("⚠️ Pas de données page {} pour '{}'", page, query);
                break;
            }

            page++;
            Thread.sleep(150); // Respecter les limites
        }

        logger.info("📋 Pagination terminée pour '{}': {} cartes sur {} pages", query, allCards.size(), page - 1);
        return allCards;
    }

    /**
     * FILTRAGE: Vérifier qu'une carte appartient bien à Final Fantasy
     */
    private boolean isValidFinCard(MtgCard card) {
        if (card == null) return false;

        // Vérifier le code d'extension
        if ("FIN".equalsIgnoreCase(card.set())) {
            return true;
        }

        // Vérifier le nom de l'extension
        if (card.setName() != null && card.setName().toLowerCase().contains("final fantasy")) {
            return true;
        }

        // Vérifier des mots-clés Final Fantasy dans le nom
        String name = card.name().toLowerCase();
        String text = card.text() != null ? card.text().toLowerCase() : "";

        String[] ffKeywords = {
                "cloud", "sephiroth", "terra", "lightning", "tifa", "aerith",
                "chocobo", "moogle", "bahamut", "shiva", "ifrit", "ramuh",
                "garland", "warrior of light", "cecil", "kain", "rydia"
        };

        for (String keyword : ffKeywords) {
            if (name.contains(keyword) || text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }


}