package com.pcagrad.magic.service;

import com.pcagrad.magic.entity.MagicCard;
import com.pcagrad.magic.entity.MagicSet;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.model.MtgSet;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
import com.pcagrad.magic.util.Localization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
public class CardPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(CardPersistenceService.class);

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private SetRepository setRepository;

    @Autowired
    private ImageDownloadService imageDownloadService;

    @Autowired
    private EntityAdaptationService adaptationService;

    /**
     * Sauvegarde ou met à jour une extension en base - VERSION ADAPTÉE
     */
    public MagicSet saveOrUpdateSet(MtgSet mtgSet) {
        logger.debug("💾 Sauvegarde de l'extension adaptée : {} ({})", mtgSet.name(), mtgSet.code());

        Optional<MagicSet> existingSet = setRepository.findByCode(mtgSet.code());
        MagicSet setEntity;

        if (existingSet.isPresent()) {
            setEntity = existingSet.get();
            updateSetEntityAdapted(setEntity, mtgSet);
            logger.debug("🔄 Mise à jour de l'extension existante : {}", mtgSet.code());
        } else {
            setEntity = createSetEntityAdapted(mtgSet);
            logger.info("✨ Nouvelle extension créée : {} - {}", mtgSet.code(), mtgSet.name());
        }

        return setRepository.save(setEntity);
    }

    /**
     * Sauvegarde les cartes d'une extension en base - VERSION ADAPTÉE
     */
    @Async
    public CompletableFuture<Integer> saveCardsForSet(String setCode, List<MtgCard> cards) {
        logger.info("💾 Début de la sauvegarde adaptée de {} cartes pour l'extension {}", cards.size(), setCode);

        return CompletableFuture.supplyAsync(() -> {
            // S'assurer que l'extension existe
            ensureSetExistsAdapted(setCode, cards);

            int savedCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;

            for (MtgCard mtgCard : cards) {
                try {
                    MagicCard result = saveOrUpdateCardAdapted(mtgCard, setCode);
                    if (result != null) {
                        // Les dates created/updated ne sont plus disponibles, on compte tout comme sauvé
                        savedCount++;

                        // Déclencher le téléchargement de l'image en arrière-plan
                        if (result.getOriginalImageUrl() != null && !result.getOriginalImageUrl().isEmpty()) {
                            imageDownloadService.downloadCardImage(result);
                        }
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    logger.error("❌ Erreur lors de la sauvegarde de la carte {} : {}",
                            mtgCard.name(), e.getMessage());
                    skippedCount++;
                }
            }

            // Mettre à jour les statistiques de l'extension
            updateSetStatisticsAdapted(setCode);

            logger.info("✅ Sauvegarde terminée pour {} : {} sauvées, {} ignorées",
                    setCode, savedCount, skippedCount);

            return savedCount;
        });
    }

    /**
     * S'assurer que l'extension existe - VERSION ADAPTÉE
     */
    private void ensureSetExistsAdapted(String setCode, List<MtgCard> cards) {
        Optional<MagicSet> existingSet = setRepository.findByCode(setCode);

        if (existingSet.isEmpty()) {
            logger.info("🔧 Extension {} non trouvée en base, création automatique adaptée", setCode);

            MagicSet newSet = new MagicSet();
            newSet.setCode(setCode);

            // Déduire le nom depuis les cartes
            String setName = cards.stream()
                    .map(MtgCard::setName)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(setCode + " (Auto-generated)");

            newSet.setName(setName);

            // Utiliser le service d'adaptation pour définir le type
            adaptationService.setMagicSetType(newSet, "expansion");
            adaptationService.prepareMagicSetForSave(newSet, "expansion");

            // Dates connues pour certaines extensions
            setKnownReleaseDateAdapted(newSet, setCode);

            setRepository.save(newSet);
            logger.info("✅ Extension {} créée automatiquement avec adaptation", setCode);
        }
    }

    /**
     * Sauvegarde ou met à jour une carte - VERSION ADAPTÉE
     */
    public MagicCard saveOrUpdateCardAdapted(MtgCard mtgCard, String setCode) {
        if (mtgCard.id() == null || mtgCard.id().isEmpty()) {
            logger.warn("⚠️ Carte sans ID externe ignorée : {}", mtgCard.name());
            return null;
        }

        // Chercher par idPrim (externalId) ET setCode
        Optional<MagicCard> existingCard = cardRepository.findByExternalIdAndSetCode(mtgCard.id(), setCode);
        MagicCard cardEntity;

        if (existingCard.isPresent()) {
            cardEntity = existingCard.get();
            updateCardEntityAdapted(cardEntity, mtgCard);
            logger.debug("🔄 Mise à jour carte existante adaptée : {}", mtgCard.name());
        } else {
            // Vérifier s'il y a une carte avec le même nom
            List<MagicCard> sameName = cardRepository.findByNameAndSetCode(mtgCard.name(), setCode);
            if (!sameName.isEmpty()) {
                cardEntity = sameName.get(0);
                cardEntity.setExternalId(mtgCard.id());
                updateCardEntityAdapted(cardEntity, mtgCard);
                logger.debug("🔄 Carte existante trouvée par nom (adaptation) : {}", mtgCard.name());
            } else {
                cardEntity = createCardEntityAdapted(mtgCard, setCode);
                logger.debug("✨ Nouvelle carte créée avec adaptation : {}", mtgCard.name());
            }
        }

        try {
            return cardRepository.save(cardEntity);
        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde carte adaptée {} : {}", mtgCard.name(), e.getMessage());
            return null;
        }
    }

    /**
     * Crée une nouvelle entité extension - VERSION ADAPTÉE
     */
    private MagicSet createSetEntityAdapted(MtgSet mtgSet) {
        MagicSet setEntity = new MagicSet();
        setEntity.setCode(mtgSet.code());
        setEntity.setName(mtgSet.name());
        setEntity.setBlock(mtgSet.block());

        // Adapter les champs spécifiques
        setEntity.setMtgoCode(mtgSet.gathererCode());
        setEntity.setTcgplayerGroupId(mtgSet.magicCardsInfoCode());
        setEntity.setVersion(mtgSet.border());

        // Utiliser le service d'adaptation pour le type
        adaptationService.setMagicSetType(setEntity, mtgSet.type());
        adaptationService.prepareMagicSetForSave(setEntity, mtgSet.type());

        // Parser la date de sortie
        if (mtgSet.releaseDate() != null && !mtgSet.releaseDate().isEmpty()) {
            try {
                setEntity.setReleaseDate(LocalDate.parse(mtgSet.releaseDate()));
            } catch (Exception e) {
                logger.warn("⚠️ Date de sortie invalide pour {} : {}", mtgSet.code(), mtgSet.releaseDate());
            }
        }

        // Définir OnlineOnly basé sur la logique métier
        if (mtgSet.onlineOnly()) {
            setEntity.setOnlineOnly(true);
        }

        return setEntity;
    }

    /**
     * Met à jour une entité extension existante - VERSION ADAPTÉE
     */
    private void updateSetEntityAdapted(MagicSet setEntity, MtgSet mtgSet) {
        setEntity.setName(mtgSet.name());
        setEntity.setBlock(mtgSet.block());
        setEntity.setMtgoCode(mtgSet.gathererCode());
        setEntity.setTcgplayerGroupId(mtgSet.magicCardsInfoCode());
        setEntity.setVersion(mtgSet.border());

        // Mettre à jour le type si nécessaire
        if (mtgSet.type() != null && !mtgSet.type().equals(setEntity.getType())) {
            adaptationService.setMagicSetType(setEntity, mtgSet.type());
        }

        // Mettre à jour la date
        if (mtgSet.releaseDate() != null && !mtgSet.releaseDate().isEmpty()) {
            try {
                setEntity.setReleaseDate(LocalDate.parse(mtgSet.releaseDate()));
            } catch (Exception e) {
                logger.warn("⚠️ Date de sortie invalide pour {} : {}", mtgSet.code(), mtgSet.releaseDate());
            }
        }

        // Mettre à jour OnlineOnly
        if (mtgSet.onlineOnly()) {
            setEntity.setOnlineOnly(true);
        }
    }

    /**
     * Crée une nouvelle entité carte - VERSION ADAPTÉE
     */
    private MagicCard createCardEntityAdapted(MtgCard mtgCard, String setCode) {
        MagicCard cardEntity = new MagicCard();
        cardEntity.setExternalId(mtgCard.id());
        cardEntity.setSetCode(setCode);
        updateCardEntityAdapted(cardEntity, mtgCard);
        return cardEntity;
    }

    /**
     * Met à jour une entité carte - VERSION ADAPTÉE
     */
    private void updateCardEntityAdapted(MagicCard cardEntity, MtgCard mtgCard) {
        // Données de base
        cardEntity.setExternalId(mtgCard.id());
        cardEntity.setName(mtgCard.name());
        cardEntity.setSetCode(cardEntity.getSetCode()); // Garder le setCode existant

        // Numéro de carte
        if (mtgCard.number() != null) {
            cardEntity.setNumber(mtgCard.number());
        }

        // Propriétés MTG dans les champs JSON adaptés
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
        String imageUrl = mtgCard.imageUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageUrl = generateImageUrlAdapted(mtgCard);
        }
        cardEntity.setOriginalImageUrl(imageUrl);

        // Propriétés spécifiques à la nouvelle structure
        cardEntity.setIsAffichable(true); // Par défaut affichable
        cardEntity.setHasRecherche(true); // Par défaut recherchable
        cardEntity.setCertifiable(false); // Par défaut non certifiable
    }

    /**
     * Met à jour les statistiques d'une extension - VERSION ADAPTÉE
     */
    private void updateSetStatisticsAdapted(String setCode) {
        long cardCount = cardRepository.countBySetCode(setCode);

        setRepository.findByCode(setCode).ifPresent(setEntity -> {
            setEntity.setCardsCount((int) cardCount);
            // La synchronisation est automatiquement déterminée par la présence de cartes
            setRepository.save(setEntity);
        });
    }

    /**
     * Définit les dates de sortie connues - VERSION ADAPTÉE
     */
    private void setKnownReleaseDateAdapted(MagicSet set, String setCode) {
        java.util.Map<String, LocalDate> knownDates = java.util.Map.of(
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

    /**
     * Génère une URL d'image - VERSION ADAPTÉE
     */
    private String generateImageUrlAdapted(MtgCard card) {
        if (card.multiverseid() != null) {
            return "https://gatherer.wizards.com/Handlers/Image.ashx?multiverseid=" + card.multiverseid() + "&type=card";
        }

        if (card.set() != null && card.number() != null) {
            return "https://api.scryfall.com/cards/" + card.set().toLowerCase() + "/" + card.number() + "?format=image";
        }

        return null;
    }

    // ========== MÉTHODES PUBLIQUES ADAPTÉES ==========

    /**
     * Méthode de compatibilité
     */
    public MagicCard saveOrUpdateCard(MtgCard mtgCard, String setCode) {
        return saveOrUpdateCardAdapted(mtgCard, setCode);
    }

    /**
     * Sauvegarde synchrone adaptée
     */
    public int saveCards(List<MtgCard> cards, String setCode) {
        logger.info("💾 Début de la sauvegarde synchrone adaptée de {} cartes pour {}", cards.size(), setCode);

        ensureSetExistsAdapted(setCode, cards);

        int savedCount = 0;
        int skippedCount = 0;

        for (MtgCard mtgCard : cards) {
            try {
                MagicCard result = saveOrUpdateCardAdapted(mtgCard, setCode);
                if (result != null) {
                    savedCount++;

                    if (result.getOriginalImageUrl() != null && !result.getOriginalImageUrl().isEmpty()) {
                        try {
                            imageDownloadService.downloadCardImage(result);
                        } catch (Exception e) {
                            logger.warn("⚠️ Erreur téléchargement image pour {} : {}", mtgCard.name(), e.getMessage());
                        }
                    }
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                logger.error("❌ Erreur sauvegarde carte {} : {}", mtgCard.name(), e.getMessage());
                skippedCount++;
            }
        }

        updateSetStatisticsAdapted(setCode);

        logger.info("✅ Sauvegarde adaptée terminée pour {} : {} sauvées, {} ignorées",
                setCode, savedCount, skippedCount);

        return savedCount;
    }

    /**
     * Récupère les cartes depuis la base - VERSION ADAPTÉE
     */
    public List<MagicCard> getCardsFromDatabase(String setCode) {
        return cardRepository.findBySetCodeOrderByNameAsc(setCode);
    }

    /**
     * Recherche de cartes avec filtres - VERSION ADAPTÉE
     */
    public Page<MagicCard> searchCards(String name, String setCode, String rarity,
                                       String type, String artist, Pageable pageable) {
        return cardRepository.findCardsWithFilters(name, setCode, rarity, type, artist, pageable);
    }

    /**
     * Vérifie si une extension est synchronisée - VERSION ADAPTÉE
     */
    public boolean isSetSynced(String setCode) {
        return setRepository.findByCode(setCode)
                .map(MagicSet::getCardsSynced)
                .orElse(false);
    }

    /**
     * Marque une extension comme synchronisée - VERSION ADAPTÉE
     */
    public void markSetAsSynced(String setCode) {
        setRepository.findByCode(setCode).ifPresent(setEntity -> {
            long cardCount = cardRepository.countBySetCode(setCode);
            setEntity.setCardsCount((int) cardCount);
            // La synchronisation est automatique basée sur nbCartes > 0
            setRepository.save(setEntity);
            logger.info("✅ Extension {} marquée comme synchronisée ({} cartes)", setCode, cardCount);
        });
    }

    /**
     * Statistiques de la base de données - VERSION ADAPTÉE
     */
    public DatabaseStats getDatabaseStats() {
        long totalCards = cardRepository.count();
        long totalSets = setRepository.count();
        long syncedSets = setRepository.countSyncedSets();

        // Compter les artistes depuis les attributs JSON
        long distinctArtists = cardRepository.countDistinctArtists();

        ImageDownloadService.ImageDownloadStats imageStats = imageDownloadService.getDownloadStats();

        return new DatabaseStats(totalCards, totalSets, syncedSets, distinctArtists, imageStats);
    }

    /**
     * Classe pour les statistiques - ADAPTÉE
     */
    public static class DatabaseStats {
        private final long totalCards;
        private final long totalSets;
        private final long syncedSets;
        private final long distinctArtists;
        private final ImageDownloadService.ImageDownloadStats imageStats;

        public DatabaseStats(long totalCards, long totalSets, long syncedSets,
                             long distinctArtists, ImageDownloadService.ImageDownloadStats imageStats) {
            this.totalCards = totalCards;
            this.totalSets = totalSets;
            this.syncedSets = syncedSets;
            this.distinctArtists = distinctArtists;
            this.imageStats = imageStats;
        }

        public long getTotalCards() { return totalCards; }
        public long getTotalSets() { return totalSets; }
        public long getSyncedSets() { return syncedSets; }
        public long getDistinctArtists() { return distinctArtists; }
        public ImageDownloadService.ImageDownloadStats getImageStats() { return imageStats; }
    }

    // ========== MÉTHODES DE NETTOYAGE ET MAINTENANCE ==========

    /**
     * Nettoie les données incohérentes
     */
    @Transactional
    public void cleanupInconsistentData() {
        logger.info("🧹 Nettoyage des données incohérentes");

        // Supprimer les cartes sans extension valide
        List<MagicCard> orphanCards = cardRepository.findAll().stream()
                .filter(card -> card.getSetCode() == null ||
                        setRepository.findByCode(card.getSetCode()).isEmpty())
                .toList();

        if (!orphanCards.isEmpty()) {
            cardRepository.deleteAll(orphanCards);
            logger.info("🗑️ {} cartes orphelines supprimées", orphanCards.size());
        }

        // Mettre à jour les compteurs d'extensions
        List<MagicSet> allSets = setRepository.findAll();
        for (MagicSet set : allSets) {
            long actualCardCount = cardRepository.countBySetCode(set.getCode());
            if (set.getCardsCount() == null || !set.getCardsCount().equals((int) actualCardCount)) {
                set.setCardsCount((int) actualCardCount);
                setRepository.save(set);
                logger.debug("🔄 Compteur mis à jour pour {} : {} cartes", set.getCode(), actualCardCount);
            }
        }

        logger.info("✅ Nettoyage terminé");
    }

    /**
     * Valide la cohérence d'une extension
     */
    public boolean validateSetConsistency(String setCode) {
        Optional<MagicSet> setOpt = setRepository.findByCode(setCode);
        if (setOpt.isEmpty()) {
            logger.error("❌ Extension {} non trouvée", setCode);
            return false;
        }

        MagicSet set = setOpt.get();

        // Valider avec le service d'adaptation
        if (!adaptationService.validateMagicSet(set)) {
            return false;
        }

        // Vérifier la cohérence des cartes
        long actualCardCount = cardRepository.countBySetCode(setCode);
        Integer declaredCardCount = set.getCardsCount();

        if (declaredCardCount != null && !declaredCardCount.equals((int) actualCardCount)) {
            logger.warn("⚠️ Incohérence pour {} : {} cartes déclarées vs {} réelles",
                    setCode, declaredCardCount, actualCardCount);
            return false;
        }

        logger.info("✅ Extension {} validée avec succès", setCode);
        return true;
    }
}