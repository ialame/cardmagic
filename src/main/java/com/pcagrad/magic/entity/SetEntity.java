package com.pcagrad.magic.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sets", indexes = {
        @Index(name = "idx_set_code", columnList = "code", unique = true),
        @Index(name = "idx_set_name", columnList = "name"),
        @Index(name = "idx_set_type", columnList = "type"),
        @Index(name = "idx_set_release_date", columnList = "releaseDate")
})
public class SetEntity {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    private String block;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "gatherer_code")
    private String gathererCode;

    @Column(name = "magic_cards_info_code")
    private String magicCardsInfoCode;

    private String border;

    @Column(name = "online_only")
    private Boolean onlineOnly = false;

    @Column(name = "cards_synced")
    private Boolean cardsSynced = false;

    @Column(name = "cards_count")
    private Integer cardsCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    // Constructeurs
    public SetEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public SetEntity(String code, String name, String type) {
        this();
        this.code = code;
        this.name = name;
        this.type = type;
    }

    // Méthodes de cycle de vie
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters et Setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getBlock() { return block; }
    public void setBlock(String block) { this.block = block; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public String getGathererCode() { return gathererCode; }
    public void setGathererCode(String gathererCode) { this.gathererCode = gathererCode; }

    public String getMagicCardsInfoCode() { return magicCardsInfoCode; }
    public void setMagicCardsInfoCode(String magicCardsInfoCode) { this.magicCardsInfoCode = magicCardsInfoCode; }

    public String getBorder() { return border; }
    public void setBorder(String border) { this.border = border; }

    public Boolean getOnlineOnly() { return onlineOnly; }
    public void setOnlineOnly(Boolean onlineOnly) { this.onlineOnly = onlineOnly; }

    public Boolean getCardsSynced() { return cardsSynced; }
    public void setCardsSynced(Boolean cardsSynced) { this.cardsSynced = cardsSynced; }

    public Integer getCardsCount() { return cardsCount; }
    public void setCardsCount(Integer cardsCount) { this.cardsCount = cardsCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}