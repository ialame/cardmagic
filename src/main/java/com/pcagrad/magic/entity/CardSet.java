package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.*;

@Data
@Entity
@Table(name = "card_set")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class CardSet{
	@Id
	@GeneratedValue
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	@OneToMany(mappedBy = "translatable", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	@MapKey(name = "localization")
	private Map<Localization, CardSetTranslation> translations = new EnumMap<>(Localization.class);

	@ManyToMany(mappedBy = "cardSets", fetch = FetchType.LAZY)
	private List<Card> cards = new ArrayList<>();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private CardSet parent;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "parent", cascade = CascadeType.ALL)
	private List<CardSet> children = new ArrayList<>();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "serie_id")
	private Serie serie;

	@Column(name = "ap_mention")
	private String apMention = "";

	@Transient
	public CardSetTranslation getTranslation(Localization localization) {
		return translations.get(localization);
	}

	@Transient
	public List<CardSetTranslation> getTranslations() {
		return List.copyOf(translations.values());
	}

	@Transient
	public void setTranslations(List<CardSetTranslation> translations) {
		translations.forEach(t -> setTranslation(t.getLocalization(), t));
	}

	public void setTranslation(Localization localization, CardSetTranslation translation) {
		if (translation == null) {
			translations.remove(localization);
			return;
		}
		translations.put(localization, translation);
		translation.setTranslatable(this);
		translation.setLocalization(localization);
	}

}
