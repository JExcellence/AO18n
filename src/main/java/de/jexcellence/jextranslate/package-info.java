/**
 * Core documentation for the {@code de.jexcellence.jextranslate} package.
 * <p>
 * Every entry point <strong>must</strong> call
 * {@link de.jexcellence.jextranslate.api.TranslationService#configure(de.jexcellence.jextranslate.api.TranslationService.ServiceConfiguration)}
 * before interacting with repositories, formatters, or locale resolvers. This configuration step wires the
 * repository, message formatter, and locale resolver that power the fluent translation APIs showcased in the
 * module {@code README}. Skipping configuration leaves the translation runtime in an undefined state and will
 * raise an {@link IllegalStateException} the first time a translation is requested.
 * </p>
 * <p>
 * Locale resolution always follows the cascade documented in the README: an explicit override supplied to
 * {@link de.jexcellence.jextranslate.api.TranslationService#create(de.jexcellence.jextranslate.api.TranslationKey, org.bukkit.entity.Player, java.util.Locale)}
 * wins first, then the resolved player locale from the configured resolver, and finally the service default
 * (usually the repository fallback locale). Implementers should document this ordering anywhere translations are
 * exposed so callers understand which locale will be selected when overrides are omitted.
 * </p>
 * <p>
 * Player locale lookups and resolved defaults are cached per player/profile. Whenever translation bundles,
 * formatter implementations, or resolver strategies change—such as during reload commands, hot-swap testing, or
 * repository synchronization—you must clear the cache through
 * {@link de.jexcellence.jextranslate.api.TranslationService#clearLocaleCache()} or its player-scoped overloads to
 * guarantee that subsequent requests observe the updated configuration.
 * </p>
 * <p>
 * Several downstream modules depend on this initialization contract: the platform bootstrap handled by
 * {@link com.raindropcentral.rplatform.localization.TranslationManager}, shared UI scaffolding in
 * {@link com.raindropcentral.rplatform.view.BaseView} and
 * {@link com.raindropcentral.rplatform.view.APaginatedView}, and gameplay flows like
 * {@link com.raindropcentral.rdq.view.bounty.BountyRewardView}. These components assume the service has already
 * been configured and that locale caches are refreshed when translations change. Keep the package documentation
 * synchronized with {@code JExTranslate/README.md} and any module guides that describe repository, formatter, or
 * resolver setup so that project-wide initialization remains consistent.
 * </p>
 */
package de.jexcellence.jextranslate;
