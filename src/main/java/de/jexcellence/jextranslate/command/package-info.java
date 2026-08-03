/**
 * Commands for maintaining runtime translation data within JExTranslate.
 *
 * <p><strong>Available Commands.</strong>
 * <ul>
 *     <li>{@link PR18n} - Command-framework bridge that delegates to the runtime handler.</li>
 *     <li>{@link PR18nCommand} - Modern R18n command for translation management using the new API.</li>
 * </ul>
 *
 * <p><strong>Expected command flows.</strong>
 * <ul>
 *     <li>{@code /r18n reload} refreshes translations and invalidates caches.</li>
 *     <li>{@code /r18n missing [page]} lists missing translation keys.</li>
 * </ul>
 *
 * <p><strong>Localization and formatting.</strong>
 * <ul>
 *     <li>All components use Adventure's MiniMessage formatting for player-facing output.</li>
 * </ul>
 */
package de.jexcellence.jextranslate.command;
