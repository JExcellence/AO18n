package de.jexcellence.jextranslate.core;

import de.jexcellence.jextranslate.bedrock.BedrockConverter;
import de.jexcellence.jextranslate.bedrock.BedrockDetectionCache;
import de.jexcellence.jextranslate.bedrock.BedrockFormatMode;
import de.jexcellence.jextranslate.bedrock.HexColorFallback;
import de.jexcellence.jextranslate.config.R18nConfiguration;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;

/**
 * Version-aware message sender that handles both legacy (1.8-1.12) and modern (1.13+) Minecraft versions.
 *
 * <p>This class automatically detects the server version and uses the appropriate method to send messages:</p>
 * <ul>
 *   <li><strong>Legacy versions (1.8-1.12)</strong>: Converts Components to legacy strings and uses player.sendMessage(String)</li>
 *   <li><strong>Modern versions (1.13+)</strong>: Uses Adventure Audiences for full Component support</li>
 * </ul>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class VersionedMessageSender {

    private static final Logger LOGGER = Logger.getLogger(VersionedMessageSender.class.getName());
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final VersionDetector versionDetector;
    private final BukkitAudiences audiences;
    private BedrockDetectionCache bedrockDetectionCache;
    private R18nConfiguration configuration;

    /**
     * Creates a new versioned message sender.
     *
     * @param versionDetector the version detector
     * @param audiences       the Adventure audiences instance (null for legacy versions)
     */
    public VersionedMessageSender(@NotNull VersionDetector versionDetector, @Nullable BukkitAudiences audiences) {
        this.versionDetector = versionDetector;
        this.audiences = audiences;
    }

    /**
     * Sets the Bedrock detection cache for automatic Bedrock player handling.
     *
     * @param cache the Bedrock detection cache
     */
    public void setBedrockDetectionCache(@Nullable BedrockDetectionCache cache) {
        this.bedrockDetectionCache = cache;
    }

    /**
     * Sets the R18n configuration for Bedrock format settings.
     *
     * @param configuration the R18n configuration
     */
    public void setConfiguration(@Nullable R18nConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Sends a component message to a player using the appropriate method for the server version.
 *
 * <p>If the player is detected as a Bedrock player and Bedrock support is enabled,
     * the message will be automatically converted to legacy format.
     *
     * @param player    the target player
     * @param component the component to send
     */
    public void sendMessage(@NotNull Player player, @NotNull Component component) {
        // Check if player is Bedrock and Bedrock support is enabled
        if (isBedrockSupportEnabled() && isBedrockPlayer(player)) {
            sendBedrockMessage(player, component);
            return;
        }

        if (supportsComponents()) {
            if (audiences != null) {
                audiences.player(player).sendMessage(component);
            } else {
                sendLegacyMessage(player, component);
            }
        } else {
            sendLegacyMessage(player, component);
        }
    }

    /**
     * Sends a component message to a command sender using the appropriate method.
     *
     * @param sender    the target command sender
     * @param component the component to send
     */
    public void sendMessage(@NotNull CommandSender sender, @NotNull Component component) {
        if (sender instanceof Player player) {
            sendMessage(player, component);
            return;
        }

        if (supportsComponents() && audiences != null) {
            audiences.sender(sender).sendMessage(component);
        } else {
            String legacyMessage = LEGACY_SERIALIZER.serialize(component);
            sender.sendMessage(legacyMessage);
        }
    }

    /**
     * Sends a component message to an Adventure audience.
     *
     * @param audience  the target audience
     * @param component the component to send
     */
    public void sendMessage(@NotNull Audience audience, @NotNull Component component) {
        if (supportsComponents() && audiences != null) {
            audience.sendMessage(component);
        } else {
            LOGGER.warning("Attempted to send to Adventure audience on unsupported version");
        }
    }

    /**
     * Broadcasts a component message to all online players.
     *
     * @param component the component to broadcast
     */
    public void broadcast(@NotNull Component component) {
        if (supportsComponents() && audiences != null) {
            audiences.all().sendMessage(component);
        } else {
            String legacyMessage = LEGACY_SERIALIZER.serialize(component);
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(legacyMessage));
        }
    }

    /**
     * Sends a message to console.
     *
     * @param component the component to send
     */
    public void console(@NotNull Component component) {
        if (supportsComponents() && audiences != null) {
            audiences.console().sendMessage(component);
        } else {
            String legacyMessage = LEGACY_SERIALIZER.serialize(component);
            Bukkit.getConsoleSender().sendMessage(legacyMessage);
        }
    }

    /**
     * Sends a legacy string message to a player.
     *
     * @param player    the target player
     * @param component the component to convert and send
     */
    private void sendLegacyMessage(@NotNull Player player, @NotNull Component component) {
        String legacyMessage = LEGACY_SERIALIZER.serialize(component);
        player.sendMessage(legacyMessage);
    }

    /**
     * Checks if the current server version supports Adventure Components.
     *
     * @return true if Components are supported
     */
    private boolean supportsComponents() {
        return versionDetector.hasNativeAdventure() ||
                (versionDetector.isModern() && audiences != null);
    }

    /**
     * Checks if Adventure audiences are available.
     *
     * @return true if audiences are available
     */
    public boolean hasAudiences() {
        return audiences != null;
    }

    /**
     * Gets the version detector.
     *
     * @return the version detector
     */
    @NotNull
    public VersionDetector getVersionDetector() {
        return versionDetector;
    }

    /**
     * Gets the Adventure audiences instance.
     *
     * @return the audiences instance, or null if not available
     */
    @Nullable
    public BukkitAudiences getAudiences() {
        return audiences;
    }

    /**
     * Converts a Component to a legacy string for older versions.
     *
     * @param component the component to convert
     * @return the legacy string representation
     */
    @NotNull
    public String toLegacyString(@NotNull Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Checks if the server supports modern features.
     *
     * @return true if modern features are supported
     */
    public boolean isModern() {
        return versionDetector.isModern();
    }

    /**
     * Checks if the server is legacy (pre-1.13).
     *
     * @return true if the server is legacy
     */
    public boolean isLegacy() {
        return versionDetector.isLegacy();
    }

    /**
     * Sends a Bedrock-compatible message to a player.
 *
 * <p>Geyser handles Adventure components properly - it only lacks support for
     * click events, hover events, and custom fonts. We strip those unsupported
     * features but keep the component format with colors intact.
     *
     * @param player    the target player
     * @param component the component to convert and send
     */
    private void sendBedrockMessage(@NotNull Player player, @NotNull Component component) {
        // Strip only unsupported features (click/hover events, custom fonts)
        // but keep colors and formatting - Geyser handles those fine
        Component strippedComponent = BedrockConverter.stripUnsupportedFormatting(component);
        
        // Send as component through Adventure - Geyser translates it properly
        if (audiences != null) {
            audiences.player(player).sendMessage(strippedComponent);
        } else {
            // Fallback to legacy only if Adventure is not available
            String legacyMessage = LEGACY_SERIALIZER.serialize(strippedComponent);
            player.sendMessage(legacyMessage);
        }
    }

    /**
     * Checks if Bedrock support is enabled.
     *
     * @return true if Bedrock support is enabled
     */
    private boolean isBedrockSupportEnabled() {
        return configuration == null || configuration.bedrockSupportEnabled();
    }

    /**
     * Checks if a player is a Bedrock player.
     *
     * @param player the player to check
     * @return true if the player is a Bedrock player
     */
    private boolean isBedrockPlayer(@NotNull Player player) {
        return bedrockDetectionCache != null && bedrockDetectionCache.isBedrockPlayer(player);
    }
}
