package com.pcagrad.magic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcagrad.magic.dto.ApiResponse;
import com.pcagrad.magic.entity.CardEntity;
import com.pcagrad.magic.entity.SetEntity;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.model.MtgSet;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
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

    // ENDPOINT DE DEBUG POUR RETROUVER FIN
// Ajoutez cet endpoint dans MtgController.java

    @GetMapping("/debug/find-fin")
    public ResponseEntity<ApiResponse<Object>> debugFindFin() {
        try {
            logger.info("🔍 DEBUG - Recherche extension FIN après migration UUID");

            Map<String, Object> debugInfo = new HashMap<>();

            // 1. Compter toutes les extensions
            long totalSets = setRepository.count();
            debugInfo.put("totalSetsInDb", totalSets);

            // 2. Rechercher FIN par code (nouvelle méthode)
            Optional<SetEntity> finByCode = setRepository.findByCode("FIN");
            debugInfo.put("finFoundByCode", finByCode.isPresent());

            if (finByCode.isPresent()) {
                SetEntity fin = finByCode.get();
                Map<String, Object> finInfo = new HashMap<>();
                finInfo.put("uuid", fin.getId().toString());
                finInfo.put("code", fin.getCode());
                finInfo.put("name", fin.getName());
                finInfo.put("type", fin.getType());
                finInfo.put("releaseDate", fin.getReleaseDate());
                finInfo.put("cardsSynced", fin.getCardsSynced());
                finInfo.put("cardsCount", fin.getCardsCount());
                debugInfo.put("finDetails", finInfo);

                // Compter les cartes FIN
                long cardsCount = cardRepository.countBySetCode("FIN");
                debugInfo.put("cardsInDbForFin", cardsCount);
            }

            // 3. Rechercher toutes les extensions qui contiennent "FIN"
            List<SetEntity> setsWithFin = setRepository.findByNameContainingIgnoreCaseOrderByReleaseDateDesc("FINAL");
            debugInfo.put("setsContainingFinal", setsWithFin.size());

            List<Map<String, Object>> finLikeSets = setsWithFin.stream()
                    .map(set -> {
                        Map<String, Object> setInfo = new HashMap<>();
                        setInfo.put("uuid", set.getId().toString());
                        setInfo.put("code", set.getCode());
                        setInfo.put("name", set.getName());
                        return setInfo;
                    })
                    .collect(Collectors.toList());
            debugInfo.put("setsWithFinalInName", finLikeSets);

            // 4. Lister les 10 dernières extensions par date
            List<SetEntity> recentSets = setRepository.findLatestSets();
            List<Map<String, Object>> recentSetsInfo = recentSets.stream()
                    .limit(10)
                    .map(set -> {
                        Map<String, Object> setInfo = new HashMap<>();
                        setInfo.put("code", set.getCode());
                        setInfo.put("name", set.getName());
                        setInfo.put("releaseDate", set.getReleaseDate());
                        setInfo.put("cardsCount", set.getCardsCount());
                        return setInfo;
                    })
                    .collect(Collectors.toList());
            debugInfo.put("recent10Sets", recentSetsInfo);

            return ResponseEntity.ok(ApiResponse.success(debugInfo, "Debug FIN terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur debug FIN : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur debug : " + e.getMessage()));
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

// CORRECTION DANS getLatestSet() pour gérer le fallback FIN
// Dans MtgService.java, corrigez cette partie :

    public Mono<MtgSet> getLatestSet() {
        logger.debug("🔍 Récupération de la dernière extension");

        // Chercher d'abord en base avec priorité aux extensions avec cartes
        List<SetEntity> allSets = setRepository.findLatestSets();

        if (!allSets.isEmpty()) {
            // ... logique existante ...
        }

        // CORRECTION : Fallback vers FIN avec findByCode au lieu de findById
        Optional<SetEntity> finFallback = setRepository.findByCode("FIN");
        if (finFallback.isPresent() && finFallback.get().getCardsSynced()) {
            logger.info("🎯 Fallback vers FIN (Final Fantasy) qui a des cartes synchronisées");
            return Mono.just(entityToModel(finFallback.get()));
        }

        // ... reste de la méthode
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

    // Ajoutez cet endpoint dans votre MtgController

    @GetMapping("/sets/{setCode}/with-cards")
    public ResponseEntity<ApiResponse<Object>> getSetWithCards(@PathVariable String setCode) {
        try {
            logger.info("🔍 Récupération de l'extension {} avec cartes", setCode);

            // Vérifier si l'extension existe dans la base
            Optional<SetEntity> setEntity = setRepository.findByCode(setCode);
            if (setEntity.isEmpty()) {
                logger.warn("⚠️ Extension non trouvée : {}", setCode);
                return ResponseEntity.notFound().build();
            }

            SetEntity set = setEntity.get();

            // Récupérer les cartes de cette extension
            List<CardEntity> cards = cardRepository.findBySetCodeOrderByNameAsc(setCode);

            // Créer une réponse avec les informations de l'extension et ses cartes
            Map<String, Object> response = new HashMap<>();
            response.put("code", set.getCode());
            response.put("name", set.getName());
            response.put("type", set.getType());
            response.put("releaseDate", set.getReleaseDate());
            response.put("cardsSynced", set.getCardsSynced());
            response.put("totalCards", cards.size());

            // Statistiques par rareté
            Map<String, Long> rarityStats = cards.stream()
                    .collect(Collectors.groupingBy(
                            card -> card.getRarity() != null ? card.getRarity() : "unknown",
                            Collectors.counting()
                    ));
            response.put("rarityStats", rarityStats);

            // Liste des cartes (limitée pour éviter une réponse trop grosse)
            List<Map<String, Object>> cardList = cards.stream()
                    .limit(100) // Limiter à 100 cartes pour la performance
                    .map(card -> {
                        Map<String, Object> cardMap = new HashMap<>();
                        cardMap.put("id", card.getId());
                        cardMap.put("name", card.getName());
                        cardMap.put("manaCost", card.getManaCost());
                        cardMap.put("type", card.getType());
                        cardMap.put("rarity", card.getRarity());
                        cardMap.put("setCode", card.getSetCode());
                        cardMap.put("artist", card.getArtist());
                        cardMap.put("imageDownloaded", card.getImageDownloaded());
                        return cardMap;
                    })
                    .collect(Collectors.toList());

            response.put("cards", cardList);
            response.put("hasMoreCards", cards.size() > 100);

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