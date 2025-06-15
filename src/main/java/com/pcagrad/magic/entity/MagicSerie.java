package com.pcagrad.magic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "magic_serie")
@DiscriminatorValue("mag")
public class MagicSerie extends Serie{

    @Column(name = "id_pca")
    private Integer idPca;

}