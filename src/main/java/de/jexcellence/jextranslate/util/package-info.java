/**
 * Utility helpers that support debugging and validating translations built with
 * {@link de.jexcellence.jextranslate.api.TranslationService}.
 *
 * <p>Before accessing any repositories, formatters, or locale resolvers make sure
 * {@link de.jexcellence.jextranslate.api.TranslationService#configure(de.jexcellence.jextranslate.api.TranslationService.ServiceConfiguration)}
 * has been invoked. The configuration bootstrap wires the immutable placeholder builders,
 * repository adapters, and MiniMessage-aware formatters used across this package.</p>
 *
 * <h2>Placeholder builders and immutability</h2>
 * <ul>
 *     <li>Each call to {@code TranslationService.create(...)} returns a fresh, immutable builder.</li>
 *     <li>Always chain {@code .with(...)} calls on that builder instance; never cache or reuse
 *     mutable state between threads.</li>
 *     <li>Derive new variants by calling {@code message.with(...)} which returns a new builder
 *     instead of mutating the original.</li>
 * </ul>
 *
 * <pre>{@code
 * final TranslatedMessage base = TranslationService
 *         .create(TranslationKey.of("quests.start"), player)
 *         .with("quest_name", Placeholder.unparsed("quest_name", "<gold>Voyager</gold>"))
 *         .build();
 *
 * final TranslatedMessage personalized = base
 *         .with("player", Placeholder.component("player", player.displayName()))
 *         .build();
 * }</pre>
 *
 * <h2>MiniMessage token formatting</h2>
 * <p>MiniMessage placeholders use {@code {placeholder}} tokens and must honour Adventure
 * escaping rules. When you need literal braces inside a translation, escape them as
 * {@code \{literal\}} in resource files or {@code "\\{literal\\}"} inside Java strings.</p>
 *
 * <pre>{@code
 * // Resource YAML
 * quests:
 *   start: "<gray>Quest:</gray> {quest_name} <gray>for</gray> {player}"
 *
 * // Java builder composition with correct escaping
 * TranslationService.create(TranslationKey.of("quests.start"), player)
 *         .with("quest_name", Placeholder.unparsed("quest_name", "<green>Explorer</green>"))
 *         .with("player", Placeholder.component("player", player.displayName()))
 *         .with("raw_braces", Placeholder.unparsed("raw_braces", "\\{escaped\\}"))
 *         .build();
 * }</pre>
 *
 * <p>Compose tokens declaratively: feed already formatted MiniMessage fragments through
 * {@link de.jexcellence.jextranslate.api.Placeholder#unparsed(String, String)} and supply
 * component-safe values via {@link de.jexcellence.jextranslate.api.Placeholder#component(String, net.kyori.adventure.text.Component)}.
 * This ensures MiniMessage output remains well-formed while keeping placeholder builders immutable.</p>
 */
package de.jexcellence.jextranslate.util;
