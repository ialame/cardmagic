package com.pcagrad.magic.repository;

import com.pcagrad.magic.entity.Serie;
import com.pcagrad.magic.util.Localization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SerieRepository extends JpaRepository<Serie, UUID> {

    // Recherche par nom dans les traductions
    @Query("SELECT s FROM Serie s " +
            "JOIN s.translations t " +
            "WHERE t.name = :name")
    Optional<Serie> findByName(@Param("name") String name);

    // Recherche par nom dans une localisation spécifique
    @Query("SELECT s FROM Serie s " +
            "JOIN s.translations t " +
            "WHERE t.name = :name AND t.localization = :localization")
    Optional<Serie> findByNameAndLocalization(@Param("name") String name, @Param("localization") Localization localization);

    // Recherche partielle par nom
    @Query("SELECT s FROM Serie s " +
            "JOIN s.translations t " +
            "WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Serie> findByNameContaining(@Param("name") String name);

    // Séries actives
    @Query("SELECT s FROM Serie s " +
            "JOIN s.translations t " +
            "WHERE t.active = true")
    List<Serie> findActiveSeries();

    // Série avec le plus d'extensions
    @Query("SELECT s FROM Serie s " +
            "ORDER BY SIZE(s.sets) DESC")
    List<Serie> findSeriesOrderBySetCountDesc();

    // Compter les extensions par série
    @Query("SELECT s, COUNT(cs) FROM Serie s " +
            "LEFT JOIN s.sets cs " +
            "GROUP BY s " +
            "ORDER BY COUNT(cs) DESC")
    List<Object[]> countSetsBySerie();
}