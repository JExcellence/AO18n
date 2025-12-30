package de.jexcellence.jextranslate.bedrock;

/**
 * Defines how hex colors should be handled when converting messages for Bedrock Edition players.
 * <p>
 * Bedrock Edition clients have limited color support compared to Java Edition. While Java Edition
 * supports full RGB hex colors (e.g., {@code #FF5733}), Bedrock Edition only supports the 16
 * legacy color codes (§0-§9, §a-§f). This enum specifies the fallback strategy when hex colors
 * are encountered.
 *
 * @since 3.1.0
 */
public enum HexColorFallback {

    /**
     * Completely removes hex colors from the message.
     * <p>
     * Text that was colored with hex codes will appear in the default color (usually white).
     * This is the safest option but results in loss of color information.
     * <p>
     * Example: {@code <#FF5733>Hello</color>} becomes {@code Hello}
     */
    STRIP,

    /**
     * Converts hex colors to the nearest matching legacy color using color distance calculation.
     * <p>
     * Uses Euclidean distance in RGB color space to find the closest of the 16 legacy colors.
     * This preserves the general color intent while ensuring Bedrock compatibility.
     * <p>
     * Example: {@code <#FF5733>Hello</color>} becomes {@code §6Hello} (gold, the nearest legacy color)
     */
    NEAREST_LEGACY,

    /**
     * Converts hex colors to grayscale equivalents (white, gray, or dark gray).
     * <p>
     * Calculates the luminance of the hex color and maps it to one of three grayscale
     * legacy colors: white (§f), gray (§7), or dark gray (§8). This is useful when
     * color distinction is less important than brightness distinction.
     * <p>
     * Example: {@code <#FF5733>Hello</color>} becomes {@code §7Hello} (gray)
     */
    GRAYSCALE
}
