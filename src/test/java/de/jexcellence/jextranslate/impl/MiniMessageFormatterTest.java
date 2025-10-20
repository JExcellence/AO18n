package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.MessageFormatter;
import de.jexcellence.jextranslate.api.Placeholder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniMessageFormatterTest {

    private static final Locale LOCALE = Locale.ENGLISH;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
        .tags(TagResolver.resolver(StandardTags.defaults()))
        .build();

    private final MiniMessageFormatter formatter = new MiniMessageFormatter();

    @Test
    void formatTextReplacesPlaceholders() throws MessageFormatter.FormattingException {
        final String template = "Hello, {name}!";
        final List<Placeholder> placeholders = List.of(Placeholder.of("name", "Alex"));

        final String result = this.formatter.formatText(template, placeholders, LOCALE);

        assertEquals("Hello, Alex!", result);
    }

    @Test
    void formatComponentParsesMiniMessageWithPlaceholders() {
        final String template = "<bold>Hello, {name}!</bold>";
        final List<Placeholder> placeholders = List.of(Placeholder.of("name", "Alex"));

        final Component result = this.formatter.formatComponent(template, placeholders, LOCALE);

        final Component expected = MINI_MESSAGE.deserialize("<bold>Hello, Alex!</bold>");
        assertEquals(expected, result);
    }

    @Test
    void formatComponentFallsBackToPlainTextOnMalformedMiniMessage() {
        final String template = "<bold>{name";
        final List<Placeholder> placeholders = List.of(Placeholder.of("name", "Alex"));

        final Component result = this.formatter.formatComponent(template, placeholders, LOCALE);

        assertEquals(Component.text(template), result);
    }

    @Test
    void validateTemplateDetectsBalancedBracesAndPlaceholders() {
        final String template = "Welcome, {player}!";

        final MessageFormatter.ValidationResult result = this.formatter.validateTemplate(template);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(List.of("player"), result.getPlaceholders());
    }

    @Test
    void validateTemplateDetectsMismatchedBraces() {
        final String template = "Welcome, {player!";

        final MessageFormatter.ValidationResult result = this.formatter.validateTemplate(template);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).startsWith("Mismatched braces"));
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getPlaceholders().isEmpty());
    }

    @Test
    void strategyDefaultsToMiniMessageAndCanBeUpdated() {
        assertEquals(MessageFormatter.FormattingStrategy.MINI_MESSAGE, this.formatter.getStrategy());

        this.formatter.setStrategy(MessageFormatter.FormattingStrategy.SIMPLE_REPLACEMENT);

        assertEquals(MessageFormatter.FormattingStrategy.SIMPLE_REPLACEMENT, this.formatter.getStrategy());
    }
}
