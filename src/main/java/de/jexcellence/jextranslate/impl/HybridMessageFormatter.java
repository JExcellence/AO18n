package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.MessageFormatter;
import de.jexcellence.jextranslate.api.Placeholder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link MessageFormatter} implementation combining {@link MessageFormat}, simple replacements, and MiniMessage parsing.
 * Designed to support the fluent placeholder API exposed by {@link de.jexcellence.jextranslate.api.TranslationService},
 * ensuring repository templates can mix indexed, named, and MiniMessage placeholders across reload cycles.
 *
 * <p>Caches formatting metadata for performance while respecting strategy changes triggered by configuration reloads.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.1
 */
public class HybridMessageFormatter implements MessageFormatter {

    private static final Logger LOGGER = Logger.getLogger(HybridMessageFormatter.class.getName());
    private static final Pattern INDEXED_PLACEHOLDER = Pattern.compile("\\{(\\d+)(?:,([^}]+))?}");
    private static final Pattern NAMED_PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)(?:,([^}]+))?}");
    private static final Pattern PERCENT_PLACEHOLDER = Pattern.compile("%([a-zA-Z_][a-zA-Z0-9_]*)%");
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<[^>]+>");

    private final Map<String, MessageFormat> formatCache = new ConcurrentHashMap<>();
    private final Map<String, ValidationResult> validationCache = new ConcurrentHashMap<>();
    private volatile FormattingStrategy strategy = FormattingStrategy.HYBRID;
    private volatile MiniMessage miniMessage;

    /**
     * Creates a formatter using {@link FormattingStrategy#HYBRID} and the default {@link MiniMessage} instance.
     */
    public HybridMessageFormatter() {
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Creates a formatter with the supplied strategy.
     *
     * @param strategy the initial formatting strategy to apply
     */
    public HybridMessageFormatter(@NotNull final FormattingStrategy strategy) {
        this();
        this.strategy = Objects.requireNonNull(strategy, "Strategy cannot be null");
    }

    @Override
    @NotNull
    public String formatText(@NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Locale locale) throws FormattingException {
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(placeholders, "Placeholders cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");

        try {
            return switch (this.strategy) {
                case MESSAGE_FORMAT -> formatWithMessageFormat(template, placeholders, locale);
                case SIMPLE_REPLACEMENT -> formatWithSimpleReplacement(template, placeholders, locale);
                case HYBRID -> formatWithHybrid(template, placeholders, locale);
                case MINI_MESSAGE -> formatWithMiniMessage(template, placeholders, locale);
            };
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to format message template: " + template, exception);
            throw new FormattingException("Failed to format message: " + exception.getMessage(), template, placeholders, exception);
        }
    }

    @Override
    @NotNull
    public Component formatComponent(@NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Locale locale) {
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(placeholders, "Placeholders cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");

        try {
            if (this.strategy == FormattingStrategy.MINI_MESSAGE || containsMiniMessageTags(template) || hasComponentPlaceholders(placeholders)) {
                return formatComponentWithRichText(template, placeholders, locale);
            } else {
                final String formatted = formatText(template, placeholders, locale);
                return Component.text(formatted);
            }
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to format component template: " + template, exception);
            return Component.text(template);
        }
    }

    @Override
    @NotNull
    public ValidationResult validateTemplate(@NotNull final String template) {
        Objects.requireNonNull(template, "Template cannot be null");
        return this.validationCache.computeIfAbsent(template, this::performValidation);
    }

    @Override
    @NotNull
    public FormattingStrategy getStrategy() {
        return this.strategy;
    }

    @Override
    public void setStrategy(@NotNull final FormattingStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "Strategy cannot be null");
        this.formatCache.clear();
        this.validationCache.clear();
    }

    /**
     * Replaces the MiniMessage instance used when formatting rich text components. Primarily used during configuration
     * reloads to apply custom tags or resolvers.
     *
     * @param miniMessage the MiniMessage instance to use
     */
    public void setMiniMessage(@NotNull final MiniMessage miniMessage) {
        this.miniMessage = Objects.requireNonNull(miniMessage, "MiniMessage cannot be null");
    }

    @NotNull
    private String formatWithMessageFormat(@NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Locale locale) {
        final Object[] arguments = resolvePlaceholdersToArguments(placeholders);
        final MessageFormat messageFormat = getOrCreateMessageFormat(template, locale);

        try {
            return messageFormat.format(arguments);
        } catch (final IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "MessageFormat failed for template: " + template, exception);
            return formatWithFallback(template, placeholders);
        }
    }

    @NotNull
    private String formatWithSimpleReplacement(@NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Locale locale) {
        String result = template;
        for (final Placeholder placeholder : placeholders) {
            final String braceKey = "{" + placeholder.key() + "}";
            final String percentKey = "%" + placeholder.key() + "%";
            final String value = placeholder.asText();
            result = result.replace(braceKey, value);
            result = result.replace(percentKey, value);
        }
        return result;
    }

    @NotNull
    private String formatWithHybrid(@NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Locale locale) {
        final String processedTemplate = convertNamedToIndexed(template, placeholders);
        return formatWithMessageFormat(processedTemplate, placeholders, locale);
    }

    @NotNull
    private String formatWithMiniMessage(@NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Locale locale) {
        String processedTemplate = formatWithSimpleReplacement(template, placeholders, locale);

        if (containsMiniMessageTags(processedTemplate)) {
            try {
                final Component component = this.miniMessage.deserialize(processedTemplate);
                return PlainTextComponentSerializer.plainText().serialize(component);
            } catch (final Exception exception) {
                LOGGER.log(Level.WARNING, "MiniMessage parsing failed, falling back to simple replacement", exception);
                return processedTemplate;
            }
        }
        return processedTemplate;
    }

    @NotNull
    private Component formatComponentWithRichText(@NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Locale locale) {
        final Map<String, Component> componentReplacements = new HashMap<>();
        final Map<String, String> stringReplacements = new HashMap<>();

        for (final Placeholder placeholder : placeholders) {
            final String braceKey = "{" + placeholder.key() + "}";
            final String percentKey = "%" + placeholder.key() + "%";

            if (placeholder.type() == Placeholder.PlaceholderType.RICH_TEXT) {
                componentReplacements.put(braceKey, placeholder.asComponent());
                componentReplacements.put(percentKey, placeholder.asComponent());
            } else {
                stringReplacements.put(braceKey, placeholder.asText());
                stringReplacements.put(percentKey, placeholder.asText());
            }
        }

        String processedTemplate = template;
        for (final Map.Entry<String, String> entry : stringReplacements.entrySet()) {
            processedTemplate = processedTemplate.replace(entry.getKey(), entry.getValue());
        }

        Component result;
        if (containsMiniMessageTags(processedTemplate)) {
            try {
                result = this.miniMessage.deserialize(processedTemplate);
            } catch (final Exception exception) {
                LOGGER.log(Level.WARNING, "MiniMessage parsing failed, using plain text", exception);
                result = Component.text(processedTemplate);
            }
        } else {
            result = Component.text(processedTemplate);
        }

        for (final Map.Entry<String, Component> entry : componentReplacements.entrySet()) {
            final String plainResult = PlainTextComponentSerializer.plainText().serialize(result);
            if (plainResult.contains(entry.getKey())) {
                final String replacedText = plainResult.replace(entry.getKey(), PlainTextComponentSerializer.plainText().serialize(entry.getValue()));
                result = Component.text(replacedText);
            }
        }

        return result;
    }

    @NotNull
    private Object[] resolvePlaceholdersToArguments(@NotNull final List<Placeholder> placeholders) {
        final Object[] arguments = new Object[placeholders.size()];
        for (int i = 0; i < placeholders.size(); i++) {
            final Placeholder placeholder = placeholders.get(i);
            arguments[i] = switch (placeholder.type()) {
                case NUMBER -> placeholder.value() instanceof Number ? placeholder.value() : placeholder.asText();
                case DATE_TIME -> {
                    if (placeholder.value() instanceof java.time.LocalDateTime localDateTime) {
                        yield java.sql.Timestamp.valueOf(localDateTime);
                    } else {
                        yield placeholder.asText();
                    }
                }
                case RICH_TEXT -> placeholder.asText();
                default -> placeholder.asText();
            };
        }
        return arguments;
    }

    @NotNull
    private MessageFormat getOrCreateMessageFormat(@NotNull final String template, @NotNull final Locale locale) {
        final String cacheKey = template + "_" + locale.toString();
        return this.formatCache.computeIfAbsent(cacheKey, key -> {
            try {
                return new MessageFormat(template, locale);
            } catch (final IllegalArgumentException exception) {
                LOGGER.log(Level.WARNING, "Invalid MessageFormat pattern: " + template, exception);
                try {
                    final String fallbackTemplate = createFallbackTemplate(template);
                    return new MessageFormat(fallbackTemplate, locale);
                } catch (final Exception fallbackException) {
                    LOGGER.log(Level.WARNING, "Fallback MessageFormat creation failed", fallbackException);
                    return new MessageFormat("{0}", locale);
                }
            }
        });
    }

    @NotNull
    private String convertNamedToIndexed(@NotNull final String template, @NotNull final List<Placeholder> placeholders) {
        final Map<String, Integer> keyToIndex = new HashMap<>();
        for (int i = 0; i < placeholders.size(); i++) {
            keyToIndex.put(placeholders.get(i).key(), i);
        }

        String result = template;
        for (final Placeholder placeholder : placeholders) {
            final String namedBraceKey = "{" + placeholder.key() + "}";
            final String namedPercentKey = "%" + placeholder.key() + "%";
            final Integer index = keyToIndex.get(placeholder.key());

            if (index != null) {
                final String indexedKey = "{" + index + "}";
                result = result.replace(namedBraceKey, indexedKey);
                result = result.replace(namedPercentKey, indexedKey);
            }
        }
        return result;
    }

    @NotNull
    private String formatWithFallback(@NotNull final String template, @NotNull final List<Placeholder> placeholders) {
        LOGGER.info("Using fallback formatting for template: " + template);
        String result = template;

        for (int i = 0; i < placeholders.size(); i++) {
            final String indexedKey = "{" + i + "}";
            if (result.contains(indexedKey)) {
                result = result.replace(indexedKey, placeholders.get(i).asText());
            }
        }

        for (final Placeholder placeholder : placeholders) {
            final String namedBraceKey = "{" + placeholder.key() + "}";
            final String namedPercentKey = "%" + placeholder.key() + "%";
            result = result.replace(namedBraceKey, placeholder.asText());
            result = result.replace(namedPercentKey, placeholder.asText());
        }

        return result;
    }

    private boolean hasComponentPlaceholders(@NotNull final List<Placeholder> placeholders) {
        return placeholders.stream().anyMatch(p -> p.type() == Placeholder.PlaceholderType.RICH_TEXT);
    }

    private boolean containsMiniMessageTags(@NotNull final String template) {
        return MINI_MESSAGE_TAG.matcher(template).find();
    }

    @NotNull
    private String createFallbackTemplate(@NotNull final String template) {
        String fallbackTemplate = template;
        fallbackTemplate = fallbackTemplate.replaceAll("\\{[^}]*\\\\[^}]*}", "{0}");
        if (fallbackTemplate.equals(template)) {
            fallbackTemplate = template.replaceAll("\\{[^}]+}", "{0}");
        }
        return fallbackTemplate;
    }

    @NotNull
    private ValidationResult performValidation(@NotNull final String template) {
        return new DefaultValidationResult(template);
    }

    /**
     * Default validation result implementation recording template diagnostics.
     */
    private static final class DefaultValidationResult implements ValidationResult {
        private final String template;
        private final List<String> errors;
        private final List<String> warnings;
        private final List<String> placeholders;

        DefaultValidationResult(@NotNull final String template) {
            this.template = template;
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
            this.placeholders = new ArrayList<>();
            validateTemplate();
        }

        private void validateTemplate() {
            try {
                new MessageFormat(this.template);

                final Matcher indexedMatcher = INDEXED_PLACEHOLDER.matcher(this.template);
                while (indexedMatcher.find()) {
                    this.placeholders.add(indexedMatcher.group(1));
                }

                final Matcher namedMatcher = NAMED_PLACEHOLDER.matcher(this.template);
                while (namedMatcher.find()) {
                    this.placeholders.add(namedMatcher.group(1));
                }

                final Matcher percentMatcher = PERCENT_PLACEHOLDER.matcher(this.template);
                while (percentMatcher.find()) {
                    this.placeholders.add(percentMatcher.group(1));
                }

                if (this.placeholders.isEmpty()) {
                    this.warnings.add("Template contains no placeholders");
                }

                if (MINI_MESSAGE_TAG.matcher(this.template).find()) {
                    this.warnings.add("Template contains MiniMessage tags - consider using MINI_MESSAGE strategy");
                }

                final boolean hasIndexed = INDEXED_PLACEHOLDER.matcher(this.template).find();
                final boolean hasNamed = NAMED_PLACEHOLDER.matcher(this.template).find();
                final boolean hasPercent = PERCENT_PLACEHOLDER.matcher(this.template).find();

                if ((hasIndexed && hasNamed) || (hasIndexed && hasPercent) || (hasNamed && hasPercent)) {
                    this.warnings.add("Template mixes different placeholder types - consider using HYBRID strategy");
                }
            } catch (final IllegalArgumentException exception) {
                this.errors.add("Invalid MessageFormat syntax: " + exception.getMessage());
            }
        }

        @Override
        public boolean isValid() {
            return this.errors.isEmpty();
        }

        @Override
        @NotNull
        public List<String> getErrors() {
            return List.copyOf(this.errors);
        }

        @Override
        @NotNull
        public List<String> getWarnings() {
            return List.copyOf(this.warnings);
        }

        @Override
        @NotNull
        public List<String> getPlaceholders() {
            return List.copyOf(this.placeholders);
        }
    }
}
