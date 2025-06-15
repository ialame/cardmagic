package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "magic_set")
public class MagicSet extends CardSet {

    @Column(name = "id_pca")
    private Integer idPca;

    // Le code reste unique et sert de clé métier
    @Size(max = 100)
    @Column(name = "code", length = 100)
    private String code;

    @Size(max = 100)
    @Column(name = "tcgplayer_group_id", length = 100)
    private String tcgplayerGroupId;

    @Size(max = 400)
    @Column(name = "mtgo_code", length = 400)
    private String mtgoCode;

    @Column(name = "base_set_size")
    private Integer baseSetSize;

    @Size(max = 500)
    @Column(name = "boosterV3", length = 500)
    private String boosterV3;

    @Size(max = 100)
    @Column(name = "version", length = 100)
    private String version;

    @Size(max = 100)
    @Column(name = "block", length = 100)
    private String block;

    @Size(max = 400)
    @Column(name = "total_set_size", length = 400)
    private String totalSetSize;

    @Column(name = "nb_cartes")
    private Integer nbCartes;

    @Column(name = "nb_images")
    private Integer nbImages;

    @Size(max = 125)
    @Column(name = "nom_dossier", length = 125)
    private String nomDossier;

    @Size(max = 25)
    @Column(name = "num_sur", length = 25)
    private String numSur;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_magic_id", nullable = false)
    private MagicType typeMagic;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "certifiable", nullable = false)
    private Boolean certifiable = false;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "FR", nullable = false)
    private Boolean fr = false;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "US", nullable = false)
    private Boolean us = false;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "has_date_sortie_fr", nullable = false)
    private Boolean hasDateSortieFr = false;

    // NOUVELLES MÉTHODES pour adapter l'ancienne logique

    /**
     * Retourne le nom depuis les translations US par défaut
     */
    public String getName() {
        if (getTranslation(Localization.USA) != null) {
            return getTranslation(Localization.USA).getName();
        }
        return "Extension " + code; // Fallback
    }

    /**
     * Définit le nom dans la translation US
     */
    public void setName(String name) {
        ensureTranslationExists(Localization.USA);
        getTranslation(Localization.USA).setName(name);
    }

    /**
     * Retourne le type depuis typeMagic
     */
    public String getType() {
        return typeMagic != null ? typeMagic.getType() : "expansion";
    }

    /**
     * Simule setType en trouvant/créant le MagicType approprié
     */
    public void setType(String type) {
        // Cette méthode devra être complétée avec un service pour chercher/créer MagicType
        // Pour l'instant, on laisse vide car cela nécessite une injection de dépendance
    }

    /**
     * Retourne la date de release depuis la translation
     */
    public java.time.LocalDate getReleaseDate() {
        if (getTranslation(Localization.USA) != null &&
                getTranslation(Localization.USA).getReleaseDate() != null) {
            return getTranslation(Localization.USA).getReleaseDate().toLocalDate();
        }
        return null;
    }

    /**
     * Définit la date de release dans la translation US
     */
    public void setReleaseDate(java.time.LocalDate releaseDate) {
        ensureTranslationExists(Localization.USA);
        if (releaseDate != null) {
            getTranslation(Localization.USA).setReleaseDate(releaseDate.atStartOfDay());
        }
    }

    /**
     * Simule les propriétés de l'ancienne version
     */
    public Integer getCardsCount() {
        return nbCartes;
    }

    public void setCardsCount(Integer count) {
        this.nbCartes = count;
    }

    public Boolean getCardsSynced() {
        // Logique métier : considérer comme synced si nbCartes > 0
        return nbCartes != null && nbCartes > 0;
    }

    public void setCardsSynced(Boolean synced) {
        // Ne peut pas être persisté directement, mais influence nbCartes
        // La logique sera dans les services
    }

    public LocalDateTime getLastSyncAt() {
        // Pas de champ équivalent dans la nouvelle structure
        // Retourner la date de modification de la translation
        if (getTranslation(Localization.USA) != null) {
            return getTranslation(Localization.USA).getReleaseDate();
        }
        return null;
    }

    public void setLastSyncAt(LocalDateTime dateTime) {
        // Simulé via la date de release pour l'instant
        if (dateTime != null) {
            setReleaseDate(dateTime.toLocalDate());
        }
    }

    /**
     * Propriétés manquantes simulées
     */
    public String getGathererCode() {
        return mtgoCode; // Approximation
    }

    public void setGathererCode(String code) {
        this.mtgoCode = code;
    }

    public String getMagicCardsInfoCode() {
        return tcgplayerGroupId; // Approximation
    }

    public void setMagicCardsInfoCode(String code) {
        this.tcgplayerGroupId = code;
    }

    public String getBorder() {
        return version; // Approximation
    }

    public void setBorder(String border) {
        this.version = border;
    }

    public Boolean getOnlineOnly() {
        return !fr && !us; // Logique : si ni FR ni US, alors online only
    }

    public void setOnlineOnly(Boolean onlineOnly) {
        if (onlineOnly != null && onlineOnly) {
            this.fr = false;
            this.us = false;
        } else {
            this.us = true; // Par défaut US
        }
    }

    // MÉTHODES UTILITAIRES PRIVÉES

    private void ensureTranslationExists(Localization localization) {
        if (getTranslation(localization) == null) {
            CardSetTranslation translation = new CardSetTranslation();
            translation.setLocalization(localization);
            translation.setAvailable(true);
            setTranslation(localization, translation);
        }
    }

    // CONSTRUCTEURS ADAPTÉS

    public MagicSet() {
        super();
        // Initialiser une translation US par défaut
        ensureTranslationExists(Localization.USA);
    }

    public MagicSet(String code, String name, String type) {
        this();
        this.code = code;
        setName(name);
        // setType nécessitera un service pour résoudre le MagicType
    }
}