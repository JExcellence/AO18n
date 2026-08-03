package de.jexcellence.jextranslate.bedrock;

/**
 * Defines the formatting mode used when converting messages for Bedrock Edition players.
 *
 * <p>This enum controls the trade-off between maximum compatibility with all Bedrock clients
 * and preserving advanced formatting features that may work on newer Bedrock versions
 * or specific proxy configurations (e.g., Geyser/Floodgate).
 *
 * @since 3.1.0
 */
public enum BedrockFormatMode {

    /**
     * Maximum compatibility mode - converts all formatting to legacy § codes only.
 *
 * <p>This mode ensures messages display correctly on all Bedrock Edition clients,
     * regardless of version or proxy configuration. All hex colors are converted
     * according to the {@link HexColorFallback} setting, and gradients are reduced
     * to a single dominant color.
 *
 * <p>Supported formatting in this mode:
     * <ul>
     *   <li>Legacy colors: §0-§9, §a-§f (16 colors)</li>
     *   <li>Bold: §l</li>
     *   <li>Italic: §o</li>
     *   <li>Underline: §n (limited support on some clients)</li>
     *   <li>Strikethrough: §m (limited support on some clients)</li>
     *   <li>Obfuscated: §k</li>
     *   <li>Reset: §r</li>
     * </ul>
 *
 * <p>Stripped formatting:
     * <ul>
     *   <li>Click events</li>
     *   <li>Hover events</li>
     *   <li>Custom fonts</li>
     *   <li>Insertion text</li>
     *   <li>Hex colors (converted via {@link HexColorFallback})</li>
     *   <li>Gradients (reduced to dominant color)</li>
     * </ul>
 *
 * <p>This is the recommended mode for production servers with diverse player bases.
     */
    CONSERVATIVE,

    /**
     * Modern mode - preserves hex colors and attempts gradient rendering for newer Bedrock clients.
 *
 * <p>Some newer Bedrock versions and certain proxy setups (Geyser/Floodgate) may support
     * hex colors through JSON text or specific APIs. This mode preserves hex colors and
     * attempts to render gradients by applying character-by-character coloring.
 *
 * <p><strong>Warning:</strong> This mode may result in broken or missing formatting on
     * older Bedrock clients or incompatible proxy configurations. Use only when you are
     * certain your player base uses compatible clients.
 *
 * <p>Additional features attempted in this mode:
     * <ul>
     *   <li>Hex colors (preserved as-is)</li>
     *   <li>Gradients (character-by-character coloring)</li>
     * </ul>
 *
 * <p>Still stripped (not supported on any Bedrock client):
     * <ul>
     *   <li>Click events</li>
     *   <li>Hover events</li>
     *   <li>Custom fonts</li>
     *   <li>Insertion text</li>
     * </ul>
     */
    MODERN
}
