// AJOUTS NÉCESSAIRES en haut de ScryfallController.java

package com.pcagrad.magic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcagrad.magic.dto.ApiResponse;
import com.pcagrad.magic.entity.CardEntity;
import com.pcagrad.magic.entity.SetEntity;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.service.ScryfallService;
import com.pcagrad.magic.service.CardPersistenceService;
import com.pcagrad.magic.service.ImageDownloadService; // AJOUT
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

import java.net.URLEncoder; // AJOUT
import java.nio.charset.StandardCharsets; // AJOUT
import java.time.Duration;
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
    private CardPersistenceService cardPersistenceService; // CORRECTION du nom

    @Autowired
    private ImageDownloadService imageDownloadService; // AJOUT

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private SetRepository setRepository;

    // AJOUT de l'ObjectMapper
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ... reste des méthodes existantes ...

    /**
     * MÉTHODE CORRIGÉE - Synchronisation AVANCÉE Final Fantasy avec toutes les variantes
     */
    @PostMapping("/sync-final-fantasy-advanced")
    public ResponseEntity<ApiResponse<Object>> syncFinalFantasyAdvanced() {
        try {
            logger.info("🎮 Synchronisation AVANCÉE Final Fantasy avec toutes les variantes");

            // Supprimer les anciennes cartes FIN - CORRECTION: int au lieu de long
            int deletedCount = cardRepository.deleteBySetCodeIgnoreCase("FIN");
            logger.info("🗑️ {} anciennes cartes Final Fantasy supprimées", deletedCount);

            Map<String, Object> result = new HashMap<>();
            List<MtgCard> allFinCards = new ArrayList<>();

            // ÉTAPE 1: Requête principale avec toutes les variantes
            String[] finQueries = {
                    "set:fin",                    // Requête de base
                    "set:fin unique:prints",      // Avec toutes les impressions
                    "e:fin",                     // Notation alternative
                    "(set:fin OR e:fin)",        // Combinaison
                    "set:fin include:extras"     // Avec cartes bonus/extra
            };

            Map<String, Integer> queryResults = new HashMap<>();
            String bestQuery = "";
            int maxCards = 0;

            for (String query : finQueries) {
                try {
                    logger.info("🔍 Test requête FIN: {}", query);

                    List<MtgCard> queryCards = fetchCardsWithQuery(query);
                    queryResults.put(query, queryCards.size());

                    logger.info("📊 Requête '{}' : {} cartes trouvées", query, queryCards.size());

                    if (queryCards.size() > maxCards) {
                        maxCards = queryCards.size();
                        bestQuery = query;
                        allFinCards = new ArrayList<>(queryCards); // Copie de la meilleure liste
                    }

                    // Délai entre requêtes
                    Thread.sleep(300);

                } catch (Exception e) {
                    logger.error("❌ Erreur requête '{}': {}", query, e.getMessage());
                    queryResults.put(query, 0);
                }
            }

            result.put("queriesTestées", queryResults);
            result.put("meilleureRequête", bestQuery);
            result.put("cartesMaxTrouvées", maxCards);

            // ÉTAPE 2: Si on n'a toujours pas 586, essayer des requêtes spécialisées
            if (maxCards < 586) {
                logger.info("🔍 Recherche variantes supplémentaires FIN...");

                String[] extraQueries = {
                        "set:fin lang:en",           // Anglais seulement
                        "set:fin (rarity:c OR rarity:u OR rarity:r OR rarity:m OR rarity:s)", // Toutes raretés
                        "set:fin frame:2015",        // Frame moderne
                        "set:fin -is:digital",       // Papier seulement
                        "set:fin (is:booster OR is:commander)" // Sources spéciales
                };

                for (String extraQuery : extraQueries) {
                    try {
                        List<MtgCard> extraCards = fetchCardsWithQuery(extraQuery);
                        logger.info("📊 Requête extra '{}' : {} cartes", extraQuery, extraCards.size());

                        if (extraCards.size() > maxCards) {
                            maxCards = extraCards.size();
                            bestQuery = extraQuery;
                            allFinCards = extraCards;
                        }

                        Thread.sleep(300);
                    } catch (Exception e) {
                        logger.error("❌ Erreur requête extra '{}': {}", extraQuery, e.getMessage());
                    }
                }
            }

            // ÉTAPE 3: Sauvegarde des cartes
            if (!allFinCards.isEmpty()) {
                cardPersistenceService.saveCards(allFinCards, "FIN");

                result.put("cartesSauvegardées", allFinCards.size());
                result.put("objectifAtteint", allFinCards.size() >= 586);

                // Statistiques par rareté - CORRECTION
                Map<String, Long> rarityStats = allFinCards.stream()
                        .collect(Collectors.groupingBy(
                                card -> card.rarity() != null ? card.rarity() : "Unknown", // CORRECTION: utiliser rarity()
                                Collectors.counting()
                        ));
                result.put("répartitionRareté", rarityStats);

                logger.info("💾 {} cartes Final Fantasy sauvegardées", allFinCards.size());
                logger.info("🎯 Répartition: {}", rarityStats);

                // Démarrer téléchargement images en arrière-plan
                CompletableFuture.runAsync(() -> {
                    try {
                        imageDownloadService.downloadImagesForSet("FIN");
                    } catch (Exception e) {
                        logger.error("❌ Erreur téléchargement images FIN: {}", e.getMessage());
                    }
                });

                String message = String.format("Final Fantasy synchronisé: %d cartes (objectif: 586)",
                        allFinCards.size());
                return ResponseEntity.ok(ApiResponse.success(result, message));

            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Aucune carte Final Fantasy trouvée avec les requêtes avancées"));
            }

        } catch (Exception e) {
            logger.error("❌ Erreur synchronisation FIN avancée: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur sync FIN avancée: " + e.getMessage()));
        }
    }

    /**
     * MÉTHODE AJOUTÉE - Exécute une requête Scryfall spécifique et retourne les cartes
     */
    private List<MtgCard> fetchCardsWithQuery(String query) throws Exception {
        List<MtgCard> cards = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        int page = 1;
        boolean hasMore = true;

        while (hasMore && page <= 15) {
            String url = String.format(
                    "https://api.scryfall.com/cards/search?q=%s&format=json&order=name&page=%d",
                    URLEncoder.encode(query, StandardCharsets.UTF_8), page // CORRECTION
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
                        MtgCard card = parseScryfallCard(cardNode); // CORRECTION: utiliser la méthode du service
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
     * MÉTHODE AJOUTÉE - Parse une carte depuis JSON Scryfall (délégation au service)
     */
    private MtgCard parseScryfallCard(JsonNode cardNode) {
        return scryfallService.parseScryfallCard(cardNode);
    }
}