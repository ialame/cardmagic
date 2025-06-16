// Modification dans ApplicationStartupService.java

package com.pcagrad.magic.service;

import com.pcagrad.magic.entity.MagicSerie;
import com.pcagrad.magic.entity.MagicSet;
import com.pcagrad.magic.entity.Serie;
import com.pcagrad.magic.entity.SerieTranslation;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.MagicSerieRepository;
import com.pcagrad.magic.repository.SetRepository;
import com.pcagrad.magic.repository.SerieRepository; // Ajoutez ce repository
import com.pcagrad.magic.util.Localization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

//@Service
@Order(1)
@ConditionalOnProperty(name = "app.startup.enabled", havingValue = "true", matchIfMissing = false)
public class ApplicationStartupService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartupService.class);

    //@Autowired
    private SetRepository setRepository;

    //@Autowired
    private CardRepository cardRepository;

    //@Autowired
    private SerieRepository serieRepository; // Ajoutez cette injection

    //@Autowired
    private EntityAdaptationService adaptationService; // Ajoutez cette injection

    //@Autowired
    private MagicSerieRepository magicSerieRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // *** AJOUT : Vérification si l'initialisation automatique est désactivée ***
        if (args.containsOption("skip-startup") ||
                "false".equals(System.getProperty("app.startup.enabled", "false"))) {
            logger.info("⏭️ Initialisation automatique désactivée");
            return;
        }

        logger.info("🚀 Initialisation de l'application MTG Cards...");

        // 1. Créer la série par défaut 2025
        Serie defaultSerie = getOrCreateSerie2025();

        // 2. Créer les extensions essentielles
        initializeEssentialSets(defaultSerie);

        // 3. Vérifier la cohérence des données
        checkDataConsistency();

        logger.info("✅ Initialisation terminée");
    }

    /**
     * Crée ou récupère la série par défaut "2025"
     */
    @Transactional
    protected Serie getOrCreateSerie2025() {
        logger.info("📁 Recherche/création de la série Magic 2025...");

        try {
            // Chercher une MagicSerie existante avec le nom "2025"
            Optional<MagicSerie> existingSerie = magicSerieRepository.findByName("2025");

            if (existingSerie.isPresent()) {
                logger.info("✅ Série Magic 2025 trouvée : {}", existingSerie.get().getId());
                return existingSerie.get();
            }
        } catch (Exception e) {
            logger.warn("⚠️ Erreur lors de la recherche de série existante : {}", e.getMessage());
        }

        // Créer une nouvelle MagicSerie
        logger.info("🔧 DEBUG: Création MagicSerie...");
        MagicSerie serie2025 = new MagicSerie();

        logger.info("🔧 DEBUG: Création traduction US...");
        SerieTranslation translationUS = new SerieTranslation();
        translationUS.setName("2025");
        translationUS.setLocalization(Localization.USA);
        translationUS.setActive(true);

        logger.info("🔧 DEBUG: Création traduction FR...");
        SerieTranslation translationFR = new SerieTranslation();
        translationFR.setName("Année 2025");
        translationFR.setLocalization(Localization.FRANCE);
        translationFR.setActive(true);

        logger.info("🔧 DEBUG: Attribution des traductions...");
        serie2025.setTranslation(Localization.USA, translationUS);
        serie2025.setTranslation(Localization.FRANCE, translationFR);

        logger.info("🔧 DEBUG: Sauvegarde en cours...");

        // Sauvegarder avec le repository approprié
        //MagicSerie savedSerie = magicSerieRepository.save(serie2025);

        try {
            // Sauvegarder avec le repository approprié
            MagicSerie savedSerie = magicSerieRepository.save(serie2025);
            logger.info("🎉 Série Magic 2025 créée avec succès : {}", savedSerie.getId());
            return savedSerie;
        } catch (Exception e) {
            logger.error("❌ Erreur lors de la création de la série Magic 2025 : {}", e.getMessage());
            return null;
        }
    }


    /**
     * Initialise les extensions essentielles avec la série par défaut
     */
    @Transactional
    protected void initializeEssentialSets(Serie defaultSerie) {

        if (defaultSerie == null) {
            logger.warn("⚠️ Aucune série par défaut disponible - Extensions non créées");
            return;
        }

        logger.info("📦 Initialisation des extensions essentielles avec série 2025...");

        // Extensions 2024-2025 prioritaires
        Map<String, SetData> essentialSets = Map.of(
                "FIN", new SetData(
                        "Magic: The Gathering - FINAL FANTASY",
                        "expansion",
                        LocalDate.of(2025, 6, 13),
                        true
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

            Optional<MagicSet> existing = setRepository.findByCode(code);
            if (existing.isEmpty()) {
                MagicSet set = new MagicSet();
                set.setCode(code);
                set.setName(data.name);
                set.setReleaseDate(data.releaseDate);
                set.setCardsCount(0);

                // *** SOLUTION : Assigner la série par défaut ***
                set.setSerie(defaultSerie);

                // Utiliser les services d'adaptation pour le type Magic
                adaptationService.setMagicSetType(set, data.type);
                adaptationService.prepareMagicSetForSave(set, data.type);

                setRepository.save(set);
                createdCount++;

                if (data.isPriority) {
                    logger.info("🌟 Extension prioritaire créée : {} - {} (Série: 2025)", code, data.name);
                } else {
                    logger.info("📦 Extension créée : {} - {} (Série: 2025)", code, data.name);
                }
            } else {
                // Mettre à jour la série si elle n'est pas définie
                MagicSet existingSet = existing.get();
                if (existingSet.getSerie() == null) {
                    existingSet.setSerie(defaultSerie);
                    setRepository.save(existingSet);
                    logger.info("📁 Série 2025 assignée à l'extension existante : {}", code);
                }
            }
        }

        if (createdCount > 0) {
            logger.info("✅ {} extensions créées avec la série 2025", createdCount);
        } else {
            logger.info("ℹ️ Toutes les extensions essentielles existent déjà");
        }
    }

    /**
     * Vérification basique de la cohérence des données
     */
    private void checkDataConsistency() {
        logger.info("🔍 Vérification de la cohérence des données...");

        long totalSets = setRepository.count();
        long totalCards = cardRepository.count();

        logger.info("📊 Statistiques : {} extensions, {} cartes", totalSets, totalCards);

        if (totalSets == 0) {
            logger.warn("⚠️ Aucune extension en base - c'est peut-être normal si c'est la première fois");
        }
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