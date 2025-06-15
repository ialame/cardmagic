package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import lombok.Data;
import java.util.*;

@Data
@Entity
@Table(name = "card")
@DiscriminatorColumn(name = "discriminator")
public class Card {
	@Id
	@GeneratedValue
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	@OneToMany(fetch = FetchType.EAGER, mappedBy = "translatable", cascade = CascadeType.ALL)
	@MapKey(name = "localization")
	private Map<Localization, CardTranslation> translations = new EnumMap<>(Localization.class);

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "card_card_set", joinColumns = @JoinColumn(name = "card_id"), inverseJoinColumns = @JoinColumn(name = "card_set_id"))
	private Set<CardSet> cardSets = new HashSet<>();

	@Column(name = "num")
	private String number = "";

	@Lob
	@Column(name = "attributes", columnDefinition = "LONGTEXT")
	private String attributes = "{\"reverse\": 0, \"edition\": 1, \"shadowless\": 0}";

	@Lob
	@Column(name = "allowed_notes", columnDefinition = "LONGTEXT")
	private String allowedNotes = "[]";

	@Column(name = "image_id")
	private Integer imageId;

	@Transient
	public CardTranslation getTranslation(Localization localization) {
		return translations.get(localization);
	}

	@Transient
	public List<CardTranslation> getTranslations() {
		return List.copyOf(translations.values());
	}

	@Transient
	public void setTranslations(List<CardTranslation> translations) {
		translations.forEach(translation -> setTranslation(translation.getLocalization(), translation));
	}

	@Transient
	public Map<Localization, CardTranslation> getTranslationMap() {
		return translations;
	}

	@Transient
	public void setTranslation(Localization localization, CardTranslation translation) {
		if (translation != null) {
			translations.put(localization, translation);
			translation.setTranslatable(this);
			translation.setLocalization(localization);
		} else {
			translations.remove(localization);
		}
	}

}
