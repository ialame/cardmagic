package com.pcagrad.magic.model;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MtgCard(
        String id,
        String name,
        @JsonProperty("manaCost") String manaCost,
        Integer cmc,
        List<String> colors,
        @JsonProperty("colorIdentity") List<String> colorIdentity,
        String type,
        List<String> supertypes,
        List<String> types,
        List<String> subtypes,
        String rarity,
        String set,
        @JsonProperty("setName") String setName,
        String text,
        String artist,
        String number,
        String power,
        String toughness,
        String layout,
        @JsonProperty("multiverseid") Integer multiverseid,
        @JsonProperty("imageUrl") String imageUrl
) {}