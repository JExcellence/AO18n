package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.MessageFormatter;
import de.jexcellence.jextranslate.api.Placeholder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link MessageFormatter} specialization that always renders templates using Adventure MiniMessage. Intended for
 * configurations where repository entries are guaranteed to use MiniMessage markup exclusively.
 *
 * <p>Relies on placeholder replacement performed prior to MiniMessage parsing, aligning with
 * {@link de.jexcellence.jextranslate.api.TranslationService} expectations.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.1
 */
public class MiniMessageFormatter implements MessageFormatter {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_-]+)}");
    private FormattingStrategy strategy = FormattingStrategy.MINI_MESSAGE;

    @Override
    @NotNull
    public String formatText(@NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Locale locale) throws FormattingException {
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(placeholders, "Placeholders cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");

        try {
            String result = template;
            for (final Placeholder placeholder : placeholders) {
                final String key = "{" + placeholder.key() + "}";
                result = result.replace(key, placeholder.asText());
            }
            return result;
        } catch (final Exception exception) {
            throw new FormattingException("Failed to format text", template, placeholders, exception);
        }
    }

    @Override
    @NotNull
    public Component formatComponent(@NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Locale locale) {
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(placeholders, "Placeholders cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");

        try {
            String processedTemplate = template;
            for (final Placeholder placeholder : placeholders) {
                final String key = "{" + placeholder.key() + "}";
                processedTemplate = processedTemplate.replace(key, placeholder.asText());
            }

            return MiniMessage.builder()
                .tags(TagResolver.resolver(StandardTags.defaults()))
                .build()
                .deserialize(processedTemplate);
        } catch (final Exception exception) {
            return Component.text(template);
        }
    }

    @Override
    @NotNull
    public ValidationResult validateTemplate(@NotNull final String template) {
        Objects.requireNonNull(template, "Template cannot be null");

        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> placeholderKeys = new ArrayList<>();

        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            placeholderKeys.add(matcher.group(1));
        }

        int openBraces = 0;
        int closeBraces = 0;
        for (final char c : template.toCharArray()) {
            if (c == '{') openBraces++;
            if (c == '}') closeBraces++;
        }

        if (openBraces != closeBraces) {
            errors.add("Mismatched braces: " + openBraces + " open, " + closeBraces + " close");
        }

        return new ValidationResultImpl(errors.isEmpty(), errors, warnings, placeholderKeys);
    }

    @Override
    @NotNull
    public FormattingStrategy getStrategy() {
        return this.strategy;
    }

    @Override
    public void setStrategy(@NotNull final FormattingStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "Strategy cannot be null");
    }

    /**
     * Simple {@link ValidationResult} implementation capturing MiniMessage template diagnostics.
     */
    private record ValidationResultImpl(
        boolean isValid,
        @NotNull List<String> errors,
        @NotNull List<String> warnings,
        @NotNull List<String> placeholders
    ) implements ValidationResult {

        @Override
        @NotNull
        public List<String> getErrors() {
            return List.copyOf(errors);
        }

        @Override
        @NotNull
        public List<String> getWarnings() {
            return List.copyOf(warnings);
        }

        @Override
        @NotNull
        public List<String> getPlaceholders() {
            return List.copyOf(placeholders);
        }
    }
}
