package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.Placeholder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridMessageFormatterTest {

    @Test
    void messageFormatStrategyFormatsIndexedPlaceholders() throws HybridMessageFormatter.FormattingException {
        final HybridMessageFormatter formatter = new HybridMessageFormatter(HybridMessageFormatter.FormattingStrategy.MESSAGE_FORMAT);
        final List<Placeholder> placeholders = List.of(
            Placeholder.of("player", "Alex"),
            Placeholder.of("score", 42),
            Placeholder.of("event_time", LocalDateTime.of(2024, Month.JANUARY, 2, 3, 4, 5))
        );
        final String template = "Player {0} scored {1,number,integer} points at {2,time,HH:mm}.";

        final String formatted = formatter.formatText(template, placeholders, Locale.US);
        assertEquals("Player Alex scored 42 points at 03:04.", formatted, "Indexed placeholders should be resolved using MessageFormat");

        final Component component = formatter.formatComponent(template, placeholders, Locale.US);
        assertEquals(Component.text("Player Alex scored 42 points at 03:04."), component, "Component formatting should mirror plain text output");
    }

    @Test
    void simpleReplacementStrategyHandlesMixedPlaceholderTypes() throws HybridMessageFormatter.FormattingException {
        final HybridMessageFormatter formatter = new HybridMessageFormatter(HybridMessageFormatter.FormattingStrategy.SIMPLE_REPLACEMENT);
        final List<Placeholder> placeholders = List.of(
            Placeholder.of("name", "Steve"),
            Placeholder.of("score", 1337),
            Placeholder.of("event_time", LocalDateTime.of(2025, Month.JUNE, 15, 18, 45), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            Placeholder.of("title", Component.text("Champion"))
        );
        final String template = "Player {name} scored %score% points on {event_time} as {title}.";

        final String formatted = formatter.formatText(template, placeholders, Locale.US);
        assertEquals("Player Steve scored 1337 points on 2025-06-15 18:45 as Champion.", formatted, "Simple replacement should substitute named and percent placeholders");

        final Component component = formatter.formatComponent(template, placeholders, Locale.US);
        assertEquals("Player Steve scored 1337 points on 2025-06-15 18:45 as Champion.", PlainTextComponentSerializer.plainText().serialize(component),
            "Component formatting should retain plain text output when using rich text placeholders");
    }

    @Test
    void hybridStrategySupportsNamedPlaceholdersAndComponents() throws HybridMessageFormatter.FormattingException {
        final HybridMessageFormatter formatter = new HybridMessageFormatter(HybridMessageFormatter.FormattingStrategy.HYBRID);
        final List<Placeholder> placeholders = List.of(
            Placeholder.of("name", "Alex"),
            Placeholder.of("score", 256),
            Placeholder.of("event_time", LocalDateTime.of(2030, Month.MARCH, 10, 7, 30, 0), DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
            Placeholder.of("title", Component.text("Hero"))
        );
        final String template = "Player {name} scored {1,number,integer} points at {2,time,HH:mm} as {title}.";

        final String formatted = formatter.formatText(template, placeholders, Locale.US);
        assertEquals("Player Alex scored 256 points at 07:30 as Hero.", formatted, "Hybrid strategy should combine named and indexed placeholders");

        final Component component = formatter.formatComponent(template, placeholders, Locale.US);
        assertEquals("Player Alex scored 256 points at 07:30 as Hero.", PlainTextComponentSerializer.plainText().serialize(component),
            "Component formatting should reflect hybrid placeholder resolution");
    }

    @Test
    void miniMessageStrategyParsesTemplatesAndProducesPlainText() throws HybridMessageFormatter.FormattingException {
        final HybridMessageFormatter formatter = new HybridMessageFormatter(HybridMessageFormatter.FormattingStrategy.MINI_MESSAGE);
        final List<Placeholder> placeholders = List.of(
            Placeholder.of("name", "Alex"),
            Placeholder.of("score", 9001),
            Placeholder.of("event_time", LocalDateTime.of(2040, Month.JANUARY, 1, 0, 0), DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            Placeholder.of("title", Component.text("Legend"))
        );
        final String template = "<green>{name}</green> has <yellow>{score}</yellow> power at {event_time} as {title}.";

        final String formatted = formatter.formatText(template, placeholders, Locale.US);
        assertEquals("Alex has 9001 power at 2040-01-01 as Legend.", formatted, "MiniMessage strategy should strip formatting to plain text");

        final Component component = formatter.formatComponent(template, placeholders, Locale.US);
        assertEquals("Alex has 9001 power at 2040-01-01 as Legend.", PlainTextComponentSerializer.plainText().serialize(component),
            "Component formatting should apply MiniMessage parsing and retain placeholder substitution");
    }

    @Test
    void invalidPatternFallsBackToSimpleReplacement() throws HybridMessageFormatter.FormattingException {
        final HybridMessageFormatter formatter = new HybridMessageFormatter(HybridMessageFormatter.FormattingStrategy.MESSAGE_FORMAT);
        final List<Placeholder> placeholders = List.of(Placeholder.of("value", "Fallback"));
        final String template = "Value {0";

        final String formatted = formatter.formatText(template, placeholders, Locale.US);
        assertEquals("Value Fallback", formatted, "Invalid MessageFormat patterns should fall back to manual replacement");
    }

    @Test
    void setStrategyClearsCaches() throws Exception {
        final HybridMessageFormatter formatter = new HybridMessageFormatter(HybridMessageFormatter.FormattingStrategy.MESSAGE_FORMAT);
        final List<Placeholder> placeholders = List.of(Placeholder.of("name", "Alex"));
        final String template = "Hello {0}";

        formatter.formatText(template, placeholders, Locale.US);
        formatter.validateTemplate(template);

        final Field formatCacheField = HybridMessageFormatter.class.getDeclaredField("formatCache");
        formatCacheField.setAccessible(true);
        final Field validationCacheField = HybridMessageFormatter.class.getDeclaredField("validationCache");
        validationCacheField.setAccessible(true);

        final Object formatCache = formatCacheField.get(formatter);
        final Object validationCache = validationCacheField.get(formatter);

        assertTrue(((java.util.Map<?, ?>) formatCache).size() > 0, "Formatting should populate the format cache");
        assertTrue(((java.util.Map<?, ?>) validationCache).size() > 0, "Validation should populate the validation cache");

        formatter.setStrategy(HybridMessageFormatter.FormattingStrategy.SIMPLE_REPLACEMENT);

        assertEquals(0, ((java.util.Map<?, ?>) formatCache).size(), "Changing strategy must clear the format cache");
        assertEquals(0, ((java.util.Map<?, ?>) validationCache).size(), "Changing strategy must clear the validation cache");
        assertEquals(HybridMessageFormatter.FormattingStrategy.SIMPLE_REPLACEMENT, formatter.getStrategy(), "Strategy should update after setStrategy call");
    }

    @Test
    void setMiniMessageReplacesParserAndAffectsFormatting() throws Exception {
        final HybridMessageFormatter formatter = new HybridMessageFormatter();
        formatter.setStrategy(HybridMessageFormatter.FormattingStrategy.MINI_MESSAGE);

        final MiniMessage customMiniMessage = MiniMessage.builder()
            .tags(TagResolver.resolver("cheer", (args, ctx) -> Tag.selfClosingInserting(Component.text("YAY"))))
            .build();

        formatter.setMiniMessage(customMiniMessage);

        final Field miniMessageField = HybridMessageFormatter.class.getDeclaredField("miniMessage");
        miniMessageField.setAccessible(true);
        final MiniMessage configuredMiniMessage = (MiniMessage) miniMessageField.get(formatter);
        assertSame(customMiniMessage, configuredMiniMessage, "Custom MiniMessage parser should replace the existing instance");

        final String formattedText = formatter.formatText("<cheer/>", List.of(), Locale.US);
        assertEquals("YAY", formattedText, "Formatting should use the custom MiniMessage parser");

        final Component formattedComponent = formatter.formatComponent("<cheer/>", List.of(), Locale.US);
        assertEquals("YAY", PlainTextComponentSerializer.plainText().serialize(formattedComponent),
            "Component formatting should also leverage the custom MiniMessage parser");
    }

    @Test
    void validateTemplateDetectsMixedPlaceholderTypesAndMiniMessageTags() {
        final HybridMessageFormatter formatter = new HybridMessageFormatter();
        final HybridMessageFormatter.ValidationResult result = formatter.validateTemplate("<bold>Hello {0} %player%</bold>");

        assertTrue(result.isValid(), "Template should remain valid despite warnings");
        assertTrue(result.getWarnings().contains("Template mixes different placeholder types - consider using HYBRID strategy"),
            "Validation should warn about mixed placeholder types");
        assertTrue(result.getWarnings().contains("Template contains MiniMessage tags - consider using MINI_MESSAGE strategy"),
            "Validation should warn about MiniMessage usage");
    }

    @Test
    void validateTemplateReportsErrorsForInvalidMessageFormat() {
        final HybridMessageFormatter formatter = new HybridMessageFormatter();
        final HybridMessageFormatter.ValidationResult result = formatter.validateTemplate("Hello {name");

        assertFalse(result.isValid(), "Invalid MessageFormat syntax should invalidate the template");
        assertFalse(result.getErrors().isEmpty(), "Errors collection should contain parsing diagnostics");
        assertTrue(result.getErrors().getFirst().startsWith("Invalid MessageFormat syntax"),
            "Validation should report invalid MessageFormat syntax");
    }
}
