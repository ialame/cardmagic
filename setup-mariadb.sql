-- Script de configuration MariaDB pour MTG Cards API
-- Exécutez ce script en tant qu'administrateur MariaDB

-- 1. Créer la base de données
CREATE DATABASE IF NOT EXISTS mtg_cards
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 2. Créer l'utilisateur dédié
CREATE USER IF NOT EXISTS 'mtg_user'@'localhost' IDENTIFIED BY 'mtg_password';
CREATE USER IF NOT EXISTS 'mtg_user'@'%' IDENTIFIED BY 'mtg_password';

-- 3. Accorder les privilèges
GRANT ALL PRIVILEGES ON mtg_cards.* TO 'mtg_user'@'localhost';
GRANT ALL PRIVILEGES ON mtg_cards.* TO 'mtg_user'@'%';

-- 4. Rafraîchir les privilèges
FLUSH PRIVILEGES;

-- 5. Utiliser la base de données
USE mtg_cards;

-- 6. Vérifier la configuration
SHOW VARIABLES LIKE 'character_set%';
SHOW VARIABLES LIKE 'collation%';

-- 7. Configuration recommandée pour les performances (optionnel)
-- Ajoutez ces lignes dans votre fichier my.cnf/my.ini :
/*
[mysqld]
# Encodage
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci

# Performance
innodb_buffer_pool_size = 1G
innodb_log_file_size = 256M
innodb_flush_log_at_trx_commit = 2
innodb_file_per_table = 1

# Connexions
max_connections = 100
max_connect_errors = 10000

# Cache des requêtes
query_cache_type = 1
query_cache_size = 128M
query_cache_limit = 4M
*/

-- 8. Index recommandés (seront créés automatiquement par JPA, mais listés ici pour référence)
/*
-- Sur la table des cartes
CREATE INDEX idx_cards_name ON cards(name);
CREATE INDEX idx_cards_set_code ON cards(set_code);
CREATE INDEX idx_cards_rarity ON cards(rarity);
CREATE INDEX idx_cards_type ON cards(type);
CREATE INDEX idx_cards_artist ON cards(artist);
CREATE INDEX idx_cards_image_downloaded ON cards(image_downloaded);
CREATE INDEX idx_cards_created_at ON cards(created_at);

-- Sur la table des extensions
CREATE UNIQUE INDEX idx_sets_code ON sets(code);
CREATE INDEX idx_sets_name ON sets(name);
CREATE INDEX idx_sets_type ON sets(type);
CREATE INDEX idx_sets_release_date ON sets(release_date);
CREATE INDEX idx_sets_cards_synced ON sets(cards_synced);
*/

-- 9. Vue pour les statistiques (optionnel)
CREATE OR REPLACE VIEW v_cards_stats AS
SELECT
    set_code,
    COUNT(*) as total_cards,
    COUNT(DISTINCT rarity) as distinct_rarities,
    COUNT(DISTINCT artist) as distinct_artists,
    SUM(CASE WHEN image_downloaded = 1 THEN 1 ELSE 0 END) as images_downloaded,
    MIN(created_at) as first_card_added,
    MAX(updated_at) as last_update
FROM cards
GROUP BY set_code;

-- 10. Afficher les informations de la base créée
SELECT
    'Base de données créée avec succès' as status,
    DATABASE() as current_database,
    USER() as current_user,
    VERSION() as mariadb_version;