package de.jexcellence.jextranslate.api;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public interface MessageFormatter {

    @NotNull
    String formatText(@NotNull String template, @NotNull List<Placeholder> placeholders, @NotNull Locale locale) throws FormattingException;

    @NotNull
    Component formatComponent(@NotNull String template, @NotNull List<Placeholder> placeholders, @NotNull Locale locale);

    @NotNull
    ValidationResult validateTemplate(@NotNull String template);

    @NotNull
    FormattingStrategy getStrategy();

    void setStrategy(@NotNull FormattingStrategy strategy);

    enum FormattingStrategy {
        MESSAGE_FORMAT,
        SIMPLE_REPLACEMENT,
        HYBRID,
        MINI_MESSAGE
    }

    interface ValidationResult {
        boolean isValid();

        @NotNull
        List<String> getErrors();

        @NotNull
        List<String> getWarnings();

        @NotNull
        List<String> getPlaceholders();
    }

    final class FormattingException extends Exception {
        private final String template;
        private final List<Placeholder> placeholders;

        public FormattingException(@NotNull final String message, @NotNull final String template, @NotNull final List<Placeholder> placeholders) {
            super(message);
            this.template = template;
            this.placeholders = List.copyOf(placeholders);
        }

        public FormattingException(@NotNull final String message, @NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Throwable cause) {
            super(message, cause);
            this.template = template;
            this.placeholders = List.copyOf(placeholders);
        }

        @NotNull
        public String getTemplate() {
            return this.template;
        }

        @NotNull
        public List<Placeholder> getPlaceholders() {
            return this.placeholders;
        }
    }
}
