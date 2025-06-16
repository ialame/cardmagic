package com.pcagrad.magic.util;

import com.pcagrad.magic.util.Localization;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LocalizationConverter implements AttributeConverter<Localization, String> {

    @Override
    public String convertToDatabaseColumn(Localization localization) {
        if (localization == null) {
            return null;
        }
        // Stocke le code court (us, fr, de, etc.) au lieu du nom (USA, FRANCE, GERMANY)
        return localization.getCode();
    }

    @Override
    public Localization convertToEntityAttribute(String code) {
        if (code == null) {
            return null;
        }
        return Localization.getByCode(code);
    }
}