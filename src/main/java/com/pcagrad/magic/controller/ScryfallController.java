package com.pcagrad.magic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcagrad.magic.dto.ApiResponse;
import com.pcagrad.magic.entity.MagicSet;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.service.ScryfallService;
import com.pcagrad.magic.service.CardPersistenceService;
import com.pcagrad.magic.service.ImageDownloadService;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/scryfall")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:8080"})
public class ScryfallController {

    private static final Logger logger = LoggerFactory.getLogger(ScryfallController.class);

    @Autowired
    private ScryfallService scryfallService;

    @Autowired
    private CardPersistenceService cardPersistenceService;

    @Autowired
    private ImageDownloadService imageDownloadService;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private SetRepository setRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Synchronisation standard d'une extension via Scryfall
     */
    @PostMapping("/sync/{setCode}")
    public ResponseEntity<ApiResponse<Object>> syncSetFromScryfall(@PathVariable String setCode) {
        try {
            logger.info("🔮 Synchronisation Scryfall pour : {}", setCode);

            // Vérifier si l'extension existe sur Scryfall
            Mono<ScryfallService.SetInfo> setInfoMono = scryfallService.getSetInfo(setCode);
            ScryfallService.SetInfo setInfo = setInfoMono.block();

            if (setInfo == null || !setInfo.exists()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Extension " + setCode + " non trouvée sur Scryfall"));
            }

            logger.info("📊 Extension {} trouvée : {} - {} cartes attendues",
                    setCode, setInfo.name(), setInfo.expectedCardCount());

            // Supprimer les anciennes cartes si elles existent
            int deletedCount = cardRepository.deleteBySetCodeIgnoreCase(setCode);
            if (deletedCount > 0) {
                logger.info("🗑️ {} anciennes cartes supprimées pour {}", deletedCount, setCode);
            }

            // Récupérer et sauvegarder les cartes
            List<MtgCard> cards = scryfallService.fetchAllCardsFromSet(setCode);

            if (!cards.isEmpty()) {
                int savedCount = cardPersistenceService.saveCards(cards, setCode);

                // Mettre à jour l'extension
                updateSetEntity(setCode, setInfo.name(), cards.size());

                // Déclencher le téléchargement des images en arrière-plan
                CompletableFuture.runAsync(() -> {
                    try {
                        imageDownloadService.downloadImagesForSet(setCode);
                    } catch (Exception e) {
                        logger.error("❌ Erreur téléchargement images {} : {}", setCode, e.getMessage());
                    }
                });

                Map<String, Object> result = new HashMap<>();
                result.put("setCode", setCode);
                result.put("setName", setInfo.name());
                result.put("cardsFound", cards.size());
                result.put("cardsSaved", savedCount);
                result.put("expectedCards", setInfo.expectedCardCount());

                String message = String.format("Extension %s synchronisée : %d cartes trouvées",
                        setCode, cards.size());

                return ResponseEntity.ok(ApiResponse.success(result, message));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte trouvée pour l'extension " + setCode));
            }

        } catch (Exception e) {
            logger.error("❌ Erreur synchronisation Scryfall {} : {}", setCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur synchronisation : " + e.getMessage()));
        }
    }

    @PostMapping("/sync-final-fantasy-advanced")
    public ResponseEntity<ApiResponse<Object>> syncFinalFantasyAdvanced() {
        try {
            logger.info("🎮 Synchronisation AVANCÉE Final Fantasy avec pagination forcée");

            Map<String, Object> result = new HashMap<>();

            // CORRECTION: Supprimer COMPLÈTEMENT les anciennes cartes pour éviter les conflits UUID
            try {
                int deletedCount = cardRepository.deleteBySetCodeIgnoreCase("FIN");
                logger.info("🗑️ {} anciennes cartes Final Fantasy SUPPRIMÉES", deletedCount);
                result.put("deletedCards", deletedCount);

                // Forcer le flush pour s'assurer que la suppression est effective
                cardRepository.flush();

            } catch (Exception e) {
                logger.error("❌ Erreur suppression anciennes cartes : {}", e.getMessage());
            }

            // Attendre un peu que la suppression soit effective
            Thread.sleep(1000);

            // NOUVELLE APPROCHE : Utiliser directement la méthode corrigée du service
            List<MtgCard> allFinCards = scryfallService.fetchAllCardsFromSet("FIN");

            if (!allFinCards.isEmpty()) {
                int savedCount = cardPersistenceService.saveCards(allFinCards, "FIN");

                result.put("cartesSauvegardées", savedCount);
                result.put("cartesTotales", allFinCards.size());
                result.put("objectifAtteint", savedCount >= 300); // Objectif réaliste

                // Statistiques par rareté
                Map<String, Long> rarityStats = allFinCards.stream()
                        .collect(Collectors.groupingBy(
                                card -> card.rarity() != null ? card.rarity() : "Unknown",
                                Collectors.counting()
                        ));
                result.put("répartitionRareté", rarityStats);

                // Mettre à jour l'extension
                updateSetEntity("FIN", "Magic: The Gathering - FINAL FANTASY", savedCount);

                logger.info("💾 {} cartes Final Fantasy sauvegardées", savedCount);
                logger.info("🎯 Répartition: {}", rarityStats);

                // Démarrer téléchargement images en arrière-plan
                CompletableFuture.runAsync(() -> {
                    try {
                        imageDownloadService.downloadImagesForSet("FIN");
                    } catch (Exception e) {
                        logger.error("❌ Erreur téléchargement images FIN: {}", e.getMessage());
                    }
                });

                String message = String.format("Final Fantasy synchronisé: %d cartes récupérées avec pagination forcée",
                        savedCount);
                return ResponseEntity.ok(ApiResponse.success(result, message));

            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte Final Fantasy trouvée même avec pagination forcée"));
            }

        } catch (Exception e) {
            logger.error("❌ Erreur synchronisation FIN avancée: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur sync FIN avancée: " + e.getMessage()));
        }
    }

    /**
     * ENDPOINT MANQUANT - Diagnostic Final Fantasy complet
     */
    @GetMapping("/diagnostic-fin-complete")
    public ResponseEntity<ApiResponse<Object>> diagnosticFinalFantasyComplet() {
        try {
            logger.info("🔬 Diagnostic Final Fantasy COMPLET");

            Map<String, Object> data = new HashMap<>();

            // Compter les cartes actuelles en base
            long cartesEnBase = cardRepository.countBySetCodeIgnoreCase("FIN");
            data.put("cartesEnBase", cartesEnBase);
            data.put("objectif", "312 cartes (objectif réaliste pour FIN)");
            data.put("problemeActuel", cartesEnBase < 312 ? "Extension incomplète" : "Extension complète");

            // Tester différentes requêtes pour voir le maximum possible
            Map<String, Integer> testRequetes = new HashMap<>();
            String[] queries = {
                    "set:fin",
                    "e:fin",
                    "set:fin unique:prints",
                    "set:fin include:extras"
            };

            int maxTrouve = 0;
            String meilleureRequete = "";

            for (String query : queries) {
                try {
                    int count = countCardsWithQuery(query);
                    testRequetes.put(query, count);
                    if (count > maxTrouve) {
                        maxTrouve = count;
                        meilleureRequete = query;
                    }
                    Thread.sleep(200);
                } catch (Exception e) {
                    testRequetes.put(query, 0);
                }
            }

            data.put("testRequetes", testRequetes);
            data.put("maxCardsFound", maxTrouve);
            data.put("bestQuery", meilleureRequete);

            // Analyse
            Map<String, Object> analysis = new HashMap<>();
            if (cartesEnBase >= 312) {
                analysis.put("statut", "✅ COMPLET");
                analysis.put("explication", "Extension Final Fantasy complète avec un nombre réaliste de cartes");
            } else if (cartesEnBase >= 250) {
                analysis.put("statut", "📊 QUASI-COMPLET");
                analysis.put("explication", "Extension presque complète, probablement toutes les cartes principales");
            } else {
                analysis.put("statut", "⚠️ INCOMPLET");
                analysis.put("explication", "Extension incomplète, synchronisation recommandée");
            }

            data.put("analysis", analysis);

            return ResponseEntity.ok(ApiResponse.success(data, "Diagnostic Final Fantasy terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur diagnostic FIN: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur diagnostic: " + e.getMessage()));
        }
    }

    /**
     * ENDPOINT MANQUANT - Debug pagination
     */
    @GetMapping("/debug-pagination/{setCode}")
    public ResponseEntity<ApiResponse<Object>> debugPagination(@PathVariable String setCode) {
        try {
            logger.info("🔍 Debug pagination pour : {}", setCode);

            Map<String, Object> debugInfo = new HashMap<>();
            List<Map<String, Object>> pagesInfo = new ArrayList<>();

            int page = 1;
            int totalCards = 0;
            boolean hasMore = true;

            while (hasMore && page <= 5) { // Limité à 5 pages pour le debug
                String url = String.format(
                        "https://api.scryfall.com/cards/search?q=set:%s&format=json&order=name&page=%d",
                        setCode.toLowerCase(), page
                );

                try {
                    String response = restTemplate.getForObject(url, String.class);
                    if (response == null) break;

                    JsonNode root = objectMapper.readTree(response);
                    JsonNode dataNode = root.get("data");

                    Map<String, Object> pageInfo = new HashMap<>();
                    pageInfo.put("page", page);
                    pageInfo.put("url", url);

                    if (dataNode != null && dataNode.isArray()) {
                        int cardsInPage = dataNode.size();
                        totalCards += cardsInPage;

                        pageInfo.put("cardsInPage", cardsInPage);
                        pageInfo.put("totalSoFar", totalCards);

                        hasMore = root.has("has_more") && root.get("has_more").asBoolean();
                        pageInfo.put("hasMore", hasMore);
                    } else {
                        pageInfo.put("error", "Pas de données");
                        hasMore = false;
                    }

                    pagesInfo.add(pageInfo);
                    page++;

                    if (hasMore) {
                        Thread.sleep(200);
                    }

                } catch (Exception e) {
                    Map<String, Object> errorInfo = new HashMap<>();
                    errorInfo.put("page", page);
                    errorInfo.put("error", e.getMessage());
                    pagesInfo.add(errorInfo);
                    break;
                }
            }

            debugInfo.put("setCode", setCode);
            debugInfo.put("totalPages", page - 1);
            debugInfo.put("totalCards", totalCards);
            debugInfo.put("pagesDetails", pagesInfo);

            return ResponseEntity.ok(ApiResponse.success(debugInfo, "Debug pagination terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur debug pagination: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur debug: " + e.getMessage()));
        }
    }

    /**
     * ENDPOINT MANQUANT - Debug 312 cartes (objectif réaliste)
     */
    @GetMapping("/debug-312-cards")
    public ResponseEntity<ApiResponse<Object>> debug312Cards() {
        try {
            logger.info("🔬 Debug objectif 312 cartes FIN");

            Map<String, Object> data = new HashMap<>();

            long cartesEnBase = cardRepository.countBySetCodeIgnoreCase("FIN");
            data.put("cartesEnBase", cartesEnBase);
            data.put("objectifRealiste", 312);

            Map<String, Object> conclusion = new HashMap<>();
            if (cartesEnBase >= 312) {
                conclusion.put("statut", "✅ OBJECTIF ATTEINT");
                conclusion.put("explication", "312+ cartes FIN récupérées - Set complet");
            } else {
                conclusion.put("statut", "⚠️ OBJECTIF NON ATTEINT");
                conclusion.put("explication", String.format("Seulement %d cartes sur 312 - Synchronisation recommandée", cartesEnBase));
            }

            data.put("conclusion", conclusion);

            return ResponseEntity.ok(ApiResponse.success(data, "Debug 312 cartes terminé"));

        } catch (Exception e) {
            logger.error("❌ Erreur debug 312: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur debug: " + e.getMessage()));
        }
    }

    // ========== MÉTHODES UTILITAIRES ==========

    /**
     * Exécute une requête Scryfall et retourne les cartes
     */
    private List<MtgCard> fetchCardsWithQuery(String query) throws Exception {
        List<MtgCard> cards = new ArrayList<>();
        int page = 1;
        boolean hasMore = true;

        while (hasMore && page <= 15) {
            String url = String.format(
                    "https://api.scryfall.com/cards/search?q=%s&format=json&order=name&page=%d",
                    URLEncoder.encode(query, StandardCharsets.UTF_8), page
            );

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) break;

            JsonNode root = objectMapper.readTree(response);

            if (root.has("type") && "error".equals(root.get("type").asText())) {
                if (cards.isEmpty()) {
                    throw new Exception("Requête invalide: " + query);
                } else {
                    break; // Fin normale
                }
            }

            JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode cardNode : dataNode) {
                    try {
                        MtgCard card = scryfallService.parseScryfallCard(cardNode);
                        cards.add(card);
                    } catch (Exception e) {
                        logger.warn("⚠️ Erreur parsing carte: {}", e.getMessage());
                    }
                }
            }

            hasMore = root.has("has_more") && root.get("has_more").asBoolean();
            page++;

            if (hasMore) {
                Thread.sleep(150);
            }
        }

        return cards;
    }

    /**
     * Compte les cartes pour une requête sans les récupérer
     */
    private int countCardsWithQuery(String query) throws Exception {
        String url = String.format(
                "https://api.scryfall.com/cards/search?q=%s&format=json&page=1",
                URLEncoder.encode(query, StandardCharsets.UTF_8)
        );

        String response = restTemplate.getForObject(url, String.class);
        if (response == null) return 0;

        JsonNode root = objectMapper.readTree(response);

        if (root.has("total_cards")) {
            return root.get("total_cards").asInt();
        }

        return 0;
    }

    /**
     * Met à jour ou crée l'entité extension
     */
    private void updateSetEntity(String setCode, String setName, int cardsCount) {
        try {
            Optional<MagicSet> setOpt = setRepository.findByCode(setCode);
            MagicSet setEntity;

            if (setOpt.isPresent()) {
                setEntity = setOpt.get();
            } else {
                setEntity = new MagicSet();
                setEntity.setCode(setCode);
                setEntity.setType("expansion");
            }

            setEntity.setName(setName);
            setEntity.setCardsCount(cardsCount);
            setEntity.setCardsSynced(true);
            setEntity.setLastSyncAt(LocalDateTime.now());

            setRepository.save(setEntity);
            logger.info("✅ Extension {} mise à jour : {} cartes", setCode, cardsCount);

        } catch (Exception e) {
            logger.error("❌ Erreur mise à jour extension {} : {}", setCode, e.getMessage());
        }
    }

    @GetMapping("/test-fin-queries")
    public ResponseEntity<ApiResponse<Object>> testFinQueries() {
        Map<String, Object> results = new HashMap<>();

        String[] testQueries = {
                "final fantasy",
                "\"final fantasy\"",
                "game:paper final fantasy",
                "chocobo OR moogle OR cloud OR sephiroth",
                "\"Square Enix\""
        };

        for (String query : testQueries) {
            try {
                int count = countCardsWithQuery(query);
                results.put(query, count);
            } catch (Exception e) {
                results.put(query, "ERROR: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(ApiResponse.success(results, "Test des requêtes FIN"));
    }


    /**
     * ENDPOINT CORRIGÉ: Synchronisation Final Fantasy avec méthode fixée
     */
    @PostMapping("/sync-final-fantasy-fixed")
    public ResponseEntity<ApiResponse<Object>> syncFinalFantasyFixed() {
        try {
            logger.info("🎮 Synchronisation Final Fantasy CORRIGÉE - Objectif 312 cartes");

            Map<String, Object> result = new HashMap<>();

            // Supprimer les anciennes cartes (les 6 incorrectes)
            int deletedCount = cardRepository.deleteBySetCodeIgnoreCase("FIN");
            logger.info("🗑️ {} anciennes cartes supprimées", deletedCount);
            result.put("deletedCards", deletedCount);

            cardRepository.flush();
            Thread.sleep(1000);

            // Utiliser la méthode corrigée
            List<MtgCard> finCards = scryfallService.fetchAllCardsFromSetFixed("FIN");

            if (!finCards.isEmpty()) {
                int savedCount = cardPersistenceService.saveCards(finCards, "FIN");

                result.put("cartesTotales", finCards.size());
                result.put("cartesSauvegardées", savedCount);
                result.put("objectif312Atteint", savedCount >= 312);

                // Statistiques par rareté
                Map<String, Long> rarityStats = finCards.stream()
                        .collect(Collectors.groupingBy(
                                card -> card.rarity() != null ? card.rarity() : "Unknown",
                                Collectors.counting()
                        ));
                result.put("répartitionRareté", rarityStats);

                // Mettre à jour l'extension
                updateSetEntity("FIN", "Magic: The Gathering - FINAL FANTASY", savedCount);

                logger.info("🎉 SUCCESS: {} cartes Final Fantasy récupérées et sauvegardées", savedCount);

                // Démarrer téléchargement images
                CompletableFuture.runAsync(() -> {
                    try {
                        imageDownloadService.downloadImagesForSet("FIN");
                    } catch (Exception e) {
                        logger.error("❌ Erreur téléchargement images: {}", e.getMessage());
                    }
                });

                String message = String.format("Final Fantasy synchronisé avec succès: %d cartes récupérées", savedCount);
                return ResponseEntity.ok(ApiResponse.success(result, message));

            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte Final Fantasy trouvée avec les requêtes corrigées"));
            }

        } catch (Exception e) {
            logger.error("❌ Erreur sync FIN corrigée: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur sync corrigée: " + e.getMessage()));
        }
    }

    /**
     * ENDPOINT DE DEBUG: Tester toutes les requêtes possibles
     */
    @GetMapping("/debug-fin-queries-all")
    public ResponseEntity<ApiResponse<Object>> debugFinQueriesAll() {
        try {
            Map<String, Object> results = new HashMap<>();

            String[] testQueries = {
                    "set:fin",
                    "e:fin",
                    "\"final fantasy\"",
                    "set=\"Magic: The Gathering—FINAL FANTASY\"",
                    "(set:fin OR e:fin)",
                    "game:paper set:fin",
                    "is:booster set:fin",
                    "legal:legacy set:fin"
            };

            for (String query : testQueries) {
                try {
                    // Test simple count d'abord
                    int count = countCardsWithQuery(query);
                    results.put(query + "_count", count);

                    if (count > 0) {
                        // Si on trouve des cartes, tester une page
                        String url = String.format(
                                "https://api.scryfall.com/cards/search?q=%s&format=json&page=1",
                                URLEncoder.encode(query, StandardCharsets.UTF_8)
                        );

                        String response = restTemplate.getForObject(url, String.class);
                        if (response != null) {
                            JsonNode root = objectMapper.readTree(response);
                            if (root.has("total_cards")) {
                                results.put(query + "_total", root.get("total_cards").asInt());
                            }
                        }
                    }

                    Thread.sleep(200);

                } catch (Exception e) {
                    results.put(query + "_error", e.getMessage());
                }
            }

            return ResponseEntity.ok(ApiResponse.success(results, "Debug toutes les requêtes FIN"));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur debug: " + e.getMessage()));
        }
    }
}