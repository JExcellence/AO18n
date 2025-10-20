package de.jexcellence.jextranslate.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceholderTest {

    @Test
    void textPlaceholderRetainsKeyValueAndSerializesToComponent() {
        final Placeholder placeholder = Placeholder.of("player", "Notch");

        assertEquals("player", placeholder.key(), "Placeholder key should be retained");
        assertEquals("Notch", placeholder.value(), "Placeholder value should be retained");
        assertEquals("Notch", placeholder.asText(), "Plain text serialization should return the raw value");
        assertEquals(Component.text("Notch"), placeholder.asComponent(), "Component fallback should produce a text component");
        assertEquals(Placeholder.PlaceholderType.TEXT, placeholder.type(), "Type should report TEXT");
    }

    @Test
    void numberPlaceholderUsesDefaultFormattingAndComponentFallback() {
        final Placeholder placeholder = Placeholder.of("balance", 42.5d);

        assertEquals("balance", placeholder.key(), "Placeholder key should be retained");
        assertEquals(42.5d, placeholder.value(), "Placeholder value should be retained");
        assertEquals("42.5", placeholder.asText(), "Default formatting should rely on Number#toString()");
        assertEquals(Component.text("42.5"), placeholder.asComponent(), "Component fallback should use plain text output");
        assertEquals(Placeholder.PlaceholderType.NUMBER, placeholder.type(), "Type should report NUMBER");
    }

    @Test
    void numberPlaceholderUsesCustomNumberFormatWhenProvided() {
        final NumberFormat germanFormat = NumberFormat.getInstance(Locale.GERMANY);
        final Placeholder placeholder = Placeholder.of("balance", 1337_42.5d, germanFormat);

        final String expectedText = germanFormat.format(1337_42.5d);
        assertEquals(expectedText, placeholder.asText(), "Custom format should be applied to numeric placeholders");
        assertEquals(Component.text(expectedText), placeholder.asComponent(), "Component fallback should reflect formatted text");
        assertEquals(Placeholder.PlaceholderType.NUMBER, placeholder.type(), "Type should report NUMBER");
    }

    @Test
    void richTextPlaceholderRetainsComponentAndUsesPlainTextSerialization() {
        final Component component = Component.text("<gold>Champion</gold>").color(NamedTextColor.GOLD);
        final Placeholder placeholder = Placeholder.of("title", component);

        assertEquals("title", placeholder.key(), "Placeholder key should be retained");
        assertSame(component, placeholder.value(), "Rich text placeholder should expose the original component as value()");
        final String expectedPlain = PlainTextComponentSerializer.plainText().serialize(component);
        assertEquals(expectedPlain, placeholder.asText(), "Plain text serialization should use PlainTextComponentSerializer");
        assertSame(component, placeholder.asComponent(), "Rich text placeholder should expose the original component");
        assertEquals(Placeholder.PlaceholderType.RICH_TEXT, placeholder.type(), "Type should report RICH_TEXT");
    }

    @Test
    void dateTimePlaceholderUsesIsoFormattingAndComponentFallback() {
        final LocalDateTime dateTime = LocalDateTime.of(2024, Month.JULY, 18, 10, 15, 30);
        final Placeholder placeholder = Placeholder.of("event_time", dateTime);

        assertEquals("event_time", placeholder.key(), "Placeholder key should be retained");
        assertSame(dateTime, placeholder.value(), "DateTime placeholder should expose the original LocalDateTime");
        final String expectedText = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(dateTime);
        assertEquals(expectedText, placeholder.asText(), "ISO formatter should be used when no formatter is provided");
        assertEquals(Component.text(expectedText), placeholder.asComponent(), "Component fallback should use formatted text");
        assertEquals(Placeholder.PlaceholderType.DATE_TIME, placeholder.type(), "Type should report DATE_TIME");
    }

    @Test
    void dateTimePlaceholderUsesCustomFormatterWhenProvided() {
        final LocalDateTime dateTime = LocalDateTime.of(2030, Month.DECEMBER, 25, 6, 0);
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
        final Placeholder placeholder = Placeholder.of("holiday", dateTime, formatter);

        final String expectedText = formatter.format(dateTime);
        assertEquals(expectedText, placeholder.asText(), "Custom formatter should control temporal serialization");
        assertEquals(Component.text(expectedText), placeholder.asComponent(), "Component fallback should reuse formatted text");
        assertEquals(Placeholder.PlaceholderType.DATE_TIME, placeholder.type(), "Type should report DATE_TIME");
    }

    @Test
    void customPlaceholderUsesFormatterFunctionAndComponentFallback() {
        final UUID identifier = UUID.randomUUID();
        final Placeholder placeholder = Placeholder.of("id", identifier, UUID::toString);

        assertEquals("id", placeholder.key(), "Placeholder key should be retained");
        assertSame(identifier, placeholder.value(), "Custom placeholder should expose the original value");
        final String expectedText = identifier.toString();
        assertEquals(expectedText, placeholder.asText(), "Formatter function should be applied to the custom value");
        assertEquals(Component.text(expectedText), placeholder.asComponent(), "Component fallback should use formatted text");
        assertEquals(Placeholder.PlaceholderType.CUSTOM, placeholder.type(), "Type should report CUSTOM");
    }

    @Test
    void textPlaceholderRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> Placeholder.of(null, "value"), "Null keys must be rejected");
        assertThrows(NullPointerException.class, () -> Placeholder.of("key", (String) null), "Null values must be rejected");
    }

    @Test
    void numberPlaceholderRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> Placeholder.of(null, 5), "Null keys must be rejected");
        assertThrows(NullPointerException.class, () -> Placeholder.of("amount", (Number) null), "Null values must be rejected");
    }

    @Test
    void numberPlaceholderWithFormatRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> Placeholder.of(null, 5, NumberFormat.getInstance()), "Null keys must be rejected");
        assertThrows(NullPointerException.class, () -> Placeholder.of("amount", (Number) null, NumberFormat.getInstance()), "Null values must be rejected");
    }

    @Test
    void richTextPlaceholderRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> Placeholder.of(null, Component.empty()), "Null keys must be rejected");
        assertThrows(NullPointerException.class, () -> Placeholder.of("title", (Component) null), "Null components must be rejected");
    }

    @Test
    void dateTimePlaceholderRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> Placeholder.of(null, LocalDateTime.now()), "Null keys must be rejected");
        assertThrows(NullPointerException.class, () -> Placeholder.of("when", (LocalDateTime) null), "Null date-times must be rejected");
    }

    @Test
    void dateTimePlaceholderWithFormatterRejectsNullArguments() {
        final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        assertThrows(NullPointerException.class, () -> Placeholder.of(null, LocalDateTime.now(), formatter), "Null keys must be rejected");
        assertThrows(NullPointerException.class, () -> Placeholder.of("when", (LocalDateTime) null, formatter), "Null date-times must be rejected");
    }

    @Test
    void customPlaceholderRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> Placeholder.of(null, "value", String::valueOf), "Null keys must be rejected");
        assertThrows(NullPointerException.class, () -> Placeholder.of("key", null, String::valueOf), "Null values must be rejected");
        assertThrows(NullPointerException.class, () -> Placeholder.of("key", "value", null), "Null formatter must be rejected");
    }
}
