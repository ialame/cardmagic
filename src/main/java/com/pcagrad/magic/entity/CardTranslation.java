package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "card_translation")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@DiscriminatorValue("bas")
public class CardTranslation  extends AbstractUuidEntity{
	@Id
	@GeneratedValue
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	private boolean available;

	// *** CORRECTION : Force VARCHAR au lieu d'ENUM ***
	@Enumerated(EnumType.STRING)
	@Column(name = "locale", columnDefinition = "VARCHAR(5)")
	private Localization localization;

	@ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	private Card translatable;

	@Column
	private String name;


	@Column(name = "label_name")
	private String labelName;

}
