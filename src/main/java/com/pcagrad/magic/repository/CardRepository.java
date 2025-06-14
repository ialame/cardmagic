// CARDREPOSITORY COMPLET avec toutes les méthodes manquantes

package com.pcagrad.magic.repository;

import com.pcagrad.magic.entity.CardEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<CardEntity, String> {

    // Méthodes de base
    List<CardEntity> findBySetCode(String setCode);
    List<CardEntity> findBySetCodeOrderByNameAsc(String setCode);
    long countBySetCode(String setCode);
    Page<CardEntity> findBySetCode(String setCode, Pageable pageable);

    // MÉTHODES MANQUANTES AJOUTÉES

    // 1. Pour CardPersistenceService
    boolean existsByIdAndSetCode(String id, String setCode);

    // 2. Pour la recherche avec filtres
    @Query("SELECT c FROM CardEntity c WHERE " +
            "(:setCode IS NULL OR c.setCode = :setCode) AND " +
            "(:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:type IS NULL OR LOWER(c.type) LIKE LOWER(CONCAT('%', :type, '%'))) AND " +
            "(:rarity IS NULL OR c.rarity = :rarity) AND " +
            "(:artist IS NULL OR LOWER(c.artist) LIKE LOWER(CONCAT('%', :artist, '%')))")
    Page<CardEntity> findCardsWithFilters(
            @Param("setCode") String setCode,
            @Param("name") String name,
            @Param("type") String type,
            @Param("rarity") String rarity,
            @Param("artist") String artist,
            Pageable pageable
    );

    // 3. Pour ImageDownloadService
    List<CardEntity> findByImageDownloadedTrueAndLocalImagePathIsNotNull();
    List<CardEntity> findByImageDownloadedFalseOrderByCreatedAtAsc();

    // 4. Suppression par code d'extension - CORRECTION: int au lieu de long
    @Modifying
    @Transactional
    @Query("DELETE FROM CardEntity c WHERE UPPER(c.setCode) = UPPER(:setCode)")
    int deleteBySetCodeIgnoreCase(@Param("setCode") String setCode);

    @Modifying
    @Transactional
    int deleteBySetCode(String setCode);

    // 5. Méthodes utiles pour les statistiques
    @Query("SELECT COUNT(c) FROM CardEntity c WHERE UPPER(c.setCode) = UPPER(:setCode)")
    long countBySetCodeIgnoreCase(@Param("setCode") String setCode);

    @Query("SELECT c FROM CardEntity c WHERE UPPER(c.setCode) = UPPER(:setCode) ORDER BY c.name ASC")
    List<CardEntity> findBySetCodeIgnoreCaseOrderByNameAsc(@Param("setCode") String setCode);

    @Query("SELECT COUNT(DISTINCT c.artist) FROM CardEntity c")
    long countDistinctArtists();

    @Query("SELECT c.rarity, COUNT(c) FROM CardEntity c WHERE UPPER(c.setCode) = UPPER(:setCode) GROUP BY c.rarity")
    List<Object[]> getRarityStatsForSet(@Param("setCode") String setCode);
}