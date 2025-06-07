package com.pcagrad.magic.repository;

import com.pcagrad.magic.entity.CardEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CardRepository extends JpaRepository<CardEntity, String> {

    // Rechercher par extension
    List<CardEntity> findBySetCodeOrderByNameAsc(String setCode);

    // Rechercher par nom (partiel)
    List<CardEntity> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    // Rechercher par rareté
    List<CardEntity> findByRarityOrderByNameAsc(String rarity);

    // Rechercher par type
    List<CardEntity> findByTypeContainingIgnoreCaseOrderByNameAsc(String type);

    // Rechercher par artiste
    List<CardEntity> findByArtistContainingIgnoreCaseOrderByNameAsc(String artist);

    // Recherche combinée avec pagination
    @Query("SELECT c FROM CardEntity c WHERE " +
            "(:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:setCode IS NULL OR c.setCode = :setCode) AND " +
            "(:rarity IS NULL OR c.rarity = :rarity) AND " +
            "(:type IS NULL OR LOWER(c.type) LIKE LOWER(CONCAT('%', :type, '%'))) AND " +
            "(:artist IS NULL OR LOWER(c.artist) LIKE LOWER(CONCAT('%', :artist, '%')))")
    Page<CardEntity> findCardsWithFilters(
            @Param("name") String name,
            @Param("setCode") String setCode,
            @Param("rarity") String rarity,
            @Param("type") String type,
            @Param("artist") String artist,
            Pageable pageable
    );

    // Compter les cartes par extension
    long countBySetCode(String setCode);

    // Compter les cartes par rareté dans une extension
    @Query("SELECT c.rarity, COUNT(c) FROM CardEntity c WHERE c.setCode = :setCode GROUP BY c.rarity")
    List<Object[]> countByRarityInSet(@Param("setCode") String setCode);

    // Cartes sans image téléchargée
    List<CardEntity> findByImageDownloadedFalseOrderByCreatedAtAsc();

    // Cartes avec image locale
    List<CardEntity> findByImageDownloadedTrueAndLocalImagePathIsNotNull();

    // Vérifier si une carte existe
    boolean existsByIdAndSetCode(String id, String setCode);

    // Statistiques générales
    @Query("SELECT COUNT(DISTINCT c.setCode) FROM CardEntity c")
    long countDistinctSets();

    @Query("SELECT COUNT(DISTINCT c.artist) FROM CardEntity c WHERE c.artist IS NOT NULL")
    long countDistinctArtists();

    // Cartes les plus récentes
    List<CardEntity> findTop10ByOrderByCreatedAtDesc();
}