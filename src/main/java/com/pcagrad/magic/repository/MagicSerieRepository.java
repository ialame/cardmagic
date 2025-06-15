package com.pcagrad.magic.repository;

import com.pcagrad.magic.entity.MagicSerie;
import com.pcagrad.magic.util.Localization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MagicSerieRepository extends JpaRepository<MagicSerie, UUID> {

    // Recherche par nom dans les traductions (filtre discriminateur "mag")
    @Query("SELECT ms FROM MagicSerie ms " +
            "JOIN ms.translations t " +
            "WHERE t.name = :name")
    Optional<MagicSerie> findByName(@Param("name") String name);

    // Recherche par nom dans une localisation spécifique
    @Query("SELECT ms FROM MagicSerie ms " +
            "JOIN ms.translations t " +
            "WHERE t.name = :name AND t.localization = :localization")
    Optional<MagicSerie> findByNameAndLocalization(@Param("name") String name, @Param("localization") Localization localization);

    // Toutes les séries Magic actives
    @Query("SELECT ms FROM MagicSerie ms " +
            "JOIN ms.translations t " +
            "WHERE t.active = true")
    List<MagicSerie> findActiveMagicSeries();
}
