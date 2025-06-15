package com.pcagrad.magic.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "magic_type")
public class MagicType {
    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "id_pca")
    private Integer idPca;

    @Size(max = 50)
    @NotNull
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Size(max = 50)
    @NotNull
    @Column(name = "type_pcafr", nullable = false, length = 50)
    private String typePcafr;

    @Size(max = 50)
    @NotNull
    @Column(name = "type_pcaus", nullable = false, length = 50)
    private String typePcaus;

    @Size(max = 50)
    @NotNull
    @Column(name = "sous_type_pcafr", nullable = false, length = 50)
    private String sousTypePcafr;

    @Size(max = 50)
    @NotNull
    @Column(name = "sous_type_pcaus", nullable = false, length = 50)
    private String sousTypePcaus;

}