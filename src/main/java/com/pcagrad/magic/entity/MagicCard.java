package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "magic_card")
public class MagicCard extends Card {

    @Size(max = 20)
    @Column(name="id_prim", length = 20)
    private String idPrim;

    @Column(name = "numero")
    private Integer numero;

    @Size(max = 50)
    @Column(name = "fusion_pca", length = 50)
    private String fusionPca;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "has_img", nullable = false)
    private Boolean hasImg = false;

    @Column(name = "has_foil")
    private Boolean hasFoil;

    @Column(name = "has_non_foil")
    private Boolean hasNonFoil;

    @Column(name = "is_foil_only")
    private Boolean isFoilOnly;

    @Column(name = "is_online_only")
    private Boolean isOnlineOnly;

    @Column(name = "is_oversized")
    private Boolean isOversized;

    @Column(name = "is_timeshifted")
    private Boolean isTimeshifted;

    @Size(max = 100)
    @Column(name = "side", length = 100)
    private String side;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "certifiable", nullable = false)
    private Boolean certifiable = false;

    @Column(name = "is_token")
    private Boolean isToken;

    @Column(name = "filtre")
    private Integer filtre;

    @Column(name = "is_reclassee")
    private Boolean isReclassee;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "has_recherche", nullable = false)
    private Boolean hasRecherche = false;

    @Size(max = 50)
    @Column(name = "z_post_extension", length = 50)
    private String zPostExtension;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "has_date_fr", nullable = false)
    private Boolean hasDateFr = false;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "is_affichable", nullable = false)
    private Boolean isAffichable = false;

    // ADAPTATIONS POUR L'ANCIENNE API

    /**
     * Stockage de l'ID externe dans idPrim pour compatibilité
     */
    public String getExternalId() {
        return idPrim;
    }

    public void setExternalId(String externalId) {
        this.idPrim = externalId;
    }

    /**
     * Nom depuis la translation US
     */
    public String getName() {
        CardTranslation translation = getTranslation(Localization.USA);
        return translation != null ? translation.getName() : "Carte inconnue";
    }

    public void setName(String name) {
        ensureTranslationExists(Localization.USA);
        getTranslation(Localization.USA).setName(name);
    }

    /**
     * Numero de carte mappé sur number field
     */
    public String getNumber() {
        return numero != null ? numero.toString() : null;
    }

    public void setNumber(String number) {
        try {
            this.numero = number != null ? Integer.parseInt(number.replaceAll("\\D", "")) : null;
        } catch (NumberFormatException e) {
            this.numero = null;
        }
    }

    /**
     * Images - mapping vers has_img
     */
    public Boolean getImageDownloaded() {
        return hasImg;
    }

    public void setImageDownloaded(Boolean downloaded) {
        this.hasImg = downloaded != null ? downloaded : false;
    }

    public String getLocalImagePath() {
        // Pas de champ direct, construire depuis fusion_pca ou autre
        return hasImg ? generateImagePath() : null;
    }

    public void setLocalImagePath(String path) {
        // Stocker dans fusion_pca en attendant
        this.fusionPca = path;
        this.hasImg = (path != null && !path.isEmpty());
    }

    /**
     * URL d'image originale - stockée dans attributes JSON
     */
    public String getOriginalImageUrl() {
        return extractFromAttributes("originalImageUrl");
    }

    public void setOriginalImageUrl(String url) {
        updateAttributes("originalImageUrl", url);
    }

    /**
     * Propriétés MTG stockées dans attributes JSON
     */
    public String getManaCost() {
        return extractFromAttributes("manaCost");
    }

    public void setManaCost(String manaCost) {
        updateAttributes("manaCost", manaCost);
    }

    public Integer getCmc() {
        String cmcStr = extractFromAttributes("cmc");
        try {
            return cmcStr != null ? Integer.parseInt(cmcStr) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setCmc(Integer cmc) {
        updateAttributes("cmc", cmc != null ? cmc.toString() : null);
    }

    public String getRarity() {
        return extractFromAttributes("rarity");
    }

    public void setRarity(String rarity) {
        updateAttributes("rarity", rarity);
    }

    public String getType() {
        return extractFromAttributes("type");
    }

    public void setType(String type) {
        updateAttributes("type", type);
    }

    public String getText() {
        return extractFromAttributes("text");
    }

    public void setText(String text) {
        updateAttributes("text", text);
    }

    public String getArtist() {
        return extractFromAttributes("artist");
    }

    public void setArtist(String artist) {
        updateAttributes("artist", artist);
    }

    public String getPower() {
        return extractFromAttributes("power");
    }

    public void setPower(String power) {
        updateAttributes("power", power);
    }

    public String getToughness() {
        return extractFromAttributes("toughness");
    }

    public void setToughness(String toughness) {
        updateAttributes("toughness", toughness);
    }

    public String getLayout() {
        return extractFromAttributes("layout");
    }

    public void setLayout(String layout) {
        updateAttributes("layout", layout);
    }

    public Integer getMultiverseid() {
        String idStr = extractFromAttributes("multiverseid");
        try {
            return idStr != null ? Integer.parseInt(idStr) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setMultiverseid(Integer multiverseid) {
        updateAttributes("multiverseid", multiverseid != null ? multiverseid.toString() : null);
    }

    /**
     * Code d'extension stocké dans z_post_extension
     */
    public String getSetCode() {
        return zPostExtension;
    }

    public void setSetCode(String setCode) {
        this.zPostExtension = setCode;
    }

    public String getSetName() {
        return extractFromAttributes("setName");
    }

    public void setSetName(String setName) {
        updateAttributes("setName", setName);
    }

    /**
     * Collections (colors, types, etc.) stockées dans allowedNotes JSON
     */
    public List<String> getColors() {
        return extractListFromAllowedNotes("colors");
    }

    public void setColors(List<String> colors) {
        updateAllowedNotes("colors", colors);
    }

    public List<String> getColorIdentity() {
        return extractListFromAllowedNotes("colorIdentity");
    }

    public void setColorIdentity(List<String> colorIdentity) {
        updateAllowedNotes("colorIdentity", colorIdentity);
    }

    public List<String> getSupertypes() {
        return extractListFromAllowedNotes("supertypes");
    }

    public void setSupertypes(List<String> supertypes) {
        updateAllowedNotes("supertypes", supertypes);
    }

    public List<String> getTypes() {
        return extractListFromAllowedNotes("types");
    }

    public void setTypes(List<String> types) {
        updateAllowedNotes("types", types);
    }

    public List<String> getSubtypes() {
        return extractListFromAllowedNotes("subtypes");
    }

    public void setSubtypes(List<String> subtypes) {
        updateAllowedNotes("subtypes", subtypes);
    }

    /**
     * Dates de création/modification simulées
     */
    public LocalDateTime getCreatedAt() {
        // Pas de champ direct, retourner une date par défaut
        return LocalDateTime.now().minusDays(30); // Simulation
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        // Ne peut pas être persisté
    }

    public LocalDateTime getUpdatedAt() {
        return LocalDateTime.now(); // Toujours maintenant
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        // Ne peut pas être persisté
    }

    // MÉTHODES UTILITAIRES PRIVÉES

    private void ensureTranslationExists(Localization localization) {
        if (getTranslation(localization) == null) {
            CardTranslation translation = new CardTranslation();
            translation.setLocalization(localization);
            translation.setAvailable(true);
            setTranslation(localization, translation);
        }
    }

    private String generateImagePath() {
        if (fusionPca != null && !fusionPca.isEmpty()) {
            return fusionPca;
        }
        // Générer un chemin basique
        return "/images/" + getId() + ".jpg";
    }

    private String extractFromAttributes(String key) {
        if (getAttributes() == null) return null;
        try {
            // Parse JSON simple pour extraire la valeur
            String attrs = getAttributes();
            String searchKey = "\"" + key + "\":\"";
            int startIndex = attrs.indexOf(searchKey);
            if (startIndex == -1) return null;

            startIndex += searchKey.length();
            int endIndex = attrs.indexOf("\"", startIndex);
            if (endIndex == -1) return null;

            return attrs.substring(startIndex, endIndex);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateAttributes(String key, String value) {
        if (getAttributes() == null) {
            setAttributes("{\"reverse\": 0, \"edition\": 1, \"shadowless\": 0}");
        }

        try {
            String attrs = getAttributes();
            String searchKey = "\"" + key + "\":\"";

            if (value == null) {
                // Supprimer la clé
                int startIndex = attrs.indexOf(searchKey);
                if (startIndex != -1) {
                    int endIndex = attrs.indexOf("\"", startIndex + searchKey.length());
                    if (endIndex != -1) {
                        String before = attrs.substring(0, startIndex);
                        String after = attrs.substring(endIndex + 1);
                        // Nettoyer les virgules en trop
                        if (before.endsWith(",")) before = before.substring(0, before.length() - 1);
                        if (after.startsWith(",")) after = after.substring(1);
                        setAttributes(before + after);
                    }
                }
            } else {
                // Ajouter ou remplacer la clé
                if (attrs.contains(searchKey)) {
                    // Remplacer
                    int startIndex = attrs.indexOf(searchKey) + searchKey.length();
                    int endIndex = attrs.indexOf("\"", startIndex);
                    String newAttrs = attrs.substring(0, startIndex) + value + attrs.substring(endIndex);
                    setAttributes(newAttrs);
                } else {
                    // Ajouter (avant la dernière accolade)
                    int lastBrace = attrs.lastIndexOf("}");
                    String newAttrs = attrs.substring(0, lastBrace) +
                            (attrs.length() > 2 ? ", " : "") +
                            "\"" + key + "\":\"" + value + "\"" +
                            attrs.substring(lastBrace);
                    setAttributes(newAttrs);
                }
            }
        } catch (Exception e) {
            // En cas d'erreur, remplacer complètement
            setAttributes("{\"" + key + "\":\"" + (value != null ? value : "") + "\"}");
        }
    }

    private List<String> extractListFromAllowedNotes(String key) {
        if (getAllowedNotes() == null) return new ArrayList<>();

        try {
            // Parse JSON basique pour extraire un array
            String notes = getAllowedNotes();
            String searchKey = "\"" + key + "\":[";
            int startIndex = notes.indexOf(searchKey);
            if (startIndex == -1) return new ArrayList<>();

            startIndex += searchKey.length();
            int endIndex = notes.indexOf("]", startIndex);
            if (endIndex == -1) return new ArrayList<>();

            String arrayContent = notes.substring(startIndex, endIndex);
            List<String> result = new ArrayList<>();

            // Split simple par virgules et nettoyer
            String[] items = arrayContent.split(",");
            for (String item : items) {
                String cleaned = item.trim().replace("\"", "");
                if (!cleaned.isEmpty()) {
                    result.add(cleaned);
                }
            }

            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void updateAllowedNotes(String key, List<String> values) {
        if (getAllowedNotes() == null) {
            setAllowedNotes("[]");
        }

        try {
            // Construire le JSON array
            StringBuilder arrayBuilder = new StringBuilder("[");
            if (values != null) {
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) arrayBuilder.append(",");
                    arrayBuilder.append("\"").append(values.get(i)).append("\"");
                }
            }
            arrayBuilder.append("]");

            String notes = getAllowedNotes();
            String searchKey = "\"" + key + "\":[";

            if (notes.contains(searchKey)) {
                // Remplacer
                int startIndex = notes.indexOf(searchKey);
                int endIndex = notes.indexOf("]", startIndex + searchKey.length()) + 1;
                String newNotes = notes.substring(0, startIndex) +
                        "\"" + key + "\":" + arrayBuilder.toString() +
                        notes.substring(endIndex);
                setAllowedNotes(newNotes);
            } else {
                // Ajouter
                if (notes.equals("[]")) {
                    setAllowedNotes("{\"" + key + "\":" + arrayBuilder.toString() + "}");
                } else {
                    int lastBrace = notes.lastIndexOf("}");
                    if (lastBrace == -1) lastBrace = notes.lastIndexOf("]");
                    String newNotes = notes.substring(0, lastBrace) +
                            (notes.length() > 2 ? ", " : "") +
                            "\"" + key + "\":" + arrayBuilder.toString() +
                            notes.substring(lastBrace);
                    setAllowedNotes(newNotes);
                }
            }
        } catch (Exception e) {
            // En cas d'erreur, créer un nouveau JSON
            setAllowedNotes("{\"" + key + "\":" + (values != null ? values.toString() : "[]") + "}");
        }
    }

    // CONSTRUCTEURS

    public MagicCard() {
        super();
        ensureTranslationExists(Localization.USA);
        if (getAttributes() == null) {
            setAttributes("{\"reverse\": 0, \"edition\": 1, \"shadowless\": 0}");
        }
        if (getAllowedNotes() == null) {
            setAllowedNotes("[]");
        }
    }

    public MagicCard(String externalId, String name, String setCode) {
        this();
        setExternalId(externalId);
        setName(name);
        setSetCode(setCode);
    }
}