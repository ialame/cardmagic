package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Data
@Entity
@Table(name = "card_translation")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@DiscriminatorValue("bas")
public class CardTranslation{
	@Id
	@GeneratedValue
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	private boolean available;

	@Column(name = "locale")
	private Localization localization;

	@ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	private Card translatable;

	@Column
	private String name;


	@Column(name = "label_name")
	private String labelName;

}
