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
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

        Optional<SetEntity> existingSet = setRepository.findById(mtgSet.code());
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
     * Sauvegarde les cartes d'une extension en base
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
                    CardEntity result = saveOrUpdateCard(mtgCard, setCode);
                    if (result != null) {
                        if (cardRepository.existsByIdAndSetCode(mtgCard.id(), setCode)) {
                            updatedCount++;
                        } else {
                            savedCount++;
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

    /**
     * Sauvegarde ou met à jour une carte individuelle
     */
    public CardEntity saveOrUpdateCard(MtgCard mtgCard, String setCode) {
        if (mtgCard.id() == null || mtgCard.id().isEmpty()) {
            logger.warn("⚠️ Carte sans ID ignorée : {}", mtgCard.name());
            return null;
        }

        Optional<CardEntity> existingCard = cardRepository.findById(mtgCard.id());
        CardEntity cardEntity;

        if (existingCard.isPresent()) {
            cardEntity = existingCard.get();
            updateCardEntity(cardEntity, mtgCard);
        } else {
            cardEntity = createCardEntity(mtgCard, setCode);
        }

        return cardRepository.save(cardEntity);
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
        return setRepository.findById(setCode)
                .map(SetEntity::getCardsSynced)
                .orElse(false);
    }

    /**
     * Marque une extension comme synchronisée
     */
    public void markSetAsSynced(String setCode) {
        setRepository.findById(setCode).ifPresent(setEntity -> {
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

        setRepository.findById(setCode).ifPresent(setEntity -> {
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
     * Crée une nouvelle entité carte
     */
    private CardEntity createCardEntity(MtgCard mtgCard, String setCode) {
        CardEntity cardEntity = new CardEntity();
        updateCardEntity(cardEntity, mtgCard);
        cardEntity.setSetCode(setCode);
        return cardEntity;
    }

    /**
     * Met à jour une entité carte
     */
    private void updateCardEntity(CardEntity cardEntity, MtgCard mtgCard) {
        cardEntity.setId(mtgCard.id());
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