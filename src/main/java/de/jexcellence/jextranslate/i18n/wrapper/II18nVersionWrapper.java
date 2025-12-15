package de.jexcellence.jextranslate.i18n.wrapper;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interface for wrapping and handling internationalized (i18n) message versions.
 * Provides methods for sending, retrieving, formatting, and manipulating messages,
 * including support for placeholders and prefixes.
 *
 * @param <T> The type of message object handled by the implementation (e.g., String, Component).
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public interface II18nVersionWrapper<T> {

    /**
     * The key used to identify the prefix in message resources.
     */
    String PREFIX_KEY = "prefix";

    /**
     * Sends a single message to the intended recipient or output.
     */
    void sendMessage();

    /**
     * Sends multiple messages to the intended recipient or output.
     */
    void sendMessages();

    /**
     * Gets the type of message object handled by this wrapper.
     *
     * @return the message type.
     */
    @NotNull
    T getMessageType();

    /**
     * Displays a single message, typically to a user interface or console.
     *
     * @return the displayed message.
     */
    @NotNull
    T displayMessage();

    /**
     * Displays multiple messages, typically to a user interface or console.
     *
     * @return a list of displayed messages.
     */
    @NotNull
    List<T> displayMessages();

    /**
     * Retrieves the prefix used for messages.
     *
     * @return the prefix.
     */
    @NotNull
    T getPrefix();

    /**
     * Retrieves messages by a specific key.
     *
     * @return a list of messages associated with the key.
     */
    @NotNull
    List<T> getMessagesByKey();

    /**
     * Retrieves prefixes by a specific key.
     *
     * @return a list of prefixes associated with the key.
     */
    @NotNull
    List<T> getPrefixByKey();

    /**
     * Retrieves messages including placeholders and the prefix.
     *
     * @return a list of messages with placeholders and prefix included.
     */
    @NotNull
    List<T> getMessagesIncludingPlaceholdersAndPrefix();

    /**
     * Gets the class type of the message object.
     *
     * @return the class of the message type.
     */
    @SuppressWarnings("unchecked")
    @NotNull
    default Class<T> getType() {
        return (Class<T>) getFormattedMessage().getClass();
    }

    /**
     * Retrieves messages including placeholders.
     *
     * @return a list of messages with placeholders included.
     */
    @NotNull
    List<T> getMessagesIncludingPlaceholders();

    /**
     * Retrieves a joined message, typically concatenating multiple messages.
     *
     * @return the joined message.
     */
    @NotNull
    T getJoinedMessage();

    /**
     * Retrieves a single message.
     *
     * @return the message.
     */
    @NotNull
    T getMessage();

    /**
     * Retrieves a formatted message, with placeholders replaced and formatting applied.
     *
     * @return the formatted message.
     */
    @NotNull
    T getFormattedMessage();

    /**
     * Retrieves raw messages by a specific key.
     *
     * @param key the key to look up messages.
     * @return a list of raw messages associated with the key.
     */
    @NotNull
    List<T> getRawMessagesByKey(@NotNull String key);

    /**
     * Replaces placeholders in the messages with actual values.
     *
     * @return a list of messages with placeholders replaced.
     */
    @NotNull
    List<T> replacePlaceholders();

    /**
     * Returns the formatted message as a plain string for use in placeholders.
     * This method is useful when you need to use the message content within
     * placeholder systems or other string-based contexts.
     *
     * @return the formatted message as a plain string
     */
    @NotNull
    String asPlaceholder();
}
