package com.pcagrad.magic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcagrad.magic.dto.ApiResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
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

    // AJOUTER cette injection de dépendance en haut de la classe MtgService
    @Autowired
    private ScryfallService scryfallService;

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
     * Récupère la dernière extension - LOGIQUE CORRIGÉE POUR PRIORISER LES EXTENSIONS AVEC CARTES
     */
    public Mono<MtgSet> getLatestSet() {
        logger.debug("🔍 Récupération de la dernière extension");

        // Chercher d'abord en base avec priorité aux extensions avec cartes
        List<SetEntity> allSets = setRepository.findLatestSets();

        if (!allSets.isEmpty()) {
            // Filtrer et trier avec priorité aux extensions avec cartes
            Optional<SetEntity> latest = allSets.stream()
                    .filter(set -> set.getReleaseDate() != null)
                    .filter(set -> !isExcludedSetType(set.getType()))
                    .filter(set -> set.getReleaseDate().isBefore(LocalDate.now().plusDays(30)))
                    .sorted((a, b) -> {
                        // D'abord comparer par présence de cartes
                        boolean aHasCards = a.getCardsSynced() && (a.getCardsCount() != null && a.getCardsCount() > 0);
                        boolean bHasCards = b.getCardsSynced() && (b.getCardsCount() != null && b.getCardsCount() > 0);

                        if (aHasCards && !bHasCards) return -1;
                        if (!aHasCards && bHasCards) return 1;

                        // Si même statut de cartes, trier par date
                        return b.getReleaseDate().compareTo(a.getReleaseDate());
                    })
                    .findFirst();

            if (latest.isPresent()) {
                SetEntity latestSet = latest.get();
                logger.info("✅ Dernière extension trouvée en base : {} ({}) - {} cartes",
                        latestSet.getName(), latestSet.getCode(), latestSet.getCardsCount());
                return Mono.just(entityToModel(latestSet));
            }
        }

        // Fallback : chercher explicitement BLB qui fonctionne
        Optional<SetEntity> blbFallback = setRepository.findByCode("BLB");
        if (blbFallback.isPresent() && blbFallback.get().getCardsSynced()) {
            logger.info("🎯 Fallback vers BLB (Bloomburrow) qui a des cartes synchronisées");
            return Mono.just(entityToModel(blbFallback.get()));
        }

        // Sinon depuis l'API avec logique améliorée
        return getAllSets()
                .map(sets -> {
                    Optional<MtgSet> latest = sets.stream()
                            .filter(set -> set.releaseDate() != null && !set.releaseDate().isEmpty())
                            .filter(set -> !isExcludedSetType(set.type()))
                            .filter(set -> isValidReleaseDate(set.releaseDate()))
                            .max(Comparator.comparing(set -> parseReleaseDate(set.releaseDate())));

                    if (latest.isPresent()) {
                        logger.info("✅ Dernière extension trouvée depuis API : {} ({})",
                                latest.get().name(), latest.get().code());
                        return latest.get();
                    } else {
                        // Fallback absolu vers BLB
                        logger.warn("⚠️ Aucune extension récente trouvée, fallback absolu vers BLB");
                        return sets.stream()
                                .filter(set -> "BLB".equals(set.code()))
                                .findFirst()
                                .orElse(null);
                    }
                });
    }
    /**
     * Détermine si un type d'extension doit être exclu
     */
    private boolean isExcludedSetType(String type) {
        if (type == null) return false;

        String lowerType = type.toLowerCase();
        return lowerType.contains("promo") ||
                lowerType.contains("token") ||
                lowerType.contains("memorabilia") ||
                lowerType.contains("vanguard") ||
                lowerType.contains("planechase") ||
                lowerType.contains("archenemy");
    }

    /**
     * Valide si une date de sortie est acceptable
     */
    private boolean isValidReleaseDate(String releaseDateStr) {
        try {
            LocalDate releaseDate = parseReleaseDate(releaseDateStr);
            LocalDate now = LocalDate.now();
            LocalDate twoYearsAgo = now.minusYears(2);
            LocalDate oneMonthFuture = now.plusMonths(1);

            // Accepter les extensions des 2 dernières années et jusqu'à 1 mois dans le futur
            return releaseDate.isAfter(twoYearsAgo) && releaseDate.isBefore(oneMonthFuture);
        } catch (Exception e) {
            logger.warn("⚠️ Date invalide : {}", releaseDateStr);
            return false;
        }
    }

    /**
     * Parse une date de sortie avec gestion d'erreurs
     */
    private LocalDate parseReleaseDate(String releaseDateStr) {
        try {
            return LocalDate.parse(releaseDateStr);
        } catch (Exception e) {
            // Essayer d'autres formats si nécessaire
            throw new IllegalArgumentException("Format de date invalide : " + releaseDateStr);
        }
    }

    /**
     * Récupère une extension par code (hybride)
     */
    public Mono<MtgSet> getSetByCode(String setCode) {
        logger.debug("🔍 Récupération de l'extension : {}", setCode);

        // D'abord chercher en base
        Optional<SetEntity> dbSet = setRepository.findByCode(setCode);
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
                    } else {
                        logger.warn("⚠️ Aucune carte trouvée pour l'extension {}", setCode);
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
                        logger.warn("⚠️ Extension {} non trouvée", setCode);
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
     * Récupère la dernière extension avec logique intelligente
     * 1. Cherche la vraie dernière extension
     * 2. Si pas de cartes, essaie de les synchroniser
     * 3. Si échec, prend la dernière extension AVEC cartes
     */
    public Mono<MtgSet> getLatestSetWithCards() {
        return getLatestSet()
                .flatMap(latestSet -> {
                    if (latestSet == null) {
                        logger.warn("⚠️ Aucune dernière extension trouvée");
                        return Mono.just(null);
                    }

                    logger.info("🎯 Dernière extension détectée : {} ({})",
                            latestSet.name(), latestSet.code());

                    // Vérifier si elle a déjà des cartes
                    return getCardsFromSet(latestSet.code())
                            .flatMap(cards -> {
                                if (!cards.isEmpty()) {
                                    logger.info("✅ {} cartes trouvées pour {}", cards.size(), latestSet.code());
                                    return Mono.just(new MtgSet(
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
                                } else {
                                    // Pas de cartes pour la dernière extension, chercher la dernière AVEC cartes
                                    logger.warn("⚠️ Extension {} n'a pas de cartes, recherche d'une extension avec cartes...",
                                            latestSet.code());

                                    return getLatestSetWithSyncedCards()
                                            .flatMap(fallbackSet -> {
                                                if (fallbackSet != null) {
                                                    logger.info("🔄 Fallback vers {} qui a des cartes", fallbackSet.code());
                                                    return getCardsFromSet(fallbackSet.code())
                                                            .map(fallbackCards -> new MtgSet(
                                                                    fallbackSet.code(),
                                                                    fallbackSet.name(),
                                                                    fallbackSet.type(),
                                                                    fallbackSet.block(),
                                                                    fallbackSet.releaseDate(),
                                                                    fallbackSet.gathererCode(),
                                                                    fallbackSet.magicCardsInfoCode(),
                                                                    fallbackSet.border(),
                                                                    fallbackSet.onlineOnly(),
                                                                    fallbackCards
                                                            ));
                                                } else {
                                                    return Mono.just(null);
                                                }
                                            });
                                }
                            });
                });
    }

    /**
     * Trouve la dernière extension qui a des cartes synchronisées
     */
    private Mono<MtgSet> getLatestSetWithSyncedCards() {
        List<SetEntity> setsWithCards = setRepository.findLatestSets().stream()
                .filter(set -> set.getReleaseDate() != null)
                .filter(set -> !isExcludedSetType(set.getType()))
                .filter(set -> set.getCardsSynced() && set.getCardsCount() != null && set.getCardsCount() > 0)
                .filter(set -> set.getReleaseDate().isBefore(LocalDate.now().plusDays(1))) // Pas trop dans le futur
                .sorted((a, b) -> b.getReleaseDate().compareTo(a.getReleaseDate())) // Plus récent en premier
                .collect(Collectors.toList());

        if (!setsWithCards.isEmpty()) {
            SetEntity latestWithCards = setsWithCards.get(0);
            logger.info("📦 Extension avec cartes la plus récente : {} ({}) - {} cartes",
                    latestWithCards.getName(), latestWithCards.getCode(), latestWithCards.getCardsCount());
            return Mono.just(entityToModel(latestWithCards));
        }

        return Mono.just(null);
    }

    /**
     * Endpoint pour vérifier le statut de synchronisation de FIN
     */
    @GetMapping("/debug/fin-status")
    public ResponseEntity<ApiResponse<Object>> checkFINStatus() {
        try {
            Optional<SetEntity> finSet = setRepository.findByCode("FIN");
            if (finSet.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            SetEntity fin = finSet.get();
            long cardCount = cardRepository.countBySetCode("FIN");

            Map<String, Object> status = new HashMap<>();
            status.put("code", fin.getCode());
            status.put("name", fin.getName());
            status.put("releaseDate", fin.getReleaseDate());
            status.put("cardsSynced", fin.getCardsSynced());
            status.put("cardsCount", cardCount);
            status.put("type", fin.getType());

            // Vérifier si c'est aujourd'hui
            boolean isToday = fin.getReleaseDate().equals(LocalDate.now());
            status.put("isReleasedToday", isToday);

            String message;
            if (isToday && cardCount == 0) {
                message = "Extension sortie aujourd'hui mais cartes pas encore synchronisées";
            } else if (cardCount > 0) {
                message = "Extension avec " + cardCount + " cartes synchronisées";
            } else {
                message = "Extension sans cartes synchronisées";
            }

            return ResponseEntity.ok(ApiResponse.success(status, message));

        } catch (Exception e) {
            logger.error("❌ Erreur vérification FIN : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
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
                })
                .doOnError(error -> logger.error("❌ Erreur lors de la récupération des extensions : {}", error.getMessage()));
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
                })
                .doOnError(error -> logger.error("❌ Erreur lors de la récupération des cartes pour {} : {}", setCode, error.getMessage()));
    }

    // ========== MÉTHODES DE MAPPING (inchangées) ==========

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

    // AJOUTER cette méthode dans MtgService.java

    /**
     * Extensions qui n'existent que sur Scryfall (Universes Beyond)
     */
    private static final Set<String> SCRYFALL_ONLY_SETS = Set.of(
            "FIN", "FIC", "FCA", "TFIN", "TFIC", "RFIN", // Final Fantasy
            "WHO", "TWD", "SLD", "UNF" // Autres Universes Beyond
    );

    /**
     * MÉTHODE CORRIGÉE - Récupère les cartes d'une extension
     * Utilise Scryfall pour les sets Universes Beyond
     */
    public Mono<List<MtgCard>> getCardsFromSet(String setCode) {
        logger.info("🔍 Récupération des cartes pour l'extension: {}", setCode);

        // Vérifier d'abord en base de données
        List<CardEntity> cardsInDb = cardRepository.findBySetCodeOrderByNameAsc(setCode);
        if (!cardsInDb.isEmpty()) {
            logger.info("✅ {} cartes trouvées en base pour {}", cardsInDb.size(), setCode);
            List<MtgCard> cards = cardsInDb.stream()
                    .map(this::entityToModel)
                    .collect(Collectors.toList());
            return Mono.just(cards);
        }

        // Si le set n'existe que sur Scryfall, utiliser ScryfallService
        if (SCRYFALL_ONLY_SETS.contains(setCode.toUpperCase())) {
            logger.info("🔮 Extension {} détectée comme Universes Beyond - utilisation de Scryfall", setCode);
            return scryfallService.getCardsFromScryfall(setCode)
                    .doOnNext(cards -> {
                        if (!cards.isEmpty()) {
                            logger.info("✅ {} cartes récupérées depuis Scryfall pour {}", cards.size(), setCode);
                            // Sauvegarder en arrière-plan
                            CompletableFuture.runAsync(() -> {
                                try {
                                    persistenceService.saveCardsForSet(setCode, cards);
                                } catch (Exception e) {
                                    logger.error("❌ Erreur sauvegarde {} : {}", setCode, e.getMessage());
                                }
                            });
                        } else {
                            logger.warn("⚠️ Aucune carte Scryfall trouvée pour {}", setCode);
                        }
                    });
        }

        // Sinon, utiliser l'API MTG classique
        logger.info("🌐 Récupération depuis l'API MTG officielle pour : {}", setCode);
        return fetchCardsFromMtgApi(setCode);
    }

    /**
     * MÉTHODE CORRIGÉE - Récupère la dernière extension avec gestion Scryfall
     */
    public Mono<MtgSet> getLatestSetWithCards() {
        logger.debug("🔍 Récupération de la dernière extension avec cartes");

        // Chercher d'abord en base avec priorité aux extensions avec cartes
        List<SetEntity> recentSets = setRepository.findLatestSets();

        if (!recentSets.isEmpty()) {
            // Prendre la première extension qui a des cartes
            Optional<SetEntity> setWithCards = recentSets.stream()
                    .filter(set -> {
                        long cardCount = cardRepository.countBySetCode(set.getCode());
                        return cardCount > 0;
                    })
                    .findFirst();

            if (setWithCards.isPresent()) {
                SetEntity setEntity = setWithCards.get();
                logger.info("✅ Dernière extension trouvée en base : {} ({}) - {} cartes",
                        setEntity.getName(), setEntity.getCode(),
                        cardRepository.countBySetCode(setEntity.getCode()));

                // Récupérer les cartes
                return getCardsFromSet(setEntity.getCode())
                        .map(cards -> {
                            MtgSet mtgSet = entityToModel(setEntity);
                            return new MtgSet(
                                    mtgSet.code(),
                                    mtgSet.name(),
                                    mtgSet.type(),
                                    mtgSet.block(),
                                    mtgSet.releaseDate(),
                                    mtgSet.gathererCode(),
                                    mtgSet.magicCardsInfoCode(),
                                    mtgSet.border(),
                                    mtgSet.onlineOnly(),
                                    cards // Ajouter les cartes
                            );
                        });
            }
        }

        // Si aucune extension en base, récupérer depuis les APIs
        logger.info("🌐 Aucune extension récente en base, récupération depuis les APIs");
        return fetchLatestSetFromApis();
    }

    /**
     * Récupère les cartes depuis l'API MTG classique
     */
    private Mono<List<MtgCard>> fetchCardsFromMtgApi(String setCode) {
        String url = baseUrl + "/cards?set=" + setCode + "&pageSize=500";

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .map(response -> {
                    try {
                        JsonNode root = new ObjectMapper().readTree(response);
                        JsonNode cardsArray = root.get("cards");

                        if (cardsArray != null && cardsArray.isArray()) {
                            List<MtgCard> cards = new ArrayList<>();
                            for (JsonNode cardNode : cardsArray) {
                                try {
                                    MtgCard card = parseCardFromMtgApi(cardNode);
                                    if (card != null) {
                                        cards.add(card);
                                    }
                                } catch (Exception e) {
                                    logger.warn("⚠️ Erreur parsing carte MTG API: {}", e.getMessage());
                                }
                            }

                            logger.info("✅ {} cartes parsées depuis MTG API pour {}", cards.size(), setCode);
                            return cards;
                        }

                        logger.warn("⚠️ Aucune carte trouvée dans la réponse MTG API pour {}", setCode);
                        return Collections.<MtgCard>emptyList();

                    } catch (Exception e) {
                        logger.error("❌ Erreur parsing réponse MTG API pour {} : {}", setCode, e.getMessage());
                        return Collections.<MtgCard>emptyList();
                    }
                })
                .onErrorReturn(Collections.emptyList());
    }



}