// ========== SetRepository.java ==========
package com.pcagrad.magic.repository;

import com.pcagrad.magic.entity.SetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SetRepository extends JpaRepository<SetEntity, UUID> {

    // Recherche par code (clé métier)
    Optional<SetEntity> findByCode(String code);
    boolean existsByCode(String code);
    void deleteByCode(String code);

    // Rechercher par nom (partiel)
    List<SetEntity> findByNameContainingIgnoreCaseOrderByReleaseDateDesc(String name);

    // Rechercher par bloc
    List<SetEntity> findByBlockOrderByReleaseDateDesc(String block);

    // Extensions avec cartes synchronisées
    List<SetEntity> findByCardsSyncedTrueOrderByReleaseDateDesc();

    // Extension la plus récente
    @Query("SELECT s FROM SetEntity s WHERE s.releaseDate IS NOT NULL AND s.type != 'promo' AND s.type != 'token' ORDER BY s.releaseDate DESC")
    List<SetEntity> findLatestSets();

    // Extensions par date de sortie
    List<SetEntity> findByReleaseDateBetweenOrderByReleaseDateDesc(LocalDate start, LocalDate end);

    // Extensions récentes (derniers 2 ans)
    @Query("SELECT s FROM SetEntity s WHERE s.releaseDate >= :since ORDER BY s.releaseDate DESC")
    List<SetEntity> findRecentSets(@Param("since") LocalDate since);

    // Recherche combinée
    @Query("SELECT s FROM SetEntity s WHERE " +
            "(:name IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:type IS NULL OR s.type = :type) AND " +
            "(:block IS NULL OR LOWER(s.block) LIKE LOWER(CONCAT('%', :block, '%')))")
    List<SetEntity> findSetsWithFilters(
            @Param("name") String name,
            @Param("type") String type,
            @Param("block") String block
    );

    // Statistiques
    @Query("SELECT s.type, COUNT(s) FROM SetEntity s GROUP BY s.type ORDER BY COUNT(s) DESC")
    List<Object[]> countByType();

    @Query("SELECT SUM(s.cardsCount) FROM SetEntity s WHERE s.cardsCount IS NOT NULL")
    Long getTotalCardsCount();

    // Extensions populaires (avec le plus de cartes)
    List<SetEntity> findTop10ByOrderByCardsCountDesc();

    // Méthodes existantes
    List<SetEntity> findByCardsSyncedFalseOrderByReleaseDateDesc();

    @Query("SELECT COUNT(s) FROM SetEntity s WHERE s.cardsSynced = true")
    long countSyncedSets();

    // Optionnel : autres méthodes utiles
    Optional<SetEntity> findByName(String name);
    List<SetEntity> findByTypeOrderByReleaseDateDesc(String type);

    @Query("SELECT s FROM SetEntity s WHERE YEAR(s.releaseDate) = :year ORDER BY s.releaseDate DESC")
    List<SetEntity> findByReleaseDateYear(@Param("year") int year);
}