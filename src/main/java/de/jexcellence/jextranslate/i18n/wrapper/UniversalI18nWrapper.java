package de.jexcellence.jextranslate.i18n.wrapper;

import de.jexcellence.jextranslate.R18nManager;
import de.jexcellence.jextranslate.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Universal message formatting and sending wrapper for all Minecraft versions.
 *
 * <p>Uses the Adventure platform for universal compatibility with Paper, Bukkit, and Spigot servers.
 * Supports rich text formatting with MiniMessage, placeholder replacement, prefix inclusion,
 * and locale-aware message retrieval across all supported Minecraft versions.</p>
 *
 * <p>This implementation replaces the version-specific wrappers with a single, unified approach
 * that works across all Minecraft versions by leveraging Adventure's compatibility layer.</p>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public class UniversalI18nWrapper implements II18nVersionWrapper<Component> {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Player player;
    private final String key;
    private final Map<String, String> placeholders;
    private final boolean includePrefix;

    /**
     * Constructs a new UniversalI18nWrapper.
     *
     * @param player        The player to whom messages will be sent.
     * @param key           The translation key for the message.
     * @param placeholders  A map of placeholder names to their replacement values.
     * @param includePrefix Whether to include the prefix in the formatted message.
     */
    public UniversalI18nWrapper(@NotNull Player player,
                                 @NotNull String key,
                                 @NotNull Map<String, String> placeholders,
                                 boolean includePrefix) {
        this.player = player;
        this.key = key;
        this.placeholders = placeholders;
        this.includePrefix = includePrefix;
    }

    @Override
    public void sendMessage() {
        R18nManager manager = R18nManager.getInstance();
        if (manager != null && manager.getAudiences() != null) {
            manager.getAudiences().player(this.player).sendMessage(this.getFormattedMessage());
        }
    }

    @Override
    public void sendMessages() {
        R18nManager manager = R18nManager.getInstance();
        if (manager != null && manager.getAudiences() != null) {
            this.getMessagesIncludingPlaceholdersAndPrefix().forEach(message ->
                    manager.getAudiences().player(this.player).sendMessage(message));
        }
    }

    @Override
    @NotNull
    public Component getMessageType() {
        return Component.text("Component");
    }

    @Override
    @NotNull
    public Component displayMessage() {
        return this.getFormattedMessage();
    }

    @Override
    @NotNull
    public List<Component> displayMessages() {
        return this.getMessagesIncludingPlaceholdersAndPrefix();
    }

    @Override
    @NotNull
    public Component getPrefix() {
        return this.getJoinedMessageByKey();
    }

    @Override
    @NotNull
    public List<Component> getMessagesByKey() {
        return this.getRawMessagesByKey(this.key);
    }

    @Override
    @NotNull
    public List<Component> getPrefixByKey() {
        return this.getRawMessagesByKey(PREFIX_KEY);
    }

    @Override
    @NotNull
    public List<Component> getMessagesIncludingPlaceholdersAndPrefix() {
        List<Component> messages = new ArrayList<>(this.getMessagesByKey());
        if (this.includePrefix) {
            messages.addAll(0, this.getPrefixByKey());
        }
        return messages;
    }

    @Override
    @NotNull
    public Class<Component> getType() {
        return Component.class;
    }

    @Override
    @NotNull
    public List<Component> getMessagesIncludingPlaceholders() {
        return this.getMessagesByKey();
    }

    @Override
    @NotNull
    public Component getJoinedMessage() {
        return this.joinComponents(this.getMessagesIncludingPlaceholdersAndPrefix());
    }

    @Override
    @NotNull
    public Component getMessage() {
        return this.joinComponents(this.getMessagesByKey());
    }

    @Override
    @NotNull
    public Component getFormattedMessage() {
        return this.convertLegacyColorsInComponent(
                this.includePrefix
                        ? this.getPrefix().appendSpace().append(this.getMessage())
                        : this.getMessage()
        );
    }


    @Override
    @NotNull
    public List<Component> getRawMessagesByKey(@NotNull String key) {
        R18nManager manager = R18nManager.getInstance();
        String locale = this.getPlayerLocale();
        
        List<String> messages = null;
        if (manager != null) {
            Optional<List<String>> translation = manager.getTranslationLoader()
                    .getRawTranslation(key, locale);
            messages = translation.orElse(null);
        }

        if (messages == null || messages.isEmpty()) {
            messages = List.of("<gold>Message key <red>'" + key + "'</red> is missing!</gold>");
        }

        List<Component> result = new ArrayList<>();
        for (String message : messages) {
            String processedMessage = message;
            for (Map.Entry<String, String> entry : this.placeholders.entrySet()) {
                String placeholderPercent = "%" + entry.getKey() + "%";
                String placeholderBracket = "{" + entry.getKey() + "}";
                String value = ColorUtil.convertLegacyColorsToMiniMessage(entry.getValue());
                processedMessage = processedMessage.replace(placeholderPercent, value);
                processedMessage = processedMessage.replace(placeholderBracket, value);
            }
            result.add(MINI_MESSAGE.deserialize(processedMessage));
        }
        return result;
    }

    @Override
    @NotNull
    public List<Component> replacePlaceholders() {
        return new ArrayList<>(); // Placeholder for future PlaceholderAPI integration
    }

    @Override
    @NotNull
    public String asPlaceholder() {
        Component formattedMessage = this.getFormattedMessage();
        return MINI_MESSAGE.serialize(formattedMessage);
    }

    /**
     * Gets the player's locale with fallback support for older versions.
     *
     * @return The player's locale string, or default locale if unavailable.
     */
    @SuppressWarnings("deprecation")
    private String getPlayerLocale() {
        R18nManager manager = R18nManager.getInstance();
        String defaultLocale = manager != null 
                ? manager.getConfiguration().defaultLocale() 
                : "en_US";
        try {
            return this.player.getLocale();
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            return defaultLocale;
        }
    }

    /**
     * Joins all prefix messages into a single Component.
     *
     * @return The joined prefix component.
     */
    private Component getJoinedMessageByKey() {
        return this.joinComponents(this.getRawMessagesByKey(II18nVersionWrapper.PREFIX_KEY));
    }

    /**
     * Converts legacy color codes in a Component to MiniMessage format.
     *
     * @param component The component to convert.
     * @return The component with legacy colors converted.
     */
    private Component convertLegacyColorsInComponent(@NotNull Component component) {
        String serialized = MINI_MESSAGE.serialize(component);
        serialized = ColorUtil.convertLegacyColorsToMiniMessage(serialized);
        return MINI_MESSAGE.deserialize(serialized);
    }

    /**
     * Joins a list of Component objects into a single component.
     *
     * @param components The list of components to join.
     * @return The joined component.
     */
    private Component joinComponents(@NotNull List<Component> components) {
        Component joinedComponent = Component.empty();
        for (Component component : components) {
            joinedComponent = joinedComponent.append(component);
        }
        joinedComponent = joinedComponent.replaceText(
                TextReplacementConfig.builder()
                        .replacement(Component.text(""))
                        .match("\n")
                        .match("<br>")
                        .build()
        );
        return joinedComponent;
    }
}
