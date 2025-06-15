package com.pcagrad.magic.service;

import com.pcagrad.magic.entity.MagicSet;
import com.pcagrad.magic.entity.MagicType;
import com.pcagrad.magic.entity.CardSetTranslation;
import com.pcagrad.magic.repository.MagicTypeRepository;
import com.pcagrad.magic.repository.SetRepository;
import com.pcagrad.magic.util.Localization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service de migration et d'initialisation des données pour la nouvelle structure
 */
@Service
@Order(2) // Après ApplicationStartupService
public class DataMigrationService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataMigrationService.class);

    @Autowired
    private MagicTypeRepository magicTypeRepository;

    @Autowired
    private SetRepository setRepository;

    @Autowired
    private EntityAdaptationService adaptationService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("🔄 Début de la migration des données vers la nouvelle structure");

        try {
            // 1. Initialiser les types Magic
            initializeMagicTypes();

            // 2. Migrer les extensions existantes
            migrateExistingSets();

            // 3. Valider la migration
            validateMigration();

            logger.info("✅ Migration des données terminée avec succès");

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la migration : {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Initialise les types Magic standards
     */
    @Transactional
    public void initializeMagicTypes() {
        logger.info("🎯 Initialisation des types Magic");

        Map<String, TypeData> standardTypes = getStandardMagicTypes();
        int createdCount = 0;

        for (Map.Entry<String, TypeData> entry : standardTypes.entrySet()) {
            String typeCode = entry.getKey();
            TypeData typeData = entry.getValue();

            Optional<MagicType> existing = magicTypeRepository.findByType(typeCode);
            if (existing.isEmpty()) {
                MagicType magicType = new MagicType();
                magicType.setType(typeCode);
                magicType.setTypePcafr(typeData.frenchName);
                magicType.setTypePcaus(typeData.englishName);
                magicType.setSousTypePcafr("");
                magicType.setSousTypePcaus("");

                magicTypeRepository.save(magicType);
                createdCount++;
                logger.debug("✨ Type Magic créé : {} ({})", typeCode, typeData.frenchName);
            }
        }

        logger.info("✅ {} types Magic initialisés", createdCount);
    }

    /**
     * Migre les extensions existantes vers la nouvelle structure
     */
    @Transactional
    public void migrateExistingSets() {
        logger.info("📦 Migration des extensions existantes");

        List<MagicSet> allSets = setRepository.findAll();
        int migratedCount = 0;
        int errorCount = 0;

        for (MagicSet set : allSets) {
            try {
                if (migrateSet(set)) {
                    migratedCount++;
                }
            } catch (Exception e) {
                errorCount++;
                logger.error("❌ Erreur migration extension {} : {}", set.getCode(), e.getMessage());
            }
        }

        logger.info("✅ Migration terminée : {} extensions migrées, {} erreurs", migratedCount, errorCount);
    }

    /**
     * Migre une extension individuelle
     */
    private boolean migrateSet(MagicSet set) {
        boolean migrated = false;

        // 1. S'assurer qu'elle a un type Magic valide
        if (set.getTypeMagic() == null) {
            String typeToAssign = determineSetType(set);
            adaptationService.setMagicSetType(set, typeToAssign);
            migrated = true;
            logger.debug("🔧 Type assigné à {} : {}", set.getCode(), typeToAssign);
        }

        // 2. S'assurer qu'elle a au moins une translation US
        if (set.getTranslations().isEmpty() || set.getTranslation(Localization.USA) == null) {
            createDefaultTranslation(set);
            migrated = true;
            logger.debug("🌐 Translation créée pour {}", set.getCode());
        }

        // 3. Valider et corriger les données
        if (!adaptationService.validateMagicSet(set)) {
            adaptationService.prepareMagicSetForSave(set, set.getType());
            migrated = true;
            logger.debug("🛠️ Données corrigées pour {}", set.getCode());
        }

        if (migrated) {
            setRepository.save(set);
        }

        return migrated;
    }

    /**
     * Détermine le type approprié pour une extension
     */
    private String determineSetType(MagicSet set) {
        String code = set.getCode();
        String name = set.getName();

        // Types spéciaux basés sur le code
        if ("FIN".equals(code) || (name != null && name.contains("FINAL FANTASY"))) {
            return "expansion";
        }

        // Types basés sur des patterns de noms
        if (name != null) {
            String lowerName = name.toLowerCase();

            if (lowerName.contains("core") || lowerName.contains("edition")) {
                return "core";
            }
            if (lowerName.contains("commander")) {
                return "commander";
            }
            if (lowerName.contains("horizons") || lowerName.contains("innovation")) {
                return "draft_innovation";
            }
            if (lowerName.contains("masters")) {
                return "masters";
            }
            if (lowerName.contains("reprint") || lowerName.contains("remastered")) {
                return "reprint";
            }
            if (lowerName.contains("promo")) {
                return "promo";
            }
            if (lowerName.contains("token")) {
                return "token";
            }
        }

        // Par défaut
        return "expansion";
    }

    /**
     * Crée une translation par défaut pour une extension
     */
    private void createDefaultTranslation(MagicSet set) {
        CardSetTranslation translation = new CardSetTranslation();
        translation.setLocalization(Localization.USA);
        translation.setName(set.getCode() + " Extension"); // Nom par défaut
        translation.setLabelName(translation.getName());
        translation.setAvailable(true);
        translation.setReleaseDate(LocalDateTime.now());

        set.setTranslation(Localization.USA, translation);
    }

    /**
     * Valide la migration
     */
    public void validateMigration() {
        logger.info("🔍 Validation de la migration");

        // Vérifier les types Magic
        long typesCount = magicTypeRepository.count();
        logger.info("📊 {} types Magic en base", typesCount);

        // Vérifier les extensions
        long setsCount = setRepository.count();
        long setsWithoutType = setRepository.findAll().stream()
                .mapToLong(set -> set.getTypeMagic() == null ? 1 : 0)
                .sum();

        long setsWithoutTranslation = setRepository.findAll().stream()
                .mapToLong(set -> set.getTranslations().isEmpty() ? 1 : 0)
                .sum();

        logger.info("📊 {} extensions en base", setsCount);
        logger.info("📊 {} extensions sans type", setsWithoutType);
        logger.info("📊 {} extensions sans translation", setsWithoutTranslation);

        // Valider Final Fantasy spécifiquement
        validateFinalFantasy();

        if (setsWithoutType == 0 && setsWithoutTranslation == 0) {
            logger.info("✅ Migration validée avec succès");
        } else {
            logger.warn("⚠️ Migration incomplète - corrections nécessaires");
        }
    }

    /**
     * Validation spécifique de Final Fantasy
     */
    private void validateFinalFantasy() {
        Optional<MagicSet> finSet = setRepository.findByCode("FIN");
        if (finSet.isPresent()) {
            MagicSet fin = finSet.get();
            boolean isValid = fin.getTypeMagic() != null &&
                    !fin.getTranslations().isEmpty();

            if (isValid) {
                logger.info("🎮 Final Fantasy validé ✅");
            } else {
                logger.warn("🎮 Final Fantasy nécessite des corrections ⚠️");
            }
        } else {
            logger.info("🎮 Final Fantasy non présent en base");
        }
    }

    /**
     * Obtient la liste des types Magic standards
     */
    private Map<String, TypeData> getStandardMagicTypes() {
        Map<String, TypeData> types = new HashMap<>();

        types.put("expansion", new TypeData("Extension", "Expansion"));
        types.put("core", new TypeData("Edition de base", "Core Set"));
        types.put("commander", new TypeData("Commander", "Commander"));
        types.put("draft_innovation", new TypeData("Innovation Draft", "Draft Innovation"));
        types.put("reprint", new TypeData("Réimpression", "Reprint"));
        types.put("masters", new TypeData("Masters", "Masters"));
        types.put("duel_deck", new TypeData("Deck Duel", "Duel Deck"));
        types.put("premium_deck", new TypeData("Deck Premium", "Premium Deck"));
        types.put("from_the_vault", new TypeData("From the Vault", "From the Vault"));
        types.put("spellbook", new TypeData("Grimoire", "Spellbook"));
        types.put("conspiracy", new TypeData("Conspiracy", "Conspiracy"));
        types.put("planechase", new TypeData("Planechase", "Planechase"));
        types.put("archenemy", new TypeData("Archenemy", "Archenemy"));
        types.put("vanguard", new TypeData("Vanguard", "Vanguard"));
        types.put("funny", new TypeData("Humoristique", "Un-Set"));
        types.put("promo", new TypeData("Promotionnel", "Promo"));
        types.put("token", new TypeData("Jeton", "Token"));
        types.put("memorabilia", new TypeData("Collector", "Memorabilia"));
        types.put("box", new TypeData("Coffret", "Box Set"));
        types.put("starter", new TypeData("Starter", "Starter"));
        types.put("arsenal", new TypeData("Arsenal", "Arsenal"));
        types.put("treasure_chest", new TypeData("Coffre au Trésor", "Treasure Chest"));
        types.put("masterpiece", new TypeData("Chef-d'œuvre", "Masterpiece"));

        return types;
    }

    /**
     * Classe interne pour les données de type
     */
    private static class TypeData {
        final String frenchName;
        final String englishName;

        TypeData(String frenchName, String englishName) {
            this.frenchName = frenchName;
            this.englishName = englishName;
        }
    }

    // ========== MÉTHODES PUBLIQUES POUR L'ADMINISTRATION ==========

    /**
     * Force la re-migration d'une extension spécifique
     */
    @Transactional
    public boolean forceMigrationForSet(String setCode) {
        logger.info("🔄 Migration forcée pour l'extension : {}", setCode);

        Optional<MagicSet> setOpt = setRepository.findByCode(setCode);
        if (setOpt.isEmpty()) {
            logger.warn("⚠️ Extension {} non trouvée", setCode);
            return false;
        }

        try {
            boolean migrated = migrateSet(setOpt.get());
            logger.info("✅ Migration forcée terminée pour {} : {}", setCode, migrated ? "succès" : "aucun changement");
            return migrated;
        } catch (Exception e) {
            logger.error("❌ Erreur migration forcée pour {} : {}", setCode, e.getMessage());
            return false;
        }
    }

    /**
     * Crée une extension Final Fantasy si elle n'existe pas
     */
    @Transactional
    public void ensureFinalFantasyExists() {
        Optional<MagicSet> finSet = setRepository.findByCode("FIN");
        if (finSet.isEmpty()) {
            logger.info("🎮 Création de l'extension Final Fantasy");

            MagicSet finalFantasy = new MagicSet();
            finalFantasy.setCode("FIN");

            // Utiliser l'adaptation pour créer correctement l'extension
            adaptationService.setMagicSetType(finalFantasy, "expansion");
            adaptationService.prepareMagicSetForSave(finalFantasy, "expansion");

            // Créer la translation
            CardSetTranslation translation = new CardSetTranslation();
            translation.setLocalization(Localization.USA);
            translation.setName("Magic: The Gathering - FINAL FANTASY");
            translation.setLabelName("Magic: The Gathering - FINAL FANTASY");
            translation.setAvailable(true);
            translation.setReleaseDate(LocalDate.of(2025, 6, 13).atStartOfDay());

            finalFantasy.setTranslation(Localization.USA, translation);

            setRepository.save(finalFantasy);
            logger.info("✅ Extension Final Fantasy créée avec migration");
        }
    }

    /**
     * Obtient un rapport de migration
     */
    public MigrationReport getMigrationReport() {
        long totalSets = setRepository.count();
        long totalTypes = magicTypeRepository.count();

        long setsWithoutType = setRepository.findAll().stream()
                .mapToLong(set -> set.getTypeMagic() == null ? 1 : 0)
                .sum();

        long setsWithoutTranslation = setRepository.findAll().stream()
                .mapToLong(set -> set.getTranslations().isEmpty() ? 1 : 0)
                .sum();

        boolean finExists = setRepository.findByCode("FIN").isPresent();

        return new MigrationReport(
                totalSets, totalTypes, setsWithoutType,
                setsWithoutTranslation, finExists
        );
    }

    /**
     * Rapport de migration
     */
    public static class MigrationReport {
        private final long totalSets;
        private final long totalTypes;
        private final long setsWithoutType;
        private final long setsWithoutTranslation;
        private final boolean finalFantasyExists;

        public MigrationReport(long totalSets, long totalTypes, long setsWithoutType,
                               long setsWithoutTranslation, boolean finalFantasyExists) {
            this.totalSets = totalSets;
            this.totalTypes = totalTypes;
            this.setsWithoutType = setsWithoutType;
            this.setsWithoutTranslation = setsWithoutTranslation;
            this.finalFantasyExists = finalFantasyExists;
        }

        // Getters
        public long getTotalSets() { return totalSets; }
        public long getTotalTypes() { return totalTypes; }
        public long getSetsWithoutType() { return setsWithoutType; }
        public long getSetsWithoutTranslation() { return setsWithoutTranslation; }
        public boolean isFinalFantasyExists() { return finalFantasyExists; }
        public boolean isMigrationComplete() {
            return setsWithoutType == 0 && setsWithoutTranslation == 0;
        }
        public double getMigrationProgress() {
            if (totalSets == 0) return 100.0;
            long problematicSets = setsWithoutType + setsWithoutTranslation;
            return ((double)(totalSets - problematicSets) / totalSets) * 100.0;
        }
    }
}