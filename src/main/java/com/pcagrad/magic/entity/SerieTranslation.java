package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import com.pcagrad.magic.util.LocalizationConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "serie_translation")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@DiscriminatorValue("mag")
public class SerieTranslation extends AbstractUuidEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // *** SOLUTION : Utiliser un converter personnalisé pour stocker les codes courts ***
    @Convert(converter = LocalizationConverter.class)
    @Column(name = "locale", length = 5)
    private Localization localization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "translatable_id")
    private Serie translatable;

    @Column(name = "name")
    private String name;

    @Column(name = "active")
    private boolean active;
}

// *** AJOUTEZ CETTE CLASSE CONVERTER - CRÉEZ UN FICHIER SÉPARÉ ***