package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "serie")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@DiscriminatorValue("mag")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Serie  extends AbstractUuidEntity{

	@OneToMany(mappedBy = "translatable", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	@MapKey(name = "localization")
	private Map<Localization, SerieTranslation> translations = new EnumMap<>(Localization.class);


	@OneToMany(mappedBy = "serie", fetch = FetchType.LAZY)
	private List<CardSet> sets;

	@Transient
	public SerieTranslation getTranslation(Localization localization) {
		return translations.get(localization);
	}

	@Transient
	public List<SerieTranslation> getTranslations() {
		return List.copyOf(translations.values());
	}

	@Transient
	public void setTranslations(List<SerieTranslation> translations) {
		translations.forEach(t -> setTranslation(t.getLocalization(), t));
	}

	public void setTranslation(Localization localization, SerieTranslation translation) {
		translations.put(localization, translation);
		translation.setTranslatable(this);
		translation.setLocalization(localization);
	}

	public List<CardSet> getSets() {
		return sets;
	}

	public void setSets(List<CardSet> sets) {
		this.sets = sets;
	}
}
