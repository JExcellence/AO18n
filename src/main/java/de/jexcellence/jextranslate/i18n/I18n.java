package de.jexcellence.jextranslate.i18n;

import de.jexcellence.jextranslate.i18n.wrapper.I18nConsoleWrapper;
import de.jexcellence.jextranslate.i18n.wrapper.II18nVersionWrapper;
import de.jexcellence.jextranslate.i18n.wrapper.VersionWrapper;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Legacy entry point for internationalization (i18n) message handling.
 *
 * <p><strong>Deprecated:</strong> Use {@link de.jexcellence.jextranslate.MessageBuilder} via
 * {@code r18n.msg("key")} instead. The {@code MessageBuilder} API is more concise, supports
 * locale overrides, plural forms, Bedrock sending, and does not require a separate {@code .build()} step.</p>
 *
 * <p>Migration example:</p>
 * <pre>{@code
 * // Before (I18n)
 * new I18n.Builder("welcome", player).withPlaceholder("name", "Steve").build().sendMessage();
 *
 * // After (MessageBuilder)
 * r18n.msg("welcome").with("name", "Steve").send(player);
 * }</pre>
 *
 * @author JExcellence
 * @version 3.0.0
 * @since 2.0.0
 * @deprecated since 3.0.0 — use {@link de.jexcellence.jextranslate.MessageBuilder} instead
 */
@Deprecated(since = "3.0.0")
public class I18n {

    /**
     * The version-specific i18n wrapper used to handle message formatting and sending.
     */
    private final II18nVersionWrapper<?> i18nVersionWrapper;

    /**
     * Constructs an I18n instance using the provided {@link Builder}.
     *
     * @param builder the builder containing configuration for this I18n instance
     */
    private I18n(@NotNull Builder builder) {
        if (builder.player == null) {
            this.i18nVersionWrapper = new I18nConsoleWrapper(
                    builder.key,
                    builder.placeholders,
                    builder.includePrefix
            );
        } else {
            this.i18nVersionWrapper = new VersionWrapper(
                    builder.player,
                    builder.key,
                    builder.placeholders,
                    builder.includePrefix
            ).getI18nVersionWrapper();
        }
    }

    /**
     * Sends a localized message to the player.
     */
    public void sendMessage() {
        this.i18nVersionWrapper.sendMessage();
    }

    /**
     * Sends multiple localized messages to the player.
     */
    public void sendMultiple() {
        this.i18nVersionWrapper.sendMessages();
    }

    /**
     * Returns the formatted localized message for display.
     *
     * <p><strong>Note:</strong> This method uses an unchecked cast. The caller is responsible for
     * ensuring the assigned type matches the wrapper's actual output type (e.g., {@code Component}
     * on modern servers, {@code String} on legacy ones).</p>
     *
     * @param <T> the type of the message (e.g., String, Component)
     * @return the formatted message
     */
    @SuppressWarnings("unchecked")
    public <T> T component() {
        return (T) this.i18nVersionWrapper.displayMessage();
    }

    /**
     * Returns a list of formatted localized messages for display.
     *
     * <p><strong>Note:</strong> This method uses an unchecked cast. The caller is responsible for
     * ensuring the assigned type matches the wrapper's actual output type.</p>
     *
     * @param <T> the type of the messages (e.g., String, Component)
     * @return the list of formatted messages
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> children() {
        return (List<T>) this.i18nVersionWrapper.displayMessages();
    }

    /**
     * Returns the underlying version-specific i18n wrapper.
     *
     * @return the {@link II18nVersionWrapper} instance
     */
    public II18nVersionWrapper<?> getI18nVersionWrapper() {
        return this.i18nVersionWrapper;
    }

    /**
     * Builder for constructing {@link I18n} instances.
     *
     * @deprecated since 3.0.0 — use {@link de.jexcellence.jextranslate.MessageBuilder} instead
     */
    @Deprecated(since = "3.0.0")
    public static class Builder {

        /**
         * Placeholders to be replaced in the message.
         */
        private final Map<String, String> placeholders = new HashMap<>();

        /**
         * The player to whom the message will be sent, or {@code null} for the console sender.
         */
        @Nullable
        private final Player player;

        /**
         * The message key to be localized.
         */
        private final String key;

        /**
         * Whether to include the prefix in the message.
         */
        private boolean includePrefix = false;

        /**
         * Creates a new Builder for the given message key and player.
         *
         * @param key    the message key
         * @param player the player to send the message to
         * @throws NullPointerException if key or player is null
         */
        public Builder(@NotNull String key, @NotNull Player player) {
            this.key = Objects.requireNonNull(key, "key");
            this.player = Objects.requireNonNull(player, "player");
        }

        /**
         * Creates a new Builder for sending the message to the console sender (no player context).
         * The player field will be {@code null}; the message is routed to the console wrapper.
         *
         * @param key the message key
         * @throws NullPointerException if key is null
         */
        public Builder(@NotNull String key) {
            this.key = Objects.requireNonNull(key, "key");
            this.player = null;
        }

        /**
         * Adds multiple placeholders to the message.
         *
         * @param placeholders a map of placeholder keys to values
         * @return this builder instance
         */
        public Builder withPlaceholders(@NotNull Map<String, Object> placeholders) {
            for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                String value = entry.getValue() != null
                        ? entry.getValue().toString()
                        : "<null>";
                this.placeholders.put(entry.getKey(), value);
            }
            return this;
        }

        /**
         * Configures the message to include the prefix.
         *
         * @return this builder instance
         */
        public Builder includePrefix() {
            this.includePrefix = true;
            return this;
        }

        /**
         * Adds a single placeholder to the message.
         *
         * @param key   the placeholder key
         * @param value the placeholder value (null will be replaced with "&lt;null&gt;")
         * @return this builder instance
         */
        public Builder withPlaceholder(@NotNull String key, @Nullable Object value) {
            this.placeholders.put(
                    key,
                    value != null ? value.toString() : "<null>"
            );
            return this;
        }

        /**
         * Builds a new {@link I18n} instance with the configured options.
         *
         * @return the constructed I18n instance
         */
        public I18n build() {
            return new I18n(this);
        }
    }
}
