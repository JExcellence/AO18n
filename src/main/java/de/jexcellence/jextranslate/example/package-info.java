/**
 * Sample integrations demonstrating how to bootstrap {@link de.jexcellence.jextranslate.api.TranslationService}
 * and exercise the full translation workflow.
 * <p>
 * These examples show how to configure a {@link de.jexcellence.jextranslate.api.TranslationRepository},
 * {@link de.jexcellence.jextranslate.api.MessageFormatter}, and
 * {@link de.jexcellence.jextranslate.api.LocaleResolver} before any translation calls. The
 * {@link de.jexcellence.jextranslate.api.TranslationService#configure(de.jexcellence.jextranslate.api.TranslationService.ServiceConfiguration)}
 * method must be invoked during plugin startup so repositories, formatters, and resolvers are ready for
 * use.
 * <p>
 * Locale fallback follows the cascade documented in the module guide: explicit overrides, then resolved
 * player locales, and finally the service default. Locale resolutions are cached per player and should be
 * cleared whenever repositories reload, resolver behaviour changes, or new formatter implementations are
 * registered. Placeholder usage relies on MiniMessage-compatible tokens (for example {@code {player}}),
 * and each message instance should build placeholders fluently without reusing mutable state across threads.
 * <p>
 * Integrators adapting these samples should adjust the repository location, default locale, and any
 * placeholder keys to match their plugin's configuration files and feature set. Reuse the lifecycle hooks
 * demonstrated here (such as reload commands) to refresh repositories and invalidate caches, and wire the
 * commands or listeners into your own plugin's services so that translations align with your domain logic.
 */
package de.jexcellence.jextranslate.example;
