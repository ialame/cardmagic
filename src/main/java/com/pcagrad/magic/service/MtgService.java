package com.pcagrad.magic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcagrad.magic.entity.MagicCard;
import com.pcagrad.magic.entity.MagicSet;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.model.MtgSet;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
import com.pcagrad.magic.util.Localization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    @Autowired
    private EntityAdaptationService adaptationService;

    @Value("${mtg.api.base-url:https://api.magicthegathering.io/v1}")
    private String baseUrl;

    public MtgService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    // Extensions qui n'existent que sur Scryfall
    private static final Set<String> SCRYFALL_ONLY_SETS = Set.of(
            "FIN", "FIC", "FCA", "TFIN", "TFIC", "RFIN",
            "WHO", "TWD", "SLD", "UNF", "UGL", "UNH",
            "LTR", "40K", "CLB"
    );

    // Extensions prioritaires
    private static final Map<String, Integer> SET_PRIORITY = Map.of(
            "FIN", 100,
            "BLB", 90,
            "MH3", 85,
            "OTJ", 80,
            "MKM", 75
    );

    /**
     * Récupère toutes les extensions - VERSION ADAPTÉE
     */
    public Mono<List<MtgSet>> getAllSets() {
        logger.debug("🔍 Récupération de toutes les extensions (adaptée)");

        List<MagicSet> dbSets = setRepository.findAll();

        if (!dbSets.isEmpty()) {
            logger.debug("✅ {} extensions trouvées en base de données", dbSets.size());
            List<MtgSet> mtgSets = dbSets.stream()
                    .map(this::entityToModelAdapted)
                    .collect(Collectors.toList());
            return Mono.just(mtgSets);
        }

        logger.info("🌐 Récupération des extensions depuis l'API externe (sans sauvegarde)");
        return fetchSetsFromApi();
    }

    /**
     * Récupère la dernière extension - VERSION ADAPTÉE
     */
    public Mono<MtgSet> getLatestSet() {
        logger.debug("🔍 Récupération de la dernière extension avec logique adaptée");

        return Mono.fromCallable(() -> {
            // 1. PRIORITÉ : Final Fantasy s'il a des cartes
            Optional<MagicSet> finSet = setRepository.findByCode("FIN");
            if (finSet.isPresent()) {
                long finCardCount = cardRepository.countBySetCode("FIN");
                if (finCardCount > 0) {
                    logger.info("🎮 Final Fantasy sélectionné comme dernière extension ({} cartes)", finCardCount);
                    return entityToModelAdapted(finSet.get());
                }
            }

            // 2. Chercher parmi les autres extensions récentes avec cartes
            List<MagicSet> candidateSets = setRepository.findLatestSets();

            Optional<MagicSet> bestSet = candidateSets.stream()
                    .filter(set -> hasValidReleaseDate(set))
                    .filter(set -> !isExcludedSetType(set.getType()))
                    .filter(set -> cardRepository.countBySetCode(set.getCode()) > 0)
                    .filter(set -> isInValidDateRange(set.getReleaseDate()))
                    .sorted((a, b) -> {
                        int priorityA = SET_PRIORITY.getOrDefault(a.getCode(), 0);
                        int priorityB = SET_PRIORITY.getOrDefault(b.getCode(), 0);

                        if (priorityA != priorityB) {
                            return Integer.compare(priorityB, priorityA);
                        }

                        LocalDate dateA = a.getReleaseDate();
                        LocalDate dateB = b.getReleaseDate();
                        if (dateA == null && dateB == null) return 0;
                        if (dateA == null) return 1;
                        if (dateB == null) return -1;
                        return dateB.compareTo(dateA);
                    })
                    .findFirst();

            if (bestSet.isPresent()) {
                MagicSet set = bestSet.get();
                long cardCount = cardRepository.countBySetCode(set.getCode());
                logger.info("✅ Extension sélectionnée : {} ({}) - {} cartes",
                        set.getName(), set.getCode(), cardCount);
                return entityToModelAdapted(set);
            }

            // 3. FALLBACK : FIN même sans cartes
            if (finSet.isPresent()) {
                logger.info("🎮 Fallback vers Final Fantasy");
                return entityToModelAdapted(finSet.get());
            }

            logger.error("❌ Aucune extension trouvée");
            return null;
        });
    }

    /**
     * Récupère la dernière extension avec cartes - VERSION ADAPTÉE
     */
    public Mono<MtgSet> getLatestSetWithCards() {
        logger.debug("🔍 Récupération de la dernière extension avec cartes (adaptée)");

        return getLatestSet()
                .flatMap(latestSet -> {
                    if (latestSet == null) {
                        logger.error("❌ Aucune dernière extension trouvée");
                        return Mono.empty();
                    }

                    String setCode = latestSet.code();
                    long cardCount = cardRepository.countBySetCode(setCode);

                    if (cardCount > 0) {
                        logger.info("✅ {} cartes trouvées en base pour {}", cardCount, setCode);

                        return Mono.fromCallable(() -> {
                            List<MagicCard> cardEntities = cardRepository.findBySetCodeOrderByNameAsc(setCode);
                            List<MtgCard> cards = cardEntities.stream()
                                    .map(this::entityToModelAdapted)
                                    .collect(Collectors.toList());

                            return new MtgSet(
                                    latestSet.code(), latestSet.name(), latestSet.type(), latestSet.block(),
                                    latestSet.releaseDate(), latestSet.gathererCode(), latestSet.magicCardsInfoCode(),
                                    latestSet.border(), latestSet.onlineOnly(), cards
                            );
                        });
                    } else {
                        logger.warn("⚠️ Aucune carte en base pour {}", setCode);
                        return getCardsFromSet(setCode)
                                .map(cards -> new MtgSet(
                                        latestSet.code(), latestSet.name(), latestSet.type(), latestSet.block(),
                                        latestSet.releaseDate(), latestSet.gathererCode(), latestSet.magicCardsInfoCode(),
                                        latestSet.border(), latestSet.onlineOnly(), cards
                                ));
                    }
                });
    }

    /**
     * Récupère les cartes d'une extension - VERSION ADAPTÉE
     */
    public Mono<List<MtgCard>> getCardsFromSet(String setCode) {
        logger.info("🔍 Récupération des cartes pour l'extension: {} (adaptée)", setCode);

        // Vérifier d'abord en base
        List<MagicCard> cardsInDb = cardRepository.findBySetCodeOrderByNameAsc(setCode);
        if (!cardsInDb.isEmpty()) {
            logger.info("✅ {} cartes trouvées en base pour {}", cardsInDb.size(), setCode);
            List<MtgCard> cards = cardsInDb.stream()
                    .map(this::entityToModelAdapted)
                    .collect(Collectors.toList());
            return Mono.just(cards);
        }

        // Si le set n'existe que sur Scryfall
        if (SCRYFALL_ONLY_SETS.contains(setCode.toUpperCase())) {
            logger.info("🔮 Extension {} détectée comme Universes Beyond - Scryfall", setCode);
            return scryfallService.getCardsFromScryfall(setCode);
        }

        // Sinon utiliser l'API MTG classique
        logger.info("🌐 Récupération depuis l'API MTG officielle pour : {}", setCode);
        return fetchCardsFromMtgApi(setCode);
    }

    /**
     * Force la synchronisation d'une extension - VERSION ADAPTÉE
     */
    public Mono<MtgSet> forceSyncSet(String setCode) {
        logger.info("🔄 Synchronisation forcée de l'extension : {}", setCode);

        return getCardsFromSet(setCode)
                .flatMap(cards -> {
                    if (cards.isEmpty()) {
                        logger.warn("⚠️ Aucune carte trouvée pour {}", setCode);
                        return getSetByCode(setCode);
                    }

                    // Sauvegarder les cartes
                    persistenceService.saveCardsForSet(setCode, cards);

                    return getSetByCode(setCode)
                            .map(set -> new MtgSet(
                                    set.code(), set.name(), set.type(), set.block(),
                                    set.releaseDate(), set.gathererCode(), set.magicCardsInfoCode(),
                                    set.border(), set.onlineOnly(), cards
                            ));
                });
    }

    /**
     * Forcer FIN comme dernière extension - VERSION ADAPTÉE
     */
    public void forceFinalFantasyAsLatest() {
        Optional<MagicSet> finSet = setRepository.findByCode("FIN");
        if (finSet.isPresent()) {
            MagicSet fin = finSet.get();
            fin.setReleaseDate(LocalDate.now());

            long cardCount = cardRepository.countBySetCode("FIN");
            if (cardCount > 0) {
                fin.setCardsCount((int) cardCount);
            }

            setRepository.save(fin);
            logger.info("🎮 Final Fantasy forcé comme dernière extension");
        }
    }

    /**
     * S'assurer que FIN existe - VERSION ADAPTÉE
     */
    public void ensureFinalFantasyExists() {
        Optional<MagicSet> finSet = setRepository.findByCode("FIN");

        if (finSet.isEmpty()) {
            logger.info("🎮 Création automatique de l'extension Final Fantasy");

            MagicSet finalFantasy = new MagicSet();
            finalFantasy.setCode("FIN");
            finalFantasy.setName("Magic: The Gathering - FINAL FANTASY");
            finalFantasy.setReleaseDate(LocalDate.of(2025, 6, 13));

            // Utiliser le service d'adaptation
            adaptationService.setMagicSetType(finalFantasy, "expansion");
            adaptationService.prepareMagicSetForSave(finalFantasy, "expansion");

            setRepository.save(finalFantasy);
            logger.info("✅ Extension Final Fantasy créée automatiquement");
        }
    }

    // ========== CONVERSION ENTITY <-> MODEL ADAPTÉE ==========

    /**
     * Convertit une entité MagicSet vers le modèle MtgSet - VERSION ADAPTÉE
     */
    private MtgSet entityToModelAdapted(MagicSet entity) {
        return new MtgSet(
                entity.getCode(),
                entity.getName(), // Utilise la méthode adaptée qui lit les translations
                entity.getType(), // Utilise la méthode adaptée qui lit typeMagic
                entity.getBlock(),
                entity.getReleaseDate() != null ? entity.getReleaseDate().toString() : null,
                entity.getGathererCode(), // Mapped vers mtgoCode
                entity.getMagicCardsInfoCode(), // Mapped vers tcgplayerGroupId
                entity.getBorder(), // Mapped vers version
                entity.getOnlineOnly(), // Logique basée sur fr/us
                null
        );
    }

    /**
     * Convertit une entité MagicCard vers le modèle MtgCard - VERSION ADAPTÉE
     */
    private MtgCard entityToModelAdapted(MagicCard entity) {
        return new MtgCard(
                entity.getExternalId(), // Utilise idPrim
                entity.getName(), // Utilise la méthode adaptée qui lit les translations
                entity.getManaCost(), // Extrait du JSON attributes
                entity.getCmc(), // Extrait du JSON attributes
                entity.getColors(), // Extrait du JSON allowedNotes
                entity.getColorIdentity(), // Extrait du JSON allowedNotes
                entity.getType(), // Extrait du JSON attributes
                entity.getSupertypes(), // Extrait du JSON allowedNotes
                entity.getTypes(), // Extrait du JSON allowedNotes
                entity.getSubtypes(), // Extrait du JSON allowedNotes
                entity.getRarity(), // Extrait du JSON attributes
                entity.getSetCode(), // Utilise zPostExtension
                entity.getSetName(), // Extrait du JSON attributes
                entity.getText(), // Extrait du JSON attributes
                entity.getArtist(), // Extrait du JSON attributes
                entity.getNumber(), // Converti depuis numero
                entity.getPower(), // Extrait du JSON attributes
                entity.getToughness(), // Extrait du JSON attributes
                entity.getLayout(), // Extrait du JSON attributes
                entity.getMultiverseid(), // Extrait du JSON attributes
                // URL d'image : utiliser l'UUID pour l'endpoint local ou l'URL originale
                entity.getLocalImagePath() != null ? "/api/images/" + entity.getId() : entity.getOriginalImageUrl()
        );
    }

    // ========== MÉTHODES UTILITAIRES ADAPTÉES ==========

    private boolean hasValidReleaseDate(MagicSet set) {
        return set.getReleaseDate() != null;
    }

    private boolean isExcludedSetType(String type) {
        if (type == null) return false;
        String lowerType = type.toLowerCase();
        return lowerType.contains("promo") ||
                lowerType.contains("token") ||
                lowerType.contains("memorabilia");
    }

    private boolean isInValidDateRange(LocalDate releaseDate) {
        if (releaseDate == null) return false;
        LocalDate now = LocalDate.now();
        return releaseDate.isAfter(now.minusYears(2)) &&
                releaseDate.isBefore(now.plusMonths(6));
    }

    // ========== MÉTHODES API EXTERNES ==========

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
                .doOnError(error -> logger.error("❌ Erreur API externe : {}", error.getMessage()));
    }

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
                            return cards;
                        }
                        return Collections.<MtgCard>emptyList();
                    } catch (Exception e) {
                        logger.error("❌ Erreur parsing réponse MTG API pour {} : {}", setCode, e.getMessage());
                        return Collections.<MtgCard>emptyList();
                    }
                })
                .onErrorReturn(Collections.emptyList());
    }

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

    // Méthodes utilitaires inchangées
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

    public Mono<MtgSet> getSetByCode(String setCode) {
        Optional<MagicSet> dbSet = setRepository.findByCode(setCode);
        if (dbSet.isPresent()) {
            return Mono.just(entityToModelAdapted(dbSet.get()));
        }

        return getAllSets()
                .map(sets -> sets.stream()
                        .filter(set -> setCode.equalsIgnoreCase(set.code()))
                        .findFirst()
                        .orElse(null));
    }

    // Nouvelles méthodes de sauvegarde manuelle adaptées
    public Mono<String> saveSetsToDatabaseManually(List<MtgSet> sets) {
        return Mono.fromCallable(() -> {
            logger.info("💾 Sauvegarde MANUELLE adaptée de {} extensions", sets.size());

            int savedCount = 0;
            for (MtgSet set : sets) {
                try {
                    persistenceService.saveOrUpdateSet(set);
                    savedCount++;
                } catch (Exception e) {
                    logger.error("❌ Erreur sauvegarde extension {} : {}", set.code(), e.getMessage());
                }
            }

            String message = String.format("✅ %d/%d extensions sauvegardées (adaptation)", savedCount, sets.size());
            logger.info(message);
            return message;
        });
    }

    public Mono<String> saveCardsToDatabaseManually(String setCode, List<MtgCard> cards) {
        return Mono.fromCallable(() -> {
            logger.info("💾 Sauvegarde MANUELLE adaptée de {} cartes pour {}", cards.size(), setCode);

            try {
                CompletableFuture<Integer> future = persistenceService.saveCardsForSet(setCode, cards);
                Integer savedCount = future.get();

                String message = String.format("✅ %d cartes sauvegardées (adaptation) pour %s", savedCount, setCode);
                logger.info(message);
                return message;
            } catch (Exception e) {
                String errorMessage = String.format("❌ Erreur sauvegarde cartes adaptées %s : %s", setCode, e.getMessage());
                logger.error(errorMessage);
                throw new RuntimeException(errorMessage);
            }
        });
    }
}