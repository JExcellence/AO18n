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

/**
 * Represents a substitution value supplied to {@link MessageFormatter} implementations when rendering templates.
 * Placeholder instances are immutable and thread-safe, supporting MiniMessage-compatible workflows promoted by
 * {@link TranslationService}.
 *
 * <p>Factory helpers convert common Java types into {@link PlaceholderType}-specific implementations so repository,
 * formatter, and resolver components can share a consistent placeholder API.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.1
 */
public sealed interface Placeholder
    permits Placeholder.Text,
            Placeholder.Number,
            Placeholder.RichText,
            Placeholder.DateTime,
            Placeholder.Custom {

    /**
     * Creates a plain text placeholder.
     *
     * @param key   the placeholder key without braces
     * @param value the string value to substitute
     * @return a text placeholder
     */
    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final String value) {
        return new Text(key, value);
    }

    /**
     * Creates a numeric placeholder using default formatting for the provided {@link java.lang.Number}.
     *
     * @param key   the placeholder key without braces
     * @param value the numeric value to substitute
     * @return a number placeholder
     */
    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final java.lang.Number value) {
        return new Number(key, value, null);
    }

    /**
     * Creates a numeric placeholder using the supplied {@link NumberFormat} for localization.
     *
     * @param key     the placeholder key without braces
     * @param value   the numeric value to substitute
     * @param format  number format to apply when rendering
     * @return a number placeholder
     */
    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final java.lang.Number value, @NotNull final NumberFormat format) {
        return new Number(key, value, format);
    }

    /**
     * Creates a rich text placeholder that retains Adventure component metadata.
     *
     * @param key       the placeholder key without braces
     * @param component the component to substitute
     * @return a rich text placeholder
     */
    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final Component component) {
        return new RichText(key, component);
    }

    /**
     * Creates a temporal placeholder using ISO formatting by default.
     *
     * @param key      the placeholder key without braces
     * @param dateTime the date-time value to substitute
     * @return a date-time placeholder
     */
    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final LocalDateTime dateTime) {
        return new DateTime(key, dateTime, null);
    }

    /**
     * Creates a temporal placeholder using the provided {@link DateTimeFormatter}.
     *
     * @param key        the placeholder key without braces
     * @param dateTime   the date-time value to substitute
     * @param formatter  the formatter to apply when rendering the placeholder
     * @return a date-time placeholder
     */
    @NotNull
    static Placeholder of(@NotNull final String key, @NotNull final LocalDateTime dateTime, @NotNull final DateTimeFormatter formatter) {
        return new DateTime(key, dateTime, formatter);
    }

    /**
     * Creates a custom placeholder that maps the supplied value using the provided formatter function.
     *
     * @param key        the placeholder key without braces
     * @param value      the value to substitute
     * @param formatter  function converting the value to text
     * @param <T>        value type
     * @return a custom placeholder
     */
    @NotNull
    static <T> Placeholder of(@NotNull final String key, @NotNull final T value, @NotNull final Function<T, String> formatter) {
        return new Custom<>(key, value, formatter);
    }

    /**
     * @return the placeholder key (without MiniMessage braces)
     */
    @NotNull
    String key();

    /**
     * @return the raw value stored by the placeholder (may be {@code null})
     */
    @Nullable
    Object value();

    /**
     * @return the placeholder rendered as plain text
     */
    @NotNull
    String asText();

    /**
     * @return the placeholder rendered as an Adventure component, or {@code null} when not applicable
     */
    @Nullable
    Component asComponent();

    /**
     * @return the placeholder type identifier
     */
    @NotNull
    PlaceholderType type();

    /**
     * Enumerates supported placeholder categories used by formatter implementations.
     */
    enum PlaceholderType {
        TEXT,
        NUMBER,
        RICH_TEXT,
        DATE_TIME,
        CUSTOM
    }

    /**
     * Plain text placeholder implementation.
     */
    record Text(@NotNull String key, @NotNull String value) implements Placeholder {
        /**
         * Ensures key and value are not {@code null}.
         */
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

    /**
     * Numeric placeholder implementation.
     */
    record Number(@NotNull String key, @NotNull java.lang.Number value, @Nullable NumberFormat format) implements Placeholder {
        /**
         * Ensures key and value are not {@code null}.
         */
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

    /**
     * Rich text placeholder implementation retaining Adventure component state.
     */
    record RichText(@NotNull String key, @NotNull Component component) implements Placeholder {
        /**
         * Ensures key and component are not {@code null}.
         */
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

    /**
     * Date-time placeholder implementation.
     */
    record DateTime(@NotNull String key, @NotNull LocalDateTime dateTime, @Nullable DateTimeFormatter formatter) implements Placeholder {
        /**
         * Ensures key and date-time value are not {@code null}.
         */
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

    /**
     * Custom placeholder implementation using a caller-supplied formatter.
     */
    record Custom<T>(@NotNull String key, @NotNull T value, @NotNull Function<T, String> formatter) implements Placeholder {
        /**
         * Ensures key, value, and formatter are not {@code null}.
         */
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
