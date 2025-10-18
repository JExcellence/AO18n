package de.jexcellence.jextranslate.api;

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
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public record TranslatedMessage(@NotNull Component component, @NotNull TranslationKey originalKey) {

    private static final Logger LOGGER = Logger.getLogger(TranslatedMessage.class.getName());

    public TranslatedMessage {
        Objects.requireNonNull(component, "Component cannot be null");
        Objects.requireNonNull(originalKey, "Original key cannot be null");
    }

    @NotNull
    public String asPlainText() {
        return PlainTextComponentSerializer.plainText().serialize(this.component);
    }

    @NotNull
    public String asLegacyText() {
        return LegacyComponentSerializer.legacySection().serialize(this.component);
    }

    public void sendTo(@NotNull final Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        try {
            player.sendMessage(this.component);
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to send message to player " + player.getName() + " for key " + this.originalKey, exception);
            sendLegacyFallback(player);
        }
    }

    public void sendActionBar(@NotNull final Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        try {
            player.sendActionBar(this.component);
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to send action bar to player " + player.getName() + " for key " + this.originalKey, exception);
        }
    }

    public void sendTitle(@NotNull final Player player) {
        sendTitle(player, null, Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1));
    }

    public void sendTitle(@NotNull final Player player, @Nullable final Component subtitle, @NotNull final Duration fadeIn, @NotNull final Duration stay, @NotNull final Duration fadeOut) {
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
            LOGGER.log(Level.WARNING, "Failed to send title to player " + player.getName() + " for key " + this.originalKey, exception);
        }
    }

    public boolean isEmpty() {
        return asPlainText().trim().isEmpty();
    }

    public int length() {
        return asPlainText().length();
    }

    public boolean contains(@NotNull final String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        return asPlainText().toLowerCase().contains(text.toLowerCase());
    }

    @NotNull
    public TranslatedMessage withKey(@NotNull final TranslationKey newKey) {
        Objects.requireNonNull(newKey, "New key cannot be null");
        return new TranslatedMessage(this.component, newKey);
    }

    @NotNull
    public TranslatedMessage append(@NotNull final Component other) {
        Objects.requireNonNull(other, "Other component cannot be null");
        return new TranslatedMessage(this.component.append(other), this.originalKey);
    }

    @NotNull
    public TranslatedMessage prepend(@NotNull final Component other) {
        Objects.requireNonNull(other, "Other component cannot be null");
        return new TranslatedMessage(other.append(this.component), this.originalKey);
    }

    @NotNull
    public List<Component> splitLines() {
        final String text = asPlainText();
        if (text.isEmpty()) {
            return List.of();
        }

        final String[] lines = text.split("\\n");
        final List<Component> components = new ArrayList<>();

        for (final String line : lines) {
            if (!line.trim().isEmpty()) {
                try {
                    if (line.contains("<") && line.contains(">")) {
                        components.add(MiniMessage.miniMessage().deserialize(line));
                    } else {
                        components.add(Component.text(line));
                    }
                } catch (final Exception exception) {
                    components.add(Component.text(line));
                }
            }
        }
        return components;
    }

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
