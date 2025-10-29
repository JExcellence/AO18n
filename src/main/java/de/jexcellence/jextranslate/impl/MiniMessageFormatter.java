package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.MessageFormatter;
import de.jexcellence.jextranslate.api.Placeholder;
import de.jexcellence.jextranslate.util.TranslationLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
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
 * @version 1.0.3
 */
public class MiniMessageFormatter implements MessageFormatter {

    private static final Logger LOGGER = TranslationLogger.getLogger(MiniMessageFormatter.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_-]+)}");

    private final List<TagResolver> globalResolvers = new CopyOnWriteArrayList<>();
    private FormattingStrategy strategy = FormattingStrategy.MINI_MESSAGE;

    /**
     * Creates a formatter using {@link FormattingStrategy#MINI_MESSAGE}.
     */
    public MiniMessageFormatter() {
    }

    /**
     * Creates a formatter with the supplied strategy.
     *
     * @param strategy the initial formatting strategy to apply
     */
    public MiniMessageFormatter(@NotNull final FormattingStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "Strategy cannot be null");
    }

    @Override
    @NotNull
    public String formatText(@NotNull final String template, @NotNull final List<Placeholder> placeholders, @NotNull final Locale locale) throws FormattingException {
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(placeholders, "Placeholders cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");

        try {
            return applyPlainPlaceholderReplacement(template, placeholders);
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
            if (this.strategy != FormattingStrategy.MINI_MESSAGE
                    && !containsMiniMessageMarkup(template)
                    && !hasComponentPlaceholders(placeholders)) {
                final String formatted = formatText(template, placeholders, locale);
                return Component.text(formatted);
            }

            final TagResolver resolver = resolveTagResolvers(placeholders);
            final MiniMessage miniMessage = MiniMessage.builder()
                    .tags(resolver)
                    .build();
            return miniMessage.deserialize(template);
        } catch (final Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "MiniMessage formatting failed",
                            Map.of("template", template)
                    ),
                    exception
            );
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
     * Registers an additional {@link TagResolver} that should be applied to every MiniMessage render.
     *
     * @param resolver the resolver to register
     */
    public void registerGlobalResolver(@NotNull final TagResolver resolver) {
        this.globalResolvers.add(Objects.requireNonNull(resolver, "Resolver cannot be null"));
    }

    /**
     * Removes all previously registered global resolvers.
     */
    public void clearGlobalResolvers() {
        this.globalResolvers.clear();
    }

    /**
     * Returns the currently registered global resolvers.
     *
     * @return immutable snapshot of global resolvers
     */
    @NotNull
    public List<TagResolver> getGlobalResolvers() {
        return List.copyOf(this.globalResolvers);
    }

    private String applyPlainPlaceholderReplacement(@NotNull final String template, @NotNull final List<Placeholder> placeholders) {
        String result = template;
        for (final Placeholder placeholder : placeholders) {
            final String key = "{" + placeholder.key() + "}";
            result = result.replace(key, placeholder.asText());
        }
        return result;
    }

    @NotNull
    private TagResolver resolveTagResolvers(@NotNull final List<Placeholder> placeholders) {
        final TagResolver.Builder builder = TagResolver.builder();
        builder.resolver(StandardTags.defaults());
        for (final TagResolver resolver : this.globalResolvers) {
            builder.resolver(resolver);
        }
        for (final Placeholder placeholder : placeholders) {
            addPlaceholderResolver(builder, placeholder);
        }
        return builder.build();
    }

    private void addPlaceholderResolver(@NotNull final TagResolver.Builder builder, @NotNull final Placeholder placeholder) {
        if (placeholder.type() == Placeholder.PlaceholderType.RICH_TEXT) {
            final Component component = placeholder.asComponent();
            builder.resolver(net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component(placeholder.key(), component));
            return;
        }
        final String escapedValue = escapeValue(placeholder.asText());
        builder.resolver(net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(placeholder.key(), escapedValue));
    }

    private boolean hasComponentPlaceholders(@NotNull final List<Placeholder> placeholders) {
        for (final Placeholder placeholder : placeholders) {
            if (placeholder.type() == Placeholder.PlaceholderType.RICH_TEXT) {
                return true;
            }
        }
        return false;
    }

    private boolean containsMiniMessageMarkup(@NotNull final String template) {
        final int open = template.indexOf('<');
        final int close = template.indexOf('>');
        return open >= 0 && close > open;
    }

    @NotNull
    private String escapeValue(@NotNull final String value) {
        return value
                .replace("<", "\\<")
                .replace(">", "\\>");
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
