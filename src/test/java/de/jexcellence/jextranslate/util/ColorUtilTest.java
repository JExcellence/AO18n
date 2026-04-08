package de.jexcellence.jextranslate.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ColorUtil}.
 */
class ColorUtilTest {

    @Test
    void testConvertLegacyAmpersandColors() {
        assertEquals("<red>Hello", ColorUtil.convertLegacyToMiniMessage("&cHello"));
        assertEquals("<gold>Gold", ColorUtil.convertLegacyToMiniMessage("&6Gold"));
        assertEquals("<green>Green", ColorUtil.convertLegacyToMiniMessage("&aGreen"));
    }

    @Test
    void testConvertLegacySectionColors() {
        assertEquals("<red>Hello", ColorUtil.convertLegacyToMiniMessage("§cHello"));
        assertEquals("<gold>Gold", ColorUtil.convertLegacyToMiniMessage("§6Gold"));
    }

    @Test
    void testConvertFormatCodes() {
        assertEquals("<bold>Bold", ColorUtil.convertLegacyToMiniMessage("&lBold"));
        assertEquals("<italic>Italic", ColorUtil.convertLegacyToMiniMessage("&oItalic"));
        assertEquals("<underlined>Under", ColorUtil.convertLegacyToMiniMessage("&nUnder"));
        assertEquals("<strikethrough>Strike", ColorUtil.convertLegacyToMiniMessage("&mStrike"));
        assertEquals("<obfuscated>Magic", ColorUtil.convertLegacyToMiniMessage("&kMagic"));
        assertEquals("<reset>Reset", ColorUtil.convertLegacyToMiniMessage("&rReset"));
    }

    @Test
    void testConvertHexColors() {
        assertEquals("<#FF0000>Red", ColorUtil.convertLegacyToMiniMessage("&#FF0000Red"));
        assertEquals("<#00FF00>Green", ColorUtil.convertLegacyToMiniMessage("§#00FF00Green"));
    }

    @Test
    void testMixedColors() {
        String input = "&6Gold &cRed &lBold";
        String expected = "<gold>Gold <red>Red <bold>Bold";
        assertEquals(expected, ColorUtil.convertLegacyToMiniMessage(input));
    }

    @Test
    void testNoColors() {
        assertEquals("Plain text", ColorUtil.convertLegacyToMiniMessage("Plain text"));
    }

    @Test
    void testEmptyString() {
        assertEquals("", ColorUtil.convertLegacyToMiniMessage(""));
    }

    @Test
    void testStripColors() {
        assertEquals("Hello World", ColorUtil.stripColors("&cHello &aWorld"));
        assertEquals("Hello World", ColorUtil.stripColors("<red>Hello <green>World"));
        assertEquals("Plain", ColorUtil.stripColors("Plain"));
    }

    @Test
    void testHasColorCodes() {
        assertTrue(ColorUtil.hasColorCodes("&cRed"));
        assertTrue(ColorUtil.hasColorCodes("§aGreen"));
        assertTrue(ColorUtil.hasColorCodes("<red>Red"));
        assertFalse(ColorUtil.hasColorCodes("Plain text"));
    }
}
