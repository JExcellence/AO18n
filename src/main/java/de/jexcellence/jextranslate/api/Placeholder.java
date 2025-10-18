package de.jexcellence.jextranslate.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Function;

public sealed interface Placeholder
    permits Placeholder.Text,
            Placeholder.Number,
            Placeholder.RichText,
            Placeholder.DateTime,
            Placeholder.Custom {

    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final String value) {
        return new Text(key, value);
    }

    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final java.lang.Number value) {
        return new Number(key, value, null);
    }

    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final java.lang.Number value, @NotNull final NumberFormat format) {
        return new Number(key, value, format);
    }

    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final Component component) {
        return new RichText(key, component);
    }

    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final LocalDateTime dateTime) {
        return new DateTime(key, dateTime, null);
    }

    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final LocalDateTime dateTime, @NotNull final DateTimeFormatter formatter) {
        return new DateTime(key, dateTime, formatter);
    }

    @NotNull
    static <T> Placeholder of(@NotNull final String key, @NotNull final T value, @NotNull final Function<T, String> formatter) {
        return new Custom<>(key, value, formatter);
    }

    @NotNull
    String key();

    @Nullable
    Object value();

    @NotNull
    String asText();

    @Nullable
    Component asComponent();

    @NotNull
    PlaceholderType type();

    enum PlaceholderType {
        TEXT,
        NUMBER,
        RICH_TEXT,
        DATE_TIME,
        CUSTOM
    }

    record Text(@NotNull String key, @NotNull String value) implements Placeholder {
        public Text {
            Objects.requireNonNull(key, "Key cannot be null");
            Objects.requireNonNull(value, "Value cannot be null");
        }

        @Override
        @NotNull
        public String asText() {
            return this.value;
        }

        @Override
        @NotNull
        public Component asComponent() {
            return Component.text(this.value);
        }

        @Override
        @NotNull
        public PlaceholderType type() {
            return PlaceholderType.TEXT;
        }
    }

    record Number(@NotNull String key, @NotNull java.lang.Number value, @Nullable NumberFormat format) implements Placeholder {
        public Number {
            Objects.requireNonNull(key, "Key cannot be null");
            Objects.requireNonNull(value, "Value cannot be null");
        }

        @Override
        @NotNull
        public String asText() {
            return this.format != null ? this.format.format(this.value) : this.value.toString();
        }

        @Override
        @NotNull
        public Component asComponent() {
            return Component.text(asText());
        }

        @Override
        @NotNull
        public PlaceholderType type() {
            return PlaceholderType.NUMBER;
        }
    }

    record RichText(@NotNull String key, @NotNull Component component) implements Placeholder {
        public RichText {
            Objects.requireNonNull(key, "Key cannot be null");
            Objects.requireNonNull(component, "Component cannot be null");
        }

        @Override
        @NotNull
        public Component value() {
            return this.component;
        }

        @Override
        @NotNull
        public String asText() {
            return PlainTextComponentSerializer.plainText().serialize(this.component);
        }

        @Override
        @NotNull
        public Component asComponent() {
            return this.component;
        }

        @Override
        @NotNull
        public PlaceholderType type() {
            return PlaceholderType.RICH_TEXT;
        }
    }

    record DateTime(@NotNull String key, @NotNull LocalDateTime dateTime, @Nullable DateTimeFormatter formatter) implements Placeholder {
        public DateTime {
            Objects.requireNonNull(key, "Key cannot be null");
            Objects.requireNonNull(dateTime, "DateTime cannot be null");
        }

        @Override
        @NotNull
        public LocalDateTime value() {
            return this.dateTime;
        }

        @Override
        @NotNull
        public String asText() {
            final DateTimeFormatter actualFormatter = this.formatter != null ? this.formatter : DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            return actualFormatter.format(this.dateTime);
        }

        @Override
        @NotNull
        public Component asComponent() {
            return Component.text(asText());
        }

        @Override
        @NotNull
        public PlaceholderType type() {
            return PlaceholderType.DATE_TIME;
        }
    }

    record Custom<T>(@NotNull String key, @NotNull T value, @NotNull Function<T, String> formatter) implements Placeholder {
        public Custom {
            Objects.requireNonNull(key, "Key cannot be null");
            Objects.requireNonNull(value, "Value cannot be null");
            Objects.requireNonNull(formatter, "Formatter cannot be null");
        }

        @Override
        @NotNull
        public String asText() {
            return this.formatter.apply(this.value);
        }

        @Override
        @NotNull
        public Component asComponent() {
            return Component.text(asText());
        }

        @Override
        @NotNull
        public PlaceholderType type() {
            return PlaceholderType.CUSTOM;
        }
    }
}
