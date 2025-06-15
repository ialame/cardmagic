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
import java.util.concurrent.CompletableFuture;
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
     * Récupère toutes les extensions (SANS sauvegarde automatique)
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

        // Sinon récupérer depuis l'API SANS SAUVEGARDER
        logger.info("🌐 Récupération des extensions depuis l'API externe (sans sauvegarde)");
        return fetchSetsFromApi()
                .doOnNext(sets -> {
                    logger.info("📥 {} extensions récupérées depuis l'API (non sauvegardées)", sets.size());
                    // SUPPRIMÉ : sets.forEach(persistenceService::saveOrUpdateSet);
                });
    }

    // CORRECTION dans MtgService.java

    /**
     * Extensions qui n'existent que sur Scryfall (Universes Beyond) - MISE À JOUR
     */
    private static final Set<String> SCRYFALL_ONLY_SETS = Set.of(
            "FIN", "FIC", "FCA", "TFIN", "TFIC", "RFIN", // Final Fantasy
            "WHO", "TWD", "SLD", "UNF", "UGL", "UNH",   // Autres Universes Beyond
            "LTR", "40K", "CLB"                          // LOTR, Warhammer, D&D
    );

    /**
     * Extensions prioritaires pour "dernière extension" - NOUVEAU
     */
    private static final Map<String, Integer> SET_PRIORITY = Map.of(
            "FIN", 100,  // Final Fantasy = priorité maximale
            "BLB", 90,   // Bloomburrow
            "MH3", 85,   // Modern Horizons 3
            "OTJ", 80,   // Outlaws of Thunder Junction
            "MKM", 75    // Murders at Karlov Manor
    );

    // CORRECTION MAJEURE dans MtgService.java

    /**
     * Récupère la dernière extension - LOGIQUE COMPLÈTEMENT REVUE
     */
    public Mono<MtgSet> getLatestSet() {
        logger.debug("🔍 Récupération de la dernière extension avec logique prioritaire");

        return Mono.fromCallable(() -> {
            // 1. PRIORITÉ ABSOLUE : Final Fantasy s'il a des cartes
            Optional<SetEntity> finSet = setRepository.findByCode("FIN");
            if (finSet.isPresent()) {
                long finCardCount = cardRepository.countBySetCode("FIN");
                if (finCardCount > 0) {
                    logger.info("🎮 Final Fantasy sélectionné comme dernière extension ({} cartes)", finCardCount);
                    return entityToModel(finSet.get());
                } else {
                    logger.info("🎮 Final Fantasy existe mais sans cartes ({} cartes)", finCardCount);
                }
            } else {
                logger.warn("⚠️ Final Fantasy n'existe pas en base - problème d'initialisation");
            }

            // 2. Chercher parmi les autres extensions récentes avec cartes
            List<SetEntity> candidateSets = setRepository.findLatestSets();

            Optional<SetEntity> bestSet = candidateSets.stream()
                    .filter(set -> set.getReleaseDate() != null)
                    .filter(set -> !isExcludedSetType(set.getType()))
                    .filter(set -> {
                        // Seulement les extensions qui ont des cartes
                        long cardCount = cardRepository.countBySetCode(set.getCode());
                        return cardCount > 0;
                    })
                    .filter(set -> {
                        // Date dans une plage raisonnable
                        LocalDate releaseDate = set.getReleaseDate();
                        LocalDate now = LocalDate.now();
                        return releaseDate.isAfter(now.minusYears(2)) &&
                                releaseDate.isBefore(now.plusMonths(6));
                    })
                    .sorted((a, b) -> {
                        // Trier par priorité puis par date
                        int priorityA = SET_PRIORITY.getOrDefault(a.getCode(), 0);
                        int priorityB = SET_PRIORITY.getOrDefault(b.getCode(), 0);

                        if (priorityA != priorityB) {
                            return Integer.compare(priorityB, priorityA);
                        }

                        return b.getReleaseDate().compareTo(a.getReleaseDate());
                    })
                    .findFirst();

            if (bestSet.isPresent()) {
                SetEntity set = bestSet.get();
                long cardCount = cardRepository.countBySetCode(set.getCode());
                logger.info("✅ Extension sélectionnée : {} ({}) - {} cartes",
                        set.getName(), set.getCode(), cardCount);
                return entityToModel(set);
            }

            // 3. FALLBACK : Retourner FIN même sans cartes s'il existe
            if (finSet.isPresent()) {
                logger.info("🎮 Fallback vers Final Fantasy (même sans cartes)");
                return entityToModel(finSet.get());
            }

            // 4. FALLBACK ULTIME : BLB ou première extension trouvée
            Optional<SetEntity> fallback = candidateSets.stream()
                    .filter(set -> "BLB".equals(set.getCode()))
                    .findFirst();

            if (fallback.isPresent()) {
                logger.warn("⚠️ Fallback vers Bloomburrow");
                return entityToModel(fallback.get());
            }

            logger.error("❌ Aucune extension trouvée - problème critique");
            return null;
        });
    }

    /**
     * Récupère la dernière extension avec cartes - VERSION UNIQUE ET CORRIGÉE
     */
    public Mono<MtgSet> getLatestSetWithCards() {
        logger.debug("🔍 Récupération de la dernière extension avec cartes");

        return getLatestSet()
                .flatMap(latestSet -> {
                    if (latestSet == null) {
                        logger.error("❌ Aucune dernière extension trouvée");
                        return Mono.just(null);
                    }

                    String setCode = latestSet.code();
                    logger.info("🎯 Extension sélectionnée : {} ({})", latestSet.name(), setCode);

                    // Vérifier s'il y a des cartes en base
                    long cardCount = cardRepository.countBySetCode(setCode);

                    if (cardCount > 0) {
                        logger.info("✅ {} cartes trouvées en base pour {}", cardCount, setCode);

                        // CORRECTION: Utiliser la méthode standard au lieu de WithCollections pour éviter les erreurs
                        return Mono.fromCallable(() -> {
                            List<CardEntity> cardEntities = cardRepository.findBySetCodeOrderByNameAsc(setCode);
                            List<MtgCard> cards = cardEntities.stream()
                                    .map(this::entityToModel)
                                    .collect(Collectors.toList());

                            return new MtgSet(
                                    latestSet.code(), latestSet.name(), latestSet.type(), latestSet.block(),
                                    latestSet.releaseDate(), latestSet.gathererCode(), latestSet.magicCardsInfoCode(),
                                    latestSet.border(), latestSet.onlineOnly(), cards
                            );
                        });
                    } else {
                        logger.warn("⚠️ Aucune carte en base pour {}. Tentative de récupération depuis les APIs", setCode);

                        // Essayer de récupérer depuis les APIs
                        return getCardsFromSet(setCode)
                                .map(cards -> {
                                    if (cards.isEmpty()) {
                                        logger.warn("⚠️ Aucune carte trouvée même depuis les APIs pour {}", setCode);
                                    }

                                    return new MtgSet(
                                            latestSet.code(), latestSet.name(), latestSet.type(), latestSet.block(),
                                            latestSet.releaseDate(), latestSet.gathererCode(), latestSet.magicCardsInfoCode(),
                                            latestSet.border(), latestSet.onlineOnly(), cards
                                    );
                                });
                    }
                })
                .doOnNext(result -> {
                    if (result != null && result.cards() != null) {
                        logger.info("🎉 Extension avec cartes récupérée : {} ({}) - {} cartes",
                                result.name(), result.code(), result.cards().size());
                    }
                })
                .doOnError(error -> {
                    logger.error("❌ Erreur récupération dernière extension avec cartes : {}", error.getMessage());
                });
    }

    /**
     * NOUVELLE MÉTHODE: Forcer FIN comme dernière extension
     */
    public void forceFinalFantasyAsLatest() {
        Optional<SetEntity> finSet = setRepository.findByCode("FIN");
        if (finSet.isPresent()) {
            SetEntity fin = finSet.get();

            // Mettre la date à aujourd'hui pour qu'elle soit "récente"
            fin.setReleaseDate(LocalDate.now());

            // S'assurer qu'elle est marquée comme synchronisée si elle a des cartes
            long cardCount = cardRepository.countBySetCode("FIN");
            if (cardCount > 0) {
                fin.setCardsSynced(true);
                fin.setCardsCount((int) cardCount);
            }

            setRepository.save(fin);
            logger.info("🎮 Final Fantasy forcé comme dernière extension (date mise à jour)");
        }
    }
    /**
     * NOUVELLE MÉTHODE: S'assurer que FIN existe en base avec les bonnes données
     */
    public void ensureFinalFantasyExists() {
        Optional<SetEntity> finSet = setRepository.findByCode("FIN");

        if (finSet.isEmpty()) {
            logger.info("🎮 Création automatique de l'extension Final Fantasy");

            SetEntity finalFantasy = new SetEntity();
            finalFantasy.setCode("FIN");
            finalFantasy.setName("Magic: The Gathering - FINAL FANTASY");
            finalFantasy.setType("expansion");
            finalFantasy.setReleaseDate(LocalDate.of(2025, 6, 13));
            finalFantasy.setCardsSynced(false);
            finalFantasy.setCardsCount(0);

            setRepository.save(finalFantasy);
            logger.info("✅ Extension Final Fantasy créée automatiquement");
        } else {
            logger.debug("🎮 Extension Final Fantasy déjà présente en base");
        }
    }

    /**
     * AMÉLIORATION: Valide si une date de sortie est acceptable - Version plus permissive pour FIN
     */
    private boolean isValidReleaseDate(String releaseDateStr) {
        try {
            LocalDate releaseDate = parseReleaseDate(releaseDateStr);
            LocalDate now = LocalDate.now();
            LocalDate twoYearsAgo = now.minusYears(2);
            LocalDate oneYearFuture = now.plusYears(1); // Plus permissif pour les extensions futures

            return releaseDate.isAfter(twoYearsAgo) && releaseDate.isBefore(oneYearFuture);
        } catch (Exception e) {
            logger.warn("⚠️ Date invalide : {}", releaseDateStr);
            return false;
        }
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
     * Parse une date de sortie avec gestion d'erreurs
     */
    private LocalDate parseReleaseDate(String releaseDateStr) {
        try {
            return LocalDate.parse(releaseDateStr);
        } catch (Exception e) {
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
     * MÉTHODE UNIFIÉE - Récupère les cartes d'une extension (SANS sauvegarde automatique)
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
            logger.info("🔮 Extension {} détectée comme Universes Beyond - récupération depuis Scryfall (sans sauvegarde)", setCode);
            return scryfallService.getCardsFromScryfall(setCode)
                    .doOnNext(cards -> {
                        if (!cards.isEmpty()) {
                            logger.info("✅ {} cartes récupérées depuis Scryfall pour {} (non sauvegardées)", cards.size(), setCode);
                            // SUPPRIMÉ : Sauvegarde automatique en arrière-plan
                        } else {
                            logger.warn("⚠️ Aucune carte Scryfall trouvée pour {}", setCode);
                        }
                    });
        }

        // Sinon, utiliser l'API MTG classique
        logger.info("🌐 Récupération depuis l'API MTG officielle pour : {} (sans sauvegarde)", setCode);
        return fetchCardsFromMtgApi(setCode);
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
     * Force la synchronisation d'une extension depuis l'API
     */
    public Mono<MtgSet> forceSyncSet(String setCode) {
        logger.info("🔄 Synchronisation forcée de l'extension : {}", setCode);

        return getCardsFromSet(setCode)
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

    public Mono<List<MtgSet>> fetchSetsFromApi() {
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

    /**
     * Parse une carte depuis l'API MTG
     */
    private MtgCard parseCardFromMtgApi(JsonNode cardNode) {
        String imageUrl = cardNode.get("imageUrl") != null ? cardNode.get("imageUrl").asText() : null;
        if (imageUrl == null || imageUrl.isEmpty()) {
            String multiverseId = cardNode.get("multiverseid") != null ? cardNode.get("multiverseid").asText() : null;
            String cardNumber = cardNode.get("number") != null ? cardNode.get("number").asText() : null;
            String setCode = cardNode.get("set") != null ? cardNode.get("set").asText() : null;
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
                cardNode.get("set") != null ? cardNode.get("set").asText() : null,
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

    private List<String> parseStringArray(JsonNode node) {
        if (node == null || !node.isArray()) return null;

        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
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

    // Dans MtgService.java - Méthode entityToModel mise à jour

    private MtgCard entityToModel(CardEntity entity) {
        return new MtgCard(
                entity.getExternalId(), // Utiliser externalId au lieu de id
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
                // Générer l'URL d'image : utiliser l'UUID pour l'endpoint local
                entity.getLocalImagePath() != null ? "/api/images/" + entity.getId() : entity.getOriginalImageUrl()
        );
    }

    // NOUVELLE MÉTHODE : Sauvegarder manuellement les extensions
    public Mono<String> saveSetsToDatabaseManually(List<MtgSet> sets) {
        return Mono.fromCallable(() -> {
            logger.info("💾 Sauvegarde MANUELLE de {} extensions en base", sets.size());

            int savedCount = 0;
            for (MtgSet set : sets) {
                try {
                    persistenceService.saveOrUpdateSet(set);
                    savedCount++;
                } catch (Exception e) {
                    logger.error("❌ Erreur sauvegarde extension {} : {}", set.code(), e.getMessage());
                }
            }

            String message = String.format("✅ %d/%d extensions sauvegardées manuellement", savedCount, sets.size());
            logger.info(message);
            return message;
        });
    }

    // NOUVELLE MÉTHODE : Sauvegarder manuellement les cartes
    public Mono<String> saveCardsToDatabaseManually(String setCode, List<MtgCard> cards) {
        return Mono.fromCallable(() -> {
            logger.info("💾 Sauvegarde MANUELLE de {} cartes pour l'extension {}", cards.size(), setCode);

            try {
                CompletableFuture<Integer> future = persistenceService.saveCardsForSet(setCode, cards);
                Integer savedCount = future.get(); // Attendre la fin

                String message = String.format("✅ %d cartes sauvegardées manuellement pour %s", savedCount, setCode);
                logger.info(message);
                return message;
            } catch (Exception e) {
                String errorMessage = String.format("❌ Erreur sauvegarde cartes %s : %s", setCode, e.getMessage());
                logger.error(errorMessage);
                throw new RuntimeException(errorMessage);
            }
        });
    }

}