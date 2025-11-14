package de.jexcellence.jextranslate.api;

import de.jexcellence.jextranslate.util.TranslationLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Value object wrapping a formatted Adventure {@link Component} produced by {@link TranslationService}. Provides helper
 * methods for sending messages to Bukkit players and inspecting translated text for diagnostics.
 *
 * <p>The original {@link TranslationKey} is retained for cross-referencing repository entries, particularly when
 * debugging MiniMessage placeholder behaviour via {@link de.jexcellence.jextranslate.util.DebugUtils}.</p>
 *
 * @param component   the rendered component ready for dispatch
 * @param originalKey the repository key that generated the component
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.2
 */
public record TranslatedMessage(@NotNull Component component, @NotNull TranslationKey originalKey) {

    private static final Logger LOGGER = TranslationLogger.getLogger(TranslatedMessage.class);

    /**
     * Creates a translated message ensuring neither component nor key are {@code null}.
     *
     * @param component   the rendered component ready for dispatch
     * @param originalKey the repository key that generated the component
     */
    public TranslatedMessage {
        Objects.requireNonNull(component, "Component cannot be null");
        Objects.requireNonNull(originalKey, "Original key cannot be null");
    }

    /**
     * Serializes the component to plain text.
     *
     * @return the plain text representation of the component
     */
    @NotNull
    public String asPlainText() {
        return PlainTextComponentSerializer.plainText().serialize(this.component);
    }

    /**
     * Serializes the component using Minecraft legacy formatting codes.
     *
     * @return the legacy text representation of the component
     */
    @NotNull
    public String asLegacyText() {
        return LegacyComponentSerializer.legacySection().serialize(this.component);
    }

    /**
     * Sends the component to the supplied player, falling back to legacy formatting when Adventure dispatch fails.
     *
     * @param player the player to receive the component
     */
    public void sendTo(@NotNull final Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        try {
            player.sendMessage(this.component);
        } catch (final Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Failed to send chat message",
                            Map.of(
                                    "player", TranslationLogger.anonymize(player.getUniqueId()),
                                    "key", this.originalKey.key()
                            )
                    ),
                    exception
            );
            sendLegacyFallback(player);
        }
    }

    /**
     * Sends the component as an action bar message to the supplied player.
     *
     * @param player the player to receive the component
     */
    public void sendActionBar(@NotNull final Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        try {
            player.sendActionBar(this.component);
        } catch (final Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Failed to send action bar",
                            Map.of(
                                    "player", TranslationLogger.anonymize(player.getUniqueId()),
                                    "key", this.originalKey.key()
                            )
                    ),
                    exception
            );
        }
    }

    /**
     * Sends the component as a title using default fade timings and an empty subtitle.
     *
     * @param player the player to receive the title
     */
    public void sendTitle(@NotNull final Player player) {
        sendTitle(player, null, Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1));
    }

    /**
     * Sends the component as a title with full control over fade timings and subtitle content.
     *
     * @param player   the player to receive the title
     * @param subtitle optional subtitle component; may be {@code null}
     * @param fadeIn   fade-in duration
     * @param stay     time to display the title
     * @param fadeOut  fade-out duration
     */
    public void sendTitle(
        @NotNull final Player player,
        @Nullable final Component subtitle,
        @NotNull final Duration fadeIn,
        @NotNull final Duration stay,
        @NotNull final Duration fadeOut
    ) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(fadeIn, "Fade-in duration cannot be null");
        Objects.requireNonNull(stay, "Stay duration cannot be null");
        Objects.requireNonNull(fadeOut, "Fade-out duration cannot be null");

        try {
            final Title title = Title.title(
                this.component,
                subtitle != null ? subtitle : Component.empty(),
                Title.Times.times(fadeIn, stay, fadeOut)
            );
            player.showTitle(title);
        } catch (final Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Failed to send title",
                            Map.of(
                                    "player", TranslationLogger.anonymize(player.getUniqueId()),
                                    "key", this.originalKey.key()
                            )
                    ),
                    exception
            );
        }
    }

    /**
     * Determines whether the message is empty once converted to plain text.
     *
     * @return {@code true} when the message is empty or whitespace only
     */
    public boolean isEmpty() {
        return asPlainText().trim().isEmpty();
    }

    /**
     * Calculates the length of the message in characters using the plain-text representation.
     *
     * @return the message length
     */
    public int length() {
        return asPlainText().length();
    }

    /**
     * Indicates whether the message contains the supplied text (case-insensitive).
     *
     * @param text the text fragment to check for
     * @return {@code true} when the fragment is present
     */
    public boolean contains(@NotNull final String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        return asPlainText().toLowerCase().contains(text.toLowerCase());
    }

    /**
     * Returns a copy of this message using a new translation key while retaining the component payload.
     *
     * @param newKey the key to associate with the cloned message
     * @return a new {@link TranslatedMessage} with the supplied key
     */
    @NotNull
    public TranslatedMessage withKey(@NotNull final TranslationKey newKey) {
        Objects.requireNonNull(newKey, "New key cannot be null");
        return new TranslatedMessage(this.component, newKey);
    }

    /**
     * Returns a new message with the supplied component appended to the existing component.
     *
     * @param other the component to append
     * @return a new {@link TranslatedMessage} containing the concatenated component
     */
    @NotNull
    public TranslatedMessage append(@NotNull final Component other) {
        Objects.requireNonNull(other, "Other component cannot be null");
        return new TranslatedMessage(this.component.append(other), this.originalKey);
    }

    /**
     * Returns a new message with the supplied component prepended to the existing component.
     *
     * @param other the component to prepend
     * @return a new {@link TranslatedMessage} containing the concatenated component
     */
    @NotNull
    public TranslatedMessage prepend(@NotNull final Component other) {
        Objects.requireNonNull(other, "Other component cannot be null");
        return new TranslatedMessage(other.append(this.component), this.originalKey);
    }

    /**
     * Splits the message into individual line components, parsing MiniMessage segments when necessary.
     * This method preserves MiniMessage formatting by serializing the component back to MiniMessage format,
     * splitting by newlines, and then deserializing each line individually.
     *
     * @return list of components representing non-empty lines
     */
    @NotNull
    public List<Component> splitLines() {
        // Try to serialize the component back to MiniMessage format to preserve formatting
        final String miniMessageText;
        try {
            miniMessageText = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(this.component);
        } catch (final Exception exception) {
            // Fallback to plain text if serialization fails
            final String plainText = asPlainText();
            if (plainText.isEmpty()) {
                return List.of();
            }
            final String[] lines = plainText.split("\\n");
            final List<Component> components = new ArrayList<>();
            for (final String line : lines) {
                if (!line.trim().isEmpty()) {
                    components.add(Component.text(line));
                }
            }
            return components;
        }

        if (miniMessageText.isEmpty()) {
            return List.of();
        }

        final String[] lines = miniMessageText.split("\\n");
        final List<Component> components = new ArrayList<>();

        for (final String line : lines) {
            if (!line.trim().isEmpty()) {
                try {
                    // Parse each line as MiniMessage to preserve formatting
                    components.add(MiniMessage.miniMessage().deserialize(line));
                } catch (final Exception exception) {
                    // Fallback to plain text if parsing fails
                    LOGGER.log(
                            Level.FINE,
                            "Failed to parse line as MiniMessage, using plain text: " + line,
                            exception
                    );
                    components.add(Component.text(line));
                }
            }
        }
        return components;
    }

    /**
     * Provides a debug representation of the message, listing key metadata useful for repository diagnostics.
     *
     * @return a human-readable debug string
     */
    @NotNull
    public String toDebugString() {
        final StringBuilder debug = new StringBuilder();
        debug.append("TranslatedMessage Debug:\n");
        debug.append("  Original Key: ").append(originalKey.key()).append("\n");
        debug.append("  Plain Text: ").append(asPlainText()).append("\n");
        debug.append("  Length: ").append(length()).append("\n");
        debug.append("  Is Empty: ").append(isEmpty()).append("\n");
        debug.append("  Lines: ").append(splitLines().size()).append("\n");

        final List<Component> lines = splitLines();
        for (int i = 0; i < lines.size(); i++) {
            final String lineText = PlainTextComponentSerializer.plainText().serialize(lines.get(i));
            debug.append("    Line ").append(i).append(": '").append(lineText).append("'\n");
        }

        debug.append("  Component: ").append(component.toString());
        return debug.toString();
    }

    /**
     * Sends a legacy-formatted fallback message to the supplied player.
     *
     * @param player the player to receive the fallback message
     */
    private void sendLegacyFallback(@NotNull final Player player) {
        try {
            player.sendMessage(asLegacyText());
        } catch (final Exception exception) {
            LOGGER.log(Level.SEVERE, "Failed to send fallback message to player " + player.getName(), exception);
        }
    }

    @Override
    public String toString() {
        return "TranslatedMessage{key=" + this.originalKey + ", text='" + asPlainText() + "'}";
    }
}
