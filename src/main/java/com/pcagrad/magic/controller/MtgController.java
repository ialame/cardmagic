package com.pcagrad.magic.controller;

import com.pcagrad.magic.dto.ApiResponse;
import com.pcagrad.magic.entity.CardEntity;
import com.pcagrad.magic.entity.SetEntity;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.model.MtgSet;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
import com.pcagrad.magic.service.MtgService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    @GetMapping("/sets")
    public Mono<ResponseEntity<ApiResponse<List<MtgSet>>>> getAllSets() {
        return mtgService.getAllSets()
                .map(sets -> ResponseEntity.ok(ApiResponse.success(sets, "Extensions récupérées avec succès")))
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la récupération des extensions")));
    }

    @GetMapping("/sets/latest")
    public Mono<ResponseEntity<ApiResponse<MtgSet>>> getLatestSet() {
        return mtgService.getLatestSet()
                .map(set -> {
                    if (set != null) {
                        return ResponseEntity.ok(ApiResponse.success(set, "Dernière extension récupérée avec succès"));
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
            setRepository.deleteById("FIN");

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

}