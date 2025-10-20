package de.jexcellence.jextranslate.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MessageFormatterFormattingExceptionTest {

    @Test
    void constructorCopiesPlaceholdersAndRetainsTemplate() {
        List<Placeholder> source = new ArrayList<>();
        source.add(Placeholder.of("player", "Alex"));
        source.add(Placeholder.of("score", 42));

        List<Placeholder> expected = List.copyOf(source);

        MessageFormatter.FormattingException exception =
                new MessageFormatter.FormattingException(
                        "Unable to format template",
                        "game.win",
                        source
                );

        assertEquals("game.win", exception.getTemplate(), "Template should match constructor argument");
        assertEquals(expected, exception.getPlaceholders(), "Placeholders should be copied");

        source.add(Placeholder.of("bonus", "true"));

        assertEquals(expected, exception.getPlaceholders(), "Copied placeholders should not change when source mutates");
    }

    @Test
    void constructorWithCauseRetainsProvidedCause() {
        List<Placeholder> source = new ArrayList<>();
        source.add(Placeholder.of("player", "Alex"));

        List<Placeholder> expected = List.copyOf(source);
        RuntimeException cause = new RuntimeException("formatter failed");

        MessageFormatter.FormattingException exception =
                new MessageFormatter.FormattingException(
                        "Unable to format template",
                        "game.loss",
                        source,
                        cause
                );

        assertEquals("game.loss", exception.getTemplate(), "Template should match constructor argument");
        assertEquals(expected, exception.getPlaceholders(), "Placeholders should be copied");
        assertSame(cause, exception.getCause(), "Cause should be the instance provided to the constructor");

        source.add(Placeholder.of("bonus", "false"));

        assertEquals(expected, exception.getPlaceholders(), "Copied placeholders should not change when source mutates");
    }
}
