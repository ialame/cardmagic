package com.pcagrad.magic.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "magic_card")
public class MagicCard extends Card{

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

    // Nouveau champ pour stocker l'ID externe (MTG API ou Scryfall)
    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Column(name = "mana_cost")
    private String manaCost;

    private Integer cmc;

    @Size(max = 100)
    @Column(name = "colors", length = 100)
    private String colors;

    @Size(max = 100)
    @Column(name = "color_identity", length = 100)
    private String colorIdentity;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_supertypes", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "supertype")
    private List<String> supertypes;

    @Size(max = 100)
    @Column(name = "types", length = 100)
    private String types;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_subtypes", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "subtype")
    private List<String> subtypes;

    @Column(nullable = false, length = 500)
    private String type;


    @Size(max = 100)
    @Column(name = "rarity", length = 100)
    private String rarity;

    @Column(name = "set_code", nullable = false)
    private String setCode;

    @Column(name = "set_name")
    private String setName;

    @Column(length = 2000)
    private String text;

    private String artist;

    @Size(max = 50)
    @Column(name = "number", length = 50)
    private String number;

    private String power;

    private String toughness;

    @Size(max = 100)
    @Column(name = "layout", length = 100)
    private String layout;

    @Column(name = "multiverse_id")
    private Integer multiverseid;

    @Column(name = "original_image_url")
    private String originalImageUrl;

    @Column(name = "local_image_path")
    private String localImagePath;

    @Column(name = "image_downloaded")
    private Boolean imageDownloaded = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructeurs
    public MagicCard() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public MagicCard(String externalId, String name, String setCode) {
        this();
        this.externalId = externalId;
        this.name = name;
        this.setCode = setCode;
    }

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

    // Méthodes de cycle de vie
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}