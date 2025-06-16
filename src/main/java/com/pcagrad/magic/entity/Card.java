package com.pcagrad.magic.entity;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "card")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
public class Card extends AbstractUuidEntity{

	// *** SOLUTION ALTERNATIVE : Correction du mapping avec CASCADE et ORPHAN REMOVAL ***
	@OneToMany(
			fetch = FetchType.EAGER,
			mappedBy = "translatable",
			cascade = CascadeType.ALL,
			orphanRemoval = true  // ← AJOUTER pour gérer les suppressions
	)
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
			// *** CORRECTION: S'assurer de la cohérence bidirectionnelle ***
			translations.put(localization, translation);
			translation.setTranslatable(this);
			translation.setLocalization(localization);

			// *** NOUVELLE SÉCURITÉ: Vérifier que l'ID de la traduction est cohérent ***
			if (translation.getId() == null) {
				translation.setId(UUID.randomUUID());
			}
		} else {
			// Supprimer proprement
			CardTranslation existingTranslation = translations.remove(localization);
			if (existingTranslation != null) {
				existingTranslation.setTranslatable(null);
			}
		}
	}

	// MÉTHODE UTILITAIRE POUR LES SOUS-CLASSES - VERSION CORRIGÉE
	protected void ensureTranslationExists(Localization localization) {
		if (getTranslation(localization) == null) {
			CardTranslation translation = new CardTranslation();
			translation.setId(UUID.randomUUID());  // ← AJOUTER génération d'UUID
			translation.setLocalization(localization);
			translation.setAvailable(true);
			setTranslation(localization, translation);
		}
	}

	/**
	 * NOUVELLE MÉTHODE : Préparation pour sauvegarde
	 * À appeler avant de sauvegarder l'entité
	 */
	@PrePersist
	@PreUpdate
	public void preparePersistence() {
		// S'assurer que toutes les traductions ont des IDs
		for (CardTranslation translation : translations.values()) {
			if (translation != null) {
				if (translation.getId() == null) {
					translation.setId(UUID.randomUUID());
				}
				// S'assurer de la référence bidirectionnelle
				translation.setTranslatable(this);
			}
		}
	}
}