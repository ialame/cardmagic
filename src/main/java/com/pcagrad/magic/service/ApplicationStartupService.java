// NOUVEAU SERVICE: ApplicationStartupService.java

package com.pcagrad.magic.service;

import com.pcagrad.magic.entity.SetEntity;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Service qui s'exécute au démarrage de l'application
 * pour initialiser les données nécessaires
 */
@Service
@Order(1) // S'exécute en premier
public class ApplicationStartupService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartupService.class);

    @Autowired
    private SetRepository setRepository;

    @Autowired
    private CardRepository cardRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("🚀 Initialisation de l'application MTG Cards...");

        // 1. Créer les extensions essentielles
        initializeEssentialSets();

        // 2. Vérifier la cohérence des données
        checkDataConsistency();

        logger.info("✅ Initialisation terminée");
    }

    /**
     * Initialise les extensions essentielles qui doivent toujours exister
     */
    private void initializeEssentialSets() {
        logger.info("📦 Initialisation des extensions essentielles...");

        // Extensions 2024-2025 prioritaires avec leurs vraies informations
        Map<String, SetData> essentialSets = Map.of(
                "FIN", new SetData(
                        "Magic: The Gathering - FINAL FANTASY",
                        "expansion",
                        LocalDate.of(2025, 6, 13),
                        true // Priorité maximale
                ),
                "BLB", new SetData(
                        "Bloomburrow",
                        "expansion",
                        LocalDate.of(2024, 8, 2),
                        false
                ),
                "MH3", new SetData(
                        "Modern Horizons 3",
                        "draft_innovation",
                        LocalDate.of(2024, 6, 14),
                        false
                ),
                "OTJ", new SetData(
                        "Outlaws of Thunder Junction",
                        "expansion",
                        LocalDate.of(2024, 4, 19),
                        false
                ),
                "MKM", new SetData(
                        "Murders at Karlov Manor",
                        "expansion",
                        LocalDate.of(2024, 2, 9),
                        false
                )
        );

        int createdCount = 0;
        for (Map.Entry<String, SetData> entry : essentialSets.entrySet()) {
            String code = entry.getKey();
            SetData data = entry.getValue();

            Optional<SetEntity> existing = setRepository.findByCode(code);
            if (existing.isEmpty()) {
                SetEntity set = new SetEntity();
                set.setCode(code);
                set.setName(data.name);
                set.setType(data.type);
                set.setReleaseDate(data.releaseDate);
                set.setCardsSynced(false);
                set.setCardsCount(0);

                setRepository.save(set);
                createdCount++;

                if (data.isPriority) {
                    logger.info("🌟 Extension prioritaire créée : {} - {}", code, data.name);
                } else {
                    logger.info("📦 Extension créée : {} - {}", code, data.name);
                }
            } else {
                // Mettre à jour les informations si nécessaire
                SetEntity existingSet = existing.get();
                boolean updated = false;

                if (!data.name.equals(existingSet.getName())) {
                    existingSet.setName(data.name);
                    updated = true;
                }

                if (existingSet.getReleaseDate() == null || !data.releaseDate.equals(existingSet.getReleaseDate())) {
                    existingSet.setReleaseDate(data.releaseDate);
                    updated = true;
                }

                if (updated) {
                    setRepository.save(existingSet);
                    logger.info("🔄 Extension mise à jour : {} - {}", code, data.name);
                }
            }
        }

        if (createdCount > 0) {
            logger.info("✅ {} extensions essentielles créées", createdCount);
        } else {
            logger.info("✅ Toutes les extensions essentielles existent déjà");
        }
    }

    /**
     * Vérifie la cohérence des données
     */
    private void checkDataConsistency() {
        logger.info("🔍 Vérification de la cohérence des données...");

        // Vérifier FIN spécifiquement
        Optional<SetEntity> finSet = setRepository.findByCode("FIN");
        if (finSet.isPresent()) {
            long cardCount = cardRepository.countBySetCode("FIN");
            SetEntity fin = finSet.get();

            logger.info("🎮 Final Fantasy : {} cartes en base", cardCount);

            // Mettre à jour le compteur de cartes
            if (fin.getCardsCount() == null || fin.getCardsCount() != cardCount) {
                fin.setCardsCount((int) cardCount);
                fin.setCardsSynced(cardCount > 0);
                setRepository.save(fin);
                logger.info("🔄 Compteur de cartes FIN mis à jour : {}", cardCount);
            }

            if (cardCount == 0) {
                logger.warn("⚠️ Final Fantasy n'a pas de cartes. Utilisez /admin/sync-final-fantasy pour les synchroniser");
            }
        }

        // Statistiques générales
        long totalSets = setRepository.count();
        long totalCards = cardRepository.count();
        long syncedSets = setRepository.countSyncedSets();

        logger.info("📊 Base de données : {} extensions, {} cartes, {} extensions synchronisées",
                totalSets, totalCards, syncedSets);
    }

    /**
     * Classe interne pour les données d'extension
     */
    private static class SetData {
        final String name;
        final String type;
        final LocalDate releaseDate;
        final boolean isPriority;

        SetData(String name, String type, LocalDate releaseDate, boolean isPriority) {
            this.name = name;
            this.type = type;
            this.releaseDate = releaseDate;
            this.isPriority = isPriority;
        }
    }
}