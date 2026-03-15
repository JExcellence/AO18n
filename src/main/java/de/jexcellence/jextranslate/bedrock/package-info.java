/**
 * Bedrock Edition support utilities for JExTranslate.
 *
 * <p>This package provides utilities for detecting Bedrock Edition players and converting
 * Adventure Components and MiniMessage strings to Bedrock-compatible legacy format.
 * Bedrock Edition clients only support legacy color codes (§ codes) and plain strings,
 * not MiniMessage or Adventure Components with advanced features like click events,
 * hover events, or gradients.
 *
 * <p>Key components:
 * <ul>
 *   <li>{@link de.jexcellence.jextranslate.bedrock.HexColorFallback} - Options for handling hex colors on Bedrock</li>
 *   <li>{@link de.jexcellence.jextranslate.bedrock.BedrockFormatMode} - Compatibility mode selection</li>
 *   <li>{@link de.jexcellence.jextranslate.bedrock.BedrockConverter} - Utility for converting messages to Bedrock format</li>
 *   <li>{@link de.jexcellence.jextranslate.bedrock.BedrockDetectionCache} - Caching for Bedrock player detection</li>
 * </ul>
 *
 * @see de.jexcellence.jextranslate.R18nManager
 * @see de.jexcellence.jextranslate.MessageBuilder
 */
package de.jexcellence.jextranslate.bedrock;
