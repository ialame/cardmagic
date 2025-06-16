package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "magic_set")
public class MagicSet extends CardSet {

    @Column(name = "id_pca")
    private Integer idPca;

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

    // CONSTRUCTEURS
    public MagicSet() {
        super();
        ensureTranslationExists(Localization.USA);
    }

    public MagicSet(String code, String name, String type) {
        this();
        this.code = code;
        setName(name);
    }

    // GETTERS ET SETTERS EXPLICITES
    public Integer getIdPca() { return idPca; }
    public void setIdPca(Integer idPca) { this.idPca = idPca; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTcgplayerGroupId() { return tcgplayerGroupId; }
    public void setTcgplayerGroupId(String tcgplayerGroupId) { this.tcgplayerGroupId = tcgplayerGroupId; }

    public String getMtgoCode() { return mtgoCode; }
    public void setMtgoCode(String mtgoCode) { this.mtgoCode = mtgoCode; }

    public Integer getBaseSetSize() { return baseSetSize; }
    public void setBaseSetSize(Integer baseSetSize) { this.baseSetSize = baseSetSize; }

    public String getBoosterV3() { return boosterV3; }
    public void setBoosterV3(String boosterV3) { this.boosterV3 = boosterV3; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getBlock() { return block; }
    public void setBlock(String block) { this.block = block; }

    public String getTotalSetSize() { return totalSetSize; }
    public void setTotalSetSize(String totalSetSize) { this.totalSetSize = totalSetSize; }

    public Integer getNbCartes() { return nbCartes; }
    public void setNbCartes(Integer nbCartes) { this.nbCartes = nbCartes; }

    public Integer getNbImages() { return nbImages; }
    public void setNbImages(Integer nbImages) { this.nbImages = nbImages; }

    public String getNomDossier() { return nomDossier; }
    public void setNomDossier(String nomDossier) { this.nomDossier = nomDossier; }

    public String getNumSur() { return numSur; }
    public void setNumSur(String numSur) { this.numSur = numSur; }

    public MagicType getTypeMagic() { return typeMagic; }
    public void setTypeMagic(MagicType typeMagic) { this.typeMagic = typeMagic; }

    public Boolean getCertifiable() { return certifiable; }
    public void setCertifiable(Boolean certifiable) { this.certifiable = certifiable; }

    public Boolean getFr() { return fr; }
    public void setFr(Boolean fr) { this.fr = fr; }

    public Boolean getUs() { return us; }
    public void setUs(Boolean us) { this.us = us; }

    public Boolean getHasDateSortieFr() { return hasDateSortieFr; }
    public void setHasDateSortieFr(Boolean hasDateSortieFr) { this.hasDateSortieFr = hasDateSortieFr; }

    // MÉTHODES MÉTIER ADAPTÉES
    public String getName() {
        if (getTranslation(Localization.USA) != null) {
            return getTranslation(Localization.USA).getName();
        }
        return "Extension " + code;
    }

    // *** AMÉLIORATION : Méthode setName() corrigée ***
    public void setName(String name) {
        ensureTranslationExists(Localization.USA);
        CardSetTranslation translation = getTranslation(Localization.USA);

        if (translation != null) {
            translation.setName(name);
            // *** CORRECTION : Mettre à jour aussi label_name ***
            translation.setLabelName(name);
        }
    }

    public String getType() {
        return typeMagic != null ? typeMagic.getType() : "expansion";
    }

    public void setType(String type) {
        // Cette méthode devra être complétée avec un service
    }

    public java.time.LocalDate getReleaseDate() {
        if (getTranslation(Localization.USA) != null &&
                getTranslation(Localization.USA).getReleaseDate() != null) {
            return getTranslation(Localization.USA).getReleaseDate().toLocalDate();
        }
        return null;
    }

    public void setReleaseDate(java.time.LocalDate releaseDate) {
        ensureTranslationExists(Localization.USA);
        if (releaseDate != null) {
            getTranslation(Localization.USA).setReleaseDate(releaseDate.atStartOfDay());
        }
    }

    // MÉTHODES UTILITAIRES
    public void ensureTranslationExists(Localization localization) {
        if (getTranslation(localization) == null) {
            CardSetTranslation translation = new CardSetTranslation();
            translation.setLocalization(localization);
            translation.setAvailable(true);

            // *** CORRECTION : Définir le name et label_name ***
            String defaultName = "Extension " + (code != null ? code : "Unknown");
            translation.setName(defaultName);
            translation.setLabelName(defaultName); // ← AJOUTÉ : Définir label_name

            // Définir une date par défaut
            translation.setReleaseDate(java.time.LocalDateTime.now());

            setTranslation(localization, translation);
        }
    }

    // PROPRIÉTÉS DE COMPATIBILITÉ
    public Integer getCardsCount() { return nbCartes; }
    public void setCardsCount(Integer count) { this.nbCartes = count; }

    public Boolean getCardsSynced() { return nbCartes != null && nbCartes > 0; }
    public void setCardsSynced(Boolean synced) { /* Ne peut pas être persisté directement */ }

    public LocalDateTime getLastSyncAt() {
        if (getTranslation(Localization.USA) != null) {
            return getTranslation(Localization.USA).getReleaseDate();
        }
        return null;
    }

    public void setLastSyncAt(LocalDateTime dateTime) {
        if (dateTime != null) {
            setReleaseDate(dateTime.toLocalDate());
        }
    }

    public String getGathererCode() { return mtgoCode; }
    public void setGathererCode(String code) { this.mtgoCode = code; }

    public String getMagicCardsInfoCode() { return tcgplayerGroupId; }
    public void setMagicCardsInfoCode(String code) { this.tcgplayerGroupId = code; }

    public String getBorder() { return version; }
    public void setBorder(String border) { this.version = border; }

    public Boolean getOnlineOnly() { return !fr && !us; }
    public void setOnlineOnly(Boolean onlineOnly) {
        if (onlineOnly != null && onlineOnly) {
            this.fr = false;
            this.us = false;
        } else {
            this.us = true;
        }
    }

    // EQUALS ET HASHCODE
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MagicSet magicSet = (MagicSet) o;
        return code != null ? code.equals(magicSet.code) : magicSet.code == null;
    }

    @Override
    public int hashCode() {
        return code != null ? code.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MagicSet{" +
                "code='" + code + '\'' +
                ", name='" + getName() + '\'' +
                ", type='" + getType() + '\'' +
                '}';
    }
}