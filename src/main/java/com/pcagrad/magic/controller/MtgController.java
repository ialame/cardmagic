package com.pcagrad.magic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import com.pcagrad.magic.dto.ApiResponse;
import com.pcagrad.magic.entity.CardEntity;
import com.pcagrad.magic.entity.SetEntity;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.model.MtgSet;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
import com.pcagrad.magic.service.CardPersistenceService;
import com.pcagrad.magic.service.ImageDownloadService;
import com.pcagrad.magic.service.MtgService;
import com.pcagrad.magic.service.ScryfallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import java.util.List;
import java.util.stream.Collectors;

import java.time.LocalDate;
import java.util.Optional;


@RestController
@RequestMapping("/api/mtg")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:8080"})
@Transactional(readOnly = true) // Ajouter cette annotation
public class MtgController {
    // Ajoutez cette ligne pour le logger
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

    // AJOUTER dans MtgController.java

    /**
     * ENDPOINT CRITIQUE: Initialiser l'application avec FIN - VERSION CORRIGÉE
     */
    @PostMapping("/admin/initialize-with-fin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initializeWithFin() {
        try {
            logger.info("🚀 Initialisation de l'application avec Final Fantasy");

            Map<String, Object> result = new HashMap<>();

            // 1. S'assurer que FIN existe
            Optional<SetEntity> finSet = setRepository.findByCode("FIN");
            if (finSet.isEmpty()) {
                SetEntity fin = new SetEntity();
                fin.setCode("FIN");
                fin.setName("Magic: The Gathering - FINAL FANTASY");
                fin.setType("expansion");
                fin.setReleaseDate(LocalDate.now()); // Date d'aujourd'hui
                fin.setCardsSynced(false);
                fin.setCardsCount(0);

                finSet = Optional.of(setRepository.save(fin));
                result.put("finCreated", true);
                logger.info("✅ Extension Final Fantasy créée");
            } else {
                result.put("finCreated", false);
                logger.info("✅ Extension Final Fantasy existante");
            }

            // 2. Vérifier les cartes
            long cardCount = cardRepository.countBySetCode("FIN");
            result.put("finCardCount", cardCount);

            // 3. Forcer FIN comme dernière extension
            mtgService.forceFinalFantasyAsLatest();
            result.put("finSetAsLatest", true);

            // 4. Tester la récupération
            try {
                MtgSet latestSet = mtgService.getLatestSet().block();
                result.put("latestSetCode", latestSet != null ? latestSet.code() : "NONE");
                result.put("latestSetName", latestSet != null ? latestSet.name() : "NONE");
            } catch (Exception e) {
                result.put("latestSetError", e.getMessage());
            }

            // 5. Recommandations
            List<String> recommendations = new ArrayList<>();
            if (cardCount == 0) {
                recommendations.add("Synchroniser les cartes FIN avec: POST /api/mtg/admin/sync-final-fantasy");
            } else {
                recommendations.add("Final Fantasy est prêt avec " + cardCount + " cartes");
            }
            result.put("recommendations", recommendations);

            return ResponseEntity.ok(ApiResponse.success(result, "Application initialisée avec Final Fantasy"));

        } catch (Exception e) {
            logger.error("❌ Erreur initialisation avec FIN : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur initialisation : " + e.getMessage()));
        }
    }
    /**
     * ENDPOINT: Test de la dernière extension - VERSION CORRIGÉE
     */
    @GetMapping("/debug/test-latest-set")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testLatestSet() {
        try {
            Map<String, Object> debug = new HashMap<>();

            // Test 1: Récupération simple
            MtgSet latestSet = mtgService.getLatestSet().block();
            if (latestSet != null) {
                Map<String, Object> latestSetInfo = new HashMap<>();
                latestSetInfo.put("code", latestSet.code());
                latestSetInfo.put("name", latestSet.name());
                debug.put("latestSet", latestSetInfo);
            } else {
                debug.put("latestSet", null);
            }

            // Test 2: Récupération avec cartes
            MtgSet latestWithCards = mtgService.getLatestSetWithCards().block();
            if (latestWithCards != null) {
                Map<String, Object> latestWithCardsInfo = new HashMap<>();
                latestWithCardsInfo.put("code", latestWithCards.code());
                latestWithCardsInfo.put("name", latestWithCards.name());
                latestWithCardsInfo.put("cardsCount", latestWithCards.cards() != null ? latestWithCards.cards().size() : 0);
                debug.put("latestWithCards", latestWithCardsInfo);
            } else {
                debug.put("latestWithCards", null);
            }

            // Test 3: État de FIN
            Optional<SetEntity> finSet = setRepository.findByCode("FIN");
            if (finSet.isPresent()) {
                SetEntity fin = finSet.get();
                long cardCount = cardRepository.countBySetCode("FIN");

                Map<String, Object> finStatus = new HashMap<>();
                finStatus.put("exists", true);
                finStatus.put("name", fin.getName());
                finStatus.put("releaseDate", fin.getReleaseDate());
                finStatus.put("cardsSynced", fin.getCardsSynced());
                finStatus.put("cardsInDb", cardCount);

                debug.put("finStatus", finStatus);
            } else {
                Map<String, Object> finStatus = new HashMap<>();
                finStatus.put("exists", false);
                debug.put("finStatus", finStatus);
            }

            // Test 4: Extensions récentes en base
            List<SetEntity> recentSets = setRepository.findLatestSets();
            List<Map<String, Object>> recentInfo = recentSets.stream()
                    .limit(5)
                    .map(set -> {
                        long cardCount = cardRepository.countBySetCode(set.getCode());
                        Map<String, Object> setInfo = new HashMap<>();
                        setInfo.put("code", set.getCode());
                        setInfo.put("name", set.getName());
                        setInfo.put("releaseDate", set.getReleaseDate());
                        setInfo.put("cardsCount", cardCount);
                        return setInfo;
                    })
                    .collect(Collectors.toList());
            debug.put("recentSets", recentInfo);

            return ResponseEntity.ok(ApiResponse.success(debug, "Test de la dernière extension"));

        } catch (Exception e) {
            logger.error("❌ Erreur test latest set : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur test : " + e.getMessage()));
        }
    }

    /**
     * ENDPOINT: Solution complète FIN
     */
    @PostMapping("/admin/complete-fin-setup")
    public ResponseEntity<ApiResponse<String>> completeFinSetup() {
        try {
            logger.info("🎮 Configuration complète de Final Fantasy");

            // 1. Initialiser
            initializeWithFin();

            // 2. Attendre un peu
            Thread.sleep(1000);

            // 3. Synchroniser en arrière-plan
            CompletableFuture.runAsync(() -> {
                try {
                    logger.info("🔄 Début synchronisation Final Fantasy...");

                    // Récupérer depuis Scryfall
                    List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");

                    if (!finCards.isEmpty()) {
                        // Sauvegarder
                        int savedCount = persistenceService.saveCards(finCards, "FIN");

                        // Forcer comme dernière extension
                        mtgService.forceFinalFantasyAsLatest();

                        logger.info("🎮 ✅ Configuration Final Fantasy terminée : {} cartes", savedCount);
                    } else {
                        logger.error("❌ Aucune carte Final Fantasy trouvée");
                    }

                } catch (Exception e) {
                    logger.error("❌ Erreur configuration FIN : {}", e.getMessage());
                }
            });

            return ResponseEntity.accepted()
                    .body(ApiResponse.success("Configuration complète de Final Fantasy démarrée"));

        } catch (Exception e) {
            logger.error("❌ Erreur complete FIN setup : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * ENDPOINT SPÉCIAL: Synchroniser Final Fantasy depuis Scryfall
     */
    @PostMapping("/admin/sync-final-fantasy")
    public ResponseEntity<ApiResponse<String>> syncFinalFantasy() {
        try {
            logger.info("🎮 Synchronisation spéciale Final Fantasy depuis Scryfall");

            // 1. S'assurer que l'extension FIN existe
            mtgService.ensureFinalFantasyExists();

            // 2. Déclencher la synchronisation en arrière-plan
            CompletableFuture.runAsync(() -> {
                try {
                    // Supprimer les anciennes cartes FIN si elles existent
                    List<CardEntity> existingCards = cardRepository.findBySetCodeOrderByNameAsc("FIN");
                    if (!existingCards.isEmpty()) {
                        cardRepository.deleteAll(existingCards);
                        logger.info("🗑️ {} anciennes cartes Final Fantasy supprimées", existingCards.size());
                    }

                    // Récupérer depuis Scryfall
                    List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");

                    if (!finCards.isEmpty()) {
                        // Sauvegarder les cartes
                        int savedCount = persistenceService.saveCards(finCards, "FIN");

                        // Mettre à jour l'extension
                        Optional<SetEntity> finSet = setRepository.findByCode("FIN");
                        if (finSet.isPresent()) {
                            SetEntity set = finSet.get();
                            set.setCardsCount(savedCount);
                            set.setCardsSynced(true);
                            set.setLastSyncAt(LocalDateTime.now());
                            setRepository.save(set);
                        }

                        logger.info("🎮 ✅ Final Fantasy synchronisé : {} cartes sauvegardées", savedCount);

                        // Déclencher le téléchargement des images
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
                    .body(ApiResponse.success("Synchronisation Final Fantasy démarrée depuis Scryfall"));

        } catch (Exception e) {
            logger.error("❌ Erreur déclenchement sync FIN : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * ENDPOINT: Forcer Final Fantasy comme dernière extension
     */
    @PostMapping("/admin/set-fin-as-latest")
    public ResponseEntity<ApiResponse<String>> setFinAsLatest() {
        try {
            logger.info("🎮 Forcer Final Fantasy comme dernière extension");

            // S'assurer que FIN existe
            mtgService.ensureFinalFantasyExists();

            // Vérifier le nombre de cartes
            long cardCount = cardRepository.countBySetCode("FIN");

            if (cardCount == 0) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Final Fantasy n'a pas de cartes. Synchronisez d'abord avec /admin/sync-final-fantasy"));
            }

            // Mettre à jour la date de sortie pour qu'elle soit récente
            Optional<SetEntity> finSet = setRepository.findByCode("FIN");
            if (finSet.isPresent()) {
                SetEntity set = finSet.get();
                set.setReleaseDate(LocalDate.now()); // Date d'aujourd'hui
                set.setCardsSynced(true);
                setRepository.save(set);

                logger.info("🎮 ✅ Final Fantasy défini comme dernière extension avec {} cartes", cardCount);

                return ResponseEntity.ok(ApiResponse.success(
                        String.format("Final Fantasy défini comme dernière extension (%d cartes)", cardCount)
                ));
            }

            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Extension Final Fantasy non trouvée"));

        } catch (Exception e) {
            logger.error("❌ Erreur set FIN as latest : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * ENDPOINT: Statut Final Fantasy
     */
    @GetMapping("/admin/final-fantasy-status")
    public ResponseEntity<ApiResponse<Object>> getFinalFantasyStatus() {
        try {
            Map<String, Object> status = new HashMap<>();

            Optional<SetEntity> finSet = setRepository.findByCode("FIN");
            if (finSet.isPresent()) {
                SetEntity set = finSet.get();
                long cardCount = cardRepository.countBySetCode("FIN");

                status.put("exists", true);
                status.put("name", set.getName());
                status.put("releaseDate", set.getReleaseDate());
                status.put("cardsSynced", set.getCardsSynced());
                status.put("cardsCount", cardCount);
                status.put("lastSyncAt", set.getLastSyncAt());

                // Vérifier les images
                List<CardEntity> cards = cardRepository.findBySetCodeOrderByNameAsc("FIN");
                long imagesDownloaded = cards.stream()
                        .mapToLong(card -> (card.getImageDownloaded() != null && card.getImageDownloaded()) ? 1 : 0)
                        .sum();

                status.put("imagesDownloaded", imagesDownloaded);
                status.put("imagesPercentage", cardCount > 0 ? (double) imagesDownloaded / cardCount * 100 : 0);

                // Recommandations
                List<String> recommendations = new ArrayList<>();
                if (cardCount == 0) {
                    recommendations.add("Synchroniser les cartes avec /admin/sync-final-fantasy");
                }
                if (cardCount > 0 && imagesDownloaded == 0) {
                    recommendations.add("Télécharger les images avec /api/images/download-set/FIN");
                }
                if (cardCount > 0) {
                    recommendations.add("Définir comme dernière extension avec /admin/set-fin-as-latest");
                }

                status.put("recommendations", recommendations);
            } else {
                status.put("exists", false);
                status.put("recommendations", List.of("Créer l'extension avec /admin/sync-final-fantasy"));
            }

            return ResponseEntity.ok(ApiResponse.success(status, "Statut Final Fantasy"));

        } catch (Exception e) {
            logger.error("❌ Erreur statut FIN : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Endpoint pour sauvegarder manuellement UNE extension spécifique
     */
    @PostMapping("/admin/save-set-manually/{setCode}")
    public Mono<ResponseEntity<? extends ApiResponse<? extends Object>>> saveSetManually(@PathVariable String setCode) {
        logger.info("🎯 Demande de sauvegarde manuelle de l'extension : {}", setCode);

        return mtgService.getAllSets()
                .map(sets -> {
                    // Trouver l'extension spécifique
                    Optional<MtgSet> targetSet = sets.stream()
                            .filter(set -> setCode.equalsIgnoreCase(set.code()))
                            .findFirst();

                    if (targetSet.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error("Extension " + setCode + " non trouvée"));
                    }

                    try {
                        // Sauvegarder uniquement cette extension
                        persistenceService.saveOrUpdateSet(targetSet.get());

                        String message = String.format("✅ Extension %s (%s) sauvegardée",
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
    /**
     * Endpoint pour sauvegarder manuellement les cartes d'une extension
     */
    @PostMapping("/admin/save-cards-manually/{setCode}")
    public Mono<ResponseEntity<ApiResponse<String>>> saveCardsManually(@PathVariable String setCode) {
        logger.info("🎯 Demande de sauvegarde manuelle des cartes pour : {}", setCode);

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

    /**
     * Endpoint pour charger les extensions depuis l'API SANS les sauvegarder
     */
    @GetMapping("/sets/load-from-api")
    public Mono<ResponseEntity<ApiResponse<List<MtgSet>>>> loadSetsFromApi() {
        logger.info("🌐 Chargement des extensions depuis l'API (sans sauvegarde)");

        return mtgService.fetchSetsFromApi()
                .map(sets -> ResponseEntity.ok(ApiResponse.success(sets,
                        sets.size() + " extensions chargées depuis l'API (non sauvegardées)")))
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors du chargement depuis l'API")));
    }

    /**
     * Endpoint pour charger les cartes depuis Scryfall SANS les sauvegarder
     */
    @GetMapping("/sets/{setCode}/load-from-scryfall")
    public Mono<ResponseEntity<ApiResponse<List<MtgCard>>>> loadCardsFromScryfall(@PathVariable String setCode) {
        logger.info("🔮 Chargement des cartes {} depuis Scryfall (sans sauvegarde)", setCode);

        return scryfallService.getCardsFromScryfall(setCode)
                .map(cards -> ResponseEntity.ok(ApiResponse.success(cards,
                        cards.size() + " cartes chargées depuis Scryfall pour " + setCode + " (non sauvegardées)")))
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors du chargement depuis Scryfall pour " + setCode)));
    }

    /**
     * Statistiques sans déclenchement de sauvegarde
     */
    @GetMapping("/admin/preview-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPreviewStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Stats base de données
            stats.put("setsInDatabase", setRepository.count());
            stats.put("cardsInDatabase", cardRepository.count());
            stats.put("syncedSets", setRepository.countSyncedSets());

            // Stats API (sans sauvegarder)
            try {
                List<MtgSet> apiSets = mtgService.getAllSets().block();
                stats.put("setsAvailableFromAPI", apiSets != null ? apiSets.size() : 0);
            } catch (Exception e) {
                stats.put("setsAvailableFromAPI", "Erreur de connexion API");
            }

            stats.put("message", "Données en lecture seule - aucune sauvegarde automatique");

            return ResponseEntity.ok(ApiResponse.success(stats, "Statistiques preview"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }


    // ENDPOINT POUR RECRÉER FIN SI NÉCESSAIRE
    @PostMapping("/debug/recreate-fin")
    public ResponseEntity<ApiResponse<String>> recreateFin() {
        try {
            logger.info("🎮 Re-création de l'extension Final Fantasy");

            // Vérifier si FIN existe déjà
            Optional<SetEntity> existingFin = setRepository.findByCode("FIN");
            if (existingFin.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success(
                        "Extension FIN existe déjà avec UUID: " + existingFin.get().getId(),
                        "Pas besoin de recréer"
                ));
            }

            // Créer nouvelle extension FIN
            SetEntity finSet = new SetEntity();
            finSet.setCode("FIN");
            finSet.setName("Magic: The Gathering - FINAL FANTASY");
            finSet.setType("expansion");
            finSet.setReleaseDate(LocalDate.of(2025, 6, 13));
            finSet.setCardsSynced(false);
            finSet.setCardsCount(0);

            SetEntity savedFin = setRepository.save(finSet);

            logger.info("✅ Extension FIN recréée avec UUID : {}", savedFin.getId());

            return ResponseEntity.ok(ApiResponse.success(
                    "Extension FIN recréée avec UUID: " + savedFin.getId(),
                    "Prête pour synchronisation"
            ));

        } catch (Exception e) {
            logger.error("❌ Erreur recréation FIN : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur recréation : " + e.getMessage()));
        }
    }

    @GetMapping("/sets/latest")
    public Mono<ResponseEntity<ApiResponse<MtgSet>>> getLatestSet() {
        return mtgService.getLatestSet()
                .map(set -> {
                    if (set != null) {
                        return ResponseEntity.ok(ApiResponse.success(set, "Dernière extension récupérée"));
                    } else {
                        return ResponseEntity.notFound().<ApiResponse<MtgSet>>build();
                    }
                })
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la récupération de la dernière extension")));
    }



// SQL DIRECT pour vérifier en base si nécessaire :
/*
-- Vérifier les extensions en base
SELECT BIN_TO_UUID(id) as uuid_id, code, name, cards_count, cards_synced
FROM sets
WHERE code = 'FIN' OR name LIKE '%FINAL%';

-- Compter les cartes FIN
SELECT COUNT(*) as fin_cards
FROM cards
WHERE set_code = 'FIN';

-- Lister toutes les extensions récentes
SELECT code, name, release_date, cards_count
FROM sets
ORDER BY release_date DESC
LIMIT 10;
*/

    /// ///////////////////

    @GetMapping("/sets")
    public Mono<ResponseEntity<ApiResponse<List<MtgSet>>>> getAllSets() {
        return mtgService.getAllSets()
                .map(sets -> ResponseEntity.ok(ApiResponse.success(sets, "Extensions récupérées avec succès")))
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la récupération des extensions")));
    }


    @GetMapping("/sets/latest/cards")
    public Mono<ResponseEntity<ApiResponse<MtgSet>>> getLatestSetWithCards() {
        return mtgService.getLatestSetWithCards()
                .map(set -> {
                    if (set != null) {
                        return ResponseEntity.ok(ApiResponse.success(set,
                                "Dernière extension avec cartes récupérée avec succès"));
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
                        "Cartes de l'extension " + setCode + " récupérées avec succès")))
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la récupération des cartes de l'extension " + setCode)));
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCards", cardRepository.count());
            stats.put("totalSets", setRepository.count());
            stats.put("syncedSets", setRepository.countSyncedSets());
            stats.put("distinctArtists", cardRepository.countDistinctArtists());

            // Stats images
            long totalImages = cardRepository.count();
            long downloadedImages = cardRepository.findByImageDownloadedTrueAndLocalImagePathIsNotNull().size();

            Map<String, Object> imageStats = new HashMap<>();
            imageStats.put("total", totalImages);
            imageStats.put("downloaded", downloadedImages);
            imageStats.put("percentage", totalImages > 0 ? (double) downloadedImages / totalImages * 100 : 0);

            stats.put("imageStats", imageStats);

            return ResponseEntity.ok(ApiResponse.success(stats, "Statistiques de la base"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    @PostMapping("/admin/sync-set/{setCode}")
    public ResponseEntity<ApiResponse<String>> forceSyncSet(@PathVariable String setCode) {
        try {
            logger.info("🔄 Synchronisation forcée demandée pour : {}", setCode);

            // Si c'est Final Fantasy et qu'elle n'existe pas, l'ajouter d'abord
            if ("FIN".equalsIgnoreCase(setCode)) {
                Optional<SetEntity> existingSet = setRepository.findByCode("FIN");
                if (existingSet.isEmpty()) {
                    logger.info("🎮 Extension Final Fantasy non trouvée, ajout en cours...");

                    SetEntity finalFantasySet = new SetEntity();
                    finalFantasySet.setCode("FIN");
                    finalFantasySet.setName("Magic: The Gathering—FINAL FANTASY");
                    finalFantasySet.setType("expansion");
                    finalFantasySet.setReleaseDate(LocalDate.of(2025, 6, 13));
                    finalFantasySet.setCardsSynced(false);

                    setRepository.save(finalFantasySet);
                    logger.info("✅ Extension Final Fantasy ajoutée automatiquement");

                    return ResponseEntity.ok(ApiResponse.success(
                            "Extension Final Fantasy créée et prête à être synchronisée : " + setCode,
                            "Extension ajoutée automatiquement car elle n'existait pas dans l'API externe"
                    ));
                }
            }

            // Lancer la synchronisation en arrière-plan (code existant)
            CompletableFuture.runAsync(() -> {
                try {
                    mtgService.forceSyncSet(setCode).subscribe(set -> {
                        if (set != null && set.cards() != null) {
                            logger.info("✅ Synchronisation terminée pour {} : {} cartes",
                                    setCode, set.cards().size());
                        }
                    });
                } catch (Exception e) {
                    logger.error("❌ Erreur sync : {}", e.getMessage());
                }
            });

            return ResponseEntity.accepted()
                    .body(ApiResponse.success("Synchronisation démarrée pour : " + setCode));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    @GetMapping("/admin/sets/unsynced")
    public ResponseEntity<ApiResponse<List<Object>>> getUnsyncedSets() {
        try {
            List<SetEntity> unsyncedSets = setRepository.findByCardsSyncedFalseOrderByReleaseDateDesc();

            // Convertir en format simple pour éviter les problèmes de sérialisation
            List<Object> result = unsyncedSets.stream()
                    .limit(20) // Limiter pour éviter une réponse trop grosse
                    .map(set -> {
                        Map<String, Object> setInfo = new HashMap<>();
                        setInfo.put("code", set.getCode());
                        setInfo.put("name", set.getName());
                        setInfo.put("type", set.getType());
                        setInfo.put("releaseDate", set.getReleaseDate());
                        setInfo.put("cardsSynced", set.getCardsSynced());
                        return setInfo;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(result,
                    "Extensions non synchronisées : " + unsyncedSets.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    // Ajoutez seulement cet endpoint dans votre MtgController
// (supprimez la partie Scryfall de mon message précédent)

    @PostMapping("/admin/add-final-fantasy-manually")
    public ResponseEntity<ApiResponse<String>> addFinalFantasyManually() {
        try {
            logger.info("🎮 Ajout manuel de l'extension Final Fantasy");

            // Vérifier si elle existe déjà
            Optional<SetEntity> existingSet = setRepository.findByCode("FIN");
            if (existingSet.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success(
                        "Extension Final Fantasy déjà présente avec le code : FIN",
                        "Extension déjà en base"
                ));
            }

            // Créer l'entité SetEntity pour Final Fantasy
            SetEntity finalFantasySet = new SetEntity();
            finalFantasySet.setCode("FIN");
            finalFantasySet.setName("Magic: The Gathering—FINAL FANTASY");
            finalFantasySet.setType("expansion");
            finalFantasySet.setReleaseDate(LocalDate.of(2025, 6, 13));
            finalFantasySet.setCardsSynced(false);

            // Sauvegarder dans la base
            SetEntity savedSet = setRepository.save(finalFantasySet);

            logger.info("✅ Extension Final Fantasy ajoutée : {}", savedSet.getCode());

            return ResponseEntity.ok(ApiResponse.success(
                    "Extension Final Fantasy ajoutée avec le code : " + savedSet.getCode(),
                    "Extension créée manuellement en attendant la mise à jour de l'API"
            ));

        } catch (Exception e) {
            logger.error("❌ Erreur lors de l'ajout manuel : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur lors de l'ajout : " + e.getMessage()));
        }
    }

    // Ajoutez aussi un endpoint pour ajouter plusieurs extensions 2025 manuellement
    @PostMapping("/admin/add-2025-sets-manually")
    public ResponseEntity<ApiResponse<String>> add2025SetsManually() {
        try {
            logger.info("🎮 Ajout manuel des extensions 2025");
            int addedCount = 0;

            // Liste des extensions 2025 connues
            Map<String, Object[]> sets2025 = Map.of(
                    "FIN", new Object[]{"Magic: The Gathering—FINAL FANTASY", "expansion", LocalDate.of(2025, 6, 13)},
                    "FIC", new Object[]{"Final Fantasy Commander", "commander", LocalDate.of(2025, 6, 13)},
                    "FCA", new Object[]{"Final Fantasy Through the Ages", "memorabilia", LocalDate.of(2025, 6, 13)},
                    "IRE", new Object[]{"Innistrad Remastered", "reprint", LocalDate.of(2025, 1, 24)},
                    "EOE", new Object[]{"Edge of Eternities", "expansion", LocalDate.of(2025, 8, 1)}
            );

            for (Map.Entry<String, Object[]> entry : sets2025.entrySet()) {
                String code = entry.getKey();
                Object[] data = entry.getValue();

                // Vérifier si elle existe déjà
                if (setRepository.findByCode(code).isEmpty()) {
                    SetEntity set = new SetEntity();
                    set.setCode(code);
                    set.setName((String) data[0]);
                    set.setType((String) data[1]);
                    set.setReleaseDate((LocalDate) data[2]);
                    set.setCardsSynced(false);

                    setRepository.save(set);
                    addedCount++;
                    logger.info("✅ Extension ajoutée : {} - {}", code, data[0]);
                }
            }

            return ResponseEntity.ok(ApiResponse.success(
                    addedCount + " extensions 2025 ajoutées",
                    "Extensions créées manuellement"
            ));

        } catch (Exception e) {
            logger.error("❌ Erreur lors de l'ajout manuel : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur lors de l'ajout : " + e.getMessage()));
        }
    }

    @GetMapping("/sets/{setCode}/with-cards")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Object>> getSetWithCards(@PathVariable String setCode) {
        try {
            logger.info("🔍 Récupération de l'extension {} avec cartes", setCode);

            // Chercher d'abord dans la base ET créer si nécessaire
            Optional<SetEntity> setEntity = setRepository.findByCode(setCode);

            if (setEntity.isEmpty()) {
                logger.info("🔧 Extension {} non trouvée, création automatique", setCode);

                SetEntity newSet = new SetEntity();
                newSet.setCode(setCode);
                newSet.setName(getSetNameFromCode(setCode));
                newSet.setType("expansion");
                newSet.setCardsSynced(false);
                newSet.setCardsCount(0);

                setKnownReleaseDate(newSet, setCode);

                setEntity = Optional.of(setRepository.save(newSet));
                logger.info("✅ Extension {} créée automatiquement", setCode);
            }

            SetEntity set = setEntity.get();

            // Récupérer les cartes avec TOUTES les collections chargées
            List<CardEntity> cards = cardRepository.findBySetCodeOrderByNameAsc(setCode);

            // CORRECTION: Construire la réponse manuellement pour éviter le lazy loading
            Map<String, Object> response = new HashMap<>();
            response.put("code", set.getCode());
            response.put("name", set.getName());
            response.put("type", set.getType());
            response.put("releaseDate", set.getReleaseDate());
            response.put("cardsSynced", set.getCardsSynced());
            response.put("totalCards", cards.size());

            // Statistiques par rareté - SÉCURISÉ
            Map<String, Long> rarityStats = cards.stream()
                    .collect(Collectors.groupingBy(
                            card -> card.getRarity() != null ? card.getRarity() : "unknown",
                            Collectors.counting()
                    ));
            response.put("rarityStats", rarityStats);

            // CORRECTION: Convertir les cartes manuellement pour éviter lazy loading
            List<Map<String, Object>> cardList = cards.stream()
                    .limit(200)
                    .map(card -> convertCardToSafeMap(card)) // Nouvelle méthode
                    .collect(Collectors.toList());

            response.put("cards", cardList);
            response.put("hasMoreCards", cards.size() > 200);

            String message = cards.isEmpty() ?
                    "Extension trouvée mais aucune carte synchronisée" :
                    String.format("Extension %s avec %d cartes", set.getName(), cards.size());

            return ResponseEntity.ok(ApiResponse.success(response, message));

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la récupération de l'extension {} : {}", setCode, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erreur lors de la récupération de l'extension : " + e.getMessage()));
        }
    }

    /**
     * NOUVELLE MÉTHODE: Convertir une CardEntity en Map de façon sécurisée
     */
    private Map<String, Object> convertCardToSafeMap(CardEntity card) {
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

            // GESTION SÉCURISÉE des collections
            try {
                cardMap.put("colors", card.getColors() != null ? new ArrayList<>(card.getColors()) : null);
            } catch (Exception e) {
                logger.debug("⚠️ Erreur chargement colors pour {} : {}", card.getName(), e.getMessage());
                cardMap.put("colors", null);
            }

            try {
                cardMap.put("colorIdentity", card.getColorIdentity() != null ? new ArrayList<>(card.getColorIdentity()) : null);
            } catch (Exception e) {
                logger.debug("⚠️ Erreur chargement colorIdentity pour {} : {}", card.getName(), e.getMessage());
                cardMap.put("colorIdentity", null);
            }

            try {
                cardMap.put("types", card.getTypes() != null ? new ArrayList<>(card.getTypes()) : null);
            } catch (Exception e) {
                logger.debug("⚠️ Erreur chargement types pour {} : {}", card.getName(), e.getMessage());
                cardMap.put("types", null);
            }

            try {
                cardMap.put("subtypes", card.getSubtypes() != null ? new ArrayList<>(card.getSubtypes()) : null);
            } catch (Exception e) {
                logger.debug("⚠️ Erreur chargement subtypes pour {} : {}", card.getName(), e.getMessage());
                cardMap.put("subtypes", null);
            }

            try {
                cardMap.put("supertypes", card.getSupertypes() != null ? new ArrayList<>(card.getSupertypes()) : null);
            } catch (Exception e) {
                logger.debug("⚠️ Erreur chargement supertypes pour {} : {}", card.getName(), e.getMessage());
                cardMap.put("supertypes", null);
            }

        } catch (Exception e) {
            logger.error("❌ Erreur conversion carte {} : {}", card.getName(), e.getMessage());
            // Retourner une carte minimale en cas d'erreur
            cardMap = new HashMap<>();
            cardMap.put("id", card.getId());
            cardMap.put("name", card.getName());
            cardMap.put("error", "Erreur chargement données");
        }

        return cardMap;
    }

    /**
     * NOUVELLE MÉTHODE HELPER: Obtenir le nom d'une extension depuis son code
     */
    private String getSetNameFromCode(String setCode) {
        Map<String, String> knownSets = Map.of(
                "BLB", "Bloomburrow",
                "MH3", "Modern Horizons 3",
                "OTJ", "Outlaws of Thunder Junction",
                "MKM", "Murders at Karlov Manor",
                "LCI", "The Lost Caverns of Ixalan",
                "WOE", "Wilds of Eldraine",
                "LTR", "The Lord of the Rings: Tales of Middle-earth",
                "FIN", "Magic: The Gathering - FINAL FANTASY"
        );

        return knownSets.getOrDefault(setCode, setCode + " (Extension)");
    }


    /**
     * NOUVELLE MÉTHODE HELPER: Définir les dates de sortie connues
     */
    private void setKnownReleaseDate(SetEntity set, String setCode) {
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

    /**
     * NOUVEAU ENDPOINT: Sauvegarder manuellement une extension ET ses cartes
     */
    @PostMapping("/admin/save-extension-complete/{setCode}")
    public ResponseEntity<ApiResponse<String>> saveExtensionComplete(@PathVariable String setCode) {
        try {
            logger.info("💾 Sauvegarde complète de l'extension : {}", setCode);

            // 1. S'assurer que l'extension existe
            Optional<SetEntity> setEntity = setRepository.findByCode(setCode);
            if (setEntity.isEmpty()) {
                SetEntity newSet = new SetEntity();
                newSet.setCode(setCode);
                newSet.setName(getSetNameFromCode(setCode));
                newSet.setType("expansion");
                newSet.setCardsSynced(false);
                setKnownReleaseDate(newSet, setCode);

                setRepository.save(newSet);
                logger.info("✅ Extension {} créée", setCode);
            }

            // 2. Déclencher la sauvegarde des cartes
            CompletableFuture.runAsync(() -> {
                try {
                    // Récupérer et sauvegarder les cartes
                    List<MtgCard> cards = mtgService.getCardsFromSet(setCode).block();
                    if (cards != null && !cards.isEmpty()) {
                        persistenceService.saveCards(cards, setCode);
                        logger.info("✅ {} cartes sauvegardées pour {}", cards.size(), setCode);
                    }
                } catch (Exception e) {
                    logger.error("❌ Erreur sauvegarde cartes {} : {}", setCode, e.getMessage());
                }
            });

            return ResponseEntity.accepted()
                    .body(ApiResponse.success("Sauvegarde complète démarrée pour : " + setCode));

        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde complète {} : {}", setCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }


    // Ajoutez aussi cet endpoint pour récupérer une extension sans cartes
    @GetMapping("/sets/{setCode}")
    public ResponseEntity<ApiResponse<Object>> getSet(@PathVariable String setCode) {
        try {
            Optional<SetEntity> setEntity = setRepository.findByCode(setCode);
            if (setEntity.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            SetEntity set = setEntity.get();
            long cardCount = cardRepository.countBySetCode(setCode);

            Map<String, Object> response = new HashMap<>();
            response.put("code", set.getCode());
            response.put("name", set.getName());
            response.put("type", set.getType());
            response.put("releaseDate", set.getReleaseDate());
            response.put("cardsSynced", set.getCardsSynced());
            response.put("totalCards", cardCount);

            return ResponseEntity.ok(ApiResponse.success(response, "Extension trouvée"));

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la récupération de l'extension {} : {}", setCode, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    // Ajoutez cet endpoint pour corriger l'encodage de Final Fantasy

    @PostMapping("/admin/fix-final-fantasy-encoding")
    public ResponseEntity<ApiResponse<String>> fixFinalFantasyEncoding() {
        try {
            logger.info("🔧 Correction de l'encodage Final Fantasy");

            // Supprimer l'ancienne entrée
            setRepository.deleteByCode("FIN");

            // Recréer avec le bon encodage
            SetEntity finalFantasySet = new SetEntity();
            finalFantasySet.setCode("FIN");
            finalFantasySet.setName("Magic: The Gathering - FINAL FANTASY"); // Sans caractères spéciaux
            finalFantasySet.setType("expansion");
            finalFantasySet.setReleaseDate(LocalDate.of(2025, 6, 13));
            finalFantasySet.setCardsSynced(false);

            SetEntity savedSet = setRepository.save(finalFantasySet);

            logger.info("✅ Extension Final Fantasy corrigée : {}", savedSet.getCode());

            return ResponseEntity.ok(ApiResponse.success(
                    "Extension Final Fantasy corrigee avec encodage UTF-8",
                    "Caracteres speciaux remplaces"
            ));

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la correction : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur lors de la correction : " + e.getMessage()));
        }
    }

    // Ajoutez ces méthodes dans votre MtgController existant

    /**
     * Endpoint de debug pour voir toutes les extensions disponibles
     */
    @GetMapping("/debug/all-sets")
    public ResponseEntity<ApiResponse<List<Object>>> debugAllSets() {
        try {
            List<SetEntity> allSets = setRepository.findAll();

            List<Object> result = allSets.stream()
                    .map(set -> {
                        Map<String, Object> setInfo = new HashMap<>();
                        setInfo.put("code", set.getCode());
                        setInfo.put("name", set.getName());
                        setInfo.put("type", set.getType());
                        setInfo.put("releaseDate", set.getReleaseDate());
                        setInfo.put("cardsSynced", set.getCardsSynced());
                        setInfo.put("cardsCount", cardRepository.countBySetCode(set.getCode()));
                        return setInfo;
                    })
                    .sorted((a, b) -> {
                        LocalDate dateA = (LocalDate) a.get("releaseDate");
                        LocalDate dateB = (LocalDate) b.get("releaseDate");
                        if (dateA == null && dateB == null) return 0;
                        if (dateA == null) return 1;
                        if (dateB == null) return -1;
                        return dateB.compareTo(dateA); // Plus récent en premier
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(result, "Debug - toutes les extensions"));
        } catch (Exception e) {
            logger.error("❌ Erreur debug all sets : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Endpoint pour tester quelle est la dernière extension détectée
     */
    @GetMapping("/debug/latest-set-detection")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> debugLatestSetDetection() {
        return mtgService.getLatestSet()
                .map(latestSet -> {
                    if (latestSet == null) {
                        Map<String, Object> debug = new HashMap<>();
                        debug.put("error", "Aucune dernière extension trouvée");
                        debug.put("suggestion", "Vérifier la logique de détection");

                        // Ajouter des infos de debug
                        List<SetEntity> recentSets = setRepository.findLatestSets();
                        debug.put("setsInDb", recentSets.size());
                        debug.put("firstFiveSets", recentSets.stream()
                                .limit(5)
                                .map(set -> set.getCode() + " - " + set.getName() + " (" + set.getReleaseDate() + ")")
                                .collect(Collectors.toList()));

                        return ResponseEntity.ok(ApiResponse.success(debug, "Debug - aucune extension trouvée"));
                    }

                    Map<String, Object> result = new HashMap<>();
                    result.put("code", latestSet.code());
                    result.put("name", latestSet.name());
                    result.put("type", latestSet.type());
                    result.put("releaseDate", latestSet.releaseDate());

                    // Vérifier si elle a des cartes
                    long cardCount = cardRepository.countBySetCode(latestSet.code());
                    result.put("cardsCount", cardCount);
                    result.put("hasSyncedCards", cardCount > 0);

                    return ResponseEntity.ok(ApiResponse.success(result, "Dernière extension détectée"));
                }).onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la détection")));
    }

    /**
     * Endpoint pour forcer le rechargement d'une extension spécifique depuis l'API
     */
    @PostMapping("/debug/force-reload-set/{setCode}")
    public ResponseEntity<ApiResponse<String>> forceReloadSet(@PathVariable String setCode) {
        try {
            logger.info("🔄 Rechargement forcé de l'extension : {}", setCode);

            // Supprimer les cartes existantes pour cette extension
            List<CardEntity> existingCards = cardRepository.findBySetCodeOrderByNameAsc(setCode);
            if (!existingCards.isEmpty()) {
                cardRepository.deleteAll(existingCards);
                logger.info("🗑️ {} cartes supprimées pour {}", existingCards.size(), setCode);
            }

            // Marquer l'extension comme non synchronisée
            setRepository.findByCode(setCode).ifPresent(set -> {
                set.setCardsSynced(false);
                set.setCardsCount(0);
                setRepository.save(set);
            });

            // Déclencher la synchronisation
            mtgService.forceSyncSet(setCode).subscribe(
                    set -> logger.info("✅ Rechargement terminé pour {}", setCode),
                    error -> logger.error("❌ Erreur rechargement {} : {}", setCode, error.getMessage())
            );

            return ResponseEntity.accepted()
                    .body(ApiResponse.success("Rechargement démarré pour : " + setCode));

        } catch (Exception e) {
            logger.error("❌ Erreur force reload : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Endpoint pour ajouter manuellement les extensions 2024-2025 populaires
     */
    @PostMapping("/debug/add-recent-sets")
    public ResponseEntity<ApiResponse<String>> addRecentSets() {
        try {
            logger.info("🎮 Ajout des extensions récentes 2024-2025");
            int addedCount = 0;

            // Extensions récentes populaires avec leurs vraies dates
            Map<String, Object[]> recentSets = Map.of(
                    "BLB", new Object[]{"Bloomburrow", "expansion", LocalDate.of(2024, 8, 2)},
                    "MH3", new Object[]{"Modern Horizons 3", "draft_innovation", LocalDate.of(2024, 6, 14)},
                    "OTJ", new Object[]{"Outlaws of Thunder Junction", "expansion", LocalDate.of(2024, 4, 19)},
                    "MKM", new Object[]{"Murders at Karlov Manor", "expansion", LocalDate.of(2024, 2, 9)},
                    "LCI", new Object[]{"The Lost Caverns of Ixalan", "expansion", LocalDate.of(2023, 11, 17)},
                    "WOE", new Object[]{"Wilds of Eldraine", "expansion", LocalDate.of(2023, 9, 8)},
                    "LTR", new Object[]{"The Lord of the Rings: Tales of Middle-earth", "expansion", LocalDate.of(2023, 6, 23)}
            );

            for (Map.Entry<String, Object[]> entry : recentSets.entrySet()) {
                String code = entry.getKey();
                Object[] data = entry.getValue();

                Optional<SetEntity> existing = setRepository.findByCode(code);
                if (existing.isEmpty()) {
                    SetEntity set = new SetEntity();
                    set.setCode(code);
                    set.setName((String) data[0]);
                    set.setType((String) data[1]);
                    set.setReleaseDate((LocalDate) data[2]);
                    set.setCardsSynced(false);

                    setRepository.save(set);
                    addedCount++;
                    logger.info("✅ Extension ajoutée : {} - {}", code, data[0]);
                } else {
                    // Mettre à jour la date si nécessaire
                    SetEntity existing_set = existing.get();
                    existing_set.setReleaseDate((LocalDate) data[2]);
                    setRepository.save(existing_set);
                    logger.info("🔄 Extension mise à jour : {} - {}", code, data[0]);
                }
            }

            return ResponseEntity.ok(ApiResponse.success(
                    addedCount + " extensions récentes ajoutées/mises à jour",
                    "Extensions 2023-2024 ajoutées"
            ));

        } catch (Exception e) {
            logger.error("❌ Erreur ajout extensions récentes : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    // Ajoutez ces endpoints dans votre MtgController existant

    /**
     * Endpoint pour sauvegarder complètement une extension (base + images)
     */
    @PostMapping("/admin/save-complete/{setCode}")
    public ResponseEntity<ApiResponse<String>> saveCompleteSet(@PathVariable String setCode) {
        try {
            logger.info("💾 Sauvegarde complète de l'extension : {}", setCode);

            // 1. Synchroniser les cartes en base
            CompletableFuture<Void> syncFuture = CompletableFuture.runAsync(() -> {
                try {
                    mtgService.forceSyncSet(setCode).subscribe(
                            set -> {
                                if (set != null && set.cards() != null) {
                                    logger.info("✅ {} cartes synchronisées pour {}", set.cards().size(), setCode);
                                }
                            },
                            error -> logger.error("❌ Erreur sync cartes {} : {}", setCode, error.getMessage())
                    );
                } catch (Exception e) {
                    logger.error("❌ Erreur sync {} : {}", setCode, e.getMessage());
                }
            });

            // 2. Attendre un peu puis déclencher le téléchargement des images
            CompletableFuture<Void> imagesFuture = syncFuture.thenRunAsync(() -> {
                try {
                    // Attendre 2 secondes que la sync soit terminée
                    Thread.sleep(2000);

                    // Déclencher le téléchargement des images
                    imageDownloadService.downloadImagesForSet(setCode)
                            .thenAccept(count -> {
                                logger.info("🖼️ {} images téléchargées pour {}", count, setCode);
                            });

                } catch (Exception e) {
                    logger.error("❌ Erreur téléchargement images {} : {}", setCode, e.getMessage());
                }
            });

            return ResponseEntity.accepted()
                    .body(ApiResponse.success("Sauvegarde complète démarrée pour : " + setCode,
                            "Les cartes seront synchronisées en base et les images téléchargées"));

        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde complète {} : {}", setCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur lors de la sauvegarde complète : " + e.getMessage()));
        }
    }

    /**
     * Endpoint pour obtenir le statut détaillé d'une extension
     */
    @GetMapping("/admin/set-status/{setCode}")
    public ResponseEntity<ApiResponse<Object>> getSetStatus(@PathVariable String setCode) {
        try {
            Optional<SetEntity> setEntity = setRepository.findByCode(setCode);
            if (setEntity.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            SetEntity set = setEntity.get();
            List<CardEntity> cards = cardRepository.findBySetCodeOrderByNameAsc(setCode);

            // Statistiques des images
            long totalCards = cards.size();
            long downloadedImages = cards.stream()
                    .mapToLong(card -> (card.getImageDownloaded() != null && card.getImageDownloaded()) ? 1 : 0)
                    .sum();

            // Statistiques par rareté
            Map<String, Long> rarityStats = cards.stream()
                    .collect(Collectors.groupingBy(
                            card -> card.getRarity() != null ? card.getRarity() : "Unknown",
                            Collectors.counting()
                    ));

            Map<String, Object> status = new HashMap<>();
            status.put("code", set.getCode());
            status.put("name", set.getName());
            status.put("type", set.getType());
            status.put("releaseDate", set.getReleaseDate());
            status.put("cardsSynced", set.getCardsSynced());
            status.put("cardsCount", totalCards);
            status.put("imagesDownloaded", downloadedImages);
            status.put("imagesPercentage", totalCards > 0 ? (double) downloadedImages / totalCards * 100 : 0);
            status.put("rarityStats", rarityStats);
            status.put("lastSyncAt", set.getLastSyncAt());

            return ResponseEntity.ok(ApiResponse.success(status, "Statut de l'extension " + setCode));

        } catch (Exception e) {
            logger.error("❌ Erreur statut extension {} : {}", setCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Endpoint pour forcer la synchronisation en temps réel (avec WebSocket si disponible)
     */
    @PostMapping("/admin/force-sync-realtime/{setCode}")
    public ResponseEntity<ApiResponse<String>> forceSyncRealtime(@PathVariable String setCode) {
        try {
            logger.info("⚡ Synchronisation temps réel pour : {}", setCode);

            // Nettoyer les anciennes données
            List<CardEntity> existingCards = cardRepository.findBySetCodeOrderByNameAsc(setCode);
            if (!existingCards.isEmpty()) {
                cardRepository.deleteAll(existingCards);
                logger.info("🗑️ {} anciennes cartes supprimées", existingCards.size());
            }

            // Marquer comme non synchronisé
            setRepository.findByCode(setCode).ifPresent(set -> {
                set.setCardsSynced(false);
                set.setCardsCount(0);
                setRepository.save(set);
            });

            // Déclencher la synchronisation
            mtgService.forceSyncSet(setCode)
                    .doOnNext(set -> {
                        if (set != null && set.cards() != null) {
                            logger.info("⚡ Sync temps réel terminée : {} cartes pour {}",
                                    set.cards().size(), setCode);
                        }
                    })
                    .subscribe();

            return ResponseEntity.accepted()
                    .body(ApiResponse.success("Synchronisation temps réel démarrée pour : " + setCode));

        } catch (Exception e) {
            logger.error("❌ Erreur sync temps réel {} : {}", setCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Endpoint pour obtenir les statistiques de toutes les extensions
     */
    @GetMapping("/admin/all-sets-status")
    public ResponseEntity<ApiResponse<List<Object>>> getAllSetsStatus() {
        try {
            List<SetEntity> allSets = setRepository.findAll();

            List<Object> result = allSets.stream()
                    .map(set -> {
                        long cardCount = cardRepository.countBySetCode(set.getCode());
                        long imageCount = cardRepository.findBySetCodeOrderByNameAsc(set.getCode())
                                .stream()
                                .mapToLong(card -> (card.getImageDownloaded() != null && card.getImageDownloaded()) ? 1 : 0)
                                .sum();

                        Map<String, Object> setInfo = new HashMap<>();
                        setInfo.put("code", set.getCode());
                        setInfo.put("name", set.getName());
                        setInfo.put("type", set.getType());
                        setInfo.put("releaseDate", set.getReleaseDate());
                        setInfo.put("cardsSynced", set.getCardsSynced());
                        setInfo.put("cardsCount", cardCount);
                        setInfo.put("imagesCount", imageCount);
                        setInfo.put("completionPercentage", cardCount > 0 ? (double) imageCount / cardCount * 100 : 0);
                        return setInfo;
                    })
                    .sorted((a, b) -> {
                        LocalDate dateA = (LocalDate) a.get("releaseDate");
                        LocalDate dateB = (LocalDate) b.get("releaseDate");
                        if (dateA == null && dateB == null) return 0;
                        if (dateA == null) return 1;
                        if (dateB == null) return -1;
                        return dateB.compareTo(dateA); // Plus récent en premier
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(result, "Statut de toutes les extensions"));

        } catch (Exception e) {
            logger.error("❌ Erreur statut toutes extensions : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

}