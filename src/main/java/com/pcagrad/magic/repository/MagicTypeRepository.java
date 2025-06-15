package com.pcagrad.magic.repository;

import com.pcagrad.magic.entity.MagicType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MagicTypeRepository extends JpaRepository<MagicType, UUID> {

    // Recherche par type exact
    Optional<MagicType> findByType(String type);

    // Recherche insensible à la casse
    @Query("SELECT mt FROM MagicType mt WHERE LOWER(mt.type) = LOWER(:type)")
    Optional<MagicType> findByTypeIgnoreCase(@Param("type") String type);

    // Recherche par n'importe quel champ type
    @Query("SELECT mt FROM MagicType mt WHERE " +
            "mt.type = :type OR mt.typePcaus = :type OR mt.typePcafr = :type")
    Optional<MagicType> findByAnyTypeField(@Param("type") String type);

    // Recherche par type US ou FR
    @Query("SELECT mt FROM MagicType mt WHERE " +
            "LOWER(mt.typePcaus) = LOWER(:type) OR LOWER(mt.typePcafr) = LOWER(:type)")
    Optional<MagicType> findByTypeUsOrFr(@Param("type") String type);

    // Tous les types pour une interface d'administration
    @Query("SELECT mt FROM MagicType mt ORDER BY mt.type ASC")
    List<MagicType> findAllOrderByType();

    // Vérifier si un type existe
    boolean existsByType(String type);
    boolean existsByTypeIgnoreCase(String type);

    // Recherche partielle pour suggestions
    @Query("SELECT mt FROM MagicType mt WHERE " +
            "LOWER(mt.type) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(mt.typePcaus) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(mt.typePcafr) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<MagicType> findByTypeContaining(@Param("query") String query);

    // Compter les extensions par type
    @Query("SELECT mt.type, COUNT(ms) FROM MagicType mt " +
            "LEFT JOIN MagicSet ms ON ms.typeMagic = mt " +
            "GROUP BY mt.type ORDER BY COUNT(ms) DESC")
    List<Object[]> countSetsByType();
}

/*
=== SCRIPTS SQL POUR INITIALISER LES DONNÉES ===

-- 1. CRÉATION DES TYPES MAGIC STANDARDS

INSERT INTO magic_type (id, id_pca, type, type_pcafr, type_pcaus, sous_type_pcafr, sous_type_pcaus) 
VALUES 
-- Types principaux
(UUID(), 1, 'expansion', 'Extension', 'Expansion', '', ''),
(UUID(), 2, 'core', 'Edition de base', 'Core Set', '', ''),
(UUID(), 3, 'commander', 'Commander', 'Commander', '', ''),
(UUID(), 4, 'draft_innovation', 'Innovation Draft', 'Draft Innovation', '', ''),
(UUID(), 5, 'reprint', 'Réimpression', 'Reprint', '', ''),
(UUID(), 6, 'masters', 'Masters', 'Masters', '', ''),
(UUID(), 7, 'duel_deck', 'Deck Duel', 'Duel Deck', '', ''),
(UUID(), 8, 'premium_deck', 'Deck Premium', 'Premium Deck', '', ''),
(UUID(), 9, 'from_the_vault', 'From the Vault', 'From the Vault', '', ''),
(UUID(), 10, 'spellbook', 'Grimoire', 'Spellbook', '', ''),
(UUID(), 11, 'conspiracy', 'Conspiracy', 'Conspiracy', '', ''),
(UUID(), 12, 'planechase', 'Planechase', 'Planechase', '', ''),
(UUID(), 13, 'archenemy', 'Archenemy', 'Archenemy', '', ''),
(UUID(), 14, 'vanguard', 'Vanguard', 'Vanguard', '', ''),
(UUID(), 15, 'funny', 'Humoristique', 'Un-Set', '', ''),
(UUID(), 16, 'promo', 'Promotionnel', 'Promo', '', ''),
(UUID(), 17, 'token', 'Jeton', 'Token', '', ''),
(UUID(), 18, 'memorabilia', 'Collector', 'Memorabilia', '', ''),
(UUID(), 19, 'box', 'Coffret', 'Box Set', '', ''),
(UUID(), 20, 'starter', 'Starter', 'Starter', '', ''),
(UUID(), 21, 'arsenal', 'Arsenal', 'Arsenal', '', ''),
(UUID(), 22, 'treasure_chest', 'Coffre au Trésor', 'Treasure Chest', '', ''),
(UUID(), 23, 'masterpiece', 'Chef-d\'œuvre', 'Masterpiece', '', '');

-- 2. VERIFICATION DES DONNÉES

SELECT 
    type, 
    type_pcafr, 
    type_pcaus,
    (SELECT COUNT(*) FROM magic_set ms WHERE ms.type_magic_id = mt.id) as nb_sets
FROM magic_type mt 
ORDER BY type;

-- 3. SCRIPT DE MIGRATION DES DONNÉES EXISTANTES (si nécessaire)

-- Mettre à jour les MagicSet existants pour pointer vers les bons MagicType
UPDATE magic_set ms 
SET type_magic_id = (
    SELECT mt.id 
    FROM magic_type mt 
    WHERE mt.type = 'expansion'  -- type par défaut
    LIMIT 1
)
WHERE ms.type_magic_id IS NULL;

-- Optionnel : Mise à jour plus précise selon les données existantes
-- UPDATE magic_set ms 
-- SET type_magic_id = (
--     SELECT mt.id 
--     FROM magic_type mt 
--     WHERE mt.type = CASE 
--         WHEN ms.ancien_type_field = 'core' THEN 'core'
--         WHEN ms.ancien_type_field = 'commander' THEN 'commander'
--         ELSE 'expansion'
--     END
--     LIMIT 1
-- );

-- 4. REQUÊTES DE VALIDATION

-- Vérifier que tous les MagicSet ont un type valide
SELECT COUNT(*) as sets_sans_type 
FROM magic_set ms 
WHERE ms.type_magic_id IS NULL;

-- Vérifier la répartition des types
SELECT 
    mt.type_pcafr as type_francais,
    mt.type as type_code,
    COUNT(ms.id) as nombre_extensions
FROM magic_type mt
LEFT JOIN magic_set ms ON ms.type_magic_id = mt.id
GROUP BY mt.id, mt.type, mt.type_pcafr
ORDER BY COUNT(ms.id) DESC;

-- 5. DONNÉES DE TEST POUR FINAL FANTASY

INSERT INTO magic_set (
    id, code, type_magic_id, nb_cartes, certifiable, FR, US, has_date_sortie_fr
) VALUES (
    UUID(), 
    'FIN', 
    (SELECT id FROM magic_type WHERE type = 'expansion' LIMIT 1),
    0,
    false,
    false,
    true,
    false
);

-- Ajouter une translation pour Final Fantasy
INSERT INTO card_set_translation (
    id, translatable_id, name, label_name, available, release_date, locale
) VALUES (
    UUID(),
    (SELECT id FROM magic_set WHERE code = 'FIN' LIMIT 1),
    'Magic: The Gathering - FINAL FANTASY',
    'Magic: The Gathering - FINAL FANTASY',
    true,
    '2025-06-13 00:00:00',
    'us'
);
*/