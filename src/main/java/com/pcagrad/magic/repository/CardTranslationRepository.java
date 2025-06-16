package com.pcagrad.magic.repository;

import com.pcagrad.magic.entity.CardTranslation;
import com.pcagrad.magic.util.Localization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardTranslationRepository extends JpaRepository<CardTranslation, UUID> {

    /**
     * ✅ MÉTHODE MANQUANTE: Supprimer les traductions orphelines
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM CardTranslation ct WHERE ct.translatable IS NULL")
    int deleteOrphanTranslations();

    /**
     * Trouver une traduction par carte et localisation
     */
    @Query("SELECT ct FROM CardTranslation ct WHERE ct.translatable.id = :cardId AND ct.localization = :localization")
    Optional<CardTranslation> findByCardIdAndLocalization(@Param("cardId") UUID cardId, @Param("localization") Localization localization);

    /**
     * Trouver toutes les traductions d'une carte
     */
    @Query("SELECT ct FROM CardTranslation ct WHERE ct.translatable.id = :cardId")
    List<CardTranslation> findByCardId(@Param("cardId") UUID cardId);

    /**
     * Supprimer toutes les traductions d'une carte
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM CardTranslation ct WHERE ct.translatable.id = :cardId")
    void deleteByCardId(@Param("cardId") UUID cardId);

    /**
     * Vérifier si une traduction existe pour une carte
     */
    @Query("SELECT COUNT(ct) > 0 FROM CardTranslation ct WHERE ct.translatable.id = :cardId AND ct.localization = :localization")
    boolean existsByCardIdAndLocalization(@Param("cardId") UUID cardId, @Param("localization") Localization localization);

}