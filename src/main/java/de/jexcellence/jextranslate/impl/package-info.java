/**
 * Concrete building blocks backing the {@code jextranslate} service layer.
 * <p>
 * Implementations in this package keep critical caches thread-safe and refreshable:
 * <ul>
 *     <li>{@link HybridMessageFormatter} maintains concurrent caches for compiled {@link java.text.MessageFormat}
 *         instances and template {@link de.jexcellence.jextranslate.api.MessageFormatter.ValidationResult ValidationResult}s.
 *         The caches are flushed whenever the active {@link de.jexcellence.jextranslate.api.MessageFormatter.FormattingStrategy}
 *         changes so reload flows or hot-swaps immediately pick up new formatting rules.</li>
 *     <li>{@link YamlTranslationRepository} stores flattened locale maps in a {@link java.util.concurrent.ConcurrentHashMap}.
 *         Repository reloads clear and rebuild the cache from disk before notifying listeners so downstream services can
 *         invalidate their own lookups.</li>
 *     <li>{@link SimpleMissingKeyTracker} accumulates tracked keys per-locale using concurrent collections, enabling
 *         diagnostics without blocking translation lookups.</li>
 * </ul>
 * <p>
 * MiniMessage handling is validated up-front: {@link HybridMessageFormatter#validateTemplate(String)} caches the result of
 * syntactic checks, warns when MiniMessage tags or mixed placeholder styles are detected, and allows callers to proactively
 * switch to the {@code MINI_MESSAGE} strategy before formatting occurs.
 * <p>
 * Locale resolvers created through {@link LocaleResolverProvider#createAutoDetecting(java.util.Locale)} cache user decisions
 * in memory while deferring to the most capable Bukkit/Paper API available. Each resolver shares a thread-safe store of player
 * locales, respects explicit overrides via {@link de.jexcellence.jextranslate.api.LocaleResolver#setPlayerLocale}, and falls
 * back to the configured default when client locale detection fails. Reload routines should clear per-player state when
 * repositories or formatters change to keep caches aligned.
 * <p>
 * Extension points:
 * <ul>
 *     <li>Create alternative {@link de.jexcellence.jextranslate.api.MessageFormatter} implementations or augment
 *         {@link HybridMessageFormatter} to support additional {@link de.jexcellence.jextranslate.api.MessageFormatter.FormattingStrategy}
 *         modes. New strategies should document when their caches must be invalidated and whether MiniMessage validation is
 *         required.</li>
 *     <li>Provide new {@link de.jexcellence.jextranslate.api.LocaleResolver} implementations for different server APIs by
 *         following the pattern used in {@link LocaleResolverProvider}. Inject them into the provider's selection logic so
 *         automatic detection or explicit configuration can prefer the new strategy.</li>
 * </ul>
 */
package de.jexcellence.jextranslate.impl;
