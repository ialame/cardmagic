package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
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
public class SerieTranslation  extends AbstractUuidEntity{

    // *** CORRECTION : Force VARCHAR au lieu d'ENUM ***
    @Enumerated(EnumType.STRING)
    @Column(name = "locale", columnDefinition = "VARCHAR(5)")
    private Localization localization;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private Serie translatable;

    @Column
    private String name;

    @Column
    private boolean active;
}
