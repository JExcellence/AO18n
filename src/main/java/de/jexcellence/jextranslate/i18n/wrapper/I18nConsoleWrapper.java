package de.jexcellence.jextranslate.i18n.wrapper;

import de.jexcellence.jextranslate.R18nManager;
import de.jexcellence.jextranslate.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Version-independent wrapper for sending translated messages to the console.
 * Uses the default language configured in R18nManager.
 * Uses the Adventure platform for universal compatibility with Paper, Bukkit, and Spigot servers.
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public class I18nConsoleWrapper implements II18nVersionWrapper<Component> {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final String key;
    private final Map<String, String> placeholders;
    private final boolean includePrefix;

    /**
     * Constructs a new I18nConsoleWrapper.
     *
     * @param key           The translation key for the message.
     * @param placeholders  A map of placeholder names to their replacement values.
     * @param includePrefix Whether to include the prefix in the formatted message.
     */
    public I18nConsoleWrapper(@NotNull String key,
                               @NotNull Map<String, String> placeholders,
                               boolean includePrefix) {
        this.key = key;
        this.placeholders = placeholders;
        this.includePrefix = includePrefix;
    }

    @Override
    public void sendMessage() {
        R18nManager manager = R18nManager.getInstance();
        if (manager != null && manager.getAudiences() != null) {
            manager.getAudiences().console().sendMessage(this.getFormattedMessage());
        }
    }

    @Override
    public void sendMessages() {
        R18nManager manager = R18nManager.getInstance();
        if (manager != null && manager.getAudiences() != null) {
            for (Component component : this.getMessagesIncludingPlaceholdersAndPrefix()) {
                manager.getAudiences().console().sendMessage(component);
            }
        }
    }

    @Override
    @NotNull
    public Component getMessageType() {
        return Component.empty();
    }

    @Override
    @NotNull
    public Component displayMessage() {
        throw new UnsupportedOperationException("This method is not supported for the console.");
    }

    @Override
    @NotNull
    public List<Component> displayMessages() {
        throw new UnsupportedOperationException("This method is not supported for the console.");
    }

    @Override
    @NotNull
    public Component getPrefix() {
        return this.joinComponents(this.getPrefixByKey());
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
        String defaultLocale = manager != null 
                ? manager.getConfiguration().defaultLocale() 
                : "en_US";

        List<String> messages = null;
        if (manager != null) {
            Optional<List<String>> translation = manager.getTranslationLoader()
                    .getRawTranslation(key, defaultLocale);
            messages = translation.orElse(null);
        }

        if (messages == null || messages.isEmpty()) {
            messages = List.of("<gold>Message key <red>'" + key + "'</red> is missing!</gold>");
        }

        List<Component> result = new ArrayList<>();
        for (String message : messages) {
            String processedMessage = message;
            for (Map.Entry<String, String> entry : this.placeholders.entrySet()) {
                String placeholder = "%" + entry.getKey() + "%";
                String value = ColorUtil.convertLegacyColorsToMiniMessage(entry.getValue());
                processedMessage = processedMessage.replace(placeholder, value);
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
    private Component joinComponents(List<Component> components) {
        Component joined = Component.empty();
        for (Component comp : components) {
            if (!joined.equals(Component.empty())) {
                joined = joined.append(Component.newline());
            }
            joined = joined.append(comp);
        }
        return joined;
    }
}
