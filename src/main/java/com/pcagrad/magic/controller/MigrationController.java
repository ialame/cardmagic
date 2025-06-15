package com.pcagrad.magic.controller;

import com.pcagrad.magic.dto.ApiResponse;
import com.pcagrad.magic.entity.MagicType;
import com.pcagrad.magic.repository.MagicTypeRepository;
import com.pcagrad.magic.service.DataMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/migration")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:8080"})
public class MigrationController {

    private static final Logger logger = LoggerFactory.getLogger(MigrationController.class);

    @Autowired
    private DataMigrationService migrationService;

    @Autowired
    private MagicTypeRepository magicTypeRepository;

    /**
     * Obtient le rapport de migration
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Object>> getMigrationStatus() {
        try {
            DataMigrationService.MigrationReport report = migrationService.getMigrationReport();

            Map<String, Object> status = new HashMap<>();
            status.put("totalSets", report.getTotalSets());
            status.put("totalTypes", report.getTotalTypes());
            status.put("setsWithoutType", report.getSetsWithoutType());
            status.put("setsWithoutTranslation", report.getSetsWithoutTranslation());
            status.put("finalFantasyExists", report.isFinalFantasyExists());
            status.put("migrationComplete", report.isMigrationComplete());
            status.put("migrationProgress", Math.round(report.getMigrationProgress() * 100.0) / 100.0);

            // Déterminer le statut
            String statusText;
            if (report.isMigrationComplete()) {
                statusText = "✅ Migration complète";
            } else if (report.getMigrationProgress() > 80) {
                statusText = "🔶 Migration presque terminée";
            } else if (report.getMigrationProgress() > 50) {
                statusText = "🔄 Migration en cours";
            } else {
                statusText = "⚠️ Migration nécessaire";
            }

            status.put("statusText", statusText);

            // Recommandations
            Map<String, Object> recommendations = new HashMap<>();
            if (report.getSetsWithoutType() > 0) {
                recommendations.put("fixTypes", "Corriger " + report.getSetsWithoutType() + " extensions sans type");
            }
            if (report.getSetsWithoutTranslation() > 0) {
                recommendations.put("fixTranslations", "Corriger " + report.getSetsWithoutTranslation() + " extensions sans translation");
            }
            if (!report.isFinalFantasyExists()) {
                recommendations.put("createFin", "Créer l'extension Final Fantasy");
            }
            if (report.isMigrationComplete()) {
                recommendations.put("status", "Migration terminée avec succès ! 🎉");
            }

            status.put("recommendations", recommendations);

            return ResponseEntity.ok(ApiResponse.success(status, "Rapport de migration"));

        } catch (Exception e) {
            logger.error("❌ Erreur rapport migration : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Initialise les types Magic
     */
    @PostMapping("/init-types")
    public ResponseEntity<ApiResponse<String>> initializeTypes() {
        try {
            logger.info("🎯 Initialisation manuelle des types Magic");

            long beforeCount = magicTypeRepository.count();
            migrationService.initializeMagicTypes();
            long afterCount = magicTypeRepository.count();

            long created = afterCount - beforeCount;

            String message = String.format("Types Magic initialisés : %d créés (%d total)",
                    created, afterCount);

            return ResponseEntity.ok(ApiResponse.success(message));

        } catch (Exception e) {
            logger.error("❌ Erreur initialisation types : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Migre toutes les extensions
     */
    @PostMapping("/migrate-sets")
    public ResponseEntity<ApiResponse<String>> migrateSets() {
        try {
            logger.info("📦 Migration manuelle des extensions");

            migrationService.migrateExistingSets();

            DataMigrationService.MigrationReport report = migrationService.getMigrationReport();
            String message = String.format("Migration terminée - Progrès : %.1f%% (%d/%d extensions)",
                    report.getMigrationProgress(),
                    report.getTotalSets() - report.getSetsWithoutType() - report.getSetsWithoutTranslation(),
                    report.getTotalSets());

            return ResponseEntity.ok(ApiResponse.success(message));

        } catch (Exception e) {
            logger.error("❌ Erreur migration extensions : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Migre une extension spécifique
     */
    @PostMapping("/migrate-set/{setCode}")
    public ResponseEntity<ApiResponse<String>> migrateSet(@PathVariable String setCode) {
        try {
            logger.info("🔧 Migration manuelle de l'extension : {}", setCode);

            boolean migrated = migrationService.forceMigrationForSet(setCode);

            String message = migrated ?
                    "Extension " + setCode + " migrée avec succès" :
                    "Extension " + setCode + " : aucune migration nécessaire";

            return ResponseEntity.ok(ApiResponse.success(message));

        } catch (Exception e) {
            logger.error("❌ Erreur migration extension {} : {}", setCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Crée Final Fantasy si nécessaire
     */
    @PostMapping("/ensure-final-fantasy")
    public ResponseEntity<ApiResponse<String>> ensureFinalFantasy() {
        try {
            logger.info("🎮 Création/vérification Final Fantasy");

            migrationService.ensureFinalFantasyExists();

            return ResponseEntity.ok(ApiResponse.success("Extension Final Fantasy assurée"));

        } catch (Exception e) {
            logger.error("❌ Erreur création Final Fantasy : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Validation complète de la migration
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Object>> validateMigration() {
        try {
            logger.info("🔍 Validation complète de la migration");

            migrationService.validateMigration();

            DataMigrationService.MigrationReport report = migrationService.getMigrationReport();

            Map<String, Object> validation = new HashMap<>();
            validation.put("migrationComplete", report.isMigrationComplete());
            validation.put("progress", report.getMigrationProgress());
            validation.put("issues", new HashMap<String, Object>() {{
                put("setsWithoutType", report.getSetsWithoutType());
                put("setsWithoutTranslation", report.getSetsWithoutTranslation());
                put("finalFantasyMissing", !report.isFinalFantasyExists());
            }});

            String status = report.isMigrationComplete() ?
                    "✅ Migration validée avec succès" :
                    "⚠️ Migration incomplète - corrections nécessaires";

            return ResponseEntity.ok(ApiResponse.success(validation, status));

        } catch (Exception e) {
            logger.error("❌ Erreur validation migration : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Migration complète en une seule opération
     */
    @PostMapping("/full-migration")
    public ResponseEntity<ApiResponse<Object>> fullMigration() {
        try {
            logger.info("🚀 Migration complète en une opération");

            Map<String, Object> results = new HashMap<>();

            // 1. Initialiser les types
            long beforeTypes = magicTypeRepository.count();
            migrationService.initializeMagicTypes();
            long afterTypes = magicTypeRepository.count();
            results.put("typesCreated", afterTypes - beforeTypes);
            results.put("totalTypes", afterTypes);

            // 2. Migrer les extensions
            migrationService.migrateExistingSets();

            // 3. S'assurer que Final Fantasy existe
            migrationService.ensureFinalFantasyExists();

            // 4. Valider
            migrationService.validateMigration();

            // 5. Rapport final
            DataMigrationService.MigrationReport report = migrationService.getMigrationReport();
            results.put("finalReport", Map.of(
                    "totalSets", report.getTotalSets(),
                    "migrationProgress", report.getMigrationProgress(),
                    "complete", report.isMigrationComplete(),
                    "finalFantasyExists", report.isFinalFantasyExists()
            ));

            String message = report.isMigrationComplete() ?
                    "🎉 Migration complète réussie !" :
                    "⚠️ Migration partiellement réussie";

            return ResponseEntity.ok(ApiResponse.success(results, message));

        } catch (Exception e) {
            logger.error("❌ Erreur migration complète : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur migration complète : " + e.getMessage()));
        }
    }

    /**
     * Liste tous les types Magic
     */
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<Object>> listMagicTypes() {
        try {
            List<MagicType> types = magicTypeRepository.findAllOrderByType();

            List<Map<String, Object>> typesList = types.stream()
                    .map(type -> {
                        Map<String, Object> typeInfo = new HashMap<>();
                        typeInfo.put("code", type.getType());
                        typeInfo.put("frenchName", type.getTypePcafr());
                        typeInfo.put("englishName", type.getTypePcaus());
                        typeInfo.put("id", type.getId());
                        return typeInfo;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("types", typesList);
            response.put("count", types.size());

            return ResponseEntity.ok(ApiResponse.success(response, "Types Magic disponibles"));

        } catch (Exception e) {
            logger.error("❌ Erreur liste types : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Statistiques des types par usage
     */
    @GetMapping("/types/stats")
    public ResponseEntity<ApiResponse<Object>> getTypeStats() {
        try {
            List<Object[]> stats = magicTypeRepository.countSetsByType();

            List<Map<String, Object>> typeStats = stats.stream()
                    .map(stat -> {
                        Map<String, Object> statInfo = new HashMap<>();
                        statInfo.put("type", stat[0]);
                        statInfo.put("setCount", stat[1]);
                        return statInfo;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("typeStats", typeStats);
            response.put("totalTypes", magicTypeRepository.count());

            return ResponseEntity.ok(ApiResponse.success(response, "Statistiques des types Magic"));

        } catch (Exception e) {
            logger.error("❌ Erreur stats types : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }

    /**
     * Endpoint de diagnostic complet
     */
    @GetMapping("/diagnostic")
    public ResponseEntity<ApiResponse<Object>> fullDiagnostic() {
        try {
            Map<String, Object> diagnostic = new HashMap<>();

            // Rapport de migration
            DataMigrationService.MigrationReport report = migrationService.getMigrationReport();
            diagnostic.put("migrationReport", Map.of(
                    "totalSets", report.getTotalSets(),
                    "totalTypes", report.getTotalTypes(),
                    "setsWithoutType", report.getSetsWithoutType(),
                    "setsWithoutTranslation", report.getSetsWithoutTranslation(),
                    "finalFantasyExists", report.isFinalFantasyExists(),
                    "migrationComplete", report.isMigrationComplete(),
                    "progress", report.getMigrationProgress()
            ));

            // Statistiques des types
            List<Object[]> typeStats = magicTypeRepository.countSetsByType();
            diagnostic.put("typeUsage", typeStats.stream()
                    .map(stat -> Map.of("type", stat[0], "count", stat[1]))
                    .collect(Collectors.toList()));

            // État de la base
            diagnostic.put("databaseStatus", Map.of(
                    "adapted", true,
                    "migrationAvailable", true,
                    "validationPassed", report.isMigrationComplete()
            ));

            // Recommandations
            Map<String, String> recommendations = new HashMap<>();
            if (!report.isMigrationComplete()) {
                recommendations.put("action", "Exécuter la migration complète");
                recommendations.put("endpoint", "POST /api/admin/migration/full-migration");
            } else {
                recommendations.put("status", "Base de données prête ✅");
            }
            diagnostic.put("recommendations", recommendations);

            return ResponseEntity.ok(ApiResponse.success(diagnostic, "Diagnostic complet de la migration"));

        } catch (Exception e) {
            logger.error("❌ Erreur diagnostic : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Erreur : " + e.getMessage()));
        }
    }
}