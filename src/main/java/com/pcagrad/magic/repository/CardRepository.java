// REPOSITORY POUR LES CARTES ADAPTÉ

package com.pcagrad.magic.repository;

import com.pcagrad.magic.entity.MagicCard;
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
public interface CardRepository extends JpaRepository<MagicCard, UUID> {

    /**
     * Supprimer les cartes sans traductions
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MagicCard mc WHERE mc.id NOT IN " +
            "(SELECT DISTINCT t.translatable.id FROM CardTranslation t)")
    int deleteCardsWithoutTranslations();

    /**
     * Trouver par ID externe ET setCode
     */
    @Query("SELECT mc FROM MagicCard mc WHERE mc.idPrim = :externalId AND mc.zPostExtension = :setCode")
    Optional<MagicCard> findByExternalIdAndSetCode(@Param("externalId") String externalId, @Param("setCode") String setCode);

    /**
     * Trouver par nom ET setCode
     */
    @Query("SELECT mc FROM MagicCard mc " +
            "JOIN mc.translations t " +
            "WHERE t.name = :name AND mc.zPostExtension = :setCode AND t.localization = com.pcagrad.magic.util.Localization.USA")
    List<MagicCard> findByNameAndSetCode(@Param("name") String name, @Param("setCode") String setCode);

    /**
     * Recherche avec filtres
     */
//    @Query("SELECT mc FROM MagicCard mc " +
//            "JOIN mc.translations t " +
//            "WHERE (:name IS NULL OR t.name LIKE %:name%) " +
//            "AND (:setCode IS NULL OR mc.zPostExtension = :setCode) " +
//            "AND (:rarity IS NULL OR mc.rarity = :rarity) " +
//            "AND (:type IS NULL OR mc.type LIKE %:type%) " +
//            "AND (:artist IS NULL OR mc.artist LIKE %:artist%) " +
//            "AND t.localization = com.pcagrad.magic.util.Localization.USA")
//    Page<MagicCard> findCardsWithFilters(@Param("name") String name,
//                                         @Param("setCode") String setCode,
//                                         @Param("rarity") String rarity,
//                                         @Param("type") String type,
//                                         @Param("artist") String artist,
//                                         Pageable pageable);


    @Query("SELECT mc FROM MagicCard mc " +
            "JOIN mc.translations t " +
            "WHERE (:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
            "AND (:setCode IS NULL OR mc.zPostExtension = :setCode) " +
            "AND (:rarity IS NULL OR mc.attributes LIKE CONCAT('%\"rarity\":\"', :rarity, '%')) " +
            "AND (:type IS NULL OR mc.attributes LIKE CONCAT('%\"type\":', '%', :type, '%')) " +
            "AND (:artist IS NULL OR mc.attributes LIKE CONCAT('%\"artist\":\"', :artist, '%')) " +
            "AND t.localization = com.pcagrad.magic.util.Localization.USA")
    Page<MagicCard> findCardsWithFilters(@Param("name") String name,
                                         @Param("setCode") String setCode,
                                         @Param("rarity") String rarity,
                                         @Param("type") String type,
                                         @Param("artist") String artist,
                                         Pageable pageable);

    /**
     * Recherche simple par nom et setCode seulement
     */
    @Query("SELECT mc FROM MagicCard mc " +
            "JOIN mc.translations t " +
            "WHERE (:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
            "AND (:setCode IS NULL OR mc.zPostExtension = :setCode) " +
            "AND t.localization = com.pcagrad.magic.util.Localization.USA")
    Page<MagicCard> searchCardsByNameAndSet(@Param("name") String name,
                                            @Param("setCode") String setCode,
                                            Pageable pageable);

    /**
     * Compter les cartes avec traductions par setCode
     */
    @Query("SELECT COUNT(DISTINCT mc) FROM MagicCard mc " +
            "JOIN mc.translations t " +
            "WHERE mc.zPostExtension = :setCode")
    long countCardsWithTranslationsBySetCode(@Param("setCode") String setCode);

    /**
     * Compter les cartes avec images par setCode
     */
    @Query("SELECT COUNT(mc) FROM MagicCard mc " +
            "WHERE mc.zPostExtension = :setCode AND mc.hasImg = true")
    long countCardsWithImagesBySetCode(@Param("setCode") String setCode);



    // Méthodes de base par setCode (stocké dans zPostExtension)
    @Query("SELECT mc FROM MagicCard mc WHERE mc.zPostExtension = :setCode")
    List<MagicCard> findBySetCode(@Param("setCode") String setCode);

    @Query("SELECT mc FROM MagicCard mc " +
            "WHERE mc.zPostExtension = :setCode " +
            "ORDER BY (SELECT t.name FROM mc.translations t WHERE t.localization = com.pcagrad.magic.util.Localization.USA) ASC")
    List<MagicCard> findBySetCodeOrderByNameAsc(@Param("setCode") String setCode);

    @Query("SELECT COUNT(mc) FROM MagicCard mc WHERE mc.zPostExtension = :setCode")
    long countBySetCode(@Param("setCode") String setCode);

    // Pagination par set
    @Query("SELECT mc FROM MagicCard mc WHERE mc.zPostExtension = :setCode")
    Page<MagicCard> findBySetCode(@Param("setCode") String setCode, Pageable pageable);

    // Recherche par ID externe (stocké dans idPrim)
    Optional<MagicCard> findByIdPrim(String idPrim);



    @Query("SELECT COUNT(mc) > 0 FROM MagicCard mc WHERE mc.idPrim = :idPrim AND mc.zPostExtension = :zPostExtension")
    boolean existsByIdPrimAndZPostExtension(@Param("idPrim") String idPrim, @Param("zPostExtension") String zPostExtension);


    // Pour ImageDownloadService (basé sur hasImg)
    @Query("SELECT mc FROM MagicCard mc WHERE mc.hasImg = true AND mc.fusionPca IS NOT NULL")
    List<MagicCard> findByImageDownloadedTrueAndLocalImagePathIsNotNull();

    @Query("SELECT mc FROM MagicCard mc WHERE mc.hasImg = false ORDER BY mc.numero ASC")
    List<MagicCard> findByImageDownloadedFalseOrderByCreatedAtAsc();


    @Modifying
    @Transactional
    @Query("DELETE FROM MagicCard mc WHERE mc.zPostExtension = :zPostExtension")
    int deleteByZPostExtension(@Param("zPostExtension") String zPostExtension);

    @Modifying
    @Transactional
    default void deleteBySetCodeAndFlush(String setCode) {
        deleteBySetCodeIgnoreCase(setCode);
        flush();
    }

    // Statistiques adaptées
    @Query("SELECT COUNT(mc) FROM MagicCard mc WHERE UPPER(mc.zPostExtension) = UPPER(:setCode)")
    long countBySetCodeIgnoreCase(@Param("setCode") String setCode);

    @Query("SELECT mc FROM MagicCard mc " +
            "WHERE UPPER(mc.zPostExtension) = UPPER(:setCode) " +
            "ORDER BY (SELECT t.name FROM mc.translations t WHERE t.localization = com.pcagrad.magic.util.Localization.USA) ASC")
    List<MagicCard> findBySetCodeIgnoreCaseOrderByNameAsc(@Param("setCode") String setCode);

    @Query("SELECT COUNT(DISTINCT mc.attributes) FROM MagicCard mc " +
            "WHERE mc.attributes LIKE '%\"artist\":%'")
    long countDistinctArtists();

    @Query(value = "SELECT " +
            "SUBSTRING(mc.attributes, LOCATE('\"rarity\":\"', mc.attributes) + 10, " +
            "LOCATE('\"', mc.attributes, LOCATE('\"rarity\":\"', mc.attributes) + 10) - LOCATE('\"rarity\":\"', mc.attributes) - 10) as rarity, " +
            "COUNT(mc.id) " +
            "FROM magic_card mc " +
            "WHERE UPPER(mc.z_post_extension) = UPPER(:setCode) " +
            "AND mc.attributes LIKE '%\"rarity\":%' " +
            "GROUP BY rarity",
            nativeQuery = true)
    List<Object[]> getRarityStatsForSet(@Param("setCode") String setCode);

    // Gestion des doublons
    @Query("SELECT mc FROM MagicCard mc " +
            "WHERE mc.idPrim = :externalId AND mc.zPostExtension = :setCode")
    List<MagicCard> findDuplicatesByExternalIdAndSetCode(
            @Param("externalId") String externalId,
            @Param("setCode") String setCode);


    // NOUVELLES MÉTHODES SPÉCIFIQUES

    // Cartes certifiables
    @Query("SELECT mc FROM MagicCard mc WHERE mc.certifiable = true")
    List<MagicCard> findCertifiableCards();

    // Cartes avec images
    @Query("SELECT mc FROM MagicCard mc WHERE mc.hasImg = true")
    List<MagicCard> findCardsWithImages();

    // Cartes affichables
    @Query("SELECT mc FROM MagicCard mc WHERE mc.isAffichable = true")
    List<MagicCard> findDisplayableCards();

    // Cartes par numéro
    @Query("SELECT mc FROM MagicCard mc WHERE mc.numero = :numero AND mc.zPostExtension = :setCode")
    Optional<MagicCard> findByNumeroAndSetCode(@Param("numero") Integer numero, @Param("setCode") String setCode);



    /**
     * Supprimer par setCode (ignore case)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MagicCard mc WHERE UPPER(mc.zPostExtension) = UPPER(:setCode)")
    int deleteBySetCodeIgnoreCase(@Param("setCode") String setCode);

    // Compter les cartes avec des champs spécifiques non-null
    @Query("SELECT COUNT(c) FROM MagicCard c WHERE c.zPostExtension = :setCode AND c.numero IS NOT NULL")
    long countBySetCodeAndNumberIsNotNull(@Param("setCode") String setCode);

    @Query("SELECT COUNT(c) FROM MagicCard c WHERE c.zPostExtension = :setCode AND c.allowedNotes LIKE '%\"colors\":%'")
    long countBySetCodeAndColorsIsNotNull(@Param("setCode") String setCode);

    @Query("SELECT COUNT(c) FROM MagicCard c WHERE c.zPostExtension = :setCode AND c.allowedNotes LIKE '%\"colorIdentity\":%'")
    long countBySetCodeAndColorIdentityIsNotNull(@Param("setCode") String setCode);

    @Query("SELECT COUNT(c) FROM MagicCard c WHERE c.zPostExtension = :setCode AND c.allowedNotes LIKE '%\"types\":%'")
    long countBySetCodeAndTypesIsNotNull(@Param("setCode") String setCode);

    @Query("SELECT COUNT(c) FROM MagicCard c WHERE c.zPostExtension = :setCode AND c.attributes LIKE '%\"layout\":%'")
    long countBySetCodeAndLayoutIsNotNull(@Param("setCode") String setCode);
}