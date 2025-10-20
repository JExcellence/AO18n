package de.jexcellence.jextranslate.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class TranslatedMessageTest {

    private static final TranslationKey KEY = TranslationKey.of("tests.example");
    private static final Component COMPONENT = MiniMessage.miniMessage().deserialize("<green>Hello</green> <red>World</red>");

    @Test
    void sendToDispatchesComponentToPlayer() {
        Player player = mock(Player.class);
        TranslatedMessage message = new TranslatedMessage(COMPONENT, KEY);

        message.sendTo(player);

        verify(player).sendMessage(COMPONENT);
        verifyNoMoreInteractions(player);
    }

    @Test
    void sendToFallsBackAndLogsWarningWhenAdventureSendFails() {
        Player player = mock(Player.class);
        TranslatedMessage message = new TranslatedMessage(COMPONENT, KEY);
        TestLogHandler handler = new TestLogHandler();
        Logger logger = Logger.getLogger(TranslatedMessage.class.getName());
        logger.addHandler(handler);

        doThrow(new IllegalStateException("Adventure failure")).when(player).sendMessage(COMPONENT);

        try {
            message.sendTo(player);
        } finally {
            logger.removeHandler(handler);
        }

        assertAll(
            () -> verify(player).sendMessage(COMPONENT),
            () -> verify(player).sendMessage(message.asLegacyText()),
            () -> assertTrue(handler.contains(Level.WARNING, "Failed to send message to player")),
            () -> assertFalse(handler.contains(Level.SEVERE, "Failed to send fallback"))
        );
    }

    @Test
    void sendToLogsSevereWhenFallbackAlsoFails() {
        Player player = mock(Player.class);
        TranslatedMessage message = new TranslatedMessage(COMPONENT, KEY);
        TestLogHandler handler = new TestLogHandler();
        Logger logger = Logger.getLogger(TranslatedMessage.class.getName());
        logger.addHandler(handler);

        doThrow(new IllegalStateException("Adventure failure")).when(player).sendMessage(COMPONENT);
        doThrow(new IllegalStateException("Legacy failure")).when(player).sendMessage(message.asLegacyText());

        try {
            message.sendTo(player);
        } finally {
            logger.removeHandler(handler);
        }

        assertAll(
            () -> verify(player).sendMessage(COMPONENT),
            () -> verify(player).sendMessage(message.asLegacyText()),
            () -> assertTrue(handler.contains(Level.WARNING, "Failed to send message to player")),
            () -> assertTrue(handler.contains(Level.SEVERE, "Failed to send fallback message"))
        );
    }

    @Test
    void sendActionBarDispatchesComponentAndLogsOnFailure() {
        Player player = mock(Player.class);
        TranslatedMessage message = new TranslatedMessage(COMPONENT, KEY);

        message.sendActionBar(player);
        verify(player).sendActionBar(COMPONENT);

        TestLogHandler handler = new TestLogHandler();
        Logger logger = Logger.getLogger(TranslatedMessage.class.getName());
        logger.addHandler(handler);
        doThrow(new IllegalStateException("Action bar failure")).when(player).sendActionBar(COMPONENT);

        try {
            message.sendActionBar(player);
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(handler.contains(Level.WARNING, "Failed to send action bar to player"));
    }

    @Test
    void sendTitleUsesDefaultDurationsAndEmptySubtitle() {
        Player player = mock(Player.class);
        TranslatedMessage message = new TranslatedMessage(COMPONENT, KEY);
        ArgumentCaptor<Title> captor = ArgumentCaptor.forClass(Title.class);

        message.sendTitle(player);

        verify(player).showTitle(captor.capture());
        Title title = captor.getValue();
        assertAll(
            () -> assertEquals(COMPONENT, title.title()),
            () -> assertEquals(Component.empty(), title.subtitle()),
            () -> assertEquals(Duration.ofMillis(500), title.times().fadeIn()),
            () -> assertEquals(Duration.ofSeconds(3), title.times().stay()),
            () -> assertEquals(Duration.ofSeconds(1), title.times().fadeOut())
        );
    }

    @Test
    void sendTitleAcceptsCustomParametersAndLogsOnFailure() {
        Player player = mock(Player.class);
        Component subtitle = Component.text("Subtitle");
        TranslatedMessage message = new TranslatedMessage(COMPONENT, KEY);
        ArgumentCaptor<Title> captor = ArgumentCaptor.forClass(Title.class);

        Duration fadeIn = Duration.ofSeconds(1);
        Duration stay = Duration.ofSeconds(2);
        Duration fadeOut = Duration.ofSeconds(3);

        message.sendTitle(player, subtitle, fadeIn, stay, fadeOut);
        verify(player).showTitle(captor.capture());

        Title sent = captor.getValue();
        assertAll(
            () -> assertEquals(COMPONENT, sent.title()),
            () -> assertEquals(subtitle, sent.subtitle()),
            () -> assertEquals(fadeIn, sent.times().fadeIn()),
            () -> assertEquals(stay, sent.times().stay()),
            () -> assertEquals(fadeOut, sent.times().fadeOut())
        );

        TestLogHandler handler = new TestLogHandler();
        Logger logger = Logger.getLogger(TranslatedMessage.class.getName());
        logger.addHandler(handler);
        doThrow(new IllegalStateException("Title failure")).when(player).showTitle(any(Title.class));

        try {
            message.sendTitle(player, subtitle, fadeIn, stay, fadeOut);
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(handler.contains(Level.WARNING, "Failed to send title to player"));
    }

    @Test
    void asPlainTextLegacyLengthContainsAndToStringAreConsistent() {
        TranslatedMessage message = new TranslatedMessage(COMPONENT, KEY);
        String plain = message.asPlainText();
        String legacy = message.asLegacyText();

        assertAll(
            () -> assertEquals("Hello World", plain),
            () -> assertEquals("§aHello §cWorld", legacy),
            () -> assertEquals(plain.length(), message.length()),
            () -> assertTrue(message.contains("hello")),
            () -> assertFalse(message.isEmpty()),
            () -> assertEquals("TranslatedMessage{key=" + KEY + ", text='" + plain + "'}", message.toString())
        );
    }

    @Test
    void isEmptyRecognizesWhitespaceOnlyMessages() {
        Component whitespace = Component.text("   \n\t");
        TranslatedMessage message = new TranslatedMessage(whitespace, TranslationKey.of("tests.empty"));

        assertAll(
            () -> assertTrue(message.isEmpty()),
            () -> assertEquals(PlainTextComponentSerializer.plainText().serialize(whitespace).length(), message.length())
        );
    }

    @Test
    void withKeyAppendPrependAndComponentImmutability() {
        TranslatedMessage original = new TranslatedMessage(Component.text("Base"), KEY);
        TranslationKey newKey = TranslationKey.of("tests.rekeyed");
        TranslatedMessage rekeyed = original.withKey(newKey);
        Component suffix = Component.text(" Suffix");
        Component prefix = Component.text("Prefix ");

        TranslatedMessage appended = original.append(suffix);
        TranslatedMessage prepended = original.prepend(prefix);
        Component originalComponent = original.component();

        assertAll(
            () -> assertSame(originalComponent, rekeyed.component()),
            () -> assertEquals(newKey, rekeyed.originalKey()),
            () -> assertEquals("Base", original.asPlainText()),
            () -> assertEquals("Base Suffix", appended.asPlainText()),
            () -> assertEquals("Prefix Base", prepended.asPlainText()),
            () -> assertSame(originalComponent, original.component()),
            () -> assertNotSame(originalComponent, appended.component()),
            () -> assertNotSame(originalComponent, prepended.component())
        );
    }

    @Test
    void splitLinesParsesMiniMessageLinesAndPlainTextLines() {
        Component base = Component.text("Line One\n<green>Line Two</green>\n   \nPlain Three");
        TranslatedMessage message = new TranslatedMessage(base, TranslationKey.of("tests.lines"));

        List<Component> lines = message.splitLines();
        assertEquals(3, lines.size());

        Component first = lines.get(0);
        Component second = lines.get(1);
        Component third = lines.get(2);

        assertAll(
            () -> assertEquals("Line One", PlainTextComponentSerializer.plainText().serialize(first)),
            () -> assertEquals("§aLine Two", LegacyComponentSerializer.legacySection().serialize(second)),
            () -> assertEquals("Plain Three", PlainTextComponentSerializer.plainText().serialize(third))
        );
    }

    @Test
    void toDebugStringSummarizesMessageDetails() {
        TranslatedMessage message = new TranslatedMessage(Component.text("Debug Line"), TranslationKey.of("tests.debug"));
        String debug = message.toDebugString();

        assertAll(
            () -> assertTrue(debug.contains("TranslatedMessage Debug:")),
            () -> assertTrue(debug.contains("Original Key: tests.debug")),
            () -> assertTrue(debug.contains("Plain Text: Debug Line")),
            () -> assertTrue(debug.contains("Length: " + message.length())),
            () -> assertTrue(debug.contains("Is Empty: " + message.isEmpty())),
            () -> assertTrue(debug.contains("Component: " + message.component()))
        );
    }

    private static final class TestLogHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            this.records.add(record);
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() {
            this.records.clear();
        }

        private boolean contains(Level level, String fragment) {
            return this.records.stream()
                .filter(record -> record.getLevel().equals(level))
                .anyMatch(record -> record.getMessage().contains(fragment));
        }
    }
}
