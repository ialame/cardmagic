package com.pcagrad.magic.service;

import com.pcagrad.magic.entity.CardEntity;
import com.pcagrad.magic.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@Service
public class ImageDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(ImageDownloadService.class);

    @Autowired
    private CardRepository cardRepository;

    private final WebClient webClient;
    private final Semaphore downloadSemaphore;

    @Value("${mtg.images.storage-path:./data/images}")
    private String storageBasePath;

    @Value("${mtg.images.download-enabled:true}")
    private boolean downloadEnabled;

    @Value("${mtg.images.max-download-threads:5}")
    private int maxDownloadThreads;

    public ImageDownloadService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.downloadSemaphore = new Semaphore(5); // Sera mis à jour dans @PostConstruct
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        // Créer le dossier de stockage s'il n'existe pas
        try {
            Path storagePath = Paths.get(storageBasePath);
            Files.createDirectories(storagePath);
            logger.info("📁 Dossier de stockage d'images configuré : {}", storagePath.toAbsolutePath());
        } catch (IOException e) {
            logger.error("❌ Impossible de créer le dossier de stockage : {}", e.getMessage());
        }

        // Réinitialiser le semaphore avec la bonne limite
        downloadSemaphore.drainPermits();
        downloadSemaphore.release(maxDownloadThreads);
    }

    /**
     * Télécharge une image pour une carte donnée
     */
    @Async
    public CompletableFuture<Boolean> downloadCardImage(CardEntity card) {
        if (!downloadEnabled) {
            logger.debug("🔒 Téléchargement d'images désactivé");
            return CompletableFuture.completedFuture(false);
        }

        if (card.getOriginalImageUrl() == null || card.getOriginalImageUrl().isEmpty()) {
            logger.debug("⚠️ Pas d'URL d'image pour la carte {}", card.getName());
            return CompletableFuture.completedFuture(false);
        }

        if (card.getImageDownloaded() != null && card.getImageDownloaded()) {
            logger.debug("✅ Image déjà téléchargée pour {}", card.getName());
            return CompletableFuture.completedFuture(true);
        }

        try {
            downloadSemaphore.acquire();
            logger.info("⬇️ Téléchargement de l'image pour : {} ({})", card.getName(), card.getSetCode());

            return downloadImageFromUrl(card.getOriginalImageUrl(), card)
                    .toFuture()
                    .thenApply(success -> {
                        if (success) {
                            card.setImageDownloaded(true);
                            cardRepository.save(card);
                            logger.info("✅ Image téléchargée avec succès : {}", card.getName());
                        } else {
                            logger.warn("❌ Échec du téléchargement pour : {}", card.getName());
                        }
                        return success;
                    })
                    .whenComplete((result, throwable) -> {
                        downloadSemaphore.release();
                        if (throwable != null) {
                            logger.error("❌ Erreur lors du téléchargement pour {} : {}",
                                    card.getName(), throwable.getMessage());
                        }
                    });

        } catch (InterruptedException e) {
            logger.error("❌ Téléchargement interrompu pour {} : {}", card.getName(), e.getMessage());
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Télécharge les images pour toutes les cartes d'une extension
     */
    @Async
    public CompletableFuture<Integer> downloadImagesForSet(String setCode) {
        logger.info("🎯 Début du téléchargement des images pour l'extension : {}", setCode);

        List<CardEntity> cards = cardRepository.findBySetCodeOrderByNameAsc(setCode);
        List<CardEntity> cardsToDownload = cards.stream()
                .filter(card -> card.getImageDownloaded() == null || !card.getImageDownloaded())
                .filter(card -> card.getOriginalImageUrl() != null && !card.getOriginalImageUrl().isEmpty())
                .toList();

        logger.info("📊 {} cartes à télécharger sur {} total pour {}",
                cardsToDownload.size(), cards.size(), setCode);

        if (cardsToDownload.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        // Télécharger les images en parallèle
        List<CompletableFuture<Boolean>> downloadFutures = cardsToDownload.stream()
                .map(this::downloadCardImage)
                .toList();

        return CompletableFuture.allOf(downloadFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    int successCount = (int) downloadFutures.stream()
                            .mapToInt(future -> future.join() ? 1 : 0)
                            .sum();

                    logger.info("🎉 Téléchargement terminé pour {} : {}/{} images téléchargées",
                            setCode, successCount, cardsToDownload.size());

                    return successCount;
                });
    }

    /**
     * Télécharge une image depuis une URL
     */
    private Mono<Boolean> downloadImageFromUrl(String imageUrl, CardEntity card) {
        return webClient.get()
                .uri(imageUrl)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(30))
                .map(imageBytes -> {
                    try {
                        String fileName = generateFileName(card);
                        Path filePath = saveImageToFile(imageBytes, fileName);

                        card.setLocalImagePath(filePath.toString());
                        return true;

                    } catch (IOException e) {
                        logger.error("❌ Erreur lors de la sauvegarde de l'image pour {} : {}",
                                card.getName(), e.getMessage());
                        return false;
                    }
                })
                .onErrorReturn(false);
    }

    /**
     * Génère un nom de fichier unique pour une carte
     */
    private String generateFileName(CardEntity card) {
        String safeName = card.getName().replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");
        String extension = ".jpg";

        // Format: SETCODE_CARDNUMBER_SAFENAME.jpg
        if (card.getNumber() != null && !card.getNumber().isEmpty()) {
            return String.format("%s_%s_%s%s",
                    card.getSetCode(), card.getNumber(), safeName, extension);
        } else {
            return String.format("%s_%s_%s%s",
                    card.getSetCode(), card.getId(), safeName, extension);
        }
    }

    /**
     * Sauvegarde les bytes d'image dans un fichier
     */
    private Path saveImageToFile(byte[] imageBytes, String fileName) throws IOException {
        Path setDirectory = Paths.get(storageBasePath);
        Files.createDirectories(setDirectory);

        Path filePath = setDirectory.resolve(fileName);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(imageBytes);
        }

        return filePath;
    }

    /**
     * Récupère une image locale
     */
    public Resource loadImageAsResource(String imagePath) throws MalformedURLException {
        Path file = Paths.get(imagePath);
        Resource resource = new UrlResource(file.toUri());

        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("Image non trouvée : " + imagePath);
        }
    }

    /**
     * Vérifie si une image existe localement
     */
    public boolean imageExists(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            return false;
        }

        Path file = Paths.get(imagePath);
        return Files.exists(file) && Files.isReadable(file);
    }

    /**
     * Supprime une image locale
     */
    public boolean deleteImage(String imagePath) {
        try {
            if (imagePath != null && !imagePath.isEmpty()) {
                Path file = Paths.get(imagePath);
                return Files.deleteIfExists(file);
            }
            return false;
        } catch (IOException e) {
            logger.error("❌ Erreur lors de la suppression de l'image {} : {}", imagePath, e.getMessage());
            return false;
        }
    }

    /**
     * Statistiques du téléchargement
     */
    public ImageDownloadStats getDownloadStats() {
        long totalCards = cardRepository.count();
        long downloadedCards = cardRepository.findByImageDownloadedTrueAndLocalImagePathIsNotNull().size();
        long pendingCards = cardRepository.findByImageDownloadedFalseOrderByCreatedAtAsc().size();

        return new ImageDownloadStats(totalCards, downloadedCards, pendingCards);
    }

    /**
     * Classe pour les statistiques
     */
    public static class ImageDownloadStats {
        private final long totalCards;
        private final long downloadedCards;
        private final long pendingCards;

        public ImageDownloadStats(long totalCards, long downloadedCards, long pendingCards) {
            this.totalCards = totalCards;
            this.downloadedCards = downloadedCards;
            this.pendingCards = pendingCards;
        }

        public long getTotalCards() { return totalCards; }
        public long getDownloadedCards() { return downloadedCards; }
        public long getPendingCards() { return pendingCards; }
        public double getDownloadPercentage() {
            return totalCards > 0 ? (double) downloadedCards / totalCards * 100 : 0;
        }
    }
}