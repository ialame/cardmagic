package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "card_set_translation")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@DiscriminatorValue("bas")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class CardSetTranslation {
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

	@Column(name = "locale")
	private Localization localization;

}
