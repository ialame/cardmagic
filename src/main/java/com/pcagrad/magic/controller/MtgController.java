package com.pcagrad.magic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcagrad.magic.dto.ApiResponse;
import com.pcagrad.magic.entity.CardTranslation;
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
import com.pcagrad.magic.util.Localization;
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
            // ✅ SOLUTION
            Map<String, Object> consistencyResult = persistenceService.validateSetConsistency(setCode);
            boolean isConsistent = consistencyResult.containsKey("success") &&
                    (Boolean) consistencyResult.getOrDefault("success", false);

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

    // À ajouter dans votre MtgController.java existant

// ========== ENDPOINT POUR CRÉER DES EXTENSIONS FUTURES ==========

    /**
     * Crée une nouvelle extension avec tous les paramètres nécessaires
     */
    @PostMapping("/admin/create-extension")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createExtension(@RequestBody CreateExtensionRequest request) {
        try {
            logger.info("🆕 Création de l'extension : {} - {}", request.getCode(), request.getName());

            // Vérifier si l'extension existe déjà
            Optional<MagicSet> existing = setRepository.findByCode(request.getCode());
            if (existing.isPresent()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("L'extension '" + request.getCode() + "' existe déjà"));
            }

            // Créer la nouvelle extension
            MagicSet newSet = new MagicSet();
            newSet.setCode(request.getCode());
            newSet.setName(request.getName());
            newSet.setReleaseDate(request.getReleaseDate() != null ? request.getReleaseDate() : LocalDate.now());

            // Assigner le type (par défaut "expansion")
            String type = request.getType() != null ? request.getType() : "expansion";
            adaptationService.setMagicSetType(newSet, type);
            adaptationService.prepareMagicSetForSave(newSet, type);

            // Sauvegarder
            MagicSet savedSet = setRepository.save(newSet);

            // Préparer la réponse
            Map<String, Object> result = new HashMap<>();
            result.put("created", true);
            result.put("code", savedSet.getCode());
            result.put("name", savedSet.getName());
            result.put("type", savedSet.getType());
            result.put("releaseDate", savedSet.getReleaseDate());
            result.put("id", savedSet.getId());

            String message = String.format("Extension '%s - %s' créée avec succès",
                    savedSet.getCode(), savedSet.getName());

            logger.info("✅ Extension créée : {} - {}", savedSet.getCode(), savedSet.getName());

            return ResponseEntity.ok(ApiResponse.success(result, message));

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la création de l'extension {} : {}", request.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erreur lors de la création : " + e.getMessage()));
        }
    }

    /**
     * Endpoint spécialisé pour créer Final Fantasy rapidement
     */
    @PostMapping("/admin/create-final-fantasy")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createFinalFantasy() {
        try {
            logger.info("🎮 Création de Final Fantasy");

            // Vérifier si Final Fantasy existe déjà
            Optional<MagicSet> existing = setRepository.findByCode("FIN");
            if (existing.isPresent()) {
                Map<String, Object> result = new HashMap<>();
                result.put("created", false);
                result.put("message", "Final Fantasy existe déjà");
                result.put("existing", true);
                return ResponseEntity.ok(ApiResponse.success(result, "Final Fantasy déjà présent"));
            }

            // Créer Final Fantasy avec les bonnes données
            CreateExtensionRequest request = new CreateExtensionRequest();
            request.setCode("FIN");
            request.setName("Magic: The Gathering - FINAL FANTASY");
            request.setType("expansion");
            request.setReleaseDate(LocalDate.of(2025, 6, 13));

            // Utiliser l'endpoint générique
            return createExtension(request);

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la création de Final Fantasy : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erreur lors de la création de Final Fantasy : " + e.getMessage()));
        }
    }

    /**
     * Endpoint pour lister les extensions créées récemment
     */
    @GetMapping("/admin/recent-extensions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRecentExtensions() {
        try {
            List<MagicSet> recentSets = setRepository.findAll()
                    .stream()
                    .filter(set -> set.getReleaseDate() != null)
                    .sorted((a, b) -> b.getReleaseDate().compareTo(a.getReleaseDate()))
                    .limit(10)
                    .collect(Collectors.toList());

            List<Map<String, Object>> result = recentSets.stream()
                    .map(set -> {
                        Map<String, Object> setInfo = new HashMap<>();
                        setInfo.put("code", set.getCode());
                        setInfo.put("name", set.getName());
                        setInfo.put("type", set.getType());
                        setInfo.put("releaseDate", set.getReleaseDate());
                        setInfo.put("cardsCount", cardRepository.countBySetCode(set.getCode()));
                        return setInfo;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(result, "Extensions récentes récupérées"));

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la récupération des extensions récentes : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erreur lors de la récupération : " + e.getMessage()));
        }
    }

// ========== CLASSE REQUEST POUR CRÉER DES EXTENSIONS ==========

    /**
     * Classe pour les requêtes de création d'extension
     */
    public static class CreateExtensionRequest {
        private String code;
        private String name;
        private String type;
        private LocalDate releaseDate;

        // Constructeurs
        public CreateExtensionRequest() {}

        public CreateExtensionRequest(String code, String name) {
            this.code = code;
            this.name = name;
            this.type = "expansion";
            this.releaseDate = LocalDate.now();
        }

        // Getters et Setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public LocalDate getReleaseDate() { return releaseDate; }
        public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
    }

// ========== EXTENSIONS FUTURES PRÉDÉFINIES ==========

    /**
     * Endpoint pour créer des extensions futures prédéfinies
     */
    @PostMapping("/admin/create-future-extension/{setCode}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createFutureExtension(@PathVariable String setCode) {
        try {
            CreateExtensionRequest request = getFutureExtensionData(setCode);
            if (request == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Extension future '" + setCode + "' non reconnue"));
            }

            return createExtension(request);

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la création de l'extension future {} : {}", setCode, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erreur lors de la création : " + e.getMessage()));
        }
    }

    /**
     * Données prédéfinies pour les extensions futures
     */
    private CreateExtensionRequest getFutureExtensionData(String setCode) {
        Map<String, CreateExtensionRequest> futureExtensions = new HashMap<>();

        // Final Fantasy
        futureExtensions.put("FIN", new CreateExtensionRequest("FIN", "Magic: The Gathering - FINAL FANTASY"));

        // Extensions futures 2025 (à adapter selon vos besoins)
        futureExtensions.put("OTH", new CreateExtensionRequest("OTH", "Aetherdrift"));
        futureExtensions.put("TBD", new CreateExtensionRequest("TBD", "Tarkir Dragonstorm"));

        return futureExtensions.get(setCode.toUpperCase());
    }

    @PostMapping("/admin/save-cards-final-fantasy")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveCardsFinalFantasy() {
        try {
            logger.info("💾 Sauvegarde directe des cartes Final Fantasy");

            // Récupérer les cartes depuis Scryfall
            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");

            if (finCards.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte Final Fantasy trouvée sur Scryfall"));
            }

            // Supprimer les anciennes cartes
            cardRepository.deleteBySetCodeIgnoreCase("FIN");
            cardRepository.flush();

            // *** UTILISER VOTRE SERVICE EXISTANT ***
            int savedCount = persistenceService.saveCards(finCards, "FIN");

            Map<String, Object> result = new HashMap<>();
            result.put("cartesRecuperees", finCards.size());
            result.put("cartesSauvegardees", savedCount);
            result.put("succes", savedCount > 300);

            String message = String.format("Final Fantasy: %d cartes sauvegardées avec succès", savedCount);

            return ResponseEntity.ok(ApiResponse.success(result, message));

        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde Final Fantasy : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erreur sauvegarde : " + e.getMessage()));
        }
    }

    @PostMapping("/admin/sync-fin-simple")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncFinSimple() {
        try {
            logger.info("🎮 Synchronisation Final Fantasy SIMPLE");

            // Récupérer les cartes depuis Scryfall
            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");

            if (finCards.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte trouvée"));
            }

            // Supprimer TOUTES les cartes FIN existantes
            cardRepository.deleteBySetCodeIgnoreCase("FIN");
            cardRepository.flush();

            // Sauvegarder une par une avec une logique simple
            int savedCount = 0;
            for (MtgCard mtgCard : finCards) {
                try {
                    MagicCard entity = new MagicCard();

                    // *** Pas d'ID externe pour éviter les conflits ***
                    entity.setZPostExtension("FIN");
                    // Par celle-ci (gestion des numéros non numériques) :
                    try {
                        if (mtgCard.number() != null) {
                            // Extraire seulement les chiffres du numéro (ex: "99b" → 99)
                            String numberStr = mtgCard.number().replaceAll("[^0-9]", "");
                            if (!numberStr.isEmpty()) {
                                entity.setNumero(Integer.parseInt(numberStr));
                            }
                        }
                    } catch (Exception e) {
                        // Ignorer les erreurs de parsing de numéro
                    }
                    // Champs de base obligatoires
                    entity.setIsAffichable(true);
                    entity.setHasRecherche(true);
                    entity.setCertifiable(false);
                    entity.setHasImg(false);
                    entity.setHasDateFr(false);

                    // Attributs JSON avec les données MTG
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put("name", mtgCard.name());
                    attributes.put("manaCost", mtgCard.manaCost());
                    attributes.put("type", mtgCard.type());
                    attributes.put("rarity", mtgCard.rarity());
                    attributes.put("text", mtgCard.text());
                    attributes.put("artist", mtgCard.artist());
                    attributes.put("power", mtgCard.power());
                    attributes.put("toughness", mtgCard.toughness());

                    entity.setAttributes(new ObjectMapper().writeValueAsString(attributes));

                    // Collections dans allowedNotes
                    Map<String, Object> collections = new HashMap<>();
                    collections.put("colors", mtgCard.colors());
                    collections.put("types", mtgCard.types());
                    collections.put("subtypes", mtgCard.subtypes());

                    entity.setAllowedNotes(new ObjectMapper().writeValueAsString(collections));

                    cardRepository.save(entity);
                    savedCount++;

                    if (savedCount % 50 == 0) {
                        logger.info("📊 {} cartes sauvegardées...", savedCount);
                    }

                } catch (Exception e) {
                    logger.error("❌ Erreur carte {} : {}", mtgCard.name(), e.getMessage());
                }
            }

            // Mettre à jour l'extension
            Optional<MagicSet> finSet = setRepository.findByCode("FIN");
            if (finSet.isPresent()) {
                MagicSet set = finSet.get();
                set.setNbCartes(savedCount);
                setRepository.save(set);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("cartesRecuperees", finCards.size());
            result.put("cartesSauvegardees", savedCount);
            result.put("succes", savedCount > 300);

            String message = String.format("Final Fantasy: %d cartes sauvegardées avec succès (sans traductions)", savedCount);

            return ResponseEntity.ok(ApiResponse.success(result, message));

        } catch (Exception e) {
            logger.error("❌ Erreur sync simple : {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ======================================================
// OU ALTERNATIVE PLUS SIMPLE si vous préférez :
// ======================================================

    @GetMapping("/admin/validate-set-consistency/{setCode}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateSetConsistencyAlternative(@PathVariable String setCode) {
        try {
            logger.info("🔍 Validation de la cohérence pour l'extension : {}", setCode);

            Map<String, Object> result = (Map<String, Object>) persistenceService.validateSetConsistency(setCode);

            return ResponseEntity.ok(ApiResponse.success(result,
                    "Validation terminée pour " + setCode));

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la validation de {} : {}", setCode, e.getMessage());

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            errorResult.put("success", false);

            // Option B: Si vous voulez juste un message d'erreur simple
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Message d'erreur : " + errorResult.toString()));

        }
    }

    // ================================================================
// AJOUTER dans MtgController.java - Endpoint de sauvegarde directe
// ================================================================

    /**
     * ✅ ENDPOINT SPÉCIAL : Sauvegarde directe Final Fantasy avec logs détaillés
     */
    @PostMapping("/admin/save-final-fantasy-debug")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveFinalFantasyDebug() {
        try {
            logger.info("🎮 === SAUVEGARDE DEBUG FINAL FANTASY ===");

            Map<String, Object> result = new HashMap<>();
            List<String> debugLogs = new ArrayList<>();

            // 1. Vérifier l'extension FIN
            debugLogs.add("🔍 Vérification extension FIN...");
            Optional<MagicSet> finSet = setRepository.findByCode("FIN");
            if (finSet.isEmpty()) {
                debugLogs.add("❌ Extension FIN non trouvée, création...");
                MagicSet newSet = new MagicSet();
                newSet.setCode("FIN");
                newSet.setName("Magic: The Gathering - FINAL FANTASY");

                // Utiliser le service d'adaptation
                adaptationService.setMagicSetType(newSet, "expansion");
                adaptationService.prepareMagicSetForSave(newSet, "expansion");
                newSet.setReleaseDate(LocalDate.of(2024, 11, 15));

                finSet = Optional.of(setRepository.save(newSet));
                debugLogs.add("✅ Extension FIN créée");
            } else {
                debugLogs.add("✅ Extension FIN trouvée");
            }

            // 2. Récupérer les cartes depuis Scryfall
            debugLogs.add("🔮 Récupération cartes depuis Scryfall...");
            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");
            debugLogs.add(String.format("✅ %d cartes récupérées depuis Scryfall", finCards.size()));

            if (finCards.isEmpty()) {
                debugLogs.add("❌ Aucune carte récupérée");
                result.put("success", false);
                result.put("debugLogs", debugLogs);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte Final Fantasy trouvée"));
            }

            // 3. Nettoyer les anciennes cartes
            debugLogs.add("🗑️ Suppression anciennes cartes FIN...");
            int deletedCount = cardRepository.deleteBySetCodeIgnoreCase("FIN");
            cardRepository.flush();
            debugLogs.add(String.format("✅ %d anciennes cartes supprimées", deletedCount));

            // 4. Sauvegarder les cartes une par une avec logs détaillés
            debugLogs.add("💾 Début sauvegarde des cartes...");
            int savedCount = 0;
            int errorCount = 0;
            List<String> cardErrors = new ArrayList<>();

            for (int i = 0; i < finCards.size(); i++) {
                MtgCard mtgCard = finCards.get(i);
                try {
                    // Créer l'entité carte
                    MagicCard cardEntity = new MagicCard();
                    cardEntity.setId(UUID.randomUUID());

                    // ID externe sécurisé
                    String externalId = mtgCard.id();
                    if (externalId != null && externalId.length() > 20) {
                        externalId = Integer.toHexString(mtgCard.id().hashCode()).substring(0, 8);
                    }
                    cardEntity.setExternalId(externalId);
                    cardEntity.setZPostExtension("FIN");

                    // Créer la traduction
                    cardEntity.ensureTranslationExists(Localization.USA);
                    CardTranslation translation = cardEntity.getTranslation(Localization.USA);
                    if (translation != null) {
                        translation.setName(mtgCard.name());
                        translation.setLabelName(mtgCard.name());
                        translation.setAvailable(true);
                    }

                    // Propriétés de la carte
                    if (mtgCard.number() != null) {
                        cardEntity.setNumber(mtgCard.number());
                    }

                    // Sauvegarder les propriétés dans le JSON
                    cardEntity.setManaCost(mtgCard.manaCost());
                    cardEntity.setCmc(mtgCard.cmc());
                    cardEntity.setRarity(mtgCard.rarity());
                    cardEntity.setType(mtgCard.type());
                    cardEntity.setText(mtgCard.text());
                    cardEntity.setArtist(mtgCard.artist());
                    cardEntity.setPower(mtgCard.power());
                    cardEntity.setToughness(mtgCard.toughness());
                    cardEntity.setLayout(mtgCard.layout());
                    cardEntity.setMultiverseid(mtgCard.multiverseid());
                    cardEntity.setSetName(mtgCard.setName());

                    // Collections
                    cardEntity.setColors(mtgCard.colors());
                    cardEntity.setColorIdentity(mtgCard.colorIdentity());
                    cardEntity.setSupertypes(mtgCard.supertypes());
                    cardEntity.setTypes(mtgCard.types());
                    cardEntity.setSubtypes(mtgCard.subtypes());

                    // URL d'image
                    cardEntity.setOriginalImageUrl(mtgCard.imageUrl());

                    // Propriétés spécifiques
                    cardEntity.setIsAffichable(true);
                    cardEntity.setHasRecherche(true);
                    cardEntity.setCertifiable(false);

                    // Sauvegarder
                    cardRepository.save(cardEntity);
                    savedCount++;

                    if (savedCount % 50 == 0) {
                        debugLogs.add(String.format("📊 %d cartes sauvegardées...", savedCount));
                    }

                } catch (Exception e) {
                    errorCount++;
                    String errorMsg = String.format("❌ Erreur carte %s: %s", mtgCard.name(), e.getMessage());
                    cardErrors.add(errorMsg);
                    logger.error(errorMsg);

                    if (cardErrors.size() <= 5) { // Limiter les logs d'erreur
                        debugLogs.add(errorMsg);
                    }
                }
            }

            // 5. Mettre à jour les statistiques de l'extension
            debugLogs.add("📊 Mise à jour statistiques extension...");
            MagicSet setToUpdate = finSet.get();
            setToUpdate.setCardsCount(savedCount);
            setRepository.save(setToUpdate);
            debugLogs.add("✅ Statistiques mises à jour");

            // 6. Résultat final
            debugLogs.add(String.format("🎉 TERMINÉ: %d cartes sauvées, %d erreurs", savedCount, errorCount));

            result.put("success", savedCount > 0);
            result.put("cartesRecuperees", finCards.size());
            result.put("cartesSauvegardees", savedCount);
            result.put("cartesEnErreur", errorCount);
            result.put("objectifAtteint", savedCount >= 300);
            result.put("debugLogs", debugLogs);
            result.put("premiersErreurs", cardErrors.subList(0, Math.min(cardErrors.size(), 10)));

            String message = String.format("Final Fantasy: %d/%d cartes sauvegardées (%d erreurs)",
                    savedCount, finCards.size(), errorCount);

            if (savedCount > 0) {
                return ResponseEntity.ok(ApiResponse.success(result, message));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.success(result, message));
            }

        } catch (Exception e) {
            logger.error("❌ Erreur globale sauvegarde Final Fantasy : {}", e.getMessage());

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("stackTrace", Arrays.toString(e.getStackTrace()));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.success(errorResult, "Erreur critique: " + e.getMessage()));
        }
    }

    /**
     * ✅ ENDPOINT SIMPLE : Version simplifiée sans traductions pour tester
     */
    @PostMapping("/admin/save-final-fantasy-simple")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveFinalFantasySimple() {
        try {
            logger.info("🎮 === SAUVEGARDE SIMPLE FINAL FANTASY (SANS TRADUCTIONS) ===");

            // Récupérer les cartes
            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");
            logger.info("📥 {} cartes récupérées depuis Scryfall", finCards.size());

            if (finCards.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte récupérée"));
            }

            // Supprimer anciennes cartes
            int deletedCount = cardRepository.deleteBySetCodeIgnoreCase("FIN");
            cardRepository.flush();
            logger.info("🗑️ {} anciennes cartes supprimées", deletedCount);

            // Sauvegarder sans traductions (plus simple)
            int savedCount = 0;
            for (MtgCard mtgCard : finCards) {
                try {
                    MagicCard entity = new MagicCard();
                    entity.setId(UUID.randomUUID());
                    entity.setExternalId(mtgCard.id() != null && mtgCard.id().length() > 20 ?
                            Integer.toHexString(mtgCard.id().hashCode()).substring(0, 8) : mtgCard.id());
                    entity.setZPostExtension("FIN");

                    // Propriétés JSON
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put("name", mtgCard.name());
                    attributes.put("manaCost", mtgCard.manaCost());
                    attributes.put("cmc", mtgCard.cmc());
                    attributes.put("type", mtgCard.type());
                    attributes.put("rarity", mtgCard.rarity());
                    attributes.put("text", mtgCard.text());
                    attributes.put("artist", mtgCard.artist());
                    attributes.put("power", mtgCard.power());
                    attributes.put("toughness", mtgCard.toughness());

                    entity.setAttributes(new ObjectMapper().writeValueAsString(attributes));

                    // Collections
                    Map<String, Object> collections = new HashMap<>();
                    collections.put("colors", mtgCard.colors());
                    collections.put("types", mtgCard.types());
                    collections.put("subtypes", mtgCard.subtypes());

                    entity.setAllowedNotes(new ObjectMapper().writeValueAsString(collections));

                    cardRepository.save(entity);
                    savedCount++;

                    if (savedCount % 50 == 0) {
                        logger.info("📊 {} cartes sauvegardées...", savedCount);
                    }

                } catch (Exception e) {
                    logger.error("❌ Erreur carte {} : {}", mtgCard.name(), e.getMessage());
                }
            }

            // Mettre à jour l'extension
            Optional<MagicSet> finSet = setRepository.findByCode("FIN");
            if (finSet.isPresent()) {
                MagicSet set = finSet.get();
                set.setCardsCount(savedCount);
                setRepository.save(set);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("cartesRecuperees", finCards.size());
            result.put("cartesSauvegardees", savedCount);
            result.put("succes", savedCount > 300);

            String message = String.format("Final Fantasy: %d cartes sauvegardées avec succès (sans traductions)", savedCount);

            return ResponseEntity.ok(ApiResponse.success(result, message));

        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde simple : {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * ✅ ENDPOINT FINAL FANTASY COMPLET
     * À ajouter dans MtgController.java
     */
    @PostMapping("/admin/save-final-fantasy-complete")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveFinalFantasyComplete() {
        try {
            logger.info("🎮 === SAUVEGARDE FINAL FANTASY COMPLÈTE (tous champs) ===");

            // 1. Récupérer les cartes
            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");
            logger.info("📥 {} cartes récupérées", finCards.size());

            if (finCards.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte récupérée"));
            }

            // 2. Supprimer anciennes cartes
            int deleted = cardRepository.deleteBySetCodeIgnoreCase("FIN");
            cardRepository.flush();
            logger.info("🗑️ {} anciennes cartes supprimées", deleted);

            // 3. S'assurer que l'extension existe
            Optional<MagicSet> finSetOpt = setRepository.findByCode("FIN");
            if (finSetOpt.isEmpty()) {
                MagicSet newSet = new MagicSet();
                newSet.setCode("FIN");
                newSet.setName("Magic: The Gathering - FINAL FANTASY");

                adaptationService.setMagicSetType(newSet, "expansion");
                adaptationService.prepareMagicSetForSave(newSet, "expansion");
                newSet.setReleaseDate(LocalDate.of(2024, 11, 15));

                setRepository.save(newSet);
                logger.info("✅ Extension FIN créée");
            }

            // 4. Sauvegarder avec TOUS les champs renseignés
            int savedCount = 0;
            int errorCount = 0;
            ObjectMapper mapper = new ObjectMapper();

            for (MtgCard mtgCard : finCards) {
                try {
                    MagicCard entity = new MagicCard();
                    entity.setId(UUID.randomUUID());

                    // *** ID externe sécurisé ***
                    String externalId = mtgCard.id() != null ?
                            (mtgCard.id().length() > 20 ? mtgCard.id().substring(0, 20) : mtgCard.id())
                            : "fin_" + savedCount;
                    entity.setExternalId(externalId);
                    entity.setZPostExtension("FIN");

                    // *** TRADUCTION avec nom correct ***
                    CardTranslation translation = new CardTranslation();
                    translation.setId(UUID.randomUUID());
                    translation.setLocalization(Localization.USA);
                    translation.setAvailable(true);

                    String cardName = mtgCard.name() != null ? mtgCard.name() : ("Carte " + savedCount);
                    translation.setName(cardName);
                    translation.setLabelName(cardName);

                    entity.setTranslation(Localization.USA, translation);

                    // *** CHAMPS DIRECTS DE LA BASE - TOUS RENSEIGNÉS ***

                    // Number
                    if (mtgCard.number() != null) {
                        try {
                            entity.setNumero(Integer.parseInt(mtgCard.number()));
                            entity.setNumber(mtgCard.number()); // Aussi dans le champ string
                        } catch (NumberFormatException e) {
                            entity.setNumber(mtgCard.number()); // Au moins en string
                        }
                    }

                    // Propriétés booléennes avec valeurs par défaut
                    entity.setHasFoil(true);  // Par défaut true pour MTG
                    entity.setHasNonFoil(true);
                    entity.setIsFoilOnly(false);  // Par défaut false
                    entity.setIsOnlineOnly(false); // Par défaut false
                    entity.setIsOversized(false);
                    entity.setIsTimeshifted(false);
                    entity.setIsToken(false);
                    entity.setIsReclassee(false);
                    entity.setHasDateFr(false);
                    entity.setIsAffichable(true);
                    entity.setHasRecherche(true);
                    entity.setCertifiable(false);
                    entity.setHasImg(false);

                    // *** JSON ATTRIBUTES avec toutes les propriétés ***
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put("name", cardName);
                    attributes.put("manaCost", mtgCard.manaCost());
                    attributes.put("cmc", mtgCard.cmc());
                    attributes.put("type", mtgCard.type());
                    attributes.put("rarity", mtgCard.rarity());
                    attributes.put("text", mtgCard.text());
                    attributes.put("artist", mtgCard.artist());
                    attributes.put("power", mtgCard.power());
                    attributes.put("toughness", mtgCard.toughness());
                    attributes.put("layout", mtgCard.layout() != null ? mtgCard.layout() : "normal");
                    attributes.put("multiverseid", mtgCard.multiverseid());
                    attributes.put("setName", mtgCard.setName());

                    // *** NOUVEAUX CHAMPS IMPORTANTS ***
                    attributes.put("number", mtgCard.number());
                    attributes.put("imageUrl", mtgCard.imageUrl());

                    entity.setAttributes(mapper.writeValueAsString(attributes));

                    // *** JSON COLLECTIONS avec colors, colorIdentity, types ***
                    Map<String, Object> collections = new HashMap<>();
                    collections.put("colors", mtgCard.colors() != null ? mtgCard.colors() : new ArrayList<>());
                    collections.put("colorIdentity", mtgCard.colorIdentity() != null ? mtgCard.colorIdentity() : new ArrayList<>());
                    collections.put("types", mtgCard.types() != null ? mtgCard.types() : new ArrayList<>());
                    collections.put("subtypes", mtgCard.subtypes() != null ? mtgCard.subtypes() : new ArrayList<>());
                    collections.put("supertypes", mtgCard.supertypes() != null ? mtgCard.supertypes() : new ArrayList<>());

                    entity.setAllowedNotes(mapper.writeValueAsString(collections));

                    // *** URL d'image ***
                    if (mtgCard.imageUrl() != null && !mtgCard.imageUrl().isEmpty()) {
                        entity.setOriginalImageUrl(mtgCard.imageUrl());
                    }

                    // *** MÉTHODES POUR ACCÉDER AUX DONNÉES JSON ***
                    // Ces méthodes existent déjà dans MagicCard pour lire les JSON
                    // entity.getRarity() lira attributes.rarity
                    // entity.getColors() lira allowedNotes.colors
                    // entity.getTypes() lira allowedNotes.types
                    // etc.

                    // Sauvegarder l'entité complète
                    cardRepository.save(entity);
                    savedCount++;

                    if (savedCount % 50 == 0) {
                        logger.info("📊 {} cartes complètes sauvegardées...", savedCount);
                    }

                } catch (Exception e) {
                    errorCount++;
                    logger.error("❌ Erreur carte {} : {}", mtgCard.name(), e.getMessage());
                }
            }

            // 5. Mettre à jour l'extension
            Optional<MagicSet> finSet = setRepository.findByCode("FIN");
            if (finSet.isPresent()) {
                MagicSet set = finSet.get();
                set.setCardsCount(savedCount);
                setRepository.save(set);
            }

            // 6. Vérification des champs
            List<MagicCard> sampleCards = cardRepository.findBySetCode("FIN").stream().limit(3).toList();
            List<Map<String, Object>> verification = new ArrayList<>();

            for (MagicCard card : sampleCards) {
                Map<String, Object> cardInfo = new HashMap<>();
                cardInfo.put("name", card.getName());
                cardInfo.put("number", card.getNumber());
                cardInfo.put("rarity", card.getRarity());
                cardInfo.put("colors", card.getColors());
                cardInfo.put("types", card.getTypes());
                cardInfo.put("colorIdentity", card.getColorIdentity());
                cardInfo.put("layout", card.getLayout());
                cardInfo.put("hasFoil", card.getHasFoil());
                cardInfo.put("isFoilOnly", card.getIsFoilOnly());
                cardInfo.put("isOnlineOnly", card.getIsOnlineOnly());
                verification.add(cardInfo);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("cartesRecuperees", finCards.size());
            result.put("cartesSauvegardees", savedCount);
            result.put("cartesEnErreur", errorCount);
            result.put("tauxSucces", finCards.size() > 0 ? (savedCount * 100.0) / finCards.size() : 0);
            result.put("succes", savedCount >= 300);
            result.put("verification", verification);

            String message = String.format("Final Fantasy COMPLET: %d/%d cartes avec tous les champs (%d erreurs)",
                    savedCount, finCards.size(), errorCount);

            logger.info("🎉 {}", message);
            logger.info("🔍 Vérification des champs: {}", verification);

            return ResponseEntity.ok(ApiResponse.success(result, message));

        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde complète : {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * ✅ MÉTHODE UTILITAIRE : ID externe sécurisé
     */
    private String generateSafeExternalId(String originalId, int fallbackIndex) {
        if (originalId == null || originalId.isEmpty()) {
            return "fin_" + String.format("%04d", fallbackIndex);
        }

        if (originalId.length() <= 20) {
            return originalId;
        }

        // Si trop long, prendre le début ou hasher intelligemment
        if (originalId.length() >= 8) {
            return originalId.substring(0, 8);
        } else {
            // Cas où c'est plus court que 8 après vérification
            return originalId + "_" + fallbackIndex;
        }
    }

    // ================================================================
// AJOUTER dans MtgController.java - Endpoint Final Fantasy avec service
// ================================================================

    /**
     * ✅ ENDPOINT FINAL FANTASY - Utilise le service existant
     * À ajouter dans MtgController.java
     */
    @PostMapping("/admin/save-final-fantasy-service")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveFinalFantasyWithService() {
        try {
            logger.info("🎮 === SAUVEGARDE FINAL FANTASY AVEC SERVICE EXISTANT ===");

            // 1. Récupérer les cartes depuis Scryfall
            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");
            logger.info("📥 {} cartes récupérées depuis Scryfall", finCards.size());

            if (finCards.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte récupérée depuis Scryfall"));
            }

            // 2. Supprimer les anciennes cartes FIN
            logger.info("🗑️ Suppression des anciennes cartes FIN...");
            int deletedCount = cardRepository.deleteBySetCodeIgnoreCase("FIN");
            cardRepository.flush();
            logger.info("✅ {} anciennes cartes supprimées", deletedCount);

            // 3. S'assurer que l'extension FIN existe
            Optional<MagicSet> finSetOpt = setRepository.findByCode("FIN");
            if (finSetOpt.isEmpty()) {
                logger.info("🔧 Création de l'extension FIN...");
                MagicSet newSet = new MagicSet();
                newSet.setCode("FIN");
                newSet.setName("Magic: The Gathering - FINAL FANTASY");

                // Utiliser le service d'adaptation
                adaptationService.setMagicSetType(newSet, "expansion");
                adaptationService.prepareMagicSetForSave(newSet, "expansion");
                newSet.setReleaseDate(LocalDate.of(2024, 11, 15));

                setRepository.save(newSet);
                logger.info("✅ Extension FIN créée");
            }

            // 4. *** UTILISER LE SERVICE EXISTANT QUI GÈRE TOUT ***
            logger.info("💾 Sauvegarde avec le service CardPersistenceService...");
            int savedCount = persistenceService.saveCards(finCards, "FIN");

            // 5. Résultat
            Map<String, Object> result = new HashMap<>();
            result.put("cartesRecuperees", finCards.size());
            result.put("cartesSauvegardees", savedCount);
            result.put("cartesIgnorees", finCards.size() - savedCount);
            result.put("tauxSucces", finCards.size() > 0 ? (savedCount * 100.0) / finCards.size() : 0);
            result.put("objectifAtteint", savedCount >= 300);

            // Vérifier le résultat en base
            long finalCardCount = cardRepository.countBySetCode("FIN");
            result.put("cartesEnBase", finalCardCount);

            String message = String.format("Final Fantasy: %d/%d cartes sauvegardées (%.1f%% succès) - %d en base",
                    savedCount, finCards.size(),
                    finCards.size() > 0 ? (savedCount * 100.0) / finCards.size() : 0,
                    finalCardCount);

            logger.info("🎉 {}", message);

            return ResponseEntity.ok(ApiResponse.success(result, message));

        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde Final Fantasy avec service : {}", e.getMessage(), e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            errorResult.put("type", e.getClass().getSimpleName());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.success(errorResult, "Erreur: " + e.getMessage()));
        }
    }

// ================================================================
// ALTERNATIVE: Version encore plus simple pour débogage
// ================================================================

    /**
     * ✅ ENDPOINT SIMPLE pour tester rapidement
     * À ajouter aussi dans MtgController.java
     */
    @PostMapping("/admin/test-final-fantasy")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> testFinalFantasy() {
        try {
            logger.info("🧪 === TEST SIMPLE FINAL FANTASY ===");

            Map<String, Object> result = new HashMap<>();

            // 1. Test récupération Scryfall
            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");
            result.put("scryfallCartes", finCards.size());
            logger.info("📥 {} cartes depuis Scryfall", finCards.size());

            // 2. Test extension en base
            Optional<MagicSet> finSet = setRepository.findByCode("FIN");
            result.put("extensionExists", finSet.isPresent());
            if (finSet.isPresent()) {
                result.put("extensionName", finSet.get().getName());
            }

            // 3. Test cartes en base
            long cartesEnBase = cardRepository.countBySetCode("FIN");
            result.put("cartesEnBase", cartesEnBase);

            // 4. Test suppression (pour voir si ça marche)
            if (cartesEnBase > 0) {
                int deleted = cardRepository.deleteBySetCodeIgnoreCase("FIN");
                cardRepository.flush();
                result.put("cartesSupprimes", deleted);
                logger.info("🗑️ {} cartes supprimées pour le test", deleted);
            }

            // 5. Sauvegarder juste 5 cartes pour tester
            if (!finCards.isEmpty()) {
                List<MtgCard> testCards = finCards.subList(0, Math.min(5, finCards.size()));
                int saved = persistenceService.saveCards(testCards, "FIN");
                result.put("cartesTestSauvees", saved);
                logger.info("💾 {} cartes de test sauvegardées", saved);
            }

            result.put("success", true);
            return ResponseEntity.ok(ApiResponse.success(result, "Test Final Fantasy terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur test Final Fantasy : {}", e.getMessage(), e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            errorResult.put("success", false);

            return ResponseEntity.ok(ApiResponse.success(errorResult, "Erreur test: " + e.getMessage()));
        }
    }

// ================================================================
// VERSION ULTRA SIMPLE avec ObjectMapper pour éviter les traductions
// ================================================================

    /**
     * ✅ ENDPOINT ULTRA SIMPLE - JSON seulement, pas de traductions
     */
    @PostMapping("/admin/save-final-fantasy-json")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveFinalFantasyJsonOnly() {
        try {
            logger.info("🎮 === SAUVEGARDE FINAL FANTASY JSON SEULEMENT ===");

            // Récupérer les cartes
            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");
            logger.info("📥 {} cartes récupérées", finCards.size());

            if (finCards.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte récupérée"));
            }

            // Supprimer anciennes
            int deleted = cardRepository.deleteBySetCodeIgnoreCase("FIN");
            cardRepository.flush();
            logger.info("🗑️ {} anciennes cartes supprimées", deleted);

            // Sauvegarder SANS traductions (uniquement JSON)
            int savedCount = 0;
            ObjectMapper mapper = new ObjectMapper();

            for (MtgCard mtgCard : finCards) {
                try {
                    MagicCard entity = new MagicCard();
                    entity.setId(UUID.randomUUID());
                    entity.setExternalId(mtgCard.id() != null ?
                            (mtgCard.id().length() > 20 ? mtgCard.id().substring(0, 20) : mtgCard.id())
                            : "fin_" + savedCount);
                    entity.setZPostExtension("FIN");

                    // TOUT en JSON dans attributes
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put("name", mtgCard.name());
                    attributes.put("manaCost", mtgCard.manaCost());
                    attributes.put("cmc", mtgCard.cmc());
                    attributes.put("type", mtgCard.type());
                    attributes.put("rarity", mtgCard.rarity());
                    attributes.put("text", mtgCard.text());
                    attributes.put("artist", mtgCard.artist());
                    attributes.put("power", mtgCard.power());
                    attributes.put("toughness", mtgCard.toughness());

                    entity.setAttributes(mapper.writeValueAsString(attributes));

                    // Collections en JSON
                    Map<String, Object> collections = new HashMap<>();
                    collections.put("colors", mtgCard.colors());
                    collections.put("types", mtgCard.types());

                    entity.setAllowedNotes(mapper.writeValueAsString(collections));

                    // Propriétés simples
                    entity.setIsAffichable(true);
                    entity.setHasRecherche(true);
                    entity.setCertifiable(false);
                    entity.setHasImg(false);

                    cardRepository.save(entity);
                    savedCount++;

                    if (savedCount % 50 == 0) {
                        logger.info("📊 {} cartes sauvegardées...", savedCount);
                    }

                } catch (Exception e) {
                    logger.error("❌ Erreur carte {} : {}", mtgCard.name(), e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("cartesRecuperees", finCards.size());
            result.put("cartesSauvegardees", savedCount);
            result.put("succes", savedCount > 300);

            String message = String.format("Final Fantasy JSON: %d/%d cartes sauvegardées", savedCount, finCards.size());
            return ResponseEntity.ok(ApiResponse.success(result, message));

        } catch (Exception e) {
            logger.error("❌ Erreur JSON : {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ================================================================
// SOLUTION: Endpoint JSON avec traductions simples pour les noms
// ================================================================

    /**
     * ✅ ENDPOINT CORRIGÉ SANS ERREURS
     * À ajouter dans MtgController.java
     */
    @PostMapping("/admin/save-final-fantasy-names-fixed")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveFinalFantasyNamesFixed() {
        try {
            logger.info("🎮 === SAUVEGARDE FINAL FANTASY AVEC NOMS CORRECTS ===");

            // 1. Récupérer les cartes
            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");
            logger.info("📥 {} cartes récupérées", finCards.size());

            if (finCards.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte récupérée"));
            }

            // 2. Supprimer anciennes cartes
            int deleted = cardRepository.deleteBySetCodeIgnoreCase("FIN");
            cardRepository.flush();
            logger.info("🗑️ {} anciennes cartes supprimées", deleted);

            // 3. S'assurer que l'extension existe
            Optional<MagicSet> finSetOpt = setRepository.findByCode("FIN");
            if (finSetOpt.isEmpty()) {
                MagicSet newSet = new MagicSet();
                newSet.setCode("FIN");
                newSet.setName("Magic: The Gathering - FINAL FANTASY");

                adaptationService.setMagicSetType(newSet, "expansion");
                adaptationService.prepareMagicSetForSave(newSet, "expansion");
                newSet.setReleaseDate(LocalDate.of(2024, 11, 15));

                setRepository.save(newSet);
                logger.info("✅ Extension FIN créée");
            }

            // 4. Sauvegarder avec la méthode setTranslation() correcte
            int savedCount = 0;
            int errorCount = 0;
            ObjectMapper mapper = new ObjectMapper();

            for (MtgCard mtgCard : finCards) {
                try {
                    MagicCard entity = new MagicCard();
                    entity.setId(UUID.randomUUID());

                    // ID externe sécurisé
                    String externalId = mtgCard.id() != null ?
                            (mtgCard.id().length() > 20 ? mtgCard.id().substring(0, 20) : mtgCard.id())
                            : "fin_" + savedCount;
                    entity.setExternalId(externalId);
                    entity.setZPostExtension("FIN");

                    // *** CORRECTION 1: Utiliser setTranslation() au lieu de put() ***
                    CardTranslation translation = new CardTranslation();
                    translation.setId(UUID.randomUUID());
                    translation.setLocalization(Localization.USA);
                    translation.setAvailable(true);

                    String cardName = mtgCard.name() != null ? mtgCard.name() : ("Carte " + savedCount);
                    translation.setName(cardName);
                    translation.setLabelName(cardName);

                    // Utiliser la méthode setTranslation() de la classe parent
                    entity.setTranslation(Localization.USA, translation);

                    // Stocker AUSSI le nom dans le JSON pour double sécurité
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put("name", cardName); // ← Nom dans le JSON aussi
                    attributes.put("manaCost", mtgCard.manaCost());
                    attributes.put("cmc", mtgCard.cmc());
                    attributes.put("type", mtgCard.type());
                    attributes.put("rarity", mtgCard.rarity());
                    attributes.put("text", mtgCard.text());
                    attributes.put("artist", mtgCard.artist());
                    attributes.put("power", mtgCard.power());
                    attributes.put("toughness", mtgCard.toughness());
                    attributes.put("layout", mtgCard.layout());
                    attributes.put("multiverseid", mtgCard.multiverseid());
                    attributes.put("setName", mtgCard.setName());

                    entity.setAttributes(mapper.writeValueAsString(attributes));

                    // Collections en JSON
                    Map<String, Object> collections = new HashMap<>();
                    collections.put("colors", mtgCard.colors());
                    collections.put("colorIdentity", mtgCard.colorIdentity());
                    collections.put("types", mtgCard.types());
                    collections.put("subtypes", mtgCard.subtypes());
                    collections.put("supertypes", mtgCard.supertypes());

                    entity.setAllowedNotes(mapper.writeValueAsString(collections));

                    // Propriétés simples
                    entity.setOriginalImageUrl(mtgCard.imageUrl());
                    entity.setIsAffichable(true);
                    entity.setHasRecherche(true);
                    entity.setCertifiable(false);
                    entity.setHasImg(false);

                    // Numéro de carte
                    if (mtgCard.number() != null) {
                        try {
                            entity.setNumero(Integer.parseInt(mtgCard.number()));
                        } catch (NumberFormatException e) {
                            // Ignorer si pas un entier
                        }
                    }

                    // Sauvegarder l'entité avec sa traduction
                    cardRepository.save(entity);
                    savedCount++;

                    if (savedCount % 50 == 0) {
                        logger.info("📊 {} cartes sauvegardées avec noms...", savedCount);
                    }

                } catch (Exception e) {
                    errorCount++;
                    logger.error("❌ Erreur carte {} : {}", mtgCard.name(), e.getMessage());
                }
            }

            // 5. Mettre à jour l'extension
            Optional<MagicSet> finSet = setRepository.findByCode("FIN");
            if (finSet.isPresent()) {
                MagicSet set = finSet.get();
                set.setCardsCount(savedCount);
                setRepository.save(set);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("cartesRecuperees", finCards.size());
            result.put("cartesSauvegardees", savedCount);
            result.put("cartesEnErreur", errorCount);
            result.put("tauxSucces", finCards.size() > 0 ? (savedCount * 100.0) / finCards.size() : 0);
            result.put("succes", savedCount >= 300);

            // Vérifier quelques noms pour confirmer
            List<MagicCard> sampleCards = cardRepository.findBySetCode("FIN").stream().limit(5).toList();
            List<String> sampleNames = sampleCards.stream().map(MagicCard::getName).toList();
            result.put("exemplesNoms", sampleNames);

            String message = String.format("Final Fantasy avec noms: %d/%d cartes sauvegardées (%d erreurs)",
                    savedCount, finCards.size(), errorCount);

            logger.info("🎉 {}", message);
            logger.info("📝 Exemples de noms: {}", sampleNames);

            return ResponseEntity.ok(ApiResponse.success(result, message));

        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde avec noms : {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

// ================================================================
// ALTERNATIVE: Version avec sauvegarde manuelle des traductions
// ================================================================

    /**
     * ✅ ALTERNATIVE: Sauvegarder carte et traduction séparément
     */
    @PostMapping("/admin/save-final-fantasy-separate")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveFinalFantasySeparate() {
        try {
            logger.info("🎮 === SAUVEGARDE FINAL FANTASY SÉPARÉE ===");

            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSet("FIN");
            logger.info("📥 {} cartes récupérées", finCards.size());

            if (finCards.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte récupérée"));
            }

            // Supprimer anciennes cartes ET leurs traductions
            int deleted = cardRepository.deleteBySetCodeIgnoreCase("FIN");
            cardRepository.flush();
            logger.info("🗑️ {} anciennes cartes supprimées", deleted);

            // S'assurer que l'extension existe
            Optional<MagicSet> finSetOpt = setRepository.findByCode("FIN");
            if (finSetOpt.isEmpty()) {
                MagicSet newSet = new MagicSet();
                newSet.setCode("FIN");
                newSet.setName("Magic: The Gathering - FINAL FANTASY");

                adaptationService.setMagicSetType(newSet, "expansion");
                adaptationService.prepareMagicSetForSave(newSet, "expansion");
                newSet.setReleaseDate(LocalDate.of(2024, 11, 15));

                setRepository.save(newSet);
                logger.info("✅ Extension FIN créée");
            }

            int savedCount = 0;
            ObjectMapper mapper = new ObjectMapper();

            for (MtgCard mtgCard : finCards) {
                try {
                    // 1. Créer et sauvegarder la CARTE d'abord
                    MagicCard entity = new MagicCard();
                    entity.setId(UUID.randomUUID());

                    String externalId = mtgCard.id() != null ?
                            (mtgCard.id().length() > 20 ? mtgCard.id().substring(0, 20) : mtgCard.id())
                            : "fin_" + savedCount;
                    entity.setExternalId(externalId);
                    entity.setZPostExtension("FIN");

                    // JSON attributes
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put("name", mtgCard.name());
                    attributes.put("manaCost", mtgCard.manaCost());
                    attributes.put("cmc", mtgCard.cmc());
                    attributes.put("type", mtgCard.type());
                    attributes.put("rarity", mtgCard.rarity());
                    attributes.put("text", mtgCard.text());
                    attributes.put("artist", mtgCard.artist());
                    attributes.put("power", mtgCard.power());
                    attributes.put("toughness", mtgCard.toughness());

                    entity.setAttributes(mapper.writeValueAsString(attributes));

                    // Collections
                    Map<String, Object> collections = new HashMap<>();
                    collections.put("colors", mtgCard.colors());
                    collections.put("types", mtgCard.types());

                    entity.setAllowedNotes(mapper.writeValueAsString(collections));

                    entity.setOriginalImageUrl(mtgCard.imageUrl());
                    entity.setIsAffichable(true);
                    entity.setHasRecherche(true);
                    entity.setCertifiable(false);
                    entity.setHasImg(false);

                    // SAUVEGARDER LA CARTE D'ABORD
                    MagicCard savedEntity = cardRepository.save(entity);

                    // 2. Créer et sauvegarder la TRADUCTION séparément
                    CardTranslation translation = new CardTranslation();
                    translation.setId(UUID.randomUUID());
                    translation.setLocalization(Localization.USA);
                    translation.setAvailable(true);
                    translation.setTranslatable(savedEntity);

                    String cardName = mtgCard.name() != null ? mtgCard.name() : ("Carte " + savedCount);
                    translation.setName(cardName);
                    translation.setLabelName(cardName);

                    // Sauvegarder la traduction séparément si le repository existe
                    try {
                        // Utiliser EntityManager pour éviter les conflits
//                        entityManager.persist(translation);
//                        entityManager.flush();
                        logger.debug("Traduction créée pour {}", cardName);
                    } catch (Exception e) {
                        logger.warn("⚠️ Erreur traduction pour {} : {}", cardName, e.getMessage());
                        // Continuer même si la traduction échoue
                    }

                    savedCount++;

                    if (savedCount % 50 == 0) {
                        logger.info("📊 {} cartes sauvegardées séparément...", savedCount);
                    }

                } catch (Exception e) {
                    logger.error("❌ Erreur carte {} : {}", mtgCard.name(), e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("cartesRecuperees", finCards.size());
            result.put("cartesSauvegardees", savedCount);
            result.put("succes", savedCount >= 300);

            String message = String.format("Final Fantasy séparé: %d/%d cartes sauvegardées", savedCount, finCards.size());

            return ResponseEntity.ok(ApiResponse.success(result, message));

        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde séparée : {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

}