package com.pcagrad.magic.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

public record MtgSet(
        String code,
        String name,
        String type,
        String block,
        @JsonProperty("releaseDate") String releaseDate,
        @JsonProperty("gathererCode") String gathererCode,
        @JsonProperty("magicCardsInfoCode") String magicCardsInfoCode,
        String border,
        @JsonProperty("onlineOnly") boolean onlineOnly,
        List<MtgCard> cards
) {
    public LocalDate getParsedReleaseDate() {
        try {
            return releaseDate != null ? LocalDate.parse(releaseDate) : null;
        } catch (Exception e) {
            return null;
        }
    }
}