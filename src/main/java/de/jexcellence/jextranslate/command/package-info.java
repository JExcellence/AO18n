/**
 * Commands for maintaining runtime translation data within JExTranslate.
 *
 * <p><strong>Prerequisites.</strong>
 * <ul>
 *     <li>Invoke {@link de.jexcellence.jextranslate.api.TranslationService#configure(de.jexcellence.jextranslate.api.TranslationService.ServiceConfiguration)}
 *     before registering command executors or listeners so that repositories, resolvers, and formatters are available.</li>
 *     <li>Ensure {@link de.jexcellence.jextranslate.api.MissingKeyTracker MissingKeyTracker} and locale caches are cleared when
 *     repository contents, formatter implementations, or resolver strategies change (for example via the {@code /translate reload}
 *     subcommand) to avoid stale lookups.</li>
 * </ul>
 *
 * <p><strong>Expected command flows.</strong>
 * <ul>
 *     <li>{@code /translate missing [&lt;locale&gt;]} lists missing keys for console senders and opens the paginated GUI for players.</li>
 *     <li>{@code /translate add &lt;key&gt;} starts the interactive chat workflow for collecting new translations.</li>
 *     <li>{@code /translate stats} renders aggregated tracking statistics for missing keys.</li>
 *     <li>{@code /translate reload} refreshes repositories and invalidates caches before acknowledging success.</li>
 *     <li>{@code /translate backup [&lt;destination&gt;]} exports the active repository to disk and confirms the saved location.</li>
 *     <li>{@code /translate info} echoes the configured repository path, resolver strategy, and cache status.</li>
 * </ul>
 *
 * <p><strong>Localization and formatting.</strong>
 * <ul>
 *     <li>Command feedback should be localized through the active {@link de.jexcellence.jextranslate.api.LocaleResolver} and
 *     repository responses so players receive MiniMessage-rendered components in their negotiated locale.</li>
 *     <li>All components returned to the command framework must be compatible with Adventure's MiniMessage formatting; avoid
 *     raw strings for player-facing output and prefer pre-formatted {@link net.kyori.adventure.text.Component} instances produced
 *     by the configured {@link de.jexcellence.jextranslate.api.MessageFormatter} (typically
 *     {@link de.jexcellence.jextranslate.impl.MiniMessageFormatter}).</li>
 * </ul>
 */
package de.jexcellence.jextranslate.command;
