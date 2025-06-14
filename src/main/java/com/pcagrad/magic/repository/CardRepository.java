// ========== CardRepository.java ==========
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
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<CardEntity, UUID> {

    // Méthodes de base par setCode
    List<CardEntity> findBySetCode(String setCode);
    List<CardEntity> findBySetCodeOrderByNameAsc(String setCode);
    long countBySetCode(String setCode);
    Page<CardEntity> findBySetCode(String setCode, Pageable pageable);

    // Recherche par ID externe (ancien ID string des APIs)
    Optional<CardEntity> findByExternalId(String externalId);
    Optional<CardEntity> findByExternalIdAndSetCode(String externalId, String setCode);
    boolean existsByExternalIdAndSetCode(String externalId, String setCode);

    // Recherche avec filtres
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

    // Pour ImageDownloadService
    List<CardEntity> findByImageDownloadedTrueAndLocalImagePathIsNotNull();
    List<CardEntity> findByImageDownloadedFalseOrderByCreatedAtAsc();

    // Suppression par code d'extension
    @Modifying
    @Transactional
    @Query("DELETE FROM CardEntity c WHERE UPPER(c.setCode) = UPPER(:setCode)")
    int deleteBySetCodeIgnoreCase(@Param("setCode") String setCode);

    @Modifying
    @Transactional
    int deleteBySetCode(String setCode);

    // Méthodes utiles pour les statistiques
    @Query("SELECT COUNT(c) FROM CardEntity c WHERE UPPER(c.setCode) = UPPER(:setCode)")
    long countBySetCodeIgnoreCase(@Param("setCode") String setCode);

    @Query("SELECT c FROM CardEntity c WHERE UPPER(c.setCode) = UPPER(:setCode) ORDER BY c.name ASC")
    List<CardEntity> findBySetCodeIgnoreCaseOrderByNameAsc(@Param("setCode") String setCode);

    @Query("SELECT COUNT(DISTINCT c.artist) FROM CardEntity c")
    long countDistinctArtists();

    @Query("SELECT c.rarity, COUNT(c) FROM CardEntity c WHERE UPPER(c.setCode) = UPPER(:setCode) GROUP BY c.rarity")
    List<Object[]> getRarityStatsForSet(@Param("setCode") String setCode);

    // Nouvelles méthodes pour la gestion des doublons avec UUID
    @Query("SELECT c FROM CardEntity c WHERE c.externalId = :externalId AND c.setCode = :setCode")
    List<CardEntity> findDuplicatesByExternalIdAndSetCode(@Param("externalId") String externalId, @Param("setCode") String setCode);

    List<CardEntity> findByNameAndSetCode(String name, String setCode);

}

