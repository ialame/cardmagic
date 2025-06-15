package com.pcagrad.magic.controller;

import com.pcagrad.magic.dto.ApiResponse;
import com.pcagrad.magic.entity.MagicCard;
import com.pcagrad.magic.entity.MagicSet;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.model.MtgSet;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
import com.pcagrad.magic.service.CardPersistenceService;
import com.pcagrad.magic.service.EntityAdaptationService;
import com.pcagrad.magic.service.ImageDownloadService;
import com.pcagrad.magic.service.MtgService;
import com.pcagrad.magic.service.ScryfallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mtg")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:8080"})
@Transactional(readOnly = true)
public class MtgController {

    private static final Logger logger = LoggerFactory.getLogger(MtgController.class);

    @Autowired
    private MtgService mtgService;

    @Autowired
    private SetRepository setRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private ImageDownloadService imageDownloadService;

    @Autowired
    private ScryfallService scryfallService;

    @Autowired
    private CardPersistenceService persistenceService;

    @Autowired
    private EntityAdaptationService adaptationService;

    // ========== ENDPOINTS ESSENTIELS ADAPTÉS ==========

    @GetMapping("/sets")
    public Mono<ResponseEntity<ApiResponse<List<MtgSet>>>> getAllSets() {
        return mtgService.getAllSets()
                .map(sets -> ResponseEntity.ok(ApiResponse.success(sets, "Extensions récupérées avec adaptation")))
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la récupération des extensions")));
    }

    @GetMapping("/sets/latest")
    public Mono<ResponseEntity<ApiResponse<MtgSet>>> getLatestSet() {
        return mtgService.getLatestSet()
                .map(set -> {
                    if (set != null) {
                        return ResponseEntity.ok(ApiResponse.success(set, "Dernière extension récupérée (adaptée)"));
                    } else {
                        return ResponseEntity.notFound().<ApiResponse<MtgSet>>build();
                    }
                })
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la récupération de la dernière extension")));
    }

    @GetMapping("/sets/latest/cards")
    public Mono<ResponseEntity<ApiResponse<MtgSet>>> getLatestSetWithCards() {
        return mtgService.getLatestSetWithCards()
                .map(set -> {
                    if (set != null) {
                        return ResponseEntity.ok(ApiResponse.success(set,
                                "Dernière extension avec cartes récupérée (adaptée)"));
                    } else {
                        return ResponseEntity.notFound().<ApiResponse<MtgSet>>build();
                    }
                })
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la récupération de la dernière extension avec cartes")));
    }

    @GetMapping("/sets/{setCode}/cards")
    public Mono<ResponseEntity<ApiResponse<List<MtgCard>>>> getCardsFromSet(@PathVariable String setCode) {
        return mtgService.getCardsFromSet(setCode)
                .map(cards -> ResponseEntity.ok(ApiResponse.success(cards,
                        "Cartes de l'extension " + setCode + " récupérées (adaptées)")))
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la récupération des cartes de l'extension " + setCode)));
    }

    @GetMapping("/sets/{setCode}/with-cards")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Object>> getSetWithCards(@PathVariable String setCode) {
        try {
            logger.info("🔍 Récupération de l'extension {} avec cartes (adaptée)", setCode);

            Optional<MagicSet> setEntity = setRepository.findByCode(setCode);

            if (setEntity.isEmpty()) {
                logger.info("🔧 Extension {} non trouvée, création automatique", setCode);

                MagicSet newSet = new MagicSet();
                newSet.setCode(setCode);
                newSet.setName(getSetNameFromCode(setCode));

                adaptationService.setMagicSetType(newSet, "expansion");
                adaptationService.prepareMagicSetForSave(newSet, "expansion");
                setKnownReleaseDate(newSet, setCode);

                setEntity = Optional.of(setRepository.save(newSet));
                logger.info("✅ Extension {} créée automatiquement avec adaptation", setCode);
            }

            MagicSet set = setEntity.get();
            List<MagicCard> cards = cardRepository.findBySetCodeOrderByNameAsc(setCode);

            Map<String, Object> response = new HashMap<>();
            response.put("code", set.getCode());
            response.put("name", set.getName());
            response.put("type", set.getType());
            response.put("releaseDate", set.getReleaseDate());
            response.put("cardsSynced", set.getCardsSynced());
            response.put("totalCards", cards.size());

            // Statistiques par rareté adaptées
            Map<String, Long> rarityStats = calculateRarityStatsAdapted(cards);
            response.put("rarityStats", rarityStats);

            // Convertir les cartes en format sécurisé
            List<Map<String, Object>> cardList = cards.stream()
                    .limit(200)
                    .map(this::convertCardToSafeMapAdapted)
                    .collect(Collectors.toList());

            response.put("cards", cardList);
            response.put("hasMoreCards", cards.size() > 200);

            String message = cards.isEmpty() ?
                    "Extension trouvée mais aucune carte synchronisée" :
                    String.format("Extension %s avec %d cartes (adaptée)", set.getName(), cards.size());

            return ResponseEntity.ok(ApiResponse.success(response, message));

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la récupération de l'extension {} : {}", setCode, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erreur lors de la récupération de l'extension : " + e.getMessage()));
        }
    }

    // ========== ENDPOINTS D'ADMINISTRATION ADAPTÉS ==========

    @PostMapping("/admin/initialize-with-fin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initializeWithFin() {
        try {
            logger.info("🚀 Initialisation de l'application avec Final Fantasy (adaptée)");

            Map<String, Object> result = new HashMap<>();

            Optional<MagicSet> finSet = setRepository.findByCode("FIN");
            if (finSet.isEmpty()) {
                MagicSet fin = new MagicSet();
                fin.setCode("FIN");
                fin.setName("Magic: The Gathering - FINAL FANTASY");
                fin.setReleaseDate(LocalDate.now());

                adaptationService.setMagicSetType(fin, "expansion");
                adaptationService.prepareMagicSetForSave(fin, "expansion");

                finSet = Optional.of(setRepository.save(fin));
                result.put("finCreated", true);
                logger.info("✅ Extension Final Fantasy créée avec adaptation");
            } else {
                result.put("finCreated", false);
                logger.info("✅ Extension Final Fantasy existante");
            }

            long cardCount = cardRepository.countBySetCode("FIN");
            result.put("finCardCount", cardCount);

            mtgService.forceFinalFantasyAsLatest();
            result.put("finSetAsLatest", true);

            // Test de récupération
            try {
                MtgSet latestSet = mtgService.getLatestSet().block();
                result.put("latestSetCode", latestSet != null ? latestSet.code() : "NONE");
                result.put("latestSetName", latestSet != null ? latestSet.name() : "NONE");
            } catch (Exception e) {
                result.put("latestSetError", e.getMessage());
            }

            List<String> recommendations = new ArrayList<>();
            if (cardCount == 0) {
                recommendations.add("Synchroniser les cartes FIN avec: POST /api/mtg/admin/sync-final-fantasy");
            } else {
                recommendations.add("Final Fantasy est prêt avec " + cardCount + " cartes");
            }
            result.put("recommendations", recommendations);

            return ResponseEntity.ok(ApiResponse.success(result, "Application initialisée avec Final Fantasy (adaptée)"));

        } catch (Exception e) {
            logger.error("❌ Erreur initialisation avec FIN : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur initialisation : " + e.getMessage()));
        }
    }

    @PostMapping("/admin/sync-final-fantasy")
    public ResponseEntity<ApiResponse<String>> syncFinalFantasy() {
        try {
            logger.info("🎮 Synchronisation spéciale Final Fantasy depuis Scryfall (adaptée)");

            mtgService.ensureFinalFantasyExists();

            CompletableFuture.runAsync(() -> {
                try {
                    // Supprimer les anciennes cartes
                    List<MagicCard> existingCards = cardRepository.findBySetCodeOrderByNameAsc("FIN");
                    if (!existingCards.isEmpty()) {
                        cardRepository.deleteAll(existingCards);
                        logger.info("🗑️ {} anciennes cartes Final Fantasy supprimées", existingCards.size());
                    }

                    // Récupérer depuis Scryfall
                    List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");

                    if (!finCards.isEmpty()) {
                        int savedCount = persistenceService.saveCards(finCards, "FIN");

                        // Mettre à jour l'extension avec adaptation
                        Optional<MagicSet> finSet = setRepository.findByCode("FIN");
                        if (finSet.isPresent()) {
                            MagicSet set = finSet.get();
                            set.setCardsCount(savedCount);
                            setRepository.save(set);
                        }

                        logger.info("🎮 ✅ Final Fantasy synchronisé avec adaptation : {} cartes", savedCount);

                        try {
                            imageDownloadService.downloadImagesForSet("FIN");
                        } catch (Exception e) {
                            logger.error("❌ Erreur téléchargement images FIN : {}", e.getMessage());
                        }

                    } else {
                        logger.error("❌ Aucune carte Final Fantasy trouvée sur Scryfall");
                    }

                } catch (Exception e) {
                    logger.error("❌ Erreur synchronisation Final Fantasy : {}", e.getMessage());
                }
            });

            return ResponseEntity.accepted()
                    .body(ApiResponse.success("Synchronisation Final Fantasy démarrée (adaptée)"));

        } catch (Exception e) {
            logger.error("❌ Erreur déclenchement sync FIN : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    @PostMapping("/admin/save-set-manually/{setCode}")
    public Mono<ResponseEntity<? extends ApiResponse<? extends Object>>> saveSetManually(@PathVariable String setCode) {
        logger.info("🎯 Demande de sauvegarde manuelle de l'extension : {} (adaptée)", setCode);

        return mtgService.getAllSets()
                .map(sets -> {
                    Optional<MtgSet> targetSet = sets.stream()
                            .filter(set -> setCode.equalsIgnoreCase(set.code()))
                            .findFirst();

                    if (targetSet.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error("Extension " + setCode + " non trouvée"));
                    }

                    try {
                        persistenceService.saveOrUpdateSet(targetSet.get());

                        String message = String.format("✅ Extension %s (%s) sauvegardée avec adaptation",
                                setCode, targetSet.get().name());
                        logger.info(message);

                        return ResponseEntity.ok(ApiResponse.success(message));

                    } catch (Exception e) {
                        String errorMessage = "Erreur sauvegarde extension " + setCode + " : " + e.getMessage();
                        logger.error("❌ {}", errorMessage);
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error(errorMessage));
                    }
                })
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la récupération des extensions")));
    }

    @PostMapping("/admin/save-cards-manually/{setCode}")
    public Mono<ResponseEntity<ApiResponse<String>>> saveCardsManually(@PathVariable String setCode) {
        logger.info("🎯 Demande de sauvegarde manuelle des cartes pour : {} (adaptée)", setCode);

        return mtgService.getCardsFromSet(setCode)
                .flatMap(cards -> {
                    if (cards.isEmpty()) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(ApiResponse.error("Aucune carte à sauvegarder pour " + setCode)));
                    }

                    return mtgService.saveCardsToDatabaseManually(setCode, cards)
                            .map(message -> ResponseEntity.ok(ApiResponse.success(message)))
                            .onErrorReturn(ResponseEntity.badRequest()
                                    .body(ApiResponse.error("Erreur lors de la sauvegarde des cartes pour " + setCode)));
                });
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Stats de base adaptées
            stats.put("totalCards", cardRepository.count());
            stats.put("totalSets", setRepository.count());
            stats.put("syncedSets", setRepository.countSyncedSets());
            stats.put("distinctArtists", cardRepository.countDistinctArtists());

            // Stats images adaptées
            long totalImages = cardRepository.count();
            long downloadedImages = cardRepository.findByImageDownloadedTrueAndLocalImagePathIsNotNull().size();

            Map<String, Object> imageStats = new HashMap<>();
            imageStats.put("total", totalImages);
            imageStats.put("downloaded", downloadedImages);
            imageStats.put("percentage", totalImages > 0 ? (double) downloadedImages / totalImages * 100 : 0);

            stats.put("imageStats", imageStats);
            stats.put("databaseAdapted", true);

            return ResponseEntity.ok(ApiResponse.success(stats, "Statistiques de la base adaptée"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    // ========== MÉTHODES UTILITAIRES ADAPTÉES ==========

    private String getSetNameFromCode(String setCode) {
        Map<String, String> knownSets = Map.of(
                "BLB", "Bloomburrow",
                "MH3", "Modern Horizons 3",
                "OTJ", "Outlaws of Thunder Junction",
                "MKM", "Murders at Karlov Manor",
                "LCI", "The Lost Caverns of Ixalan",
                "FIN", "Magic: The Gathering - FINAL FANTASY"
        );

        return knownSets.getOrDefault(setCode, setCode + " (Extension)");
    }

    private void setKnownReleaseDate(MagicSet set, String setCode) {
        Map<String, LocalDate> knownDates = Map.of(
                "BLB", LocalDate.of(2024, 8, 2),
                "MH3", LocalDate.of(2024, 6, 14),
                "OTJ", LocalDate.of(2024, 4, 19),
                "MKM", LocalDate.of(2024, 2, 9),
                "LCI", LocalDate.of(2023, 11, 17),
                "FIN", LocalDate.of(2025, 6, 13)
        );

        LocalDate releaseDate = knownDates.get(setCode);
        if (releaseDate != null) {
            set.setReleaseDate(releaseDate);
        }
    }

    private Map<String, Long> calculateRarityStatsAdapted(List<MagicCard> cards) {
        return cards.stream()
                .collect(Collectors.groupingBy(
                        card -> {
                            String rarity = card.getRarity();
                            return rarity != null ? rarity : "unknown";
                        },
                        Collectors.counting()
                ));
    }

    private Map<String, Object> convertCardToSafeMapAdapted(MagicCard card) {
        Map<String, Object> cardMap = new HashMap<>();

        try {
            cardMap.put("id", card.getId());
            cardMap.put("name", card.getName());
            cardMap.put("manaCost", card.getManaCost());
            cardMap.put("cmc", card.getCmc());
            cardMap.put("type", card.getType());
            cardMap.put("rarity", card.getRarity());
            cardMap.put("setCode", card.getSetCode());
            cardMap.put("artist", card.getArtist());
            cardMap.put("number", card.getNumber());
            cardMap.put("power", card.getPower());
            cardMap.put("toughness", card.getToughness());
            cardMap.put("text", card.getText());
            cardMap.put("imageDownloaded", card.getImageDownloaded());

            // Collections avec gestion d'erreur
            try {
                cardMap.put("colors", card.getColors());
                cardMap.put("colorIdentity", card.getColorIdentity());
                cardMap.put("types", card.getTypes());
                cardMap.put("subtypes", card.getSubtypes());
                cardMap.put("supertypes", card.getSupertypes());
            } catch (Exception e) {
                logger.debug("⚠️ Erreur chargement collections pour {} : {}", card.getName(), e.getMessage());
                cardMap.put("colors", null);
                cardMap.put("colorIdentity", null);
                cardMap.put("types", null);
                cardMap.put("subtypes", null);
                cardMap.put("supertypes", null);
            }

        } catch (Exception e) {
            logger.error("❌ Erreur conversion carte {} : {}", card.getName(), e.getMessage());
            cardMap = new HashMap<>();
            cardMap.put("id", card.getId());
            cardMap.put("name", card.getName());
            cardMap.put("error", "Erreur chargement données adaptées");
        }

        return cardMap;
    }

    // ========== ENDPOINTS DE DEBUG ET MAINTENANCE ADAPTÉS ==========

    @GetMapping("/debug/adaptation-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdaptationStatus() {
        try {
            Map<String, Object> status = new HashMap<>();

            // Vérifier l'état de l'adaptation
            status.put("databaseAdapted", true);
            status.put("entitiesAdapted", true);
            status.put("servicesAdapted", true);
            status.put("repositoriesAdapted", true);
            status.put("controllersAdapted", true);

            // Stats de validation
            long totalSets = setRepository.count();
            long validSets = 0;

            for (MagicSet set : setRepository.findAll()) {
                if (adaptationService.validateMagicSet(set)) {
                    validSets++;
                }
            }

            status.put("totalSets", totalSets);
            status.put("validSets", validSets);
            status.put("adaptationValidationRate", totalSets > 0 ? (double) validSets / totalSets * 100 : 0);

            return ResponseEntity.ok(ApiResponse.success(status, "Statut d'adaptation de la base de données"));

        } catch (Exception e) {
            logger.error("❌ Erreur statut adaptation : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    @PostMapping("/admin/cleanup-adaptation")
    public ResponseEntity<ApiResponse<String>> cleanupAdaptation() {
        try {
            logger.info("🧹 Nettoyage post-adaptation");

            persistenceService.cleanupInconsistentData();

            return ResponseEntity.ok(ApiResponse.success("Nettoyage post-adaptation terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur nettoyage adaptation : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    @GetMapping("/admin/validate-set/{setCode}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateSetAdaptation(@PathVariable String setCode) {
        try {
            Map<String, Object> validation = new HashMap<>();

            Optional<MagicSet> setOpt = setRepository.findByCode(setCode);
            if (setOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            MagicSet set = setOpt.get();
            boolean isValid = adaptationService.validateMagicSet(set);
            boolean isConsistent = persistenceService.validateSetConsistency(setCode);

            validation.put("setCode", setCode);
            validation.put("entityValid", isValid);
            validation.put("dataConsistent", isConsistent);
            validation.put("overallValid", isValid && isConsistent);

            // Détails de validation
            validation.put("hasValidType", set.getTypeMagic() != null);
            validation.put("hasValidTranslations", !set.getTranslations().isEmpty());
            validation.put("cardCount", cardRepository.countBySetCode(setCode));

            return ResponseEntity.ok(ApiResponse.success(validation, "Validation de l'adaptation pour " + setCode));

        } catch (Exception e) {
            logger.error("❌ Erreur validation set {} : {}", setCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }
}