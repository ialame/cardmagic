package com.pcagrad.magic.controller;

import com.pcagrad.magic.entity.CardEntity;
import com.pcagrad.magic.repository.CardRepository;
import com.pcagrad.magic.service.ImageDownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pcagrad.magic.service.BackupService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import java.util.Optional;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:8080"})
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    @Autowired
    private ImageDownloadService imageDownloadService;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private BackupService backupService;

    /**
     * Sert une image de carte par son ID
     */
    @GetMapping("/{cardId}")
    public ResponseEntity<Resource> getCardImage(@PathVariable UUID cardId) {
        try {
            Optional<CardEntity> cardOpt = cardRepository.findById(cardId);
            if (cardOpt.isEmpty()) {
                logger.warn("⚠️ Carte non trouvée : {}", cardId);
                return ResponseEntity.notFound().build();
            }

            CardEntity card = cardOpt.get();

            // Si l'image est téléchargée localement
            if (card.getImageDownloaded() != null && card.getImageDownloaded()
                    && card.getLocalImagePath() != null
                    && imageDownloadService.imageExists(card.getLocalImagePath())) {

                Resource resource = imageDownloadService.loadImageAsResource(card.getLocalImagePath());

                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000") // Cache 1 an
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + card.getName() + ".jpg\"")
                        .body(resource);
            }

            // Si pas d'image locale, rediriger vers l'URL originale
            if (card.getOriginalImageUrl() != null && !card.getOriginalImageUrl().isEmpty()) {
                logger.debug("🔗 Redirection vers l'image externe pour : {}", card.getName());
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, card.getOriginalImageUrl())
                        .build();
            }

            // Aucune image disponible
            logger.warn("❌ Aucune image disponible pour : {}", card.getName());
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la récupération de l'image pour {} : {}", cardId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Déclenche le téléchargement d'une image spécifique
     */
    @PostMapping("/{cardId}/download")
    public ResponseEntity<String> downloadCardImage(@PathVariable UUID cardId) {
        try {
            Optional<CardEntity> cardOpt = cardRepository.findById(cardId);
            if (cardOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            CardEntity card = cardOpt.get();

            if (card.getImageDownloaded() != null && card.getImageDownloaded()) {
                return ResponseEntity.ok("Image déjà téléchargée");
            }

            // Déclencher le téléchargement asynchrone
            imageDownloadService.downloadCardImage(card);

            return ResponseEntity.accepted().body("Téléchargement démarré pour : " + card.getName());

        } catch (Exception e) {
            logger.error("❌ Erreur lors du déclenchement du téléchargement pour {} : {}", cardId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du téléchargement");
        }
    }

    /**
     * Déclenche le téléchargement de toutes les images d'une extension
     */
    @PostMapping("/download-set/{setCode}")
    public ResponseEntity<String> downloadSetImages(@PathVariable String setCode) {
        try {
            logger.info("🎯 Déclenchement du téléchargement pour l'extension : {}", setCode);

            // Déclencher le téléchargement asynchrone
            imageDownloadService.downloadImagesForSet(setCode)
                    .thenAccept(count ->
                            logger.info("✅ Téléchargement terminé pour {} : {} images", setCode, count)
                    );

            return ResponseEntity.accepted()
                    .body("Téléchargement démarré pour l'extension : " + setCode);

        } catch (Exception e) {
            logger.error("❌ Erreur lors du déclenchement du téléchargement pour l'extension {} : {}",
                    setCode, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du téléchargement de l'extension");
        }
    }

    /**
     * Statistiques des téléchargements
     */
    @GetMapping("/stats")
    public ResponseEntity<ImageDownloadService.ImageDownloadStats> getDownloadStats() {
        try {
            ImageDownloadService.ImageDownloadStats stats = imageDownloadService.getDownloadStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("❌ Erreur lors de la récupération des statistiques : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Nettoyage des images orphelines
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<String> cleanupOrphanedImages() {
        try {
            // TODO: Implémenter le nettoyage des images orphelines
            return ResponseEntity.ok("Nettoyage en cours...");
        } catch (Exception e) {
            logger.error("❌ Erreur lors du nettoyage : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du nettoyage");
        }
    }

    /**
     * Crée une sauvegarde des images
     */
    @PostMapping("/backup")
    public ResponseEntity<String> createBackup() {
        try {
            logger.info("🔄 Déclenchement de la sauvegarde des images");

            // Lancer la sauvegarde en arrière-plan
            CompletableFuture<BackupService.BackupResult> future = backupService.createImageBackup();

            future.thenAccept(result -> {
                if (result.success()) {
                    logger.info("✅ Sauvegarde terminée : {} fichiers, {} bytes",
                            result.filesCount(), result.sizeBytes());
                } else {
                    logger.error("❌ Échec sauvegarde : {}", result.message());
                }
            });

            return ResponseEntity.accepted()
                    .body("Sauvegarde des images démarrée");

        } catch (Exception e) {
            logger.error("❌ Erreur lors du déclenchement de la sauvegarde : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la sauvegarde");
        }
    }

    /**
     * Liste les sauvegardes disponibles
     */
    @GetMapping("/backups")
    public CompletableFuture<ResponseEntity<BackupService.BackupListResult>> listBackups() {
        return backupService.listBackups()
                .thenApply(result -> ResponseEntity.ok(result))
                .exceptionally(throwable -> {
                    logger.error("❌ Erreur liste backups : {}", throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new BackupService.BackupListResult(false, "Erreur serveur",
                                    java.util.Collections.emptyList()));
                });
    }

    /**
     * Supprime une sauvegarde
     */
    @DeleteMapping("/backups/{fileName}")
    public ResponseEntity<String> deleteBackup(@PathVariable String fileName) {
        try {
            boolean deleted = backupService.deleteBackup(fileName);
            if (deleted) {
                return ResponseEntity.ok("Sauvegarde supprimée : " + fileName);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("❌ Erreur suppression backup : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la suppression");
        }
    }



}