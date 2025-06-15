package com.pcagrad.magic.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cards", indexes = {
        @Index(name = "idx_card_name", columnList = "name"),
        @Index(name = "idx_card_set", columnList = "setCode"),
        @Index(name = "idx_card_rarity", columnList = "rarity"),
        @Index(name = "idx_card_type", columnList = "type"),
        @Index(name = "idx_card_external_id", columnList = "externalId")
})
public class CardEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Nouveau champ pour stocker l'ID externe (MTG API ou Scryfall)
    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Column(name = "mana_cost")
    private String manaCost;

    private Integer cmc;


    // CORRECTION: Utiliser FetchType.EAGER pour les collections
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_colors", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "color")
    private List<String> colors;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_color_identity", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "color")
    private List<String> colorIdentity;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_supertypes", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "supertype")
    private List<String> supertypes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_types", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "type_name")
    private List<String> types;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_subtypes", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "subtype")
    private List<String> subtypes;

    @Column(nullable = false, length = 500)
    private String type;


    @Column(nullable = false)
    private String rarity;

    @Column(name = "set_code", nullable = false)
    private String setCode;

    @Column(name = "set_name")
    private String setName;

    @Column(length = 2000)
    private String text;

    private String artist;

    private String number;

    private String power;

    private String toughness;

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
    public CardEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public CardEntity(String externalId, String name, String setCode) {
        this();
        this.externalId = externalId;
        this.name = name;
        this.setCode = setCode;
    }

    // Méthodes de cycle de vie
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters et Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getManaCost() { return manaCost; }
    public void setManaCost(String manaCost) { this.manaCost = manaCost; }

    public Integer getCmc() { return cmc; }
    public void setCmc(Integer cmc) { this.cmc = cmc; }

    public List<String> getColors() { return colors; }
    public void setColors(List<String> colors) { this.colors = colors; }

    public List<String> getColorIdentity() { return colorIdentity; }
    public void setColorIdentity(List<String> colorIdentity) { this.colorIdentity = colorIdentity; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getSupertypes() { return supertypes; }
    public void setSupertypes(List<String> supertypes) { this.supertypes = supertypes; }

    public List<String> getTypes() { return types; }
    public void setTypes(List<String> types) { this.types = types; }

    public List<String> getSubtypes() { return subtypes; }
    public void setSubtypes(List<String> subtypes) { this.subtypes = subtypes; }

    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    public String getSetCode() { return setCode; }
    public void setSetCode(String setCode) { this.setCode = setCode; }

    public String getSetName() { return setName; }
    public void setSetName(String setName) { this.setName = setName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getPower() { return power; }
    public void setPower(String power) { this.power = power; }

    public String getToughness() { return toughness; }
    public void setToughness(String toughness) { this.toughness = toughness; }

    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }

    public Integer getMultiverseid() { return multiverseid; }
    public void setMultiverseid(Integer multiverseid) { this.multiverseid = multiverseid; }

    public String getOriginalImageUrl() { return originalImageUrl; }
    public void setOriginalImageUrl(String originalImageUrl) { this.originalImageUrl = originalImageUrl; }

    public String getLocalImagePath() { return localImagePath; }
    public void setLocalImagePath(String localImagePath) { this.localImagePath = localImagePath; }

    public Boolean getImageDownloaded() { return imageDownloaded; }
    public void setImageDownloaded(Boolean imageDownloaded) { this.imageDownloaded = imageDownloaded; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}