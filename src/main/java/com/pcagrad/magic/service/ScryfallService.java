package com.pcagrad.magic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcagrad.magic.model.MtgCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.Duration;

@Service
public class ScryfallService {

    private static final Logger logger = LoggerFactory.getLogger(ScryfallService.class);
    private final WebClient webClient;

    public ScryfallService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.scryfall.com")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // Augmenté pour pagination
                .build();
    }

    /**
     * Récupère TOUTES les cartes d'une extension depuis Scryfall avec pagination
     */
    public Mono<List<MtgCard>> getCardsFromScryfall(String setCode) {
        logger.info("🔮 Récupération COMPLÈTE des cartes de {} depuis Scryfall", setCode);

        return getAllCardsWithPagination(setCode.toLowerCase())
                .doOnNext(cards -> logger.info("✅ {} cartes TOTALES récupérées depuis Scryfall pour {}",
                        cards.size(), setCode))
                .doOnError(error -> logger.error("❌ Erreur Scryfall pour {} : {}", setCode, error.getMessage()))
                .onErrorReturn(Collections.emptyList());
    }

    /**
     * Récupère toutes les cartes avec pagination automatique
     */
    private Mono<List<MtgCard>> getAllCardsWithPagination(String setCode) {
        List<MtgCard> allCards = new ArrayList<>();

        return fetchCardsPage(setCode, null, allCards, 1)
                .map(cards -> {
                    logger.info("🎯 Final Fantasy récupération terminée : {} cartes au total", cards.size());
                    return cards;
                });
    }

    /**
     * Récupère une page de cartes et continue récursivement
     */
    private Mono<List<MtgCard>> fetchCardsPage(String setCode, String nextPageUrl,
                                               List<MtgCard> allCards, int pageNumber) {

        String url = nextPageUrl != null ? nextPageUrl :
                String.format("/cards/search?q=set:%s&format=json&order=name", setCode);

        logger.info("📄 Récupération page {} pour {} - URL: {}", pageNumber, setCode, url);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .flatMap(response -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(response);

                        // Vérifier s'il y a une erreur
                        if (root.has("type") && "error".equals(root.get("type").asText())) {
                            String errorMessage = root.has("details") ? root.get("details").asText() : "Erreur inconnue";
                            logger.error("❌ Erreur Scryfall API: {}", errorMessage);
                            return Mono.just(allCards);
                        }

                        JsonNode dataNode = root.get("data");
                        if (dataNode != null && dataNode.isArray()) {
                            List<MtgCard> pageCards = parseCardsFromPage(dataNode, setCode);
                            allCards.addAll(pageCards);

                            logger.info("✅ Page {} : {} cartes ajoutées (Total: {})",
                                    pageNumber, pageCards.size(), allCards.size());
                        }

                        // Vérifier s'il y a une page suivante
                        if (root.has("has_more") && root.get("has_more").asBoolean()) {
                            String nextUrl = root.get("next_page").asText();
                            logger.info("🔄 Page suivante trouvée, récupération page {}...", pageNumber + 1);

                            // Délai pour respecter les limites de l'API Scryfall
                            return Mono.delay(Duration.ofMillis(100))
                                    .then(fetchCardsPage(setCode, nextUrl, allCards, pageNumber + 1));
                        } else {
                            logger.info("🏁 Dernière page atteinte pour {} - Total final: {} cartes",
                                    setCode, allCards.size());
                            return Mono.just(allCards);
                        }

                    } catch (Exception e) {
                        logger.error("❌ Erreur parsing page {} pour {} : {}", pageNumber, setCode, e.getMessage());
                        return Mono.just(allCards);
                    }
                })
                .onErrorResume(error -> {
                    logger.error("❌ Erreur réseau page {} pour {} : {}", pageNumber, setCode, error.getMessage());
                    return Mono.just(allCards);
                });
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
                logger.warn("⚠️ Erreur parsing carte dans page: {}", e.getMessage());
            }
        }

        return cards;
    }

    /**
     * Vérifie si une extension existe sur Scryfall ET compte le nombre total de cartes
     */
    public Mono<SetInfo> getSetInfo(String setCode) {
        return webClient.get()
                .uri("/sets/{code}", setCode.toLowerCase())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(response);

                        String name = root.has("name") ? root.get("name").asText() : setCode;
                        int cardCount = root.has("card_count") ? root.get("card_count").asInt() : 0;
                        String releaseDate = root.has("released_at") ? root.get("released_at").asText() : null;

                        logger.info("🎯 Extension {} trouvée : {} - {} cartes attendues",
                                setCode, name, cardCount);

                        return new SetInfo(true, name, cardCount, releaseDate);

                    } catch (Exception e) {
                        logger.warn("⚠️ Erreur parsing info extension {}: {}", setCode, e.getMessage());
                        return new SetInfo(false, setCode, 0, null);
                    }
                })
                .onErrorReturn(new SetInfo(false, setCode, 0, null));
    }

    /**
     * Vérifie si une extension existe (méthode simplifiée)
     */
    public Mono<Boolean> setExistsOnScryfall(String setCode) {
        return getSetInfo(setCode).map(SetInfo::exists);
    }

    /**
     * Parse une carte Scryfall vers MtgCard
     */
    private MtgCard parseScryfallCard(JsonNode cardNode) {
        String imageUrl = null;
        if (cardNode.has("image_uris") && cardNode.get("image_uris").has("normal")) {
            imageUrl = cardNode.get("image_uris").get("normal").asText();
        } else if (cardNode.has("card_faces") && cardNode.get("card_faces").isArray()) {
            // Pour les cartes double-face
            JsonNode firstFace = cardNode.get("card_faces").get(0);
            if (firstFace.has("image_uris") && firstFace.get("image_uris").has("normal")) {
                imageUrl = firstFace.get("image_uris").get("normal").asText();
            }
        }

        String manaCost = null;
        if (cardNode.has("mana_cost")) {
            manaCost = cardNode.get("mana_cost").asText();
        }

        // Convertir la rareté Scryfall vers format MTG API
        String rarity = "Common";
        if (cardNode.has("rarity")) {
            String scryfallRarity = cardNode.get("rarity").asText();
            switch (scryfallRarity) {
                case "mythic" -> rarity = "Mythic Rare";
                case "rare" -> rarity = "Rare";
                case "uncommon" -> rarity = "Uncommon";
                case "common" -> rarity = "Common";
                case "special" -> rarity = "Special";
                default -> rarity = scryfallRarity;
            }
        }

        return new MtgCard(
                cardNode.get("id").asText(),
                cardNode.get("name").asText(),
                manaCost,
                cardNode.has("cmc") ? cardNode.get("cmc").asInt() : null,
                parseColors(cardNode.get("colors")),
                parseColors(cardNode.get("color_identity")),
                cardNode.get("type_line").asText(),
                parseSupertypes(cardNode.get("type_line")),
                parseTypes(cardNode.get("type_line")),
                parseSubtypes(cardNode.get("type_line")),
                rarity,
                cardNode.get("set").asText().toUpperCase(),
                cardNode.get("set_name").asText(),
                cardNode.has("oracle_text") ? cardNode.get("oracle_text").asText() : null,
                cardNode.has("artist") ? cardNode.get("artist").asText() : null,
                cardNode.has("collector_number") ? cardNode.get("collector_number").asText() : null,
                cardNode.has("power") ? cardNode.get("power").asText() : null,
                cardNode.has("toughness") ? cardNode.get("toughness").asText() : null,
                cardNode.has("layout") ? cardNode.get("layout").asText() : null,
                null, // multiverseid pas disponible sur Scryfall
                imageUrl
        );
    }

    // Méthodes de parsing inchangées...
    private List<String> parseColors(JsonNode colorsNode) {
        if (colorsNode == null || !colorsNode.isArray()) return null;

        List<String> colors = new ArrayList<>();
        for (JsonNode colorNode : colorsNode) {
            colors.add(colorNode.asText());
        }
        return colors.isEmpty() ? null : colors;
    }

    private List<String> parseSupertypes(JsonNode typeLineNode) {
        if (typeLineNode == null) return null;

        String typeLine = typeLineNode.asText();
        List<String> supertypes = new ArrayList<>();

        String[] supertypeKeywords = {"Legendary", "Basic", "Snow", "World"};
        for (String keyword : supertypeKeywords) {
            if (typeLine.contains(keyword)) {
                supertypes.add(keyword);
            }
        }

        return supertypes.isEmpty() ? null : supertypes;
    }

    private List<String> parseTypes(JsonNode typeLineNode) {
        if (typeLineNode == null) return null;

        String typeLine = typeLineNode.asText();
        List<String> types = new ArrayList<>();

        String[] parts = typeLine.split(" — ");
        if (parts.length > 0) {
            String mainTypes = parts[0].trim();
            String[] typeWords = mainTypes.split(" ");

            for (String word : typeWords) {
                if (!word.equals("Legendary") && !word.equals("Basic") && !word.equals("Snow")) {
                    types.add(word);
                }
            }
        }

        return types.isEmpty() ? null : types;
    }

    private List<String> parseSubtypes(JsonNode typeLineNode) {
        if (typeLineNode == null) return null;

        String typeLine = typeLineNode.asText();
        if (!typeLine.contains(" — ")) return null;

        String[] parts = typeLine.split(" — ");
        if (parts.length < 2) return null;

        String subtypesStr = parts[1].trim();
        List<String> subtypes = List.of(subtypesStr.split(" "));

        return subtypes.isEmpty() ? null : subtypes;
    }

    /**
     * Record pour les informations d'extension
     */
    public record SetInfo(boolean exists, String name, int expectedCardCount, String releaseDate) {}
}