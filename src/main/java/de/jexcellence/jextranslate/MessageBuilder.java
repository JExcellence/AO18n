package de.jexcellence.jextranslate;

import de.jexcellence.jextranslate.util.PluralRules;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * // Simple message
 * r18n.message("welcome.player")
 *     .placeholder("player", player.getName())
 *     .send(player);
 *
 * // Message with multiple placeholders
 * r18n.message("server.stats")
 *     .placeholder("online", server.getOnlinePlayers().size())
 *     .placeholder("max", server.getMaxPlayers())
 *     .placeholder("tps", getTPS())
 *     .broadcast();
 *
 * // Get component for custom handling
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
     * // Translation file:
     * // items.count.one: "You have {count} item"
     * // items.count.other: "You have {count} items"
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
     *
     * @param player the target player
     */
    public void send(@NotNull Player player) {
        Component component = toComponent(player);
        if (manager.getMessageSender() != null) {
            manager.getMessageSender().sendMessage(player, component);
        }
    }

    /**
     * Sends the message to a command sender.
     *
     * @param sender the target command sender
     */
    public void send(@NotNull CommandSender sender) {
        if (sender instanceof Player player) {
            send(player);
        } else {
            Component component = toComponent(null);
            if (manager.getMessageSender() != null) {
                manager.getMessageSender().sendMessage(sender, component);
            }
        }
    }

    /**
     * Sends the message to an Adventure audience.
     *
     * @param audience the target audience
     */
    public void send(@NotNull Audience audience) {
        Component component = toComponent(null);
        if (manager.getMessageSender() != null) {
            manager.getMessageSender().sendMessage(audience, component);
        }
    }

    /**
     * Broadcasts the message to all online players.
     */
    public void broadcast() {
        Component component = toComponent(null);
        if (manager.getMessageSender() != null) {
            manager.getMessageSender().broadcast(component);
        }
    }

    /**
     * Sends the message to console.
     */
    public void console() {
        Component component = toComponent(null);
        if (manager.getMessageSender() != null) {
            manager.getMessageSender().console(component);
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

    /**
     * Determines the appropriate locale for the message.
     *
     * <p>The locale is determined in the following order:</p>
     * <ol>
     *   <li>Explicitly set locale via {@link #locale(String)}</li>
     *   <li>Stored locale from {@link de.jexcellence.jextranslate.storage.LocaleStorage}</li>
     *   <li>Player's client locale from {@link Player#getLocale()}</li>
     *   <li>Default locale from configuration</li>
     * </ol>
     *
     * @param player the target player
     * @return the locale to use
     */
    @NotNull
    private String determineLocale(@Nullable Player player) {
        // Use explicitly set locale first
        if (targetLocale != null) {
            return targetLocale;
        }

        // Check LocaleStorage for stored player preference
        if (player != null) {
            var storedLocale = manager.getLocaleStorage().getLocale(player.getUniqueId());
            if (storedLocale.isPresent()) {
                String locale = storedLocale.get();
                if (manager.getConfiguration().supportedLocales().contains(locale)) {
                    return locale;
                }
            }
        }

        // Fall back to player's client locale
        if (player != null) {
            try {
                String playerLocale = player.getLocale();
                if (manager.getConfiguration().supportedLocales().contains(playerLocale)) {
                    return playerLocale;
                }
                // Try language part only (e.g., "en" from "en_GB")
                String language = playerLocale.split("[_-]")[0];
                if (manager.getConfiguration().supportedLocales().contains(language)) {
                    return language;
                }
            } catch (Exception e) {
                // Fallback for older versions or errors
            }
        }

        // Use default locale
        return manager.getConfiguration().defaultLocale();
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
        // If no count placeholders, return base key
        if (countPlaceholders.isEmpty()) {
            return baseKey;
        }

        // Get the first count placeholder for plural selection
        var entry = countPlaceholders.entrySet().iterator().next();
        int count = entry.getValue();

        // Determine the plural form based on locale and count
        String pluralForm = PluralRules.select(locale, count);
        String pluralKey = baseKey + "." + pluralForm;

        // Check if the specific plural form exists
        if (manager.getTranslationLoader().hasKey(pluralKey, locale)) {
            return pluralKey;
        }

        // Fall back to .other
        String otherKey = baseKey + "." + PluralRules.OTHER;
        if (manager.getTranslationLoader().hasKey(otherKey, locale)) {
            return otherKey;
        }

        // Fall back to base key
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
