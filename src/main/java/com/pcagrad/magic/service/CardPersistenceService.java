package com.pcagrad.magic.service;

import com.pcagrad.magic.dto.ApiResponse;
import com.pcagrad.magic.entity.CardTranslation;
import com.pcagrad.magic.entity.MagicCard;
import com.pcagrad.magic.entity.MagicSet;
import com.pcagrad.magic.model.MtgCard;
import com.pcagrad.magic.model.MtgSet;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.repository.SetRepository;
import com.pcagrad.magic.repository.CardTranslationRepository; // ← AJOUTER
import com.pcagrad.magic.util.Localization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
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
    private CardTranslationRepository cardTranslationRepository; // ← AJOUTER

    @Autowired
    private ImageDownloadService imageDownloadService;

    @Autowired
    private EntityAdaptationService adaptationService;

    // ===============================
    // MÉTHODES MANQUANTES - CORRECTIONS
    // ===============================

    /**
     * ✅ MÉTHODE 1: saveCardsForSet (asynchrone)
     */
    @Async
    public CompletableFuture<Integer> saveCardsForSet(String setCode, List<MtgCard> cards) {
        logger.info("💾 Début de la sauvegarde adaptée de {} cartes pour l'extension {}", cards.size(), setCode);

        return CompletableFuture.supplyAsync(() -> {
            // S'assurer que l'extension existe
            ensureSetExistsAdapted(setCode, cards);

            int savedCount = 0;
            int skippedCount = 0;

            for (MtgCard mtgCard : cards) {
                try {
                    MagicCard result = saveOrUpdateCardAdapted(mtgCard, setCode);
                    if (result != null) {
                        savedCount++;

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
                    logger.error("❌ Erreur lors de la sauvegarde de la carte {} : {}", mtgCard.name(), e.getMessage());
                    skippedCount++;
                }
            }

            // Mettre à jour les statistiques de l'extension
            updateSetStatisticsAdapted(setCode);

            logger.info("✅ Sauvegarde terminée pour {} : {} sauvées, {} ignorées", setCode, savedCount, skippedCount);
            return savedCount;
        });
    }

    /**
     * ✅ MÉTHODE 2: saveCards (synchrone)
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

        logger.info("✅ Sauvegarde adaptée terminée pour {} : {} sauvées, {} ignorées", setCode, savedCount, skippedCount);
        return savedCount;
    }

    /**
     * ✅ MÉTHODE 3: saveOrUpdateSet
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
     * ✅ MÉTHODE 4: cleanupInconsistentData - Version simplifiée
     */
    public void cleanupInconsistentData() {
        logger.info("🧹 Nettoyage des données incohérentes");

        try {
            // Supprimer les cartes sans traductions (si la méthode existe)
            try {
                int deletedCards = cardRepository.deleteCardsWithoutTranslations();
                logger.info("🗑️ {} cartes sans traductions supprimées", deletedCards);
            } catch (Exception e) {
                logger.warn("⚠️ Impossible de supprimer les cartes sans traductions : {}", e.getMessage());
            }

            // Supprimer les traductions orphelines (si la méthode existe)
            try {
                int deletedTranslations = cardTranslationRepository.deleteOrphanTranslations();
                logger.info("🗑️ {} traductions orphelines supprimées", deletedTranslations);
            } catch (Exception e) {
                logger.warn("⚠️ Impossible de supprimer les traductions orphelines : {}", e.getMessage());
            }

            // Supprimer les extensions vides (si la méthode existe)
            try {
                int deletedSets = setRepository.deleteEmptySets();
                logger.info("🗑️ {} extensions vides supprimées", deletedSets);
            } catch (Exception e) {
                logger.warn("⚠️ Impossible de supprimer les extensions vides : {}", e.getMessage());
            }

            logger.info("✅ Nettoyage terminé");

        } catch (Exception e) {
            logger.error("❌ Erreur lors du nettoyage : {}", e.getMessage());
        }
    }

    /**
     * ✅ MÉTHODE 5: validateSetConsistency
     */
    public Map<String, Object> validateSetConsistency(String setCode) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Vérifier l'extension
            Optional<MagicSet> set = setRepository.findByCode(setCode);
            result.put("setExists", set.isPresent());

            if (set.isPresent()) {
                result.put("setName", set.get().getName());

                // Compter les cartes
                long cardCount = cardRepository.countBySetCode(setCode);
                result.put("cardCount", cardCount);

                // Compter les cartes avec traductions (si la méthode existe)
                try {
                    long cardsWithTranslations = cardRepository.countCardsWithTranslationsBySetCode(setCode);
                    result.put("cardsWithTranslations", cardsWithTranslations);
                } catch (Exception e) {
                    result.put("cardsWithTranslations", "Non disponible");
                }

                // Compter les cartes avec images (si la méthode existe)
                try {
                    long cardsWithImages = cardRepository.countCardsWithImagesBySetCode(setCode);
                    result.put("cardsWithImages", cardsWithImages);
                } catch (Exception e) {
                    result.put("cardsWithImages", "Non disponible");
                }

                // Calculer les pourcentages
                if (cardCount > 0) {
                    Object cardsWithTranslationsObj = result.get("cardsWithTranslations");
                    if (cardsWithTranslationsObj instanceof Long) {
                        long cardsWithTranslations = (Long) cardsWithTranslationsObj;
                        result.put("translationPercentage", (cardsWithTranslations * 100.0) / cardCount);
                    }

                    Object cardsWithImagesObj = result.get("cardsWithImages");
                    if (cardsWithImagesObj instanceof Long) {
                        long cardsWithImages = (Long) cardsWithImagesObj;
                        result.put("imagePercentage", (cardsWithImages * 100.0) / cardCount);
                    }
                }

                result.put("isConsistent", cardCount > 0);
            }

            result.put("success", true);

        } catch (Exception e) {
            logger.error("❌ Erreur validation consistance {} : {}", setCode, e.getMessage());
            result.put("error", e.getMessage());
            result.put("success", false);
        }

        return result;
    }


    // ===============================
    // MÉTHODES UTILITAIRES PRIVÉES
    // ===============================

    /**
     * Sauvegarde ou met à jour une carte - VERSION CORRIGÉE
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
            // *** CORRECTION: Utiliser la méthode de sauvegarde avec traductions ***
            return saveCardWithTranslations(cardEntity);
        } catch (Exception e) {
            logger.error("❌ Erreur sauvegarde carte adaptée {} : {}", mtgCard.name(), e.getMessage());
            return null;
        }
    }

    /**
     * NOUVELLE MÉTHODE CORRIGÉE : Sauvegarde la carte avec ses traductions
     */
    @Transactional
    protected MagicCard saveCardWithTranslations(MagicCard cardEntity) {
        // 1. D'abord sauvegarder la carte pour obtenir un ID
        MagicCard savedCard = cardRepository.save(cardEntity);

        // 2. Puis sauvegarder chaque traduction individuellement
        if (!cardEntity.getTranslations().isEmpty()) {
            for (CardTranslation translation : cardEntity.getTranslations()) {
                if (translation != null) {
                    // S'assurer que la traduction référence la carte sauvegardée
                    translation.setTranslatable(savedCard);

                    // Sauvegarder la traduction
                    cardTranslationRepository.save(translation);
                }
            }
        }

        return savedCard;
    }

    /**
     * Création d'entité carte - VERSION CORRIGÉE
     */
    private MagicCard createCardEntityAdapted(MtgCard mtgCard, String setCode) {
        MagicCard cardEntity = new MagicCard();

        // Génerer un UUID unique pour la carte
        cardEntity.setId(UUID.randomUUID());

        // Stocker l'ID Scryfall
        String externalId = mtgCard.id();
        if (externalId != null && externalId.length() > 20) {
            externalId = Integer.toHexString(mtgCard.id().hashCode()).substring(0, Math.min(20, 8));
        }
        cardEntity.setExternalId(externalId);
        cardEntity.setZPostExtension(setCode);

        updateCardEntityAdapted(cardEntity, mtgCard);
        return cardEntity;
    }

    // ================================================================
// CORRECTION 2: Dans CardPersistenceService.java
// ================================================================

    /**
     * ✅ CORRECTION: updateCardEntityAdapted - Bien gérer name et label_name
     */
    private void updateCardEntityAdapted(MagicCard cardEntity, MtgCard mtgCard) {
        // Créer la traduction avec name ET label_name identiques
        cardEntity.ensureTranslationExists(Localization.USA);
        CardTranslation translation = cardEntity.getTranslation(Localization.USA);
        if (translation != null) {
            String cardName = mtgCard.name() != null ? mtgCard.name() : "Carte inconnue";
            translation.setName(cardName);
            translation.setLabelName(cardName); // ← MÊME VALEUR que name
            translation.setAvailable(true);
        }

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
     * ✅ CORRECTION 1: createSetEntityAdapted - Utiliser getParsedReleaseDate()
     */
    private MagicSet createSetEntityAdapted(MtgSet mtgSet) {
        MagicSet setEntity = new MagicSet();
        setEntity.setCode(mtgSet.code());
        setEntity.setName(mtgSet.name());

        // Utiliser le service d'adaptation
        adaptationService.setMagicSetType(setEntity, mtgSet.type());
        adaptationService.prepareMagicSetForSave(setEntity, mtgSet.type());

        // *** CORRECTION: Utiliser getParsedReleaseDate() au lieu de releaseDate() directement ***
        LocalDate parsedDate = mtgSet.getParsedReleaseDate();
        if (parsedDate != null) {
            setEntity.setReleaseDate(parsedDate);
        }

        return setEntity;
    }


    /**
     * ✅ CORRECTION 2: updateSetEntityAdapted - Utiliser getParsedReleaseDate()
     */
    private void updateSetEntityAdapted(MagicSet setEntity, MtgSet mtgSet) {
        setEntity.setName(mtgSet.name());

        // Mettre à jour le type si nécessaire
        adaptationService.setMagicSetType(setEntity, mtgSet.type());

        // *** CORRECTION: Utiliser getParsedReleaseDate() au lieu de releaseDate() directement ***
        LocalDate parsedDate = mtgSet.getParsedReleaseDate();
        if (parsedDate != null) {
            setEntity.setReleaseDate(parsedDate);
        }
    }
    /**
     * Met à jour les statistiques d'une extension
     */
    private void updateSetStatisticsAdapted(String setCode) {
        try {
            long cardCount = cardRepository.countBySetCode(setCode);
            logger.debug("📊 Extension {} : {} cartes en base", setCode, cardCount);
        } catch (Exception e) {
            logger.warn("⚠️ Erreur mise à jour statistiques {} : {}", setCode, e.getMessage());
        }
    }

    /**
     * Définit des dates de sortie connues pour certaines extensions
     */
    private void setKnownReleaseDateAdapted(MagicSet setEntity, String setCode) {
        switch (setCode.toUpperCase()) {
            case "FIN":
                setEntity.setReleaseDate(LocalDate.of(2024, 11, 15));
                break;
            // Ajouter d'autres extensions connues
        }
    }

    // ===============================
    // MÉTHODES PUBLIQUES UTILITAIRES
    // ===============================

    /**
     * Récupère les cartes depuis la base - VERSION ADAPTÉE
     */
    public List<MagicCard> getCardsFromDatabase(String setCode) {
        return cardRepository.findBySetCodeOrderByNameAsc(setCode);
    }

    /**
     * Et modifier CardPersistenceService.java :
     */
    public Page<MagicCard> searchCards(String name, String setCode, String rarity,
                                       String type, String artist, Pageable pageable) {
        // Utiliser la méthode simplifiée temporairement
        return cardRepository.searchCardsByNameAndSet(name, setCode, pageable);
    }


    /**
     * Vérifie si une extension est synchronisée
     */
    public boolean isSetSynced(String setCode) {
        return setRepository.findByCode(setCode)
                .map(set -> cardRepository.countBySetCode(setCode) > 0)
                .orElse(false);
    }

    /**
     * ✅ MÉTHODE UTILITAIRE à ajouter dans CardPersistenceService
     */
    private LocalDate parseReleaseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDate.parse(dateString);
        } catch (Exception e) {
            logger.warn("⚠️ Impossible de parser la date : {}", dateString);
            return null;
        }
    }

    /**
     * Et utiliser cette méthode dans createSetEntityAdapted et updateSetEntityAdapted :
     */
    private MagicSet createSetEntityAdaptedAlternative(MtgSet mtgSet) {
        MagicSet setEntity = new MagicSet();
        setEntity.setCode(mtgSet.code());
        setEntity.setName(mtgSet.name());

        adaptationService.setMagicSetType(setEntity, mtgSet.type());
        adaptationService.prepareMagicSetForSave(setEntity, mtgSet.type());

        // *** UTILISER LA MÉTHODE UTILITAIRE ***
        LocalDate releaseDate = parseReleaseDate(mtgSet.releaseDate());
        if (releaseDate != null) {
            setEntity.setReleaseDate(releaseDate);
        }

        return setEntity;
    }



}