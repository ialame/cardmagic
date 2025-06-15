package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.ColumnDefault;

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

/// //////////////  à supprimer
    public MagicSet(String code, String name, String type) {
        this();
        this.code = code;
        super.getTranslation(Localization.USA).setName(name);
        //this.name = name;
        this.type = type;
    }

    public MagicSet() {
    }
}