package com.pcagrad.magic.service;

import com.pcagrad.magic.entity.CardEntity;
import com.pcagrad.magic.entity.SetEntity;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.model.MtgSet;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MtgService {

    private static final Logger logger = LoggerFactory.getLogger(MtgService.class);

    private final WebClient webClient;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private SetRepository setRepository;

    @Autowired
    private CardPersistenceService persistenceService;

    @Value("${mtg.api.base-url:https://api.magicthegathering.io/v1}")
    private String baseUrl;

    public MtgService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Récupère toutes les extensions (hybride: DB + API)
     */
    public Mono<List<MtgSet>> getAllSets() {
        logger.debug("🔍 Récupération de toutes les extensions");

        // D'abord essayer depuis la base de données
        List<SetEntity> dbSets = setRepository.findAll();

        if (!dbSets.isEmpty()) {
            logger.debug("✅ {} extensions trouvées en base de données", dbSets.size());
            List<MtgSet> mtgSets = dbSets.stream()
                    .map(this::entityToModel)
                    .collect(Collectors.toList());
            return Mono.just(mtgSets);
        }

        // Sinon récupérer depuis l'API et sauvegarder
        logger.info("🌐 Récupération des extensions depuis l'API externe");
        return fetchSetsFromApi()
                .doOnNext(sets -> {
                    logger.info("💾 Sauvegarde de {} extensions en base", sets.size());
                    sets.forEach(persistenceService::saveOrUpdateSet);
                });
    }

    /**
     * Récupère la dernière extension
     */
    public Mono<MtgSet> getLatestSet() {
        logger.debug("🔍 Récupération de la dernière extension");

        // Chercher d'abord en base
        List<SetEntity> latestSets = setRepository.findLatestSets();
        if (!latestSets.isEmpty()) {
            SetEntity latest = latestSets.get(0);
            logger.debug("✅ Dernière extension trouvée en base : {} ({})", latest.getName(), latest.getCode());
            return Mono.just(entityToModel(latest));
        }

        // Sinon depuis l'API
        return getAllSets()
                .map(sets -> sets.stream()
                        .filter(set -> set.releaseDate() != null && !set.releaseDate().isEmpty())
                        .filter(set -> !"promo".equalsIgnoreCase(set.type()))
                        .filter(set -> !"token".equalsIgnoreCase(set.type()))
                        .max(Comparator.comparing(set -> {
                            try {
                                return LocalDate.parse(set.releaseDate());
                            } catch (Exception e) {
                                return LocalDate.MIN;
                            }
                        }))
                        .orElse(null));
    }

    /**
     * Récupère une extension par code (hybride)
     */
    public Mono<MtgSet> getSetByCode(String setCode) {
        logger.debug("🔍 Récupération de l'extension : {}", setCode);

        // D'abord chercher en base
        Optional<SetEntity> dbSet = setRepository.findById(setCode);
        if (dbSet.isPresent()) {
            logger.debug("✅ Extension {} trouvée en base", setCode);
            return Mono.just(entityToModel(dbSet.get()));
        }

        // Sinon récupérer toutes les extensions et filtrer
        return getAllSets()
                .map(sets -> sets.stream()
                        .filter(set -> setCode.equalsIgnoreCase(set.code()))
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Récupère les cartes d'une extension (hybride)
     */
    public Mono<List<MtgCard>> getCardsFromSet(String setCode) {
        logger.debug("🔍 Récupération des cartes pour l'extension : {}", setCode);

        // D'abord vérifier si les cartes sont en base et à jour
        if (persistenceService.isSetSynced(setCode)) {
            List<CardEntity> dbCards = persistenceService.getCardsFromDatabase(setCode);
            if (!dbCards.isEmpty()) {
                logger.debug("✅ {} cartes trouvées en base pour {}", dbCards.size(), setCode);
                List<MtgCard> mtgCards = dbCards.stream()
                        .map(this::entityToModel)
                        .collect(Collectors.toList());
                return Mono.just(mtgCards);
            }
        }

        // Sinon récupérer depuis l'API et sauvegarder
        logger.info("🌐 Récupération des cartes depuis l'API pour : {}", setCode);
        return fetchCardsFromApi(setCode)
                .doOnNext(cards -> {
                    if (!cards.isEmpty()) {
                        logger.info("💾 Sauvegarde de {} cartes en base pour {}", cards.size(), setCode);
                        persistenceService.saveCardsForSet(setCode, cards);
                    }
                });
    }

    /**
     * Récupère une extension avec ses cartes (hybride)
     */
    public Mono<MtgSet> getSetWithCards(String setCode) {
        return getSetByCode(setCode)
                .flatMap(set -> {
                    if (set == null) {
                        return Mono.just(null);
                    }
                    return getCardsFromSet(setCode)
                            .map(cards -> new MtgSet(
                                    set.code(),
                                    set.name(),
                                    set.type(),
                                    set.block(),
                                    set.releaseDate(),
                                    set.gathererCode(),
                                    set.magicCardsInfoCode(),
                                    set.border(),
                                    set.onlineOnly(),
                                    cards
                            ));
                });
    }

    /**
     * Récupère la dernière extension avec ses cartes
     */
    public Mono<MtgSet> getLatestSetWithCards() {
        return getLatestSet()
                .flatMap(latestSet -> {
                    if (latestSet == null) {
                        return Mono.just(null);
                    }
                    return getCardsFromSet(latestSet.code())
                            .map(cards -> new MtgSet(
                                    latestSet.code(),
                                    latestSet.name(),
                                    latestSet.type(),
                                    latestSet.block(),
                                    latestSet.releaseDate(),
                                    latestSet.gathererCode(),
                                    latestSet.magicCardsInfoCode(),
                                    latestSet.border(),
                                    latestSet.onlineOnly(),
                                    cards
                            ));
                });
    }

    /**
     * Force la synchronisation d'une extension depuis l'API
     */
    public Mono<MtgSet> forceSyncSet(String setCode) {
        logger.info("🔄 Synchronisation forcée de l'extension : {}", setCode);

        return fetchCardsFromApi(setCode)
                .flatMap(cards -> {
                    if (cards.isEmpty()) {
                        logger.warn("⚠️ Aucune carte trouvée pour l'extension {}", setCode);
                        return getSetByCode(setCode);
                    }

                    // Sauvegarder les cartes
                    persistenceService.saveCardsForSet(setCode, cards);

                    // Retourner l'extension avec les cartes
                    return getSetByCode(setCode)
                            .map(set -> new MtgSet(
                                    set.code(),
                                    set.name(),
                                    set.type(),
                                    set.block(),
                                    set.releaseDate(),
                                    set.gathererCode(),
                                    set.magicCardsInfoCode(),
                                    set.border(),
                                    set.onlineOnly(),
                                    cards
                            ));
                });
    }

    // ========== MÉTHODES PRIVÉES POUR L'API EXTERNE ==========

    private Mono<List<MtgSet>> fetchSetsFromApi() {
        return webClient.get()
                .uri(baseUrl + "/sets")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> setsList = (List<Map<String, Object>>) response.get("sets");
                    return setsList.stream()
                            .map(this::mapToMtgSet)
                            .collect(Collectors.toList());
                });
    }

    private Mono<List<MtgCard>> fetchCardsFromApi(String setCode) {
        String url = baseUrl + "/cards?set=" + setCode + "&pageSize=500";
        logger.debug("🌐 URL appelée: {}", url);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(body);
                        com.fasterxml.jackson.databind.JsonNode cardsNode = root.get("cards");

                        if (cardsNode == null || !cardsNode.isArray()) {
                            logger.warn("⚠️ Pas de tableau 'cards' dans la réponse pour {}", setCode);
                            return Collections.<MtgCard>emptyList();
                        }

                        List<MtgCard> cards = new ArrayList<>();
                        for (com.fasterxml.jackson.databind.JsonNode cardNode : cardsNode) {
                            try {
                                MtgCard card = mapJsonToMtgCard(cardNode, setCode);
                                cards.add(card);
                            } catch (Exception e) {
                                logger.warn("⚠️ Erreur parsing carte: {}", e.getMessage());
                            }
                        }

                        logger.info("✅ {} cartes parsées avec succès pour {}", cards.size(), setCode);
                        return cards;

                    } catch (Exception e) {
                        logger.error("❌ Erreur parsing JSON pour {} : {}", setCode, e.getMessage());
                        return Collections.<MtgCard>emptyList();
                    }
                });
    }

    // ========== MÉTHODES DE MAPPING ==========

    private MtgCard mapJsonToMtgCard(com.fasterxml.jackson.databind.JsonNode cardNode, String setCode) {
        String imageUrl = cardNode.get("imageUrl") != null ? cardNode.get("imageUrl").asText() : null;
        if (imageUrl == null || imageUrl.isEmpty()) {
            String multiverseId = cardNode.get("multiverseid") != null ? cardNode.get("multiverseid").asText() : null;
            String cardNumber = cardNode.get("number") != null ? cardNode.get("number").asText() : null;
            imageUrl = generateImageUrl(multiverseId, setCode, cardNumber);
        }

        return new MtgCard(
                cardNode.get("id") != null ? cardNode.get("id").asText() : "",
                cardNode.get("name") != null ? cardNode.get("name").asText() : "Carte inconnue",
                cardNode.get("manaCost") != null ? cardNode.get("manaCost").asText() : null,
                cardNode.get("cmc") != null ? cardNode.get("cmc").asInt() : null,
                parseStringArray(cardNode.get("colors")),
                parseStringArray(cardNode.get("colorIdentity")),
                cardNode.get("type") != null ? cardNode.get("type").asText() : "Unknown",
                parseStringArray(cardNode.get("supertypes")),
                parseStringArray(cardNode.get("types")),
                parseStringArray(cardNode.get("subtypes")),
                cardNode.get("rarity") != null ? cardNode.get("rarity").asText() : "Unknown",
                cardNode.get("set") != null ? cardNode.get("set").asText() : setCode,
                cardNode.get("setName") != null ? cardNode.get("setName").asText() : null,
                cardNode.get("text") != null ? cardNode.get("text").asText() : null,
                cardNode.get("artist") != null ? cardNode.get("artist").asText() : null,
                cardNode.get("number") != null ? cardNode.get("number").asText() : null,
                cardNode.get("power") != null ? cardNode.get("power").asText() : null,
                cardNode.get("toughness") != null ? cardNode.get("toughness").asText() : null,
                cardNode.get("layout") != null ? cardNode.get("layout").asText() : null,
                cardNode.get("multiverseid") != null ? cardNode.get("multiverseid").asInt() : null,
                imageUrl
        );
    }

    private List<String> parseStringArray(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isArray()) return null;

        List<String> result = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode item : node) {
            result.add(item.asText());
        }
        return result.isEmpty() ? null : result;
    }

    private String generateImageUrl(String multiverseId, String setCode, String cardNumber) {
        if (multiverseId != null && !multiverseId.equals("null")) {
            return "https://gatherer.wizards.com/Handlers/Image.ashx?multiverseid=" + multiverseId + "&type=card";
        }

        if (setCode != null && cardNumber != null) {
            return "https://api.scryfall.com/cards/" + setCode.toLowerCase() + "/" + cardNumber + "?format=image";
        }

        return "https://via.placeholder.com/223x311/0066cc/ffffff?text=" + setCode;
    }

    private MtgSet mapToMtgSet(Map<String, Object> setMap) {
        return new MtgSet(
                (String) setMap.get("code"),
                (String) setMap.get("name"),
                (String) setMap.get("type"),
                (String) setMap.get("block"),
                (String) setMap.get("releaseDate"),
                (String) setMap.get("gathererCode"),
                (String) setMap.get("magicCardsInfoCode"),
                (String) setMap.get("border"),
                Boolean.TRUE.equals(setMap.get("onlineOnly")),
                null
        );
    }

    // ========== CONVERSION ENTITY <-> MODEL ==========

    private MtgSet entityToModel(SetEntity entity) {
        return new MtgSet(
                entity.getCode(),
                entity.getName(),
                entity.getType(),
                entity.getBlock(),
                entity.getReleaseDate() != null ? entity.getReleaseDate().toString() : null,
                entity.getGathererCode(),
                entity.getMagicCardsInfoCode(),
                entity.getBorder(),
                entity.getOnlineOnly(),
                null
        );
    }

    private MtgCard entityToModel(CardEntity entity) {
        return new MtgCard(
                entity.getId(),
                entity.getName(),
                entity.getManaCost(),
                entity.getCmc(),
                entity.getColors(),
                entity.getColorIdentity(),
                entity.getType(),
                entity.getSupertypes(),
                entity.getTypes(),
                entity.getSubtypes(),
                entity.getRarity(),
                entity.getSetCode(),
                entity.getSetName(),
                entity.getText(),
                entity.getArtist(),
                entity.getNumber(),
                entity.getPower(),
                entity.getToughness(),
                entity.getLayout(),
                entity.getMultiverseid(),
                entity.getLocalImagePath() != null ? "/api/images/" + entity.getId() : entity.getOriginalImageUrl()
        );
    }
}