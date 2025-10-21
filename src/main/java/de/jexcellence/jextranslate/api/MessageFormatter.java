package de.jexcellence.jextranslate.api;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Strategy interface responsible for translating repository templates into rendered text or Adventure components.
 * Implementations coordinate MiniMessage placeholder resolution, numeric formatting, and validation rules for
 * translation files managed by {@link TranslationRepository}.
 *
 * <p>Formatters are injected into {@link TranslationService} via
 * {@link TranslationService#configure(TranslationService.ServiceConfiguration)}, enabling repository and resolver
 * components to remain synchronized during reload cycles.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.1
 */
public interface MessageFormatter {

    /**
     * Formats the provided template into plain text using the supplied placeholders.
     *
     * @param template     the raw template retrieved from the {@link TranslationRepository}
     * @param placeholders ordered list of placeholders provided by {@link TranslationService}
     * @param locale       the locale influencing number, date, and culture-sensitive formatting
     * @return the formatted plain text result
     * @throws FormattingException if placeholder substitution or formatting fails
     */
    @NotNull
    String formatText(@NotNull String template, @NotNull List<Placeholder> placeholders, @NotNull Locale locale) throws FormattingException;

    /**
     * Formats the template into an Adventure {@link Component}. Implementations should prefer MiniMessage parsing when
     * rich text placeholders are present, falling back to plain text when necessary.
     *
     * @param template     the raw template retrieved from the {@link TranslationRepository}
     * @param placeholders ordered list of placeholders supplied by the service
     * @param locale       the locale influencing any localized formatting behaviour
     * @return the rendered Adventure component
     */
    @NotNull
    Component formatComponent(@NotNull String template, @NotNull List<Placeholder> placeholders, @NotNull Locale locale);

    /**
     * Validates the supplied template, returning structural warnings or errors that can be surfaced during repository
     * audits.
     *
     * @param template the template string to validate
     * @return metadata describing the template's validity and discovered placeholders
     */
    @NotNull
    ValidationResult validateTemplate(@NotNull String template);

    /**
     * Gets the active formatting strategy used by this formatter implementation. Strategies often map to different
     * placeholder syntaxes (e.g., Java {@link java.text.MessageFormat} or MiniMessage markup).
     *
     * @return the strategy currently applied
     */
    @NotNull
    FormattingStrategy getStrategy();

    /**
     * Sets the active formatting strategy, clearing any internal caches as needed.
     *
     * @param strategy the new strategy to apply
     */
    void setStrategy(@NotNull FormattingStrategy strategy);

    /**
     * Standard strategies supported by out-of-the-box formatters.
     */
    enum FormattingStrategy {
        /** Uses {@link java.text.MessageFormat} placeholder rules. */
        MESSAGE_FORMAT,
        /** Uses literal string replacement (brace and percent token support). */
        SIMPLE_REPLACEMENT,
        /** Blends {@link java.text.MessageFormat} with named placeholder fallbacks. */
        HYBRID,
        /** Leverages Adventure MiniMessage parsing for fully rich components. */
        MINI_MESSAGE
    }

    /**
     * Validation metadata describing the health of a template, often surfaced by admin tooling such as
     * {@link de.jexcellence.jextranslate.command.TranslationCommand}.
     */
    interface ValidationResult {
        /**
         * Indicates whether the template passes validation checks without errors.
         *
         * @return {@code true} when the template is valid
         */
        boolean isValid();

        /**
         * Describes validation errors that would prevent successful formatting.
         *
         * @return immutable list of error messages
         */
        @NotNull
        List<String> getErrors();

        /**
         * Describes potential issues that do not block formatting but may degrade placeholder fidelity.
         *
         * @return immutable list of warnings
         */
        @NotNull
        List<String> getWarnings();

        /**
         * Provides the placeholder identifiers referenced by the template.
         *
         * @return immutable list of placeholder keys
         */
        @NotNull
        List<String> getPlaceholders();
    }

    /**
     * Exception describing placeholder or parsing failures encountered while formatting text.
     */
    final class FormattingException extends Exception {
        private final String template;
        private final List<Placeholder> placeholders;

        /**
         * Creates a new formatting exception without a root cause.
         *
         * @param message      explanation of the failure
         * @param template     the template that failed to format
         * @param placeholders the placeholder set involved in the failure
         */
        public FormattingException(@NotNull final String message, @NotNull final String template, @NotNull final List<Placeholder> placeholders) {
            super(message);
            this.template = template;
            this.placeholders = List.copyOf(placeholders);
        }

        /**
         * Creates a new formatting exception with a nested cause for deeper debugging.
         *
         * @param message      explanation of the failure
         * @param template     the template that failed to format
         * @param placeholders the placeholder set involved in the failure
         * @param cause        the originating exception
         */
        public FormattingException(@NotNull final String message, @NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Throwable cause) {
            super(message, cause);
            this.template = template;
            this.placeholders = List.copyOf(placeholders);
        }

        /**
         * Gets the template that triggered the exception.
         *
         * @return the template source string
         */
        @NotNull
        public String getTemplate() {
            return this.template;
        }

        /**
         * Gets the placeholder payload that contributed to the failure.
         *
         * @return immutable list of placeholders provided to the formatter
         */
        @NotNull
        public List<Placeholder> getPlaceholders() {
            return this.placeholders;
        }
    }
}
