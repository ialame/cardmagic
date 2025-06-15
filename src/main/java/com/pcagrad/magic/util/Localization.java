package com.pcagrad.magic.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

@Schema
public enum Localization {
    USA("us", false),
    JAPAN("jp", true),
    FRANCE("fr", false),
    ITALY("it", false),
    GERMANY("de", false),
    SPAIN("es", false),
    KOREA("kr",true),
    TAIWAN("cn", true),
    CHINA("zh", true),
    RUSSIA("ru", true),
    NETHERLANDS("nl", false),
    PORTUGAL("pt", false);

    private final String code;
    private final boolean originalName;

    Localization(@Nonnull String code, boolean originalName) {
        this.code = code;
        this.originalName = originalName;
    }


    @Nonnull
    @JsonValue
    public String getCode() {
        return code;
    }

    public boolean hasOriginalName() {
        return originalName;
    }

    @Nullable
    @JsonCreator
    public static Localization getByCode(@Nullable String code) {
        var lowerCode = StringUtils.lowerCase(code);

        if ("jap".equalsIgnoreCase(lowerCode)) {
            return Localization.JAPAN;
        } else if ("kor".equalsIgnoreCase(lowerCode)) {
            return Localization.KOREA;
        }
        for (Localization localization : values()) {
            if (localization.code.equals(lowerCode)) {
                return localization;
            }
        }
        return null;
    }

    @Deprecated
    public enum Group implements Collection<Localization> {
        USA(Localization.USA),
        JAPAN(Localization.JAPAN),
        FRANCE(Localization.FRANCE),
        ITALY(Localization.ITALY),
        GERMANY(Localization.GERMANY),
        SPAIN(Localization.SPAIN),
        KOREA(Localization.KOREA),
        TAIWAN(Localization.TAIWAN),
        CHINA(Localization.CHINA),
        RUSSIA(Localization.RUSSIA),
        NETHERLANDS(Localization.NETHERLANDS),
        PORTUGAL(Localization.PORTUGAL),
        WEST("west", Localization.USA, Localization.FRANCE, Localization.ITALY, Localization.GERMANY, Localization.SPAIN, Localization.PORTUGAL),
        BOTH_CHINESE("cn+zh", Localization.CHINA, Localization.TAIWAN),
        YUGIOH("yugioh", Localization.USA, Localization.JAPAN, Localization.GERMANY, Localization.FRANCE, Localization.ITALY, Localization.SPAIN, Localization.PORTUGAL, Localization.KOREA);

        private final String code;
        private final List<Localization> localizations;

        Group(String code, Localization... localizations) {
            this.code = code;
            this.localizations = List.of(localizations);
        }

        Group(Localization localization) {
            this (localization.code, localization);
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        public synchronized List<Localization> getLocalizations() {
            return localizations;
        }

        public Localization getDefaultLocalization() {
            return getLocalizations().get(0);
        }

        @JsonCreator
        public static Group getByCode(String code) {
            for (Group group : values()) {
                if (group.code.equals(code)) {
                    return group;
                }
            }
            return WEST;
        }

        @Override
        public int size() {
            return localizations.size();
        }

        @Override
        public boolean isEmpty() {
            return localizations.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return localizations.contains(o);
        }

        @Nonnull
        @Override
        public Iterator<Localization> iterator() {
            return localizations.iterator();
        }

        @Nonnull
        @Override
        public Object[] toArray() {
            return localizations.toArray();
        }

        @Nonnull
        @Override
        public <T> T[] toArray(@Nonnull T[] a) {
            return localizations.toArray(a);
        }

        @Override
        public boolean add(Localization localization) {
            uoe();
            return false;
        }

        @Override
        public boolean remove(Object o) {
            uoe();
            return false;
        }

        @Override
        public boolean containsAll(@Nonnull Collection<?> c) {
            return new HashSet<>(localizations).containsAll(c);
        }

        @Override
        public boolean addAll(@Nonnull Collection<? extends Localization> c) {
            uoe();
            return false;
        }

        @Override
        public boolean removeAll(@Nonnull Collection<?> c) {
            uoe();
            return false;
        }

        @Override
        public boolean retainAll(@Nonnull Collection<?> c) {
            uoe();
            return false;
        }

        @Override
        public void clear() {
            uoe();
        }

        static void uoe() {
            throw new UnsupportedOperationException();
        }
    }
}

