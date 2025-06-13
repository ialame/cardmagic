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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
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

    // Ajoutez cet endpoint dans votre ScryfallController pour tester la pagination

    // Ajoutez cet endpoint dans votre ScryfallController pour voir la réponse brute

    /**
     * Test simple pour voir la réponse brute de Scryfall
     */
    @GetMapping("/debug-raw/{setCode}")
    public ResponseEntity<ApiResponse<Object>> debugRawResponse(@PathVariable String setCode) {
        try {
            logger.info("🔍 Test RAW pour {}", setCode);

            WebClient simpleClient = WebClient.builder()
                    .baseUrl("https://api.scryfall.com")
                    .build();

            String url = "/cards/search?q=set:" + setCode.toLowerCase() + "&format=json&order=name";

            Map<String, Object> result = new HashMap<>();
            result.put("setCode", setCode);
            result.put("url", "https://api.scryfall.com" + url);

            try {
                // Récupérer la réponse brute
                String rawResponse = simpleClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(10))
                        .block();

                result.put("rawResponseLength", rawResponse.length());
                result.put("rawResponseStart", rawResponse.substring(0, Math.min(500, rawResponse.length())));

                // Essayer de parser le JSON
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(rawResponse);

                result.put("hasDataField", root.has("data"));
                result.put("hasTypeField", root.has("type"));
                result.put("hasDetailsField", root.has("details"));

                if (root.has("type")) {
                    result.put("type", root.get("type").asText());
                }

                if (root.has("details")) {
                    result.put("details", root.get("details").asText());
                }

                if (root.has("data")) {
                    JsonNode dataNode = root.get("data");
                    result.put("dataIsArray", dataNode.isArray());
                    if (dataNode.isArray()) {
                        result.put("dataArraySize", dataNode.size());
                    }
                }

                result.put("parseSuccess", true);

            } catch (Exception e) {
                result.put("parseSuccess", false);
                result.put("error", e.getMessage());
                result.put("errorType", e.getClass().getSimpleName());

                // Si c'est une WebClientResponseException, récupérer plus de détails
                if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                    org.springframework.web.reactive.function.client.WebClientResponseException wcre =
                            (org.springframework.web.reactive.function.client.WebClientResponseException) e;
                    result.put("httpStatus", wcre.getStatusCode().value());
                    result.put("responseBody", wcre.getResponseBodyAsString());
                }
            }

            return ResponseEntity.ok(ApiResponse.success(result, "Test RAW terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur test raw : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Test avec différents codes d'extension pour comparaison
     */
    @GetMapping("/debug-compare-sets")
    public ResponseEntity<ApiResponse<Object>> debugCompareSets() {
        try {
            logger.info("🔍 Test comparaison extensions");

            WebClient simpleClient = WebClient.builder()
                    .baseUrl("https://api.scryfall.com")
                    .build();

            String[] testSets = {"FIN", "BLB", "MH3", "INVALID"};
            Map<String, Object> results = new HashMap<>();

            for (String setCode : testSets) {
                Map<String, Object> setResult = new HashMap<>();
                String url = "/cards/search?q=set:" + setCode.toLowerCase() + "&format=json&order=name";

                try {
                    String response = simpleClient.get()
                            .uri(url)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(10))
                            .block();

                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(response);

                    setResult.put("success", true);
                    setResult.put("hasData", root.has("data"));
                    setResult.put("isError", root.has("type") && "error".equals(root.get("type").asText()));

                    if (root.has("type") && "error".equals(root.get("type").asText())) {
                        setResult.put("errorDetails", root.has("details") ? root.get("details").asText() : "Pas de détails");
                    } else if (root.has("data") && root.get("data").isArray()) {
                        setResult.put("cardCount", root.get("data").size());
                        setResult.put("totalCards", root.has("total_cards") ? root.get("total_cards").asInt() : "N/A");
                    }

                } catch (Exception e) {
                    setResult.put("success", false);
                    setResult.put("error", e.getMessage());
                }

                results.put(setCode, setResult);
            }

            return ResponseEntity.ok(ApiResponse.success(results, "Comparaison terminée"));

        } catch (Exception e) {
            logger.error("❌ Erreur test comparaison : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Test simple SANS gestion d'erreur pour voir la réponse brute
     */
    @GetMapping("/debug-simple/{setCode}")
    public ResponseEntity<ApiResponse<Object>> debugSimple(@PathVariable String setCode) {
        try {
            logger.info("🔍 Test SIMPLE pour {}", setCode);

            // WebClient minimal sans configuration d'erreur
            WebClient simpleClient = WebClient.builder()
                    .baseUrl("https://api.scryfall.com")
                    .build();

            String url = "/cards/search?q=set:" + setCode.toLowerCase() + "&format=json&order=name";

            Map<String, Object> result = new HashMap<>();
            result.put("setCode", setCode);
            result.put("url", "https://api.scryfall.com" + url);

            // Récupération DIRECTE sans gestion d'erreur
            String rawResponse = simpleClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            result.put("success", true);
            result.put("responseLength", rawResponse.length());
            result.put("responsePreview", rawResponse.substring(0, Math.min(200, rawResponse.length())));

            // Parse JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(rawResponse);

            result.put("jsonValid", true);
            result.put("hasType", root.has("type"));
            result.put("hasData", root.has("data"));
            result.put("hasDetails", root.has("details"));

            if (root.has("type")) {
                result.put("type", root.get("type").asText());
            }

            if (root.has("details")) {
                result.put("details", root.get("details").asText());
            }

            if (root.has("data") && root.get("data").isArray()) {
                result.put("cardCount", root.get("data").size());
                result.put("totalCards", root.has("total_cards") ? root.get("total_cards").asInt() : "N/A");

                // Première carte pour vérifier
                if (root.get("data").size() > 0) {
                    JsonNode firstCard = root.get("data").get(0);
                    if (firstCard.has("name")) {
                        result.put("firstCardName", firstCard.get("name").asText());
                    }
                }
            }

            return ResponseEntity.ok(ApiResponse.success(result, "Test simple réussi"));

        } catch (Exception e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("setCode", setCode);
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("errorType", e.getClass().getSimpleName());

            logger.error("❌ Erreur test simple {} : {}", setCode, e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(errorResult, "Test simple avec erreur"));
        }
    }

    // Ajoutez ces endpoints dans votre ScryfallController pour diagnostiquer le problème

    /**
     * Test de connectivité basique vers Scryfall
     */
    @GetMapping("/debug-connectivity")
    public ResponseEntity<ApiResponse<Object>> debugConnectivity() {
        try {
            logger.info("🔍 Test de connectivité vers Scryfall");

            Map<String, Object> result = new HashMap<>();

            // Test 1: Connectivité basique vers l'API Scryfall
            try {
                WebClient basicClient = WebClient.builder()
                        .baseUrl("https://api.scryfall.com")
                        .codecs(configurer -> {
                            configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024);
                            configurer.defaultCodecs().enableLoggingRequestDetails(true);
                        })
                        .build();

                // Test avec un endpoint simple qui devrait toujours marcher
                String simpleResponse = basicClient.get()
                        .uri("/cards/random")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(10))
                        .block();

                result.put("randomCardTest", Map.of(
                        "success", true,
                        "responseLength", simpleResponse.length(),
                        "preview", simpleResponse.substring(0, Math.min(100, simpleResponse.length()))
                ));

            } catch (Exception e) {
                result.put("randomCardTest", Map.of(
                        "success", false,
                        "error", e.getMessage(),
                        "errorType", e.getClass().getSimpleName()
                ));
            }

            // Test 2: Test des sets endpoint
            try {
                WebClient setsClient = WebClient.builder()
                        .baseUrl("https://api.scryfall.com")
                        .build();

                String setsResponse = setsClient.get()
                        .uri("/sets/blb")  // Bloomburrow devrait exister
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(10))
                        .block();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode setsRoot = mapper.readTree(setsResponse);

                result.put("bloomburrowSetTest", Map.of(
                        "success", true,
                        "setName", setsRoot.has("name") ? setsRoot.get("name").asText() : "N/A",
                        "cardCount", setsRoot.has("card_count") ? setsRoot.get("card_count").asInt() : 0
                ));

            } catch (Exception e) {
                result.put("bloomburrowSetTest", Map.of(
                        "success", false,
                        "error", e.getMessage(),
                        "errorType", e.getClass().getSimpleName()
                ));
            }

            // Test 3: Test de recherche simple
            try {
                WebClient searchClient = WebClient.builder()
                        .baseUrl("https://api.scryfall.com")
                        .build();

                // Recherche très simple qui devrait marcher
                String searchResponse = searchClient.get()
                        .uri("/cards/search?q=c%3Ared&format=json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(15))
                        .block();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode searchRoot = mapper.readTree(searchResponse);

                Map<String, Object> searchResult = new HashMap<>();
                searchResult.put("success", true);

                if (searchRoot.has("type") && "error".equals(searchRoot.get("type").asText())) {
                    searchResult.put("isError", true);
                    searchResult.put("errorDetails", searchRoot.has("details") ? searchRoot.get("details").asText() : "N/A");
                } else if (searchRoot.has("data") && searchRoot.get("data").isArray()) {
                    searchResult.put("isError", false);
                    searchResult.put("cardCount", searchRoot.get("data").size());
                    searchResult.put("totalCards", searchRoot.has("total_cards") ? searchRoot.get("total_cards").asInt() : "N/A");
                }

                result.put("simpleSearchTest", searchResult);

            } catch (Exception e) {
                result.put("simpleSearchTest", Map.of(
                        "success", false,
                        "error", e.getMessage(),
                        "errorType", e.getClass().getSimpleName()
                ));
            }

            // Test 4: Test spécifique FIN avec debugging
            try {
                WebClient finClient = WebClient.builder()
                        .baseUrl("https://api.scryfall.com")
                        .build();

                String finResponse = finClient.get()
                        .uri("/cards/search?q=set%3Afin&format=json&order=name")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(15))
                        .block();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode finRoot = mapper.readTree(finResponse);

                Map<String, Object> finResult = new HashMap<>();
                finResult.put("success", true);
                finResult.put("responseLength", finResponse.length());

                if (finRoot.has("type") && "error".equals(finRoot.get("type").asText())) {
                    finResult.put("isError", true);
                    finResult.put("errorDetails", finRoot.has("details") ? finRoot.get("details").asText() : "N/A");
                    finResult.put("errorCode", finRoot.has("code") ? finRoot.get("code").asText() : "N/A");
                } else if (finRoot.has("data") && finRoot.get("data").isArray()) {
                    finResult.put("isError", false);
                    finResult.put("cardCount", finRoot.get("data").size());
                    finResult.put("totalCards", finRoot.has("total_cards") ? finRoot.get("total_cards").asInt() : "N/A");

                    if (finRoot.get("data").size() > 0) {
                        JsonNode firstCard = finRoot.get("data").get(0);
                        finResult.put("firstCardName", firstCard.has("name") ? firstCard.get("name").asText() : "N/A");
                    }
                }

                result.put("finSpecificTest", finResult);

            } catch (Exception e) {
                result.put("finSpecificTest", Map.of(
                        "success", false,
                        "error", e.getMessage(),
                        "errorType", e.getClass().getSimpleName()
                ));
            }

            return ResponseEntity.ok(ApiResponse.success(result, "Tests de connectivité terminés"));

        } catch (Exception e) {
            logger.error("❌ Erreur test connectivité : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Test avec une approche RestTemplate pour comparaison
     */
    @GetMapping("/debug-resttemplate/{setCode}")
    public ResponseEntity<ApiResponse<Object>> debugWithRestTemplate(@PathVariable String setCode) {
        try {
            logger.info("🔍 Test avec RestTemplate pour {}", setCode);

            // Utiliser RestTemplate au lieu de WebClient
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

            String url = "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() + "&format=json&order=name";

            Map<String, Object> result = new HashMap<>();
            result.put("setCode", setCode);
            result.put("url", url);

            try {
                String response = restTemplate.getForObject(url, String.class);

                result.put("success", true);
                result.put("responseLength", response.length());

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response);

                result.put("hasType", root.has("type"));
                result.put("hasData", root.has("data"));

                if (root.has("type") && "error".equals(root.get("type").asText())) {
                    result.put("isError", true);
                    result.put("errorDetails", root.has("details") ? root.get("details").asText() : "N/A");
                } else if (root.has("data") && root.get("data").isArray()) {
                    result.put("isError", false);
                    result.put("cardCount", root.get("data").size());
                    result.put("totalCards", root.has("total_cards") ? root.get("total_cards").asInt() : "N/A");
                }

            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
                result.put("errorType", e.getClass().getSimpleName());
            }

            return ResponseEntity.ok(ApiResponse.success(result, "Test RestTemplate terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur test RestTemplate : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    // Ajoutez cet endpoint dans votre ScryfallController pour debugger la pagination RestTemplate

    /**
     * Debug de pagination avec RestTemplate pour voir exactement ce qui se passe
     */
    @GetMapping("/debug-pagination-resttemplate/{setCode}")
    public ResponseEntity<ApiResponse<Object>> debugPaginationRestTemplate(@PathVariable String setCode) {
        try {
            logger.info("🔍 Debug pagination RestTemplate pour {}", setCode);

            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> debugInfo = new HashMap<>();
            debugInfo.put("setCode", setCode);
            debugInfo.put("timestamp", LocalDateTime.now());

            List<Map<String, Object>> pagesInfo = new ArrayList<>();
            String currentUrl = "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() + "&format=json&order=name";
            int pageNumber = 1;
            int totalCards = 0;

            while (currentUrl != null && pageNumber <= 5) { // Limite à 5 pages pour le debug
                Map<String, Object> pageInfo = new HashMap<>();
                pageInfo.put("pageNumber", pageNumber);
                pageInfo.put("url", currentUrl);

                try {
                    String response = restTemplate.getForObject(currentUrl, String.class);

                    if (response == null) {
                        pageInfo.put("success", false);
                        pageInfo.put("error", "Réponse nulle");
                        break;
                    }

                    JsonNode root = mapper.readTree(response);
                    pageInfo.put("success", true);
                    pageInfo.put("responseLength", response.length());

                    // Analyser la réponse
                    if (root.has("type") && "error".equals(root.get("type").asText())) {
                        pageInfo.put("isError", true);
                        pageInfo.put("errorDetails", root.has("details") ? root.get("details").asText() : "N/A");
                        currentUrl = null; // Arrêter en cas d'erreur
                    } else {
                        pageInfo.put("isError", false);

                        if (root.has("data") && root.get("data").isArray()) {
                            int pageCards = root.get("data").size();
                            totalCards += pageCards;
                            pageInfo.put("cardsInPage", pageCards);
                            pageInfo.put("runningTotal", totalCards);

                            // Échantillon de cartes
                            JsonNode dataArray = root.get("data");
                            List<String> sampleCards = new ArrayList<>();
                            for (int i = 0; i < Math.min(3, dataArray.size()); i++) {
                                JsonNode card = dataArray.get(i);
                                if (card.has("name")) {
                                    sampleCards.add(card.get("name").asText());
                                }
                            }
                            pageInfo.put("sampleCards", sampleCards);
                        }

                        // INFORMATIONS CRITIQUES DE PAGINATION
                        pageInfo.put("hasMore", root.has("has_more") && root.get("has_more").asBoolean());
                        pageInfo.put("totalCards", root.has("total_cards") ? root.get("total_cards").asInt() : "N/A");
                        pageInfo.put("hasNextPage", root.has("next_page"));

                        if (root.has("next_page")) {
                            String nextPageUrl = root.get("next_page").asText();
                            pageInfo.put("nextPageUrl", nextPageUrl);
                            currentUrl = nextPageUrl; // URL complète pour RestTemplate
                        } else {
                            pageInfo.put("nextPageUrl", null);
                            currentUrl = null;
                        }

                        // DIAGNOSTIC DE PAGINATION
                        boolean shouldHaveMore = root.has("total_cards") &&
                                root.get("total_cards").asInt() > totalCards;
                        pageInfo.put("shouldHaveMorePages", shouldHaveMore);

                        if (shouldHaveMore && !root.has("next_page")) {
                            pageInfo.put("paginationProblem", "total_cards indique plus de cartes mais pas de next_page");
                        }
                    }

                } catch (Exception e) {
                    pageInfo.put("success", false);
                    pageInfo.put("error", e.getMessage());
                    pageInfo.put("errorType", e.getClass().getSimpleName());
                    currentUrl = null; // Arrêter en cas d'erreur
                }

                pagesInfo.add(pageInfo);
                pageNumber++;

                // Délai entre les requêtes
                if (currentUrl != null) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            debugInfo.put("pagesAnalyzed", pagesInfo);
            debugInfo.put("totalCardsFound", totalCards);
            debugInfo.put("pagesTested", pageNumber - 1);

            // COMPARAISON AVEC L'ENDPOINT /sets
            try {
                String setInfoUrl = "https://api.scryfall.com/sets/" + setCode.toLowerCase();
                String setResponse = restTemplate.getForObject(setInfoUrl, String.class);

                if (setResponse != null) {
                    JsonNode setRoot = mapper.readTree(setResponse);
                    Map<String, Object> setInfo = new HashMap<>();
                    setInfo.put("name", setRoot.has("name") ? setRoot.get("name").asText() : "N/A");
                    setInfo.put("cardCount", setRoot.has("card_count") ? setRoot.get("card_count").asInt() : 0);
                    setInfo.put("releaseDate", setRoot.has("released_at") ? setRoot.get("released_at").asText() : "N/A");
                    debugInfo.put("setEndpointInfo", setInfo);

                    // ANALYSE DE LA DIFFÉRENCE
                    int setCardCount = setRoot.has("card_count") ? setRoot.get("card_count").asInt() : 0;
                    if (setCardCount != totalCards) {
                        Map<String, Object> analysis = new HashMap<>();
                        analysis.put("setEndpointCards", setCardCount);
                        analysis.put("searchEndpointCards", totalCards);
                        analysis.put("difference", setCardCount - totalCards);

                        if (setCardCount > totalCards) {
                            analysis.put("conclusion", "L'endpoint /sets compte plus de cartes que /cards/search - possible problème de filtre");
                        } else {
                            analysis.put("conclusion", "L'endpoint /cards/search trouve plus de cartes que /sets - données incohérentes");
                        }

                        debugInfo.put("discrepancyAnalysis", analysis);
                    }
                }
            } catch (Exception e) {
                debugInfo.put("setEndpointError", e.getMessage());
            }

            // Recommandations
            List<String> recommendations = new ArrayList<>();
            if (totalCards == 0) {
                recommendations.add("Extension non trouvée sur Scryfall");
            } else if (totalCards < 300) {
                recommendations.add("Pagination semble incomplète - vérifier les filtres Scryfall");
                recommendations.add("Essayer avec des paramètres de recherche différents");
            } else {
                recommendations.add("Pagination semble fonctionner correctement");
            }

            debugInfo.put("recommendations", recommendations);

            return ResponseEntity.ok(ApiResponse.success(debugInfo,
                    "Debug pagination RestTemplate terminé - " + totalCards + " cartes trouvées"));

        } catch (Exception e) {
            logger.error("❌ Erreur debug pagination RestTemplate : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Test avec différents paramètres de recherche pour voir si on peut récupérer plus de cartes
     */
    @GetMapping("/test-search-variants/{setCode}")
    public ResponseEntity<ApiResponse<Object>> testSearchVariants(@PathVariable String setCode) {
        try {
            logger.info("🔍 Test des variantes de recherche pour {}", setCode);

            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> results = new HashMap<>();

            // Différentes variantes de recherche à tester
            Map<String, String> searchVariants = new HashMap<>();
            searchVariants.put("basic", "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() + "&format=json");
            searchVariants.put("withOrder", "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() + "&format=json&order=name");
            searchVariants.put("includeExtras", "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() + "&include_extras=true&format=json");
            searchVariants.put("includeVariations", "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() + "&include_variations=true&format=json");
            searchVariants.put("unique", "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() + "&unique=cards&format=json");
            searchVariants.put("allVersions", "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() + "&include_extras=true&include_variations=true&format=json");

            for (Map.Entry<String, String> variant : searchVariants.entrySet()) {
                String variantName = variant.getKey();
                String url = variant.getValue();

                Map<String, Object> variantResult = new HashMap<>();
                variantResult.put("url", url);

                try {
                    String response = restTemplate.getForObject(url, String.class);

                    if (response != null) {
                        JsonNode root = mapper.readTree(response);

                        if (root.has("type") && "error".equals(root.get("type").asText())) {
                            variantResult.put("success", false);
                            variantResult.put("error", root.has("details") ? root.get("details").asText() : "Erreur inconnue");
                        } else {
                            variantResult.put("success", true);
                            variantResult.put("totalCards", root.has("total_cards") ? root.get("total_cards").asInt() : 0);
                            variantResult.put("firstPageCards", root.has("data") && root.get("data").isArray() ? root.get("data").size() : 0);
                            variantResult.put("hasMore", root.has("has_more") && root.get("has_more").asBoolean());

                            // Quelques noms de cartes pour vérification
                            if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                                List<String> sampleNames = new ArrayList<>();
                                JsonNode dataArray = root.get("data");
                                for (int i = 0; i < Math.min(3, dataArray.size()); i++) {
                                    JsonNode card = dataArray.get(i);
                                    if (card.has("name")) {
                                        sampleNames.add(card.get("name").asText());
                                    }
                                }
                                variantResult.put("sampleCards", sampleNames);
                            }
                        }
                    } else {
                        variantResult.put("success", false);
                        variantResult.put("error", "Réponse nulle");
                    }

                } catch (Exception e) {
                    variantResult.put("success", false);
                    variantResult.put("error", e.getMessage());
                }

                results.put(variantName, variantResult);
            }

            // Analyser les résultats
            Map<String, Object> analysis = new HashMap<>();

            Integer maxCards = results.values().stream()
                    .filter(r -> r instanceof Map)
                    .map(r -> (Map<String, Object>) r)
                    .filter(r -> r.containsKey("totalCards"))
                    .mapToInt(r -> (Integer) r.get("totalCards"))
                    .max()
                    .orElse(0);

            analysis.put("maxCardsFound", maxCards);

            // Trouver la meilleure variante
            String bestVariant = results.entrySet().stream()
                    .filter(e -> e.getValue() instanceof Map)
                    .map(e -> Map.entry(e.getKey(), (Map<String, Object>) e.getValue()))
                    .filter(e -> e.getValue().containsKey("totalCards"))
                    .max(Map.Entry.comparingByValue((a, b) ->
                            Integer.compare((Integer) a.get("totalCards"), (Integer) b.get("totalCards"))))
                    .map(Map.Entry::getKey)
                    .orElse("aucune");

            analysis.put("bestVariant", bestVariant);
            analysis.put("recommendation", maxCards > 300 ?
                    "Utiliser la variante '" + bestVariant + "' pour plus de cartes" :
                    "Toutes les variantes donnent des résultats similaires");

            results.put("analysis", analysis);

            return ResponseEntity.ok(ApiResponse.success(results, "Test des variantes terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur test variantes : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Test de pagination manuelle pour vérifier la correction
     */
    @GetMapping("/test-manual-pagination/{setCode}")
    public ResponseEntity<ApiResponse<Object>> testManualPagination(@PathVariable String setCode) {
        try {
            logger.info("🔍 Test pagination manuelle pour {}", setCode);

            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> result = new HashMap<>();
            result.put("setCode", setCode);

            List<Map<String, Object>> pages = new ArrayList<>();
            int totalCards = 0;
            int page = 1;

            while (page <= 5) { // Tester 5 pages max
                String url = String.format(
                        "https://api.scryfall.com/cards/search?q=set:%s&format=json&order=name&page=%d",
                        setCode.toLowerCase(), page
                );

                Map<String, Object> pageInfo = new HashMap<>();
                pageInfo.put("pageNumber", page);
                pageInfo.put("url", url);

                try {
                    String response = restTemplate.getForObject(url, String.class);

                    if (response != null) {
                        JsonNode root = mapper.readTree(response);

                        if (root.has("type") && "error".equals(root.get("type").asText())) {
                            pageInfo.put("success", false);
                            pageInfo.put("error", root.has("details") ? root.get("details").asText() : "Erreur");

                            // Si 404 et qu'on a déjà des cartes, c'est normal
                            if (root.get("details").asText().contains("didn't match any cards") && totalCards > 0) {
                                pageInfo.put("normalEnd", true);
                            }

                            pages.add(pageInfo);
                            break; // Arrêter en cas d'erreur
                        } else {
                            pageInfo.put("success", true);

                            if (root.has("data") && root.get("data").isArray()) {
                                int pageCards = root.get("data").size();
                                totalCards += pageCards;

                                pageInfo.put("cardsInPage", pageCards);
                                pageInfo.put("runningTotal", totalCards);
                                pageInfo.put("hasMore", root.has("has_more") && root.get("has_more").asBoolean());
                                pageInfo.put("totalCardsReported", root.has("total_cards") ? root.get("total_cards").asInt() : "N/A");

                                // Quelques noms pour vérification
                                List<String> sampleNames = new ArrayList<>();
                                JsonNode dataArray = root.get("data");
                                for (int i = 0; i < Math.min(3, dataArray.size()); i++) {
                                    JsonNode card = dataArray.get(i);
                                    if (card.has("name")) {
                                        sampleNames.add(card.get("name").asText());
                                    }
                                }
                                pageInfo.put("sampleCards", sampleNames);

                                // Si moins de 175 cartes, probablement la dernière page
                                if (pageCards < 175) {
                                    pageInfo.put("likelyLastPage", true);
                                    pages.add(pageInfo);
                                    break;
                                }
                            }
                        }
                    } else {
                        pageInfo.put("success", false);
                        pageInfo.put("error", "Réponse nulle");
                        pages.add(pageInfo);
                        break;
                    }

                } catch (Exception e) {
                    pageInfo.put("success", false);
                    pageInfo.put("error", e.getMessage());

                    // Si 404 et qu'on a déjà des cartes, c'est normal
                    if (e.getMessage().contains("404") && totalCards > 0) {
                        pageInfo.put("normalEnd", true);
                    }

                    pages.add(pageInfo);
                    break;
                }

                pages.add(pageInfo);
                page++;

                // Délai entre pages
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            result.put("pages", pages);
            result.put("totalCardsFound", totalCards);
            result.put("pagesSuccessful", pages.stream().mapToInt(p ->
                    p instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) p).get("success")) ? 1 : 0).sum());

            // Analyse
            boolean paginationWorked = totalCards > 175;
            result.put("paginationSuccess", paginationWorked);
            result.put("recommendation", paginationWorked ?
                    "Pagination manuelle fonctionne - utiliser cette approche" :
                    "Pagination toujours limitée à 175 cartes");

            return ResponseEntity.ok(ApiResponse.success(result,
                    "Test pagination manuelle terminé - " + totalCards + " cartes trouvées"));

        } catch (Exception e) {
            logger.error("❌ Erreur test pagination manuelle : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Test pour récupérer TOUTES les variantes et cartes (objectif : 586 cartes)
     */
    @GetMapping("/test-all-variants/{setCode}")
    public ResponseEntity<ApiResponse<Object>> testAllVariants(@PathVariable String setCode) {
        try {
            logger.info("🔍 Test récupération de TOUTES les variantes pour {}", setCode);

            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> results = new HashMap<>();

            // Test différentes approches pour récupérer toutes les cartes
            Map<String, String> approaches = new HashMap<>();

            // Approche 1: Paramètres par défaut (cartes uniques)
            approaches.put("unique_cards",
                    "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() + "&unique=cards&format=json");

            // Approche 2: Inclure toutes les impressions
            approaches.put("all_prints",
                    "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() + "&unique=prints&format=json");

            // Approche 3: Inclure extras et variations
            approaches.put("include_all",
                    "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() +
                            "&include_extras=true&include_variations=true&include_multilingual=true&format=json");

            // Approche 4: Recherche plus large
            approaches.put("broad_search",
                    "https://api.scryfall.com/cards/search?q=set:" + setCode.toLowerCase() +
                            "&unique=art&include_extras=true&include_variations=true&format=json");

            // Approche 5: Recherche par nom du set
            approaches.put("by_set_name",
                    "https://api.scryfall.com/cards/search?q=set:\"Final Fantasy\"&format=json");

            for (Map.Entry<String, String> approach : approaches.entrySet()) {
                String approachName = approach.getKey();
                String baseUrl = approach.getValue();

                Map<String, Object> approachResult = new HashMap<>();
                approachResult.put("baseUrl", baseUrl);

                try {
                    // Tester juste la première page pour voir le total
                    String response = restTemplate.getForObject(baseUrl, String.class);

                    if (response != null) {
                        JsonNode root = mapper.readTree(response);

                        if (root.has("type") && "error".equals(root.get("type").asText())) {
                            approachResult.put("success", false);
                            approachResult.put("error", root.has("details") ? root.get("details").asText() : "Erreur");
                        } else {
                            approachResult.put("success", true);
                            approachResult.put("totalCards", root.has("total_cards") ? root.get("total_cards").asInt() : 0);
                            approachResult.put("firstPageCards", root.has("data") && root.get("data").isArray() ? root.get("data").size() : 0);
                            approachResult.put("hasMore", root.has("has_more") && root.get("has_more").asBoolean());

                            // Si on a trouvé plus de cartes, tester la pagination complète
                            int totalReported = root.has("total_cards") ? root.get("total_cards").asInt() : 0;
                            if (totalReported > 400) {
                                approachResult.put("recommend", "Cette approche trouve " + totalReported + " cartes - essayer la pagination complète");

                                // Test pagination rapide (2 pages max)
                                int totalFound = root.has("data") && root.get("data").isArray() ? root.get("data").size() : 0;
                                if (root.has("has_more") && root.get("has_more").asBoolean()) {
                                    try {
                                        String page2Url = baseUrl + "&page=2";
                                        String page2Response = restTemplate.getForObject(page2Url, String.class);
                                        if (page2Response != null) {
                                            JsonNode page2Root = mapper.readTree(page2Response);
                                            if (page2Root.has("data") && page2Root.get("data").isArray()) {
                                                totalFound += page2Root.get("data").size();
                                            }
                                        }
                                    } catch (Exception e) {
                                        // Ignorer erreur page 2
                                    }
                                }
                                approachResult.put("actualCardsFound", totalFound);
                            }

                            // Échantillon de noms pour vérifier
                            if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                                List<String> sampleNames = new ArrayList<>();
                                JsonNode dataArray = root.get("data");
                                for (int i = 0; i < Math.min(5, dataArray.size()); i++) {
                                    JsonNode card = dataArray.get(i);
                                    if (card.has("name")) {
                                        String cardName = card.get("name").asText();
                                        // Ajouter info sur les variantes
                                        if (card.has("collector_number")) {
                                            cardName += " (" + card.get("collector_number").asText() + ")";
                                        }
                                        sampleNames.add(cardName);
                                    }
                                }
                                approachResult.put("sampleCards", sampleNames);
                            }
                        }
                    } else {
                        approachResult.put("success", false);
                        approachResult.put("error", "Réponse nulle");
                    }

                } catch (Exception e) {
                    approachResult.put("success", false);
                    approachResult.put("error", e.getMessage());
                }

                results.put(approachName, approachResult);
            }

            // Analyser les résultats
            Map<String, Object> analysis = new HashMap<>();

            Integer maxCards = results.values().stream()
                    .filter(r -> r instanceof Map)
                    .map(r -> (Map<String, Object>) r)
                    .filter(r -> r.containsKey("totalCards"))
                    .mapToInt(r -> (Integer) r.get("totalCards"))
                    .max()
                    .orElse(0);

            analysis.put("maxCardsFound", maxCards);
            analysis.put("target", 586);
            analysis.put("closestToTarget", maxCards >= 580);

            String recommendation;
            if (maxCards >= 580) {
                recommendation = "Excellent! Une approche trouve " + maxCards + " cartes, très proche de l'objectif 586";
            } else if (maxCards >= 400) {
                recommendation = "Bon progrès: " + maxCards + " cartes trouvées, mais pas encore les 586 attendues";
            } else {
                recommendation = "Recherches limitées à " + maxCards + " cartes - explorer d'autres paramètres";
            }

            analysis.put("recommendation", recommendation);
            results.put("analysis", analysis);

            return ResponseEntity.ok(ApiResponse.success(results, "Test toutes variantes terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur test toutes variantes : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

}