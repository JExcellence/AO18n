package de.jexcellence.jextranslate.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for formatting translation lines as item lore.
 * Handles line wrapping, legacy color conversion, and MiniMessage preservation.
 *
 * @author JExcellence
 * @since 3.0.0
 */
public final class LoreFormatter {

    /** Default maximum width for lore lines. */
    public static final int DEFAULT_MAX_WIDTH = 40;

    /** Legacy section symbol serializer. */
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    /** Plain text serializer. */
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();

    private LoreFormatter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Formats components as legacy lore strings with default width.
     *
     * @param components the components to format
     * @return list of legacy-formatted lore strings
     */
    @NotNull
    public static List<String> formatLore(@NotNull final List<Component> components) {
        return formatLore(components, DEFAULT_MAX_WIDTH);
    }

    /**
     * Formats components as legacy lore strings with custom width.
     *
     * @param components the components to format
     * @param maxWidth   maximum characters per line before wrapping
     * @return list of legacy-formatted lore strings
     */
    @NotNull
    public static List<String> formatLore(@NotNull final List<Component> components, final int maxWidth) {
        Objects.requireNonNull(components, "Components cannot be null");
        if (maxWidth < 10) {
            throw new IllegalArgumentException("Max width must be at least 10");
        }

        final List<String> lore = new ArrayList<>();

        for (final Component component : components) {
            final String legacy = LEGACY_SERIALIZER.serialize(component);
            
            if (getVisibleLength(legacy) <= maxWidth) {
                lore.add(legacy);
            } else {
                lore.addAll(wrapLine(legacy, maxWidth));
            }
        }

        return lore;
    }

    /**
     * Formats plain strings as lore with automatic wrapping.
     *
     * @param lines    the lines to format
     * @param maxWidth maximum characters per line
     * @return list of wrapped lore strings
     */
    @NotNull
    public static List<String> formatPlainLore(@NotNull final List<String> lines, final int maxWidth) {
        Objects.requireNonNull(lines, "Lines cannot be null");
        if (maxWidth < 10) {
            throw new IllegalArgumentException("Max width must be at least 10");
        }

        final List<String> lore = new ArrayList<>();

        for (final String line : lines) {
            if (line.length() <= maxWidth) {
                lore.add(line);
            } else {
                lore.addAll(wrapPlainLine(line, maxWidth));
            }
        }

        return lore;
    }

    /**
     * Wraps a legacy-formatted line preserving color codes.
     */
    @NotNull
    private static List<String> wrapLine(@NotNull final String line, final int maxWidth) {
        final List<String> wrapped = new ArrayList<>();
        
        // Track the last color code to carry over to next line
        String lastColorCode = "";
        StringBuilder currentLine = new StringBuilder();
        int visibleLength = 0;
        
        int i = 0;
        while (i < line.length()) {
            // Check for color code (§X)
            if (i < line.length() - 1 && line.charAt(i) == '§') {
                final char code = line.charAt(i + 1);
                final String colorCode = "§" + code;
                
                // Reset codes clear the color state
                if (code == 'r' || code == 'R') {
                    lastColorCode = "";
                } else if (isColorCode(code)) {
                    lastColorCode = colorCode;
                } else if (isFormatCode(code)) {
                    lastColorCode += colorCode;
                }
                
                currentLine.append(colorCode);
                i += 2;
                continue;
            }
            
            // Check if we need to wrap
            if (visibleLength >= maxWidth && line.charAt(i) == ' ') {
                wrapped.add(currentLine.toString());
                currentLine = new StringBuilder(lastColorCode);
                visibleLength = 0;
                i++; // Skip the space
                continue;
            }
            
            // Add character
            currentLine.append(line.charAt(i));
            visibleLength++;
            i++;
        }
        
        // Add remaining content
        if (currentLine.length() > 0) {
            wrapped.add(currentLine.toString());
        }
        
        return wrapped;
    }

    /**
     * Wraps a plain text line at word boundaries.
     */
    @NotNull
    private static List<String> wrapPlainLine(@NotNull final String line, final int maxWidth) {
        final List<String> wrapped = new ArrayList<>();
        final String[] words = line.split(" ");
        final StringBuilder currentLine = new StringBuilder();

        for (final String word : words) {
            if (currentLine.length() + word.length() + 1 > maxWidth && currentLine.length() > 0) {
                wrapped.add(currentLine.toString());
                currentLine.setLength(0);
            }
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }

        if (currentLine.length() > 0) {
            wrapped.add(currentLine.toString());
        }

        return wrapped;
    }

    /**
     * Gets the visible length of a string (excluding color codes).
     *
     * @param text text that may contain legacy color codes
     * @return number of visible characters after color-code stripping
     */
    public static int getVisibleLength(@NotNull final String text) {
        int length = 0;
        boolean skipNext = false;
        
        for (int i = 0; i < text.length(); i++) {
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (text.charAt(i) == '§' && i < text.length() - 1) {
                skipNext = true;
                continue;
            }
            length++;
        }
        
        return length;
    }

    /**
     * Strips all color codes from a string.
     *
     * @param text text that may contain legacy color codes
     * @return text with legacy color codes removed
     */
    @NotNull
    public static String stripColors(@NotNull final String text) {
        final StringBuilder result = new StringBuilder();
        boolean skipNext = false;
        
        for (int i = 0; i < text.length(); i++) {
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (text.charAt(i) == '§' && i < text.length() - 1) {
                skipNext = true;
                continue;
            }
            result.append(text.charAt(i));
        }
        
        return result.toString();
    }

    /**
     * Converts a Component to legacy lore format.
     *
     * @param component component to serialize
     * @return legacy-string representation of the component
     */
    @NotNull
    public static String toLegacy(@NotNull final Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Converts a Component to plain text.
     *
     * @param component component to serialize
     * @return plain-text representation of the component
     */
    @NotNull
    public static String toPlainText(@NotNull final Component component) {
        return PLAIN_SERIALIZER.serialize(component);
    }

    private static boolean isColorCode(final char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isFormatCode(final char c) {
        return c == 'k' || c == 'K' || // Obfuscated
               c == 'l' || c == 'L' || // Bold
               c == 'm' || c == 'M' || // Strikethrough
               c == 'n' || c == 'N' || // Underline
               c == 'o' || c == 'O';   // Italic
    }
}
