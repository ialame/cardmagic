// ========== SetRepository.java ==========
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

    // Rechercher par nom (partiel)
    List<MagicSet> findByNameContainingIgnoreCaseOrderByReleaseDateDesc(String name);

    // Rechercher par bloc
    List<MagicSet> findByBlockOrderByReleaseDateDesc(String block);

    // Extensions avec cartes synchronisées
    List<MagicSet> findByCardsSyncedTrueOrderByReleaseDateDesc();

    // Extension la plus récente
    @Query("SELECT s FROM MagicSet s WHERE s.releaseDate IS NOT NULL AND s.type != 'promo' AND s.type != 'token' ORDER BY s.releaseDate DESC")
    List<MagicSet> findLatestSets();

    // Extensions par date de sortie
    List<MagicSet> findByReleaseDateBetweenOrderByReleaseDateDesc(LocalDate start, LocalDate end);

    // Extensions récentes (derniers 2 ans)
    @Query("SELECT s FROM MagicSet s WHERE s.releaseDate >= :since ORDER BY s.releaseDate DESC")
    List<MagicSet> findRecentSets(@Param("since") LocalDate since);

    // Recherche combinée
    @Query("SELECT s FROM MagicSet s WHERE " +
            "(:name IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:type IS NULL OR s.type = :type) AND " +
            "(:block IS NULL OR LOWER(s.block) LIKE LOWER(CONCAT('%', :block, '%')))")
    List<MagicSet> findSetsWithFilters(
            @Param("name") String name,
            @Param("type") String type,
            @Param("block") String block
    );

    // Statistiques
    @Query("SELECT s.type, COUNT(s) FROM MagicSet s GROUP BY s.type ORDER BY COUNT(s) DESC")
    List<Object[]> countByType();

    @Query("SELECT SUM(s.cardsCount) FROM MagicSet s WHERE s.cardsCount IS NOT NULL")
    Long getTotalCardsCount();

    // Extensions populaires (avec le plus de cartes)
    List<MagicSet> findTop10ByOrderByCardsCountDesc();

    // Méthodes existantes
    List<MagicSet> findByCardsSyncedFalseOrderByReleaseDateDesc();

    @Query("SELECT COUNT(s) FROM MagicSet s WHERE s.cardsSynced = true")
    long countSyncedSets();

    // Optionnel : autres méthodes utiles
    Optional<MagicSet> findByName(String name);
    List<MagicSet> findByTypeOrderByReleaseDateDesc(String type);

    @Query("SELECT s FROM MagicSet s WHERE YEAR(s.releaseDate) = :year ORDER BY s.releaseDate DESC")
    List<MagicSet> findByReleaseDateYear(@Param("year") int year);
}