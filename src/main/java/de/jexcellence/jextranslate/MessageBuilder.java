package de.jexcellence.jextranslate;

import de.jexcellence.jextranslate.bedrock.BedrockConverter;
import de.jexcellence.jextranslate.bedrock.BedrockDetectionCache;
import de.jexcellence.jextranslate.util.PluralRules;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent API builder for creating and sending localized messages.
 *
 * <p>This class provides a convenient way to build messages with placeholders,
 * locale-specific formatting, and various sending options. It supports both
 * MiniMessage formatting and legacy color codes with automatic fallback.</p>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 *
 * r18n.message("welcome.player")
 *     .placeholder("player", player.getName())
 *     .send(player);
 *
 *
 * r18n.message("server.stats")
 *     .placeholder("online", server.getOnlinePlayers().size())
 *     .placeholder("max", server.getMaxPlayers())
 *     .placeholder("tps", getTPS())
 *     .broadcast();
 *
 *
 * Component component = r18n.message("error.permission")
 *     .placeholder("permission", "admin.reload")
 *     .toComponent(player);
 * }</pre>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class MessageBuilder {

    private final R18nManager manager;
    private final String key;
    private final Map<String, Object> placeholders;
    private final Map<String, Integer> countPlaceholders;
    private boolean includePrefix;
    private String targetLocale;

    /**
     * Creates a new message builder.
     *
     * @param manager the R18n manager
     * @param key     the translation key
     */
    MessageBuilder(@NotNull R18nManager manager, @NotNull String key) {
        this.manager = manager;
        this.key = key;
        this.placeholders = new HashMap<>();
        this.countPlaceholders = new HashMap<>();
        this.includePrefix = false;
        this.targetLocale = null;
    }

    /**
     * Adds a placeholder to the message.
     *
     * @param key   the placeholder key (without braces)
     * @param value the placeholder value
     * @return this builder for chaining
     */
    @NotNull
    public MessageBuilder placeholder(@NotNull String key, @Nullable Object value) {
        placeholders.put(key, value != null ? value : "null");
        return this;
    }

    /**
     * Concise alias for {@link #placeholder(String, Object)}.
     *
     * <pre>{@code r18n.msg("welcome").with("player", name).send(player);}</pre>
     *
     * @param key   the placeholder key (without braces)
     * @param value the placeholder value
     * @return this builder for chaining
     */
    @NotNull
    public MessageBuilder with(@NotNull String key, @Nullable Object value) {
        return placeholder(key, value);
    }

    /**
     * Adds multiple placeholders to the message.
     *
     * @param placeholders the placeholders map
     * @return this builder for chaining
     */
    @NotNull
    public MessageBuilder placeholders(@NotNull Map<String, Object> placeholders) {
        this.placeholders.putAll(placeholders);
        return this;
    }

    /**
     * Adds a count placeholder for plural support.
     *
     * <p>This method registers a count value that will be used to select the
     * appropriate plural form of the translation. The count is also added as
     * a regular placeholder so it can be displayed in the message.</p>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     *
     *
     *
     *
     * r18n.message("items.count")
     *     .count("count", itemCount)
     *     .send(player);
     * }</pre>
     *
     * @param placeholder the placeholder key (without braces)
     * @param value       the count value for plural selection
     * @return this builder for chaining
     */
    @NotNull
    public MessageBuilder count(@NotNull String placeholder, int value) {
        this.countPlaceholders.put(placeholder, value);
        this.placeholders.put(placeholder, String.valueOf(value));
        return this;
    }

    /**
     * Includes the prefix in the message.
     *
     * @return this builder for chaining
     */
    @NotNull
    public MessageBuilder withPrefix() {
        this.includePrefix = true;
        return this;
    }

    /**
     * Concise alias for {@link #withPrefix()}.
     *
     * @return this builder for chaining
     */
    @NotNull
    public MessageBuilder prefix() {
        return withPrefix();
    }

    /**
     * Sets a specific locale for this message (overrides player locale).
     *
     * @param locale the target locale
     * @return this builder for chaining
     */
    @NotNull
    public MessageBuilder locale(@NotNull String locale) {
        this.targetLocale = locale;
        return this;
    }

    /**
     * Sends the message to a player.
     * If the resolved translation is empty or blank, no message is sent.
     *
     * @param player the target player
     */
    public void send(@NotNull Player player) {
        Component component = toComponent(player);
        if (isBlank(component)) return;
        if (manager.getMessageSender() != null) {
            manager.getMessageSender().sendMessage(player, component);
        }
    }

    /**
     * Sends the message to a command sender.
     * If the resolved translation is empty or blank, no message is sent.
     *
     * @param sender the target command sender
     */
    public void send(@NotNull CommandSender sender) {
        if (sender instanceof Player player) {
            send(player);
        } else {
            Component component = toComponent(null);
            if (isBlank(component)) return;
            if (manager.getMessageSender() != null) {
                manager.getMessageSender().sendMessage(sender, component);
            }
        }
    }

    /**
     * Sends the message to an Adventure audience.
     * If the resolved translation is empty or blank, no message is sent.
     *
     * @param audience the target audience
     */
    public void send(@NotNull Audience audience) {
        Component component = toComponent(null);
        if (isBlank(component)) return;
        if (manager.getMessageSender() != null) {
            manager.getMessageSender().sendMessage(audience, component);
        }
    }

    /**
     * Broadcasts the message to all online players.
     * If the resolved translation is empty or blank, no message is sent.
     */
    public void broadcast() {
        Component component = toComponent(null);
        if (isBlank(component)) return;
        if (manager.getMessageSender() != null) {
            manager.getMessageSender().broadcast(component);
        }
    }

    /**
     * Sends the message to the server console using the default locale.
     * If the resolved translation is empty or blank, no message is sent.
     */
    public void console() {
        Component component = toComponent(null);
        if (isBlank(component)) return;
        if (manager.getMessageSender() != null) {
            manager.getMessageSender().console(component);
        }
    }

    /**
     * Sends the message to the server console using a specific locale.
     *
     * <pre>{@code r18n.msg("startup.ready").console("de_DE");}</pre>
     *
     * @param locale the locale to use for the console message
     */
    public void console(@NotNull String locale) {
        locale(locale);
        console();
    }

    /**
     * Shows this message as a title to the player. The current message
     * acts as the main title; the subtitle is resolved from the given
     * key against the same placeholder map and the current locale.
     *
     * <p>Default timing: 10 ticks fade-in, 60 ticks stay, 20 ticks fade-out.
     * Use {@link #showTitle(Player, String, long, long, long)} to override.
     *
     * <pre>{@code
     * r18n.msg("achievement.title")
     *     .with("name", "First Steps")
     *     .showTitle(player, "achievement.subtitle");
     * }</pre>
     *
     * @param player the target player
     * @param subtitleKey translation key used for the subtitle line, or
     *                    {@code null} for an empty subtitle
     */
    public void showTitle(@NotNull Player player, @Nullable String subtitleKey) {
        showTitle(player, subtitleKey, 10L, 60L, 20L);
    }

    /**
     * Renders the message as an {@code ItemMeta}-ready component:
     * resolves placeholders, parses MiniMessage <strong>once</strong>,
     * and strips the default italic decoration that Bukkit applies to
     * every renamed item / lore line. Use this for anything that ends
     * up in {@code ItemMeta#displayName} or {@code ItemMeta#lore}.
     *
     * <p>Replaces the wasteful pattern
     * {@code MiniMessage.miniMessage().deserialize(builder.text(null))}
     * which double-parses (resolve → string → re-parse) and never strips
     * the italic decoration.
     *
     * <pre>{@code
     * meta.displayName(r18n.msg("gui.button.title")
     *     .with("name", recipe.key().toString())
     *     .itemComponent(null));
     * }</pre>
     */
    @NotNull
    public Component itemComponent(@Nullable Player player) {
        return toComponent(player).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    /**
     * Shows this message as a title with explicit timing.
     *
     * @param player      the target player
     * @param subtitleKey translation key used for the subtitle, or {@code null}
     * @param fadeInTicks ticks for the title to fade in
     * @param stayTicks   ticks the title stays fully visible
     * @param fadeOutTicks ticks for the title to fade out
     */
    public void showTitle(@NotNull Player player,
                           @Nullable String subtitleKey,
                           long fadeInTicks, long stayTicks, long fadeOutTicks) {
        final Component main = toComponent(player);
        final Component subtitle;
        if (subtitleKey == null) {
            subtitle = Component.empty();
        } else {
            // Build the subtitle through the same builder so it inherits
            // placeholders + locale from this MessageBuilder.
            final MessageBuilder sub = manager.msg(subtitleKey);
            for (final Map.Entry<String, Object> e : this.placeholders.entrySet()) {
                sub.with(e.getKey(), e.getValue());
            }
            subtitle = sub.toComponent(player);
        }
        final Title title = Title.title(main, subtitle, Title.Times.times(
                Duration.ofMillis(fadeInTicks * 50L),
                Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(fadeOutTicks * 50L)));
        if (manager.getAudiences() != null) {
            manager.getAudiences().player(player).showTitle(title);
        } else {
            player.showTitle(title);
        }
    }

    /**
     * Sends the message to a Bedrock player, explicitly forcing Bedrock-compatible formatting
     * regardless of automatic detection. Strips unsupported features (click/hover events)
     * and converts hex colours according to the configured fallback strategy.
     *
     * @param player the Bedrock target player
     */
    public void sendBedrock(@NotNull Player player) {
        Component component = toComponent(player);
        Component stripped = BedrockConverter.stripUnsupportedFormatting(component);
        if (manager.getMessageSender() != null) {
            if (manager.getMessageSender().hasAudiences()) {
                manager.getAudiences().player(player).sendMessage(stripped);
            } else {
                String legacy = manager.getMessageSender().toLegacyString(stripped);
                player.sendMessage(legacy);
            }
        }
    }

    /**
     * Converts the message to a Component for the specified player.
     *
     * @param player the target player (null for default locale)
     * @return the formatted component
     */
    @NotNull
    public Component toComponent(@Nullable Player player) {
        String locale = determineLocale(player);
        String resolvedKey = resolvePluralKey(key, locale);
        return manager.getMessageProvider().getComponent(resolvedKey, locale, placeholders, includePrefix);
    }

    /**
     * Converts the message to multiple Components (for multi-line messages).
     *
     * @param player the target player (null for default locale)
     * @return the list of formatted components
     */
    @NotNull
    public List<Component> toComponents(@Nullable Player player) {
        String locale = determineLocale(player);
        String resolvedKey = resolvePluralKey(key, locale);
        return manager.getMessageProvider().getComponents(resolvedKey, locale, placeholders, includePrefix);
    }

    /**
     * Converts the message to a plain string (useful for placeholders).
     *
     * @param player the target player (null for default locale)
     * @return the formatted string
     */
    @NotNull
    public String toString(@Nullable Player player) {
        String locale = determineLocale(player);
        String resolvedKey = resolvePluralKey(key, locale);
        return manager.getMessageProvider().getString(resolvedKey, locale, placeholders, includePrefix);
    }

    /**
     * Converts the message to multiple strings (for multi-line messages).
     *
     * @param player the target player (null for default locale)
     * @return the list of formatted strings
     */
    @NotNull
    public List<String> toStrings(@Nullable Player player) {
        String locale = determineLocale(player);
        String resolvedKey = resolvePluralKey(key, locale);
        return manager.getMessageProvider().getStrings(resolvedKey, locale, placeholders, includePrefix);
    }

    /**
     * Checks if the message key exists for the specified player's locale.
     *
     * @param player the target player (null for default locale)
     * @return true if the key exists
     */
    public boolean exists(@Nullable Player player) {
        String locale = determineLocale(player);
        return manager.getTranslationLoader().hasKey(key, locale);
    }

    // ── Concise aliases ──────────────────────────────────────────────────────

    /**
     * Concise alias for {@link #toComponent(Player)}.
     *
     * @param player the target player (null for default locale)
     * @return the formatted component
     */
    @NotNull
    public Component component(@Nullable Player player) {
        return toComponent(player);
    }

    /**
     * Concise alias for {@link #toComponents(Player)}.
     *
     * @param player the target player (null for default locale)
     * @return the list of formatted components
     */
    @NotNull
    public List<Component> components(@Nullable Player player) {
        return toComponents(player);
    }

    /**
     * Concise alias for {@link #toString(Player)}. Avoids confusion with {@link Object#toString()}.
     *
     * @param player the target player (null for default locale)
     * @return the formatted string
     */
    @NotNull
    public String text(@Nullable Player player) {
        return toString(player);
    }

    /**
     * Concise alias for {@link #toStrings(Player)}.
     *
     * @param player the target player (null for default locale)
     * @return the list of formatted strings
     */
    @NotNull
    public List<String> texts(@Nullable Player player) {
        return toStrings(player);
    }

    /**
     * Concise alias for {@link #toPlainString(Player)}.
     *
     * @param player the target player (null for default locale)
     * @return the plain text string with all formatting removed
     */
    @NotNull
    public String plain(@Nullable Player player) {
        return toPlainString(player);
    }

    /**
     * Converts the message to a Bedrock-compatible legacy string.
 *
 * <p>This method automatically strips unsupported formatting (click events, hover events)
     * and converts hex colors according to the configured fallback strategy.
     *
     * @param player the target player (null for default locale)
     * @return the legacy-formatted string suitable for Bedrock clients
     */
    @NotNull
    public String toBedrockString(@Nullable Player player) {
        Component component = toComponent(player);
        return BedrockConverter.toLegacyString(
                component,
                manager.getConfiguration().hexColorFallback(),
                manager.getConfiguration().bedrockFormatMode()
        );
    }

    /**
     * Converts the message to multiple Bedrock-compatible legacy strings.
 *
 * <p>This method is useful for multi-line messages that need to be displayed
     * on Bedrock clients.
     *
     * @param player the target player (null for default locale)
     * @return a list of legacy-formatted strings suitable for Bedrock clients
     */
    @NotNull
    public List<String> toBedrockStrings(@Nullable Player player) {
        List<Component> components = toComponents(player);
        List<String> result = new ArrayList<>(components.size());
        for (Component component : components) {
            result.add(BedrockConverter.toLegacyString(
                    component,
                    manager.getConfiguration().hexColorFallback(),
                    manager.getConfiguration().bedrockFormatMode()
            ));
        }
        return result;
    }

    /**
     * Converts the message to a plain text string with all formatting stripped.
 *
 * <p>This method is useful for Bedrock forms and other contexts where
     * only plain text is supported (no colors, no formatting).
     *
     * @param player the target player (null for default locale)
     * @return the plain text string with all formatting removed
     */
    @NotNull
    public String toPlainString(@Nullable Player player) {
        Component component = toComponent(player);
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Converts the message to multiple plain text strings.
 *
 * <p>This method is useful for multi-line messages in Bedrock forms.
     *
     * @param player the target player (null for default locale)
     * @return a list of plain text strings with all formatting removed
     */
    @NotNull
    public List<String> toPlainStrings(@Nullable Player player) {
        List<Component> components = toComponents(player);
        List<String> result = new ArrayList<>(components.size());
        var serializer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        for (Component component : components) {
            result.add(serializer.serialize(component));
        }
        return result;
    }

    /**
     * Checks if the target player is a Bedrock Edition player.
 *
 * <p>This method uses the BedrockDetectionCache to efficiently determine
     * if a player is connecting via Bedrock Edition through Geyser/Floodgate.
     *
     * @param player the player to check
     * @return true if the player is a Bedrock player, false otherwise or if detection is unavailable
     */
    public boolean isBedrockPlayer(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        BedrockDetectionCache cache = manager.getBedrockDetectionCache();
        return cache != null && cache.isBedrockPlayer(player);
    }

    /**
     * Checks whether a component would render as blank (empty or whitespace-only)
     * text. Used to suppress empty chat lines when a translation is set to {@code ''}.
     *
     * @param component the component to check
     * @return true if the component's plain-text serialization is blank
     */
    private boolean isBlank(@NotNull Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(component).isBlank();
    }

    /**
     * Determines the appropriate locale for the message.
     *
     * <p>The locale is determined in the following order:</p>
     * <ol>
     *   <li>Explicitly set locale via {@link #locale(String)}</li>
     *   <li>Player's client locale from {@link Player#getLocale()}</li>
     *   <li>Default locale from configuration</li>
     * </ol>
     *
     * @param player the target player
     * @return the locale to use
     */
    @NotNull
    private String determineLocale(@Nullable Player player) {
        if (targetLocale != null) {
            return targetLocale;
        }

        // Server-wide locale override — every player gets this locale
        String forced = manager.getConfiguration().forceLocale();
        if (forced != null && !forced.isEmpty()) {
            return forced;
        }

        if (player != null) {
            try {
                String playerLocale = resolvePlayerLocale(player);
                String normalizedLocale = normalizeLocale(playerLocale);

                if (manager.getConfiguration().supportedLocales().contains(normalizedLocale)) {
                    return normalizedLocale;
                }
                String language = playerLocale.split("[_-]")[0].toLowerCase();
                if (manager.getConfiguration().supportedLocales().contains(language)) {
                    return language;
                }
                for (String supportedLocale : manager.getConfiguration().supportedLocales()) {
                    if (supportedLocale.toLowerCase().startsWith(language)) {
                        return supportedLocale;
                    }
                }
            } catch (Exception e) {
                // Fallback to default locale on any failure
            }
        }

        return manager.getConfiguration().defaultLocale();
    }

    /**
     * Resolves the player's client locale as a lowercase string (e.g. {@code "en_us"}).
     * Tries Paper's {@code Player.locale()} first (returns {@link java.util.Locale}),
     * falls back to Spigot's {@code Player.getLocale()} (returns {@link String}).
     *
     * @param player the player whose locale to resolve
     * @return locale string in lowercase
     */
    @NotNull
    @SuppressWarnings("deprecation")
    private String resolvePlayerLocale(@NotNull org.bukkit.entity.Player player) {
        try {
            // Paper API: Player.locale() → java.util.Locale
            final java.util.Locale advLocale = player.locale();
            return advLocale.toLanguageTag().replace('-', '_').toLowerCase();
        } catch (NoSuchMethodError ignored) {
            // Spigot: Player.locale() does not exist, fall back to getLocale()
        }
        // Spigot API: Player.getLocale() → String (deprecated on Paper but available everywhere)
        return player.getLocale().toLowerCase();
    }

    /**
     * Normalizes a locale string to the standard format (e.g., "de_de" -> "de_DE").
     *
     * @param locale the locale to normalize
     * @return the normalized locale
     */
    @NotNull
    private String normalizeLocale(@NotNull String locale) {
        if (locale.isEmpty()) {
            return locale;
        }
        String[] parts = locale.split("[_-]");
        if (parts.length >= 2) {
            return parts[0].toLowerCase() + "_" + parts[1].toUpperCase();
        }
        return parts[0].toLowerCase();
    }

    /**
     * Resolves the appropriate plural key based on count placeholders.
     *
     * <p>This method checks if any count placeholders are set and, if so,
     * determines the appropriate plural form suffix to append to the base key.
     * The resolution follows this order:</p>
     * <ol>
     *   <li>Try the specific plural form (e.g., {@code key.one}, {@code key.few})</li>
     *   <li>Fall back to {@code key.other}</li>
     *   <li>Fall back to the base key</li>
     * </ol>
     *
     * @param baseKey the base translation key
     * @param locale  the target locale
     * @return the resolved key with plural suffix, or the base key if no plural form applies
     */
    @NotNull
    private String resolvePluralKey(@NotNull String baseKey, @NotNull String locale) {
        if (countPlaceholders.isEmpty()) {
            return baseKey;
        }

        var entry = countPlaceholders.entrySet().iterator().next();
        int count = entry.getValue();

        String pluralForm = PluralRules.select(locale, count);
        String pluralKey = baseKey + "." + pluralForm;

        if (manager.getTranslationLoader().hasKey(pluralKey, locale)) {
            return pluralKey;
        }

        String otherKey = baseKey + "." + PluralRules.OTHER;
        if (manager.getTranslationLoader().hasKey(otherKey, locale)) {
            return otherKey;
        }

        return baseKey;
    }

    /**
     * Gets the translation key.
     *
     * @return the translation key
     */
    @NotNull
    public String getKey() {
        return key;
    }

    /**
     * Gets the placeholders map.
     *
     * @return a copy of the placeholders map
     */
    @NotNull
    public Map<String, Object> getPlaceholders() {
        return new HashMap<>(placeholders);
    }

    /**
     * Checks if prefix is included.
     *
     * @return true if prefix is included
     */
    public boolean isIncludePrefix() {
        return includePrefix;
    }

    /**
     * Gets the target locale.
     *
     * @return the target locale, or null if not set
     */
    @Nullable
    public String getTargetLocale() {
        return targetLocale;
    }

    /**
     * Gets the count placeholders map.
     *
     * @return a copy of the count placeholders map
     */
    @NotNull
    public Map<String, Integer> getCountPlaceholders() {
        return new HashMap<>(countPlaceholders);
    }
}
