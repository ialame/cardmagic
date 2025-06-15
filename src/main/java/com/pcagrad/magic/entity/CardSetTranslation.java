package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import java.time.LocalDateTime;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "card_set_translation")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@DiscriminatorValue("bas")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class CardSetTranslation  extends AbstractUuidEntity{
	@Id
	@GeneratedValue
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "translatable_id")
	private CardSet translatable;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "label_name")
	private String labelName;

	@Column
	private boolean available;

	@Column(name = "release_date")
	private LocalDateTime releaseDate;

	// *** CORRECTION : Force VARCHAR au lieu d'ENUM ***
	@Enumerated(EnumType.STRING)
	@Column(name = "locale", columnDefinition = "VARCHAR(5)")
	private Localization localization;

}
