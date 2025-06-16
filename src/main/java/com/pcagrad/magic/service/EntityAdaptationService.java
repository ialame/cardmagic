package com.pcagrad.magic.service;

import com.pcagrad.magic.entity.CardSetTranslation;
import com.pcagrad.magic.entity.MagicSet;
import com.pcagrad.magic.entity.MagicType;
import com.pcagrad.magic.repository.MagicTypeRepository;
import com.pcagrad.magic.util.Localization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service pour adapter la logique métier à la nouvelle structure de base
 */
@Service
public class EntityAdaptationService {

    private static final Logger logger = LoggerFactory.getLogger(EntityAdaptationService.class);

    @Autowired
    private MagicTypeRepository magicTypeRepository;

    /**
     * Adapte le type string vers MagicType entity
     */
    public void setMagicSetType(MagicSet magicSet, String type) {
        if (type == null || type.isEmpty()) {
            type = "expansion"; // Par défaut
        }

        Optional<MagicType> magicType = findOrCreateMagicType(type);
        if (magicType.isPresent()) {
            magicSet.setTypeMagic(magicType.get());
        } else {
            logger.warn("⚠️ Impossible de créer/trouver le type magic : {}", type);
        }
    }

    /**
     * Trouve ou crée un MagicType
     */
    private Optional<MagicType> findOrCreateMagicType(String type) {
        try {
            // D'abord chercher par type exact
            Optional<MagicType> existing = magicTypeRepository.findByType(type);
            if (existing.isPresent()) {
                return existing;
            }

            // Sinon chercher par variations
            existing = magicTypeRepository.findByTypeIgnoreCase(type);
            if (existing.isPresent()) {
                return existing;
            }

            // Créer un nouveau type si autorisé
            if (canCreateNewType(type)) {
                MagicType newType = new MagicType();
                newType.setType(type);
                newType.setTypePcafr(translateTypeToFrench(type));
                newType.setTypePcaus(type); // US = original
                newType.setSousTypePcafr(""); // Pas de sous-type par défaut
                newType.setSousTypePcaus("");

                MagicType savedType = magicTypeRepository.save(newType);
                logger.info("✅ Nouveau type magic créé : {}", type);
                return Optional.of(savedType);
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la recherche/création du type {} : {}", type, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Détermine si on peut créer un nouveau type
     */
    private boolean canCreateNewType(String type) {
        // Types standards MTG autorisés
        String[] allowedTypes = {
                "expansion", "core", "commander", "draft_innovation",
                "reprint", "funny", "memorabilia", "premium_deck",
                "duel_deck", "from_the_vault", "spellbook", "arsenal",
                "planechase", "archenemy", "vanguard", "masters",
                "conspiracy", "treasure_chest", "promo", "token",
                "starter", "box", "un", "masterpiece"
        };

        for (String allowed : allowedTypes) {
            if (allowed.equalsIgnoreCase(type)) {
                return true;
            }
        }

        logger.warn("⚠️ Type non standard détecté : {}", type);
        return false; // Par sécurité, ne pas créer de types non standards
    }

    /**
     * Traduit un type en français
     */
    private String translateTypeToFrench(String englishType) {
        return switch (englishType.toLowerCase()) {
            case "expansion" -> "Extension";
            case "core" -> "Edition de base";
            case "commander" -> "Commander";
            case "draft_innovation" -> "Innovation Draft";
            case "reprint" -> "Réimpression";
            case "funny" -> "Humoristique";
            case "memorabilia" -> "Collector";
            case "premium_deck" -> "Deck Premium";
            case "duel_deck" -> "Deck Duel";
            case "from_the_vault" -> "From the Vault";
            case "spellbook" -> "Grimoire";
            case "masters" -> "Masters";
            case "promo" -> "Promotionnel";
            case "token" -> "Jeton";
            default -> englishType; // Garder l'original si pas de traduction
        };
    }

    /**
     * Valide qu'une MagicSet a tous les champs requis
     */
    public boolean validateMagicSet(MagicSet magicSet) {
        if (magicSet.getCode() == null || magicSet.getCode().trim().isEmpty()) {
            logger.error("❌ MagicSet sans code");
            return false;
        }

        if (magicSet.getTypeMagic() == null) {
            logger.error("❌ MagicSet {} sans type magic", magicSet.getCode());
            return false;
        }

        if (magicSet.getName() == null || magicSet.getName().trim().isEmpty()) {
            logger.warn("⚠️ MagicSet {} sans nom", magicSet.getCode());
            // Définir un nom par défaut
            magicSet.setName("Extension " + magicSet.getCode());
        }

        return true;
    }

    /**
     * Prépare une MagicSet pour la sauvegarde
     */
    public void prepareMagicSetForSave(MagicSet magicSet, String type) {
        // S'assurer que le type est défini
        if (magicSet.getTypeMagic() == null && type != null) {
            setMagicSetType(magicSet, type);
        }

        // Définir des valeurs par défaut
        if (magicSet.getCertifiable() == null) {
            magicSet.setCertifiable(false);
        }

        if (magicSet.getFr() == null) {
            magicSet.setFr(false);
        }

        if (magicSet.getUs() == null) {
            magicSet.setUs(true); // Par défaut US
        }

        if (magicSet.getHasDateSortieFr() == null) {
            magicSet.setHasDateSortieFr(false);
        }

        // S'assurer qu'il y a au moins une translation
        if (magicSet.getTranslations().isEmpty()) {
            magicSet.ensureTranslationExists(com.pcagrad.magic.util.Localization.USA);
        }

        // *** NOUVEAU : Vérifier que label_name est défini ***
        CardSetTranslation usTranslation = magicSet.getTranslation(Localization.USA);
        if (usTranslation != null && usTranslation.getLabelName() == null) {
            String name = usTranslation.getName() != null ? usTranslation.getName() : magicSet.getCode();
            usTranslation.setLabelName(name);
        }
    }
}

