/**
 * Public API surface for JExTranslate's runtime services, including repositories,
 * formatters, locale resolvers, and the fluent placeholder model.
 *
 * <p><strong>Bootstrap order.</strong> Consumers must wire repositories, formatters,
 * and locale resolvers and then call
 * {@link de.jexcellence.jextranslate.api.TranslationService#configure(de.jexcellence.jextranslate.api.TranslationService.ServiceConfiguration)}
 * exactly once before invoking any other API. The {@code ServiceConfiguration}
 * encapsulates these collaborators in the following order:
 * <ol>
 *     <li>Construct or obtain a {@link de.jexcellence.jextranslate.api.TranslationRepository}</li>
 *     <li>Provide a compatible {@link de.jexcellence.jextranslate.api.MessageFormatter}</li>
 *     <li>Finalize with a {@link de.jexcellence.jextranslate.api.LocaleResolver}</li>
 * </ol>
 * After {@code configure} succeeds, subsequent calls to
 * {@link de.jexcellence.jextranslate.api.TranslationService#create} (or its variants)
 * will first access the locale resolver to determine the active locale, then read from
 * the repository, and finally delegate to the formatter. Locale selection honors the
 * cascade enforced by the service: an explicit locale supplied to {@code create(...)}
 * &rarr; the cached locale resolved from the {@link de.jexcellence.jextranslate.api.LocaleResolver}
 * &rarr; the repository's default locale. Skipping {@code configure} will raise an
 * {@link IllegalStateException}, so bootstrap code must always call it during plugin
 * initialization or test setup.</p>
 *
 * <p>The resolved locales are cached per player using
 * {@link de.jexcellence.jextranslate.api.TranslationService#clearLocaleCache()} for global
 * invalidation or {@link de.jexcellence.jextranslate.api.TranslationService#clearLocaleCache(org.bukkit.entity.Player)}
 * for targeted resets. Ensure caches are purged when swapping repositories, formatters,
 * or resolver strategies so that translations remain consistent.</p>
 *
 * <p><strong>Thread safety and immutability.</strong> The fluent placeholder API is
 * intentionally immutable: each call to {@link de.jexcellence.jextranslate.api.TranslationService#with(String, Object)}
 * or {@link de.jexcellence.jextranslate.api.TranslationService#with(de.jexcellence.jextranslate.api.Placeholder)} returns a new {@code TranslationService} instance that
 * captures an immutable snapshot of placeholders. Placeholder implementations themselves
 * are records and therefore safely shareable across threads, provided the embedded values
 * are thread-safe. Avoid mutating state after handing it to the API; build new placeholder
 * instances instead. This design ensures that asynchronous builders such as
 * {@link de.jexcellence.jextranslate.api.TranslationService#buildAsync()} can operate without
 * synchronization hazards.</p>
 */
package de.jexcellence.jextranslate.api;
