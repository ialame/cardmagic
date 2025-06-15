package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.UUID;

@Data
@Entity
@Table(name = "serie_translation")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@DiscriminatorValue("bas")
public class SerieTranslation {
    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "locale")
    private Localization localization;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private Serie translatable;

    @Column
    private String name;

    @Column
    private boolean active;
}
