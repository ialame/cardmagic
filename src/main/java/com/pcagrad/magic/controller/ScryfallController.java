package com.pcagrad.magic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcagrad.magic.dto.ApiResponse;
import com.pcagrad.magic.entity.CardEntity;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.service.ScryfallService;
import com.pcagrad.magic.service.CardPersistenceService;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Contrôleur dédié à l'intégration avec l'API Scryfall
 * Séparé du MtgController pour une architecture plus propre
 */
@RestController
@RequestMapping("/api/scryfall")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:8080"})
public class ScryfallController {

    private static final Logger logger = LoggerFactory.getLogger(ScryfallController.class);

    @Autowired
    private ScryfallService scryfallService;

    @Autowired
    private CardPersistenceService persistenceService;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private SetRepository setRepository;

    /**
     * Vérifier la disponibilité d'une extension sur Scryfall
     */
    @GetMapping("/check/{setCode}")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> checkSetAvailability(@PathVariable String setCode) {
        logger.info("🔍 Vérification de l'extension {} sur Scryfall", setCode);

        return scryfallService.setExistsOnScryfall(setCode)
                .flatMap(exists -> {
                    if (exists) {
                        return scryfallService.getCardsFromScryfall(setCode)
                                .map(cards -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("setCode", setCode.toUpperCase());
                                    result.put("exists", true);
                                    result.put("cardsCount", cards.size());
                                    result.put("source", "Scryfall API");

                                    // Aperçu des cartes
                                    result.put("preview", cards.stream()
                                            .limit(3)
                                            .map(card -> Map.of(
                                                    "name", card.name(),
                                                    "rarity", card.rarity(),
                                                    "type", card.type(),
                                                    "hasImage", card.imageUrl() != null
                                            ))
                                            .collect(Collectors.toList()));

                                    return ResponseEntity.ok(ApiResponse.success(result,
                                            String.format("Extension %s trouvée sur Scryfall avec %d cartes",
                                                    setCode, cards.size())));
                                });
                    } else {
                        Map<String, Object> result = new HashMap<>();
                        result.put("setCode", setCode.toUpperCase());
                        result.put("exists", false);
                        result.put("cardsCount", 0);
                        result.put("source", "Scryfall API");

                        return Mono.just(ResponseEntity.ok(ApiResponse.success(result,
                                "Extension non trouvée sur Scryfall")));
                    }
                })
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la vérification Scryfall pour " + setCode)));
    }

    /**
     * Synchroniser une extension depuis Scryfall
     */
    @PostMapping("/sync/{setCode}")
    public ResponseEntity<ApiResponse<String>> syncFromScryfall(@PathVariable String setCode) {
        try {
            logger.info("🔮 Synchronisation de {} depuis Scryfall", setCode);

            CompletableFuture.runAsync(() -> {
                try {
                    scryfallService.getCardsFromScryfall(setCode)
                            .subscribe(cards -> {
                                if (!cards.isEmpty()) {
                                    logger.info("✅ {} cartes récupérées depuis Scryfall pour {}",
                                            cards.size(), setCode);

                                    // Sauvegarder en base de données
                                    persistenceService.saveCardsForSet(setCode, cards)
                                            .thenAccept(savedCount -> {
                                                logger.info("💾 {} cartes sauvegardées en base pour {}",
                                                        savedCount, setCode);
                                            })
                                            .exceptionally(error -> {
                                                logger.error("❌ Erreur sauvegarde pour {} : {}",
                                                        setCode, error.getMessage());
                                                return null;
                                            });
                                } else {
                                    logger.warn("⚠️ Aucune carte trouvée sur Scryfall pour {}", setCode);
                                }
                            }, error -> {
                                logger.error("❌ Erreur lors de la récupération Scryfall pour {} : {}",
                                        setCode, error.getMessage());
                            });

                } catch (Exception e) {
                    logger.error("❌ Erreur générale lors de la sync Scryfall pour {} : {}",
                            setCode, e.getMessage());
                }
            });

            return ResponseEntity.accepted()
                    .body(ApiResponse.success(
                            "Synchronisation Scryfall démarrée pour : " + setCode.toUpperCase(),
                            "Les cartes seront récupérées depuis Scryfall et sauvegardées en base"));

        } catch (Exception e) {
            logger.error("❌ Erreur lors du déclenchement de la sync Scryfall pour {} : {}",
                    setCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur lors de la synchronisation : " + e.getMessage()));
        }
    }

    /**
     * Aperçu des cartes sans sauvegarde (preview only)
     */
    @GetMapping("/preview/{setCode}")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> previewCards(@PathVariable String setCode) {
        logger.info("👁️ Aperçu des cartes de {} depuis Scryfall", setCode);

        return scryfallService.getCardsFromScryfall(setCode)
                .map(cards -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("setCode", setCode.toUpperCase());
                    result.put("totalCards", cards.size());
                    result.put("source", "Scryfall API (preview)");

                    // Statistiques par rareté
                    Map<String, Long> rarityStats = cards.stream()
                            .collect(Collectors.groupingBy(
                                    card -> card.rarity() != null ? card.rarity() : "Unknown",
                                    Collectors.counting()
                            ));
                    result.put("rarityStats", rarityStats);

                    // Échantillon de cartes (limité pour la performance)
                    result.put("sample", cards.stream()
                            .limit(10)
                            .map(card -> Map.of(
                                    "name", card.name(),
                                    "manaCost", card.manaCost() != null ? card.manaCost() : "",
                                    "type", card.type(),
                                    "rarity", card.rarity(),
                                    "artist", card.artist() != null ? card.artist() : "Unknown",
                                    "hasImage", card.imageUrl() != null
                            ))
                            .collect(Collectors.toList()));

                    return ResponseEntity.ok(ApiResponse.success(result,
                            String.format("Aperçu de %d cartes pour %s", cards.size(), setCode)));
                })
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de l'aperçu pour " + setCode)));
    }

    /**
     * Comparer les données MTG API vs Scryfall
     */
    @GetMapping("/compare/{setCode}")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> compareWithMtgApi(@PathVariable String setCode) {
        logger.info("⚖️ Comparaison MTG API vs Scryfall pour {}", setCode);

        return scryfallService.getCardsFromScryfall(setCode)
                .map(scryfallCards -> {
                    // Cartes en base (probablement depuis MTG API)
                    long localCards = cardRepository.countBySetCode(setCode);

                    Map<String, Object> comparison = new HashMap<>();
                    comparison.put("setCode", setCode.toUpperCase());
                    comparison.put("localCardsCount", localCards);
                    comparison.put("scryfallCardsCount", scryfallCards.size());
                    comparison.put("difference", scryfallCards.size() - localCards);

                    String recommendation;
                    if (scryfallCards.size() > localCards) {
                        recommendation = "Scryfall a plus de cartes - Recommandé pour sync";
                    } else if (localCards > scryfallCards.size()) {
                        recommendation = "Base locale a plus de cartes - Vérifier la source";
                    } else if (scryfallCards.size() == localCards && localCards > 0) {
                        recommendation = "Même nombre de cartes - Pas besoin de sync";
                    } else {
                        recommendation = "Aucune carte trouvée - Extension peut-être inexistante";
                    }

                    comparison.put("recommendation", recommendation);
                    comparison.put("scryfallAvailable", !scryfallCards.isEmpty());

                    return ResponseEntity.ok(ApiResponse.success(comparison,
                            "Comparaison terminée pour " + setCode));
                })
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors de la comparaison pour " + setCode)));
    }

    /**
     * Lister les extensions récentes disponibles sur Scryfall
     */
    @GetMapping("/recent-sets")
    public Mono<ResponseEntity<ApiResponse<Object>>> getRecentSets() {
        logger.info("📅 Récupération des extensions récentes depuis Scryfall");

        // Extensions récentes connues à tester
        String[] recentSetCodes = {"FIN", "ACR", "MH3", "OTJ", "MKM", "LCI", "WOE", "LTR", "BLB"};

        Map<String, Object> results = new HashMap<>();

        // Pour chaque extension, vérifier rapidement la disponibilité
        for (String setCode : recentSetCodes) {
            try {
                boolean exists = scryfallService.setExistsOnScryfall(setCode).block();
                results.put(setCode, Map.of(
                        "available", exists,
                        "status", exists ? "✅ Disponible" : "❌ Non trouvé"
                ));
            } catch (Exception e) {
                results.put(setCode, Map.of(
                        "available", false,
                        "status", "❌ Erreur: " + e.getMessage()
                ));
            }
        }

        return Mono.just(ResponseEntity.ok(ApiResponse.success(results,
                "Vérification des extensions récentes terminée")));
    }

    /**
     * Statistiques globales Scryfall
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getScryfallStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Compter les extensions synchronisées via Scryfall
            // (on pourrait ajouter un champ source dans SetEntity pour tracer)
            long totalSets = setRepository.count();
            long syncedSets = setRepository.countSyncedSets();

            stats.put("totalSetsInDb", totalSets);
            stats.put("syncedSets", syncedSets);
            stats.put("unsyncedSets", totalSets - syncedSets);
            stats.put("scryfallApiStatus", "Available");

            return ResponseEntity.ok(ApiResponse.success(stats, "Statistiques Scryfall"));

        } catch (Exception e) {
            logger.error("❌ Erreur stats Scryfall : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    // Ajoutez ces endpoints dans votre ScryfallController

    /**
     * Diagnostic complet d'une extension avec nombre de cartes attendu
     */
    @GetMapping("/diagnose/{setCode}")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> diagnoseSet(@PathVariable String setCode) {
        logger.info("🔍 Diagnostic complet de l'extension {}", setCode);

        return scryfallService.getSetInfo(setCode)
                .map(setInfo -> {
                    Map<String, Object> diagnosis = new HashMap<>();
                    diagnosis.put("setCode", setCode.toUpperCase());
                    diagnosis.put("exists", setInfo.exists());

                    if (setInfo.exists()) {
                        diagnosis.put("name", setInfo.name());
                        diagnosis.put("expectedCards", setInfo.expectedCardCount());
                        diagnosis.put("releaseDate", setInfo.releaseDate());

                        // Vérifier combien on a en local
                        long localCards = cardRepository.countBySetCode(setCode);
                        diagnosis.put("localCards", localCards);
                        diagnosis.put("missingCards", setInfo.expectedCardCount() - localCards);

                        String status;
                        if (localCards == 0) {
                            status = "❌ Aucune carte synchronisée";
                        } else if (localCards < setInfo.expectedCardCount()) {
                            status = "⚠️ Synchronisation incomplète";
                        } else {
                            status = "✅ Synchronisation complète";
                        }
                        diagnosis.put("status", status);

                        // Recommandations
                        List<String> recommendations = new ArrayList<>();
                        if (localCards < setInfo.expectedCardCount()) {
                            recommendations.add("Utiliser /api/scryfall/sync-complete/" + setCode);
                            recommendations.add("Vérifier la pagination (plus de 175 cartes nécessitent plusieurs requêtes)");
                        }
                        if (localCards == 0) {
                            recommendations.add("Première synchronisation requise");
                        }
                        diagnosis.put("recommendations", recommendations);

                    } else {
                        diagnosis.put("error", "Extension non trouvée sur Scryfall");
                    }

                    return ResponseEntity.ok(ApiResponse.success(diagnosis,
                            "Diagnostic terminé pour " + setCode));
                })
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors du diagnostic pour " + setCode)));
    }

    /**
     * Synchronisation COMPLÈTE avec pagination pour récupérer TOUTES les cartes
     */
    @PostMapping("/sync-complete/{setCode}")
    public ResponseEntity<ApiResponse<String>> syncCompleteSet(@PathVariable String setCode) {
        try {
            logger.info("🔮 Synchronisation COMPLÈTE de {} depuis Scryfall (avec pagination)", setCode);

            CompletableFuture.runAsync(() -> {
                try {
                    // D'abord obtenir les infos de l'extension
                    scryfallService.getSetInfo(setCode)
                            .subscribe(setInfo -> {
                                if (setInfo.exists()) {
                                    logger.info("🎯 Extension {} trouvée : {} cartes attendues",
                                            setCode, setInfo.expectedCardCount());

                                    // Supprimer les anciennes cartes pour éviter les doublons
                                    List<CardEntity> existingCards = cardRepository.findBySetCodeOrderByNameAsc(setCode);
                                    if (!existingCards.isEmpty()) {
                                        cardRepository.deleteAll(existingCards);
                                        logger.info("🗑️ {} anciennes cartes supprimées pour {}",
                                                existingCards.size(), setCode);
                                    }

                                    // Synchroniser TOUTES les cartes avec pagination
                                    scryfallService.getCardsFromScryfall(setCode)
                                            .subscribe(allCards -> {
                                                if (!allCards.isEmpty()) {
                                                    logger.info("✅ {} cartes récupérées depuis Scryfall pour {} (attendu: {})",
                                                            allCards.size(), setCode, setInfo.expectedCardCount());

                                                    // Sauvegarder en base de données
                                                    persistenceService.saveCardsForSet(setCode, allCards)
                                                            .thenAccept(savedCount -> {
                                                                logger.info("💾 {} cartes sauvegardées en base pour {}",
                                                                        savedCount, setCode);

                                                                if (savedCount < setInfo.expectedCardCount()) {
                                                                    logger.warn("⚠️ Cartes manquantes pour {} : {} sauvegardées sur {} attendues",
                                                                            setCode, savedCount, setInfo.expectedCardCount());
                                                                } else {
                                                                    logger.info("🎉 Synchronisation complète réussie pour {} !", setCode);
                                                                }
                                                            });
                                                } else {
                                                    logger.warn("⚠️ Aucune carte récupérée pour {}", setCode);
                                                }
                                            }, error -> {
                                                logger.error("❌ Erreur récupération cartes pour {} : {}",
                                                        setCode, error.getMessage());
                                            });
                                } else {
                                    logger.error("❌ Extension {} non trouvée sur Scryfall", setCode);
                                }
                            });

                } catch (Exception e) {
                    logger.error("❌ Erreur générale sync complète pour {} : {}", setCode, e.getMessage());
                }
            });

            return ResponseEntity.accepted()
                    .body(ApiResponse.success(
                            "Synchronisation COMPLÈTE démarrée pour : " + setCode.toUpperCase(),
                            "Toutes les cartes seront récupérées avec pagination automatique"));

        } catch (Exception e) {
            logger.error("❌ Erreur déclenchement sync complète pour {} : {}", setCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Compter les cartes disponibles sur Scryfall vs local (sans téléchargement)
     */
    @GetMapping("/count/{setCode}")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> countCards(@PathVariable String setCode) {
        logger.info("🧮 Comptage des cartes pour {}", setCode);

        return scryfallService.getSetInfo(setCode)
                .map(setInfo -> {
                    Map<String, Object> count = new HashMap<>();
                    count.put("setCode", setCode.toUpperCase());

                    if (setInfo.exists()) {
                        count.put("scryfallTotal", setInfo.expectedCardCount());
                        count.put("localTotal", cardRepository.countBySetCode(setCode));
                        count.put("difference", setInfo.expectedCardCount() - cardRepository.countBySetCode(setCode));
                        count.put("needsFullSync", setInfo.expectedCardCount() > cardRepository.countBySetCode(setCode));

                        String message = String.format("Scryfall: %d cartes, Local: %d cartes",
                                setInfo.expectedCardCount(),
                                cardRepository.countBySetCode(setCode));

                        return ResponseEntity.ok(ApiResponse.success(count, message));
                    } else {
                        count.put("error", "Extension non trouvée");
                        return ResponseEntity.ok(ApiResponse.success(count, "Extension non disponible"));
                    }
                })
                .onErrorReturn(ResponseEntity.badRequest()
                        .body(ApiResponse.error("Erreur lors du comptage pour " + setCode)));
    }

    /**
     * Statistiques détaillées de la synchronisation
     */
    @GetMapping("/sync-stats/{setCode}")
    public ResponseEntity<ApiResponse<Object>> getSyncStats(@PathVariable String setCode) {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Cartes par rareté
            List<CardEntity> cards = cardRepository.findBySetCodeOrderByNameAsc(setCode);
            Map<String, Long> rarityStats = cards.stream()
                    .collect(Collectors.groupingBy(
                            card -> card.getRarity() != null ? card.getRarity() : "Unknown",
                            Collectors.counting()
                    ));

            // Cartes avec/sans images
            long cardsWithImages = cards.stream()
                    .mapToLong(card -> (card.getImageDownloaded() != null && card.getImageDownloaded()) ? 1 : 0)
                    .sum();

            // Artistes uniques
            long uniqueArtists = cards.stream()
                    .map(card -> card.getArtist() != null ? card.getArtist() : "Unknown")
                    .distinct()
                    .count();

            stats.put("setCode", setCode.toUpperCase());
            stats.put("totalCards", cards.size());
            stats.put("rarityBreakdown", rarityStats);
            stats.put("imagesDownloaded", cardsWithImages);
            stats.put("imagesPercentage", cards.size() > 0 ? (double) cardsWithImages / cards.size() * 100 : 0);
            stats.put("uniqueArtists", uniqueArtists);
            stats.put("lastUpdate", cards.stream()
                    .map(CardEntity::getUpdatedAt)
                    .max(java.time.LocalDateTime::compareTo)
                    .orElse(null));

            return ResponseEntity.ok(ApiResponse.success(stats,
                    "Statistiques de synchronisation pour " + setCode));

        } catch (Exception e) {
            logger.error("❌ Erreur stats sync pour {} : {}", setCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    // Ajoutez cet endpoint dans ScryfallController pour debug

    /**
     * Debug de la pagination - teste manuellement les pages Scryfall
     */
    @GetMapping("/debug-pagination/{setCode}")
    public ResponseEntity<ApiResponse<Object>> debugPagination(@PathVariable String setCode) {
        try {
            logger.info("🔍 Debug pagination pour {}", setCode);

            // Test direct de l'API Scryfall
            WebClient debugClient = WebClient.builder()
                    .baseUrl("https://api.scryfall.com")
                    .build();

            Map<String, Object> debugInfo = new HashMap<>();
            debugInfo.put("setCode", setCode);

            // Test première page
            String firstPageUrl = "/cards/search?q=set:" + setCode.toLowerCase() + "&format=json&order=name";
            debugInfo.put("firstPageUrl", "https://api.scryfall.com" + firstPageUrl);

            try {
                String response = debugClient.get()
                        .uri(firstPageUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(10))
                        .block();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response);

                debugInfo.put("hasData", root.has("data"));
                debugInfo.put("dataIsArray", root.has("data") && root.get("data").isArray());

                if (root.has("data") && root.get("data").isArray()) {
                    debugInfo.put("firstPageCards", root.get("data").size());
                }

                debugInfo.put("hasMore", root.has("has_more") && root.get("has_more").asBoolean());
                debugInfo.put("totalCards", root.has("total_cards") ? root.get("total_cards").asInt() : "N/A");

                if (root.has("next_page")) {
                    String nextPageUrl = root.get("next_page").asText();
                    debugInfo.put("nextPageUrl", nextPageUrl);

                    // Test deuxième page
                    try {
                        String response2 = debugClient.get()
                                .uri(nextPageUrl)
                                .retrieve()
                                .bodyToMono(String.class)
                                .timeout(Duration.ofSeconds(10))
                                .block();

                        JsonNode root2 = mapper.readTree(response2);
                        if (root2.has("data") && root2.get("data").isArray()) {
                            debugInfo.put("secondPageCards", root2.get("data").size());
                            debugInfo.put("secondPageHasMore", root2.has("has_more") && root2.get("has_more").asBoolean());
                        }

                    } catch (Exception e) {
                        debugInfo.put("secondPageError", e.getMessage());
                    }
                }

                // Informations sur l'erreur potentielle
                if (root.has("type") && "error".equals(root.get("type").asText())) {
                    debugInfo.put("scryfallError", root.get("details").asText());
                }

            } catch (Exception e) {
                debugInfo.put("requestError", e.getMessage());
            }

            return ResponseEntity.ok(ApiResponse.success(debugInfo, "Debug pagination terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur debug pagination : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur debug : " + e.getMessage()));
        }
    }

    /**
     * Synchronisation avec logs détaillés pour debug
     */
    @PostMapping("/sync-debug/{setCode}")
    public ResponseEntity<ApiResponse<String>> syncWithDebug(@PathVariable String setCode) {
        try {
            logger.info("🐛 Synchronisation avec debug pour {}", setCode);

            CompletableFuture.runAsync(() -> {
                try {
                    // Test avec un WebClient séparé pour voir les requêtes
                    WebClient debugClient = WebClient.builder()
                            .baseUrl("https://api.scryfall.com")
                            .build();

                    List<MtgCard> allCards = new ArrayList<>();
                    String currentUrl = "/cards/search?q=set:" + setCode.toLowerCase() + "&format=json&order=name";
                    int pageNumber = 1;

                    while (currentUrl != null && pageNumber <= 10) { // Limite sécurité
                        logger.info("🔍 DEBUG - Page {} : {}", pageNumber, currentUrl);

                        try {
                            String response = debugClient.get()
                                    .uri(currentUrl)
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .timeout(Duration.ofSeconds(15))
                                    .block();

                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode root = mapper.readTree(response);

                            // Vérifier erreur
                            if (root.has("type") && "error".equals(root.get("type").asText())) {
                                logger.error("❌ Erreur Scryfall page {} : {}", pageNumber,
                                        root.has("details") ? root.get("details").asText() : "Erreur inconnue");
                                break;
                            }

                            // Parser les cartes de cette page
                            JsonNode dataNode = root.get("data");
                            if (dataNode != null && dataNode.isArray()) {
                                int pageCards = dataNode.size();
                                logger.info("✅ DEBUG - Page {} : {} cartes récupérées", pageNumber, pageCards);

                                // Parser chaque carte (version simplifiée pour debug)
                                for (JsonNode cardNode : dataNode) {
                                    try {
                                        MtgCard card = parseScryfallCardSimple(cardNode);
                                        allCards.add(card);
                                    } catch (Exception e) {
                                        logger.warn("⚠️ Erreur parsing carte page {} : {}", pageNumber, e.getMessage());
                                    }
                                }

                                logger.info("📊 DEBUG - Total cartes jusqu'à page {} : {}", pageNumber, allCards.size());
                            }

                            // Vérifier page suivante
                            if (root.has("has_more") && root.get("has_more").asBoolean() && root.has("next_page")) {
                                currentUrl = root.get("next_page").asText();
                                // Enlever le domaine si présent
                                if (currentUrl.startsWith("https://api.scryfall.com")) {
                                    currentUrl = currentUrl.substring("https://api.scryfall.com".length());
                                }
                                pageNumber++;

                                logger.info("🔄 DEBUG - Page suivante trouvée : {}", currentUrl);

                                // Délai entre les requêtes
                                Thread.sleep(150);
                            } else {
                                logger.info("🏁 DEBUG - Dernière page atteinte à la page {}", pageNumber);
                                currentUrl = null;
                            }

                        } catch (Exception e) {
                            logger.error("❌ DEBUG - Erreur page {} : {}", pageNumber, e.getMessage());
                            break;
                        }
                    }

                    logger.info("🎯 DEBUG - Résultat final : {} cartes récupérées pour {}", allCards.size(), setCode);

                    if (!allCards.isEmpty()) {
                        // Supprimer les anciennes cartes
                        List<CardEntity> existingCards = cardRepository.findBySetCodeOrderByNameAsc(setCode);
                        if (!existingCards.isEmpty()) {
                            cardRepository.deleteAll(existingCards);
                            logger.info("🗑️ DEBUG - {} anciennes cartes supprimées", existingCards.size());
                        }

                        // Sauvegarder les nouvelles cartes
                        persistenceService.saveCardsForSet(setCode, allCards)
                                .thenAccept(savedCount -> {
                                    logger.info("💾 DEBUG - {} cartes sauvegardées pour {}", savedCount, setCode);
                                });
                    }

                } catch (Exception e) {
                    logger.error("❌ DEBUG - Erreur générale : {}", e.getMessage());
                }
            });

            return ResponseEntity.accepted()
                    .body(ApiResponse.success("Synchronisation DEBUG démarrée pour : " + setCode));

        } catch (Exception e) {
            logger.error("❌ Erreur sync debug : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    // Méthode simplifiée pour le debug
    private MtgCard parseScryfallCardSimple(JsonNode cardNode) {
        String imageUrl = null;
        if (cardNode.has("image_uris") && cardNode.get("image_uris").has("normal")) {
            imageUrl = cardNode.get("image_uris").get("normal").asText();
        }

        return new MtgCard(
                cardNode.get("id").asText(),
                cardNode.get("name").asText(),
                cardNode.has("mana_cost") ? cardNode.get("mana_cost").asText() : null,
                cardNode.has("cmc") ? cardNode.get("cmc").asInt() : null,
                null, // colors - simplifié
                null, // colorIdentity - simplifié
                cardNode.get("type_line").asText(),
                null, // supertypes - simplifié
                null, // types - simplifié
                null, // subtypes - simplifié
                cardNode.has("rarity") ? cardNode.get("rarity").asText() : "Common",
                cardNode.get("set").asText().toUpperCase(),
                cardNode.get("set_name").asText(),
                cardNode.has("oracle_text") ? cardNode.get("oracle_text").asText() : null,
                cardNode.has("artist") ? cardNode.get("artist").asText() : null,
                cardNode.has("collector_number") ? cardNode.get("collector_number").asText() : null,
                cardNode.has("power") ? cardNode.get("power").asText() : null,
                cardNode.has("toughness") ? cardNode.get("toughness").asText() : null,
                cardNode.has("layout") ? cardNode.get("layout").asText() : null,
                null, // multiverseid
                imageUrl
        );
    }

}