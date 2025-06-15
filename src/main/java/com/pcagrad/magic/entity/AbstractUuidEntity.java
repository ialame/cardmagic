package com.pcagrad.magic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@MappedSuperclass
public abstract class AbstractUuidEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    @JdbcTypeCode(SqlTypes.BINARY)  // Force le type BINARY pour Hibernate 6.x
    private UUID id;

    public UUID getId() {
        return  id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}
