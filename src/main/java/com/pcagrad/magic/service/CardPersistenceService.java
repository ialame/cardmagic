package com.pcagrad.magic.service;

import com.pcagrad.magic.entity.CardEntity;
import com.pcagrad.magic.entity.SetEntity;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.model.MtgSet;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
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

    /**
     * Sauvegarde ou met à jour une extension en base
     */
    public SetEntity saveOrUpdateSet(MtgSet mtgSet) {
        logger.debug("💾 Sauvegarde de l'extension : {} ({})", mtgSet.name(), mtgSet.code());

        Optional<SetEntity> existingSet = setRepository.findByCode(mtgSet.code());
        SetEntity setEntity;

        if (existingSet.isPresent()) {
            setEntity = existingSet.get();
            updateSetEntity(setEntity, mtgSet);
            logger.debug("🔄 Mise à jour de l'extension existante : {}", mtgSet.code());
        } else {
            setEntity = createSetEntity(mtgSet);
            logger.info("✨ Nouvelle extension créée : {} - {}", mtgSet.code(), mtgSet.name());
        }

        return setRepository.save(setEntity);
    }

    /**
     * Sauvegarde les cartes d'une extension en base - VERSION UUID
     */
    @Async
    public CompletableFuture<Integer> saveCardsForSet(String setCode, List<MtgCard> cards) {
        logger.info("💾 Début de la sauvegarde de {} cartes pour l'extension {}", cards.size(), setCode);

        return CompletableFuture.supplyAsync(() -> {
            int savedCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;

            for (MtgCard mtgCard : cards) {
                try {
                    CardEntity result = saveOrUpdateCardWithUUID(mtgCard, setCode);
                    if (result != null) {
                        if (result.getCreatedAt().equals(result.getUpdatedAt())) {
                            savedCount++;
                        } else {
                            updatedCount++;
                        }

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
            updateSetStatistics(setCode);

            logger.info("✅ Sauvegarde terminée pour {} : {} nouvelles, {} mises à jour, {} ignorées",
                    setCode, savedCount, updatedCount, skippedCount);

            return savedCount + updatedCount;
        });
    }

    // Modifiez la méthode saveOrUpdateCardWithUUID dans CardPersistenceService.java

    public CardEntity saveOrUpdateCardWithUUID(MtgCard mtgCard, String setCode) {
        if (mtgCard.id() == null || mtgCard.id().isEmpty()) {
            logger.warn("⚠️ Carte sans ID externe ignorée : {}", mtgCard.name());
            return null;
        }

        // CORRECTION: Chercher par externalId ET setCode pour éviter les doublons
        Optional<CardEntity> existingCard = cardRepository.findByExternalIdAndSetCode(mtgCard.id(), setCode);
        CardEntity cardEntity;

        if (existingCard.isPresent()) {
            cardEntity = existingCard.get();

            // IMPORTANT: Ne pas changer l'UUID, juste mettre à jour les autres champs
            UUID originalId = cardEntity.getId();
            updateCardEntity(cardEntity, mtgCard);
            cardEntity.setId(originalId); // Forcer le même UUID

            logger.debug("🔄 Mise à jour carte existante : {} (UUID préservé: {})",
                    mtgCard.name(), cardEntity.getId());
        } else {
            // Vérifier s'il y a une carte avec le même nom dans le même set (doublon potentiel)
            List<CardEntity> sameName = cardRepository.findByNameAndSetCode(mtgCard.name(), setCode);
            if (!sameName.isEmpty()) {
                // Utiliser la carte existante et mettre à jour son externalId
                cardEntity = sameName.get(0);
                cardEntity.setExternalId(mtgCard.id());
                updateCardEntity(cardEntity, mtgCard);
                logger.debug("🔄 Carte existante trouvée par nom : {} (UUID préservé)", mtgCard.name());
            } else {
                // Créer une nouvelle carte
                cardEntity = createCardEntityWithUUID(mtgCard, setCode);
                logger.debug("✨ Nouvelle carte créée : {} (External ID: {})", mtgCard.name(), mtgCard.id());
            }
        }

        try {
            return cardRepository.save(cardEntity);
        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde carte {} : {}", mtgCard.name(), e.getMessage());
            // En cas d'erreur UUID, essayer de créer une nouvelle carte
            try {
                CardEntity newCard = createCardEntityWithUUID(mtgCard, setCode);
                return cardRepository.save(newCard);
            } catch (Exception e2) {
                logger.error("❌ Impossible de sauvegarder {} : {}", mtgCard.name(), e2.getMessage());
                return null;
            }
        }
    }
    /**
     * Méthode pour la compatibilité avec l'ancien code
     */
    public CardEntity saveOrUpdateCard(MtgCard mtgCard, String setCode) {
        return saveOrUpdateCardWithUUID(mtgCard, setCode);
    }

    /**
     * Méthode pour sauvegarder une liste de cartes (utilisée par ScryfallController)
     */
    public int saveCards(List<MtgCard> cards, String setCode) {
        logger.info("💾 Début de la sauvegarde de {} cartes pour l'extension {}", cards.size(), setCode);

        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (MtgCard mtgCard : cards) {
            try {
                CardEntity result = saveOrUpdateCardWithUUID(mtgCard, setCode);
                if (result != null) {
                    if (result.getCreatedAt().equals(result.getUpdatedAt())) {
                        savedCount++;
                    } else {
                        updatedCount++;
                    }

                    // Déclencher le téléchargement de l'image en arrière-plan
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
                logger.error("❌ Erreur lors de la sauvegarde de la carte {} : {}",
                        mtgCard.name(), e.getMessage());
                skippedCount++;
            }
        }

        // Mettre à jour les statistiques de l'extension
        updateSetStatistics(setCode);

        logger.info("✅ Sauvegarde terminée pour {} : {} nouvelles, {} mises à jour, {} ignorées",
                setCode, savedCount, updatedCount, skippedCount);

        return savedCount + updatedCount;
    }

    /**
     * Récupère les cartes depuis la base de données
     */
    public List<CardEntity> getCardsFromDatabase(String setCode) {
        return cardRepository.findBySetCodeOrderByNameAsc(setCode);
    }

    /**
     * Recherche de cartes avec filtres
     */
    public Page<CardEntity> searchCards(String name, String setCode, String rarity,
                                        String type, String artist, Pageable pageable) {
        return cardRepository.findCardsWithFilters(name, setCode, rarity, type, artist, pageable);
    }

    /**
     * Vérifie si une extension est déjà synchronisée
     */
    public boolean isSetSynced(String setCode) {
        return setRepository.findByCode(setCode)
                .map(SetEntity::getCardsSynced)
                .orElse(false);
    }

    /**
     * Marque une extension comme synchronisée
     */
    public void markSetAsSynced(String setCode) {
        setRepository.findByCode(setCode).ifPresent(setEntity -> {
            setEntity.setCardsSynced(true);
            setEntity.setLastSyncAt(LocalDateTime.now());
            setRepository.save(setEntity);
            logger.info("✅ Extension {} marquée comme synchronisée", setCode);
        });
    }

    /**
     * Met à jour les statistiques d'une extension
     */
    private void updateSetStatistics(String setCode) {
        long cardCount = cardRepository.countBySetCode(setCode);

        setRepository.findByCode(setCode).ifPresent(setEntity -> {
            setEntity.setCardsCount((int) cardCount);
            setEntity.setCardsSynced(true);
            setEntity.setLastSyncAt(LocalDateTime.now());
            setRepository.save(setEntity);
        });
    }

    /**
     * Crée une nouvelle entité extension
     */
    private SetEntity createSetEntity(MtgSet mtgSet) {
        SetEntity setEntity = new SetEntity();
        setEntity.setCode(mtgSet.code());
        setEntity.setName(mtgSet.name());
        setEntity.setType(mtgSet.type());
        setEntity.setBlock(mtgSet.block());
        setEntity.setGathererCode(mtgSet.gathererCode());
        setEntity.setMagicCardsInfoCode(mtgSet.magicCardsInfoCode());
        setEntity.setBorder(mtgSet.border());
        setEntity.setOnlineOnly(mtgSet.onlineOnly());

        // Parser la date de sortie
        if (mtgSet.releaseDate() != null && !mtgSet.releaseDate().isEmpty()) {
            try {
                setEntity.setReleaseDate(LocalDate.parse(mtgSet.releaseDate()));
            } catch (Exception e) {
                logger.warn("⚠️ Date de sortie invalide pour {} : {}", mtgSet.code(), mtgSet.releaseDate());
            }
        }

        return setEntity;
    }

    /**
     * Met à jour une entité extension existante
     */
    private void updateSetEntity(SetEntity setEntity, MtgSet mtgSet) {
        setEntity.setName(mtgSet.name());
        setEntity.setType(mtgSet.type());
        setEntity.setBlock(mtgSet.block());
        setEntity.setGathererCode(mtgSet.gathererCode());
        setEntity.setMagicCardsInfoCode(mtgSet.magicCardsInfoCode());
        setEntity.setBorder(mtgSet.border());
        setEntity.setOnlineOnly(mtgSet.onlineOnly());

        if (mtgSet.releaseDate() != null && !mtgSet.releaseDate().isEmpty()) {
            try {
                setEntity.setReleaseDate(LocalDate.parse(mtgSet.releaseDate()));
            } catch (Exception e) {
                logger.warn("⚠️ Date de sortie invalide pour {} : {}", mtgSet.code(), mtgSet.releaseDate());
            }
        }
    }

    /**
     * NOUVELLE MÉTHODE - Crée une nouvelle entité carte avec UUID
     */
    private CardEntity createCardEntityWithUUID(MtgCard mtgCard, String setCode) {
        CardEntity cardEntity = new CardEntity();
        cardEntity.setExternalId(mtgCard.id()); // Stocker l'ancien ID comme externalId
        updateCardEntity(cardEntity, mtgCard);
        cardEntity.setSetCode(setCode);
        return cardEntity;
    }

    /**
     * Met à jour une entité carte - VERSION UUID
     */
    private void updateCardEntity(CardEntity cardEntity, MtgCard mtgCard) {
        cardEntity.setExternalId(mtgCard.id()); // Assurer que l'externalId est correct
        cardEntity.setName(mtgCard.name());
        cardEntity.setManaCost(mtgCard.manaCost());
        cardEntity.setCmc(mtgCard.cmc());
        cardEntity.setColors(mtgCard.colors());
        cardEntity.setColorIdentity(mtgCard.colorIdentity());
        cardEntity.setType(mtgCard.type());
        cardEntity.setSupertypes(mtgCard.supertypes());
        cardEntity.setTypes(mtgCard.types());
        cardEntity.setSubtypes(mtgCard.subtypes());
        cardEntity.setRarity(mtgCard.rarity());
        cardEntity.setSetName(mtgCard.setName());
        cardEntity.setText(mtgCard.text());
        cardEntity.setArtist(mtgCard.artist());
        cardEntity.setNumber(mtgCard.number());
        cardEntity.setPower(mtgCard.power());
        cardEntity.setToughness(mtgCard.toughness());
        cardEntity.setLayout(mtgCard.layout());
        cardEntity.setMultiverseid(mtgCard.multiverseid());

        // Générer l'URL d'image si elle n'existe pas
        String imageUrl = mtgCard.imageUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageUrl = generateImageUrl(mtgCard);
        }
        cardEntity.setOriginalImageUrl(imageUrl);
    }

    /**
     * Génère une URL d'image pour une carte
     */
    private String generateImageUrl(MtgCard card) {
        // Si on a un multiverseId, utiliser l'URL Gatherer
        if (card.multiverseid() != null) {
            return "https://gatherer.wizards.com/Handlers/Image.ashx?multiverseid=" + card.multiverseid() + "&type=card";
        }

        // Sinon, utiliser Scryfall API comme fallback
        if (card.set() != null && card.number() != null) {
            return "https://api.scryfall.com/cards/" + card.set().toLowerCase() + "/" + card.number() + "?format=image";
        }

        return null;
    }

    /**
     * Statistiques de la base de données
     */
    public DatabaseStats getDatabaseStats() {
        long totalCards = cardRepository.count();
        long totalSets = setRepository.count();
        long syncedSets = setRepository.countSyncedSets();
        long distinctArtists = cardRepository.countDistinctArtists();

        ImageDownloadService.ImageDownloadStats imageStats = imageDownloadService.getDownloadStats();

        return new DatabaseStats(totalCards, totalSets, syncedSets, distinctArtists, imageStats);
    }

    /**
     * Classe pour les statistiques de la base
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
}