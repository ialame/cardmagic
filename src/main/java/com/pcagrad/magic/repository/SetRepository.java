package com.pcagrad.magic.repository;

import com.pcagrad.magic.entity.MagicSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SetRepository extends JpaRepository<MagicSet, UUID> {

    // Recherche par code (clé métier)
    Optional<MagicSet> findByCode(String code);
    boolean existsByCode(String code);
    void deleteByCode(String code);

    // Rechercher par nom dans les translations
    @Query("SELECT DISTINCT ms FROM MagicSet ms " +
            "JOIN ms.translations t " +
            "WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%')) " +
            "ORDER BY t.releaseDate DESC")
    List<MagicSet> findByNameContainingIgnoreCaseOrderByReleaseDateDesc(@Param("name") String name);

    // Rechercher par bloc
    List<MagicSet> findByBlockOrderByReleaseDateDesc(String block);

    // Extensions avec cartes synchronisées (basé sur nbCartes > 0)
    @Query("SELECT ms FROM MagicSet ms WHERE ms.nbCartes > 0 " +
            "ORDER BY (SELECT MAX(t.releaseDate) FROM ms.translations t) DESC")
    List<MagicSet> findByCardsSyncedTrueOrderByReleaseDateDesc();

    // Extension la plus récente basée sur les translations
    @Query("SELECT ms FROM MagicSet ms " +
            "JOIN ms.translations t " +
            "WHERE t.releaseDate IS NOT NULL " +
            "AND ms.typeMagic.type NOT IN ('promo', 'token') " +
            "ORDER BY t.releaseDate DESC")
    List<MagicSet> findLatestSets();

    // Extensions par date de sortie depuis les translations
    @Query("SELECT DISTINCT ms FROM MagicSet ms " +
            "JOIN ms.translations t " +
            "WHERE t.releaseDate BETWEEN :start AND :end " +
            "ORDER BY t.releaseDate DESC")
    List<MagicSet> findByReleaseDateBetweenOrderByReleaseDateDesc(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    // Extensions récentes (derniers 2 ans)
    @Query("SELECT DISTINCT ms FROM MagicSet ms " +
            "JOIN ms.translations t " +
            "WHERE t.releaseDate >= :since " +
            "ORDER BY t.releaseDate DESC")
    List<MagicSet> findRecentSets(@Param("since") LocalDate since);

    // Recherche combinée avec adaptations
    @Query("SELECT DISTINCT ms FROM MagicSet ms " +
            "JOIN ms.translations t " +
            "WHERE (:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
            "AND (:type IS NULL OR ms.typeMagic.type = :type) " +
            "AND (:block IS NULL OR LOWER(ms.block) LIKE LOWER(CONCAT('%', :block, '%')))")
    List<MagicSet> findSetsWithFilters(
            @Param("name") String name,
            @Param("type") String type,
            @Param("block") String block
    );

    // Statistiques adaptées
    @Query("SELECT mt.type, COUNT(ms) FROM MagicSet ms " +
            "JOIN ms.typeMagic mt " +
            "GROUP BY mt.type ORDER BY COUNT(ms) DESC")
    List<Object[]> countByType();

    @Query("SELECT SUM(ms.nbCartes) FROM MagicSet ms WHERE ms.nbCartes IS NOT NULL")
    Long getTotalCardsCount();

    // Extensions populaires (avec le plus de cartes)
    @Query("SELECT ms FROM MagicSet ms " +
            "WHERE ms.nbCartes IS NOT NULL " +
            "ORDER BY ms.nbCartes DESC")
    List<MagicSet> findTop10ByOrderByCardsCountDesc();

    // Extensions non synchronisées (nbCartes = 0 ou null)
    @Query("SELECT ms FROM MagicSet ms " +
            "WHERE (ms.nbCartes IS NULL OR ms.nbCartes = 0) " +
            "ORDER BY (SELECT MAX(t.releaseDate) FROM ms.translations t) DESC")
    List<MagicSet> findByCardsSyncedFalseOrderByReleaseDateDesc();

    // Compter les extensions synchronisées
    @Query("SELECT COUNT(ms) FROM MagicSet ms WHERE ms.nbCartes > 0")
    long countSyncedSets();

    // Recherche par nom exact dans les translations
    @Query("SELECT ms FROM MagicSet ms " +
            "JOIN ms.translations t " +
            "WHERE t.name = :name")
    Optional<MagicSet> findByName(@Param("name") String name);

    // Extensions par type
    @Query("SELECT ms FROM MagicSet ms " +
            "WHERE ms.typeMagic.type = :type " +
            "ORDER BY (SELECT MAX(t.releaseDate) FROM ms.translations t) DESC")
    List<MagicSet> findByTypeOrderByReleaseDateDesc(@Param("type") String type);

    // Extensions par année depuis les translations
    @Query("SELECT DISTINCT ms FROM MagicSet ms " +
            "JOIN ms.translations t " +
            "WHERE YEAR(t.releaseDate) = :year " +
            "ORDER BY t.releaseDate DESC")
    List<MagicSet> findByReleaseDateYear(@Param("year") int year);

    // NOUVELLES MÉTHODES SPÉCIFIQUES À LA STRUCTURE

    // Trouver par nom dans une localisation spécifique
    @Query("SELECT ms FROM MagicSet ms " +
            "JOIN ms.translations t " +
            "WHERE t.localization = :localization " +
            "AND LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<MagicSet> findByNameInLocalization(
            @Param("name") String name,
            @Param("localization") com.pcagrad.magic.util.Localization localization);

    // Extensions certifiables
    @Query("SELECT ms FROM MagicSet ms WHERE ms.certifiable = true")
    List<MagicSet> findCertifiableSets();

    // Extensions par marché (FR/US)
    @Query("SELECT ms FROM MagicSet ms WHERE ms.fr = :fr AND ms.us = :us")
    List<MagicSet> findByMarket(@Param("fr") Boolean fr, @Param("us") Boolean us);

    // Extensions avec images
    @Query("SELECT ms FROM MagicSet ms WHERE ms.nbImages > 0")
    List<MagicSet> findSetsWithImages();
}

