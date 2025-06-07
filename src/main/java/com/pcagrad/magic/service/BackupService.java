package com.pcagrad.magic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.FileOutputStream;

@Service
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);

    @Value("${mtg.images.storage-path:./data/images}")
    private String storageBasePath;

    @Value("${mtg.backup.path:./data/backups}")
    private String backupBasePath;

    /**
     * Crée une sauvegarde des images
     */
    @Async
    public CompletableFuture<BackupResult> createImageBackup() {
        logger.info("🔄 Début de la sauvegarde des images...");

        try {
            // Créer le dossier de sauvegarde s'il n'existe pas
            Path backupDir = Paths.get(backupBasePath);
            Files.createDirectories(backupDir);

            // Nom du fichier de sauvegarde avec timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String backupFileName = String.format("mtg-images-backup_%s.zip", timestamp);
            Path backupFilePath = backupDir.resolve(backupFileName);

            // Compter les fichiers à sauvegarder
            Path sourceDir = Paths.get(storageBasePath);
            if (!Files.exists(sourceDir)) {
                logger.warn("⚠️ Répertoire source n'existe pas : {}", sourceDir);
                return CompletableFuture.completedFuture(
                        new BackupResult(false, "Répertoire source introuvable", 0, 0, null)
                );
            }

            long totalFiles = countImageFiles(sourceDir);
            if (totalFiles == 0) {
                logger.warn("⚠️ Aucune image à sauvegarder");
                return CompletableFuture.completedFuture(
                        new BackupResult(false, "Aucune image à sauvegarder", 0, 0, null)
                );
            }

            // Créer l'archive ZIP
            long archivedFiles = createZipArchive(sourceDir, backupFilePath);
            long backupSize = Files.size(backupFilePath);

            logger.info("✅ Sauvegarde terminée : {} fichiers archivés dans {}",
                    archivedFiles, backupFilePath.getFileName());

            return CompletableFuture.completedFuture(
                    new BackupResult(true, "Sauvegarde créée avec succès",
                            archivedFiles, backupSize, backupFilePath.toString())
            );

        } catch (IOException e) {
            logger.error("❌ Erreur lors de la sauvegarde : {}", e.getMessage());
            return CompletableFuture.completedFuture(
                    new BackupResult(false, "Erreur : " + e.getMessage(), 0, 0, null)
            );
        }
    }

    /**
     * Compte le nombre de fichiers images
     */
    private long countImageFiles(Path sourceDir) throws IOException {
        if (!Files.exists(sourceDir)) {
            return 0;
        }

        try (Stream<Path> paths = Files.walk(sourceDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                                fileName.endsWith(".png") || fileName.endsWith(".gif");
                    })
                    .count();
        }
    }

    /**
     * Crée une archive ZIP des images
     */
    private long createZipArchive(Path sourceDir, Path zipFilePath) throws IOException {
        long archivedFiles = 0;

        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()));
             Stream<Path> paths = Files.walk(sourceDir)) {

            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(p -> isImageFile(p))
                    .toList()) {

                // Chemin relatif pour l'entrée ZIP
                Path relativePath = sourceDir.relativize(path);
                ZipEntry zipEntry = new ZipEntry(relativePath.toString());
                zipOut.putNextEntry(zipEntry);

                // Copier le fichier dans l'archive
                Files.copy(path, zipOut);
                zipOut.closeEntry();
                archivedFiles++;

                if (archivedFiles % 100 == 0) {
                    logger.info("📦 {} fichiers archivés...", archivedFiles);
                }
            }
        }

        return archivedFiles;
    }

    /**
     * Vérifie si un fichier est une image
     */
    private boolean isImageFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                fileName.endsWith(".png") || fileName.endsWith(".gif");
    }

    /**
     * Liste les sauvegardes existantes
     */
    public CompletableFuture<BackupListResult> listBackups() {
        try {
            Path backupDir = Paths.get(backupBasePath);
            if (!Files.exists(backupDir)) {
                return CompletableFuture.completedFuture(
                        new BackupListResult(true, "Aucune sauvegarde trouvée", java.util.Collections.emptyList())
                );
            }

            try (Stream<Path> paths = Files.list(backupDir)) {
                var backups = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".zip"))
                        .map(path -> {
                            try {
                                return new BackupInfo(
                                        path.getFileName().toString(),
                                        Files.size(path),
                                        Files.getLastModifiedTime(path).toString(),
                                        path.toString()
                                );
                            } catch (IOException e) {
                                logger.warn("⚠️ Erreur lecture backup : {}", e.getMessage());
                                return null;
                            }
                        })
                        .filter(java.util.Objects::nonNull)
                        .sorted((a, b) -> b.lastModified().compareTo(a.lastModified()))
                        .toList();

                return CompletableFuture.completedFuture(
                        new BackupListResult(true, "Sauvegardes trouvées", backups)
                );
            }

        } catch (IOException e) {
            logger.error("❌ Erreur lors de la liste des sauvegardes : {}", e.getMessage());
            return CompletableFuture.completedFuture(
                    new BackupListResult(false, "Erreur : " + e.getMessage(), java.util.Collections.emptyList())
            );
        }
    }

    /**
     * Supprime une sauvegarde
     */
    public boolean deleteBackup(String backupFileName) {
        try {
            Path backupFile = Paths.get(backupBasePath, backupFileName);
            if (Files.exists(backupFile) && backupFileName.endsWith(".zip")) {
                Files.delete(backupFile);
                logger.info("🗑️ Sauvegarde supprimée : {}", backupFileName);
                return true;
            }
            return false;
        } catch (IOException e) {
            logger.error("❌ Erreur suppression backup : {}", e.getMessage());
            return false;
        }
    }

    // Classes pour les résultats
    public record BackupResult(
            boolean success,
            String message,
            long filesCount,
            long sizeBytes,
            String filePath
    ) {}

    public record BackupListResult(
            boolean success,
            String message,
            java.util.List<BackupInfo> backups
    ) {}

    public record BackupInfo(
            String fileName,
            long sizeBytes,
            String lastModified,
            String filePath
    ) {}
}