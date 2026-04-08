package de.jexcellence.jextranslate.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Lightweight logging adapter that routes diagnostics through Raindrop Central's {@code CentralLogger}.
 * when available, falling back to JUL when executed outside the platform. Provides helpers for
 * sanitising structured context and anonymising sensitive identifiers before they reach log files.
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.0
 */
public final class TranslationLogger {

    private static final Pattern ALLOWED_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final int MAX_VALUE_LENGTH = 256;
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final String HASH_ALGORITHM = "SHA-256";

    private static final boolean CENTRAL_LOGGER_AVAILABLE;
    private static final Method CENTRAL_LOGGER_BY_CLASS;
    private static final Method CENTRAL_LOGGER_BY_NAME;

    static {
        Method byClass = null;
        Method byName = null;
        boolean available = false;
        try {
            final Class<?> centralLoggerClass = Class.forName("com.raindropcentral.rplatform.logging.CentralLogger");
            byClass = centralLoggerClass.getMethod("getLogger", Class.class);
            byName = centralLoggerClass.getMethod("getLogger", String.class);
            available = true;
        } catch (final ClassNotFoundException | NoSuchMethodException ignored) {
            available = false;
        }
        CENTRAL_LOGGER_AVAILABLE = available;
        CENTRAL_LOGGER_BY_CLASS = byClass;
        CENTRAL_LOGGER_BY_NAME = byName;
    }

    private TranslationLogger() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Obtains a logger using {@code CentralLogger} when available.
     *
     * @param type the backing class
     * @return logger instance routed through the shared logging pipeline when possible
     */
    @NotNull
    public static Logger getLogger(@NotNull final Class<?> type) {
        Objects.requireNonNull(type, "Type cannot be null");
        if (CENTRAL_LOGGER_AVAILABLE && CENTRAL_LOGGER_BY_CLASS != null) {
            try {
                return (Logger) CENTRAL_LOGGER_BY_CLASS.invoke(null, type);
            } catch (final Exception ignored) {
                // Fallback to JUL below
            }
        }
        return Logger.getLogger(type.getName());
    }

    /**
     * Obtains a logger by name using {@code CentralLogger} when available.
     *
     * @param name the logger name
     * @return logger instance routed through the shared logging pipeline when possible
     */
    @NotNull
    public static Logger getLogger(@NotNull final String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        if (CENTRAL_LOGGER_AVAILABLE && CENTRAL_LOGGER_BY_NAME != null) {
            try {
                return (Logger) CENTRAL_LOGGER_BY_NAME.invoke(null, name);
            } catch (final Exception ignored) {
                // Fallback to JUL below
            }
        }
        return Logger.getLogger(name);
    }

    /**
     * Formats a message with structured key-value context appended.
     *
     * @param message the base message
     * @param context optional context map
     * @return formatted message containing context when provided
     */
    @NotNull
    public static String message(@NotNull final String message, @Nullable final Map<String, ?> context) {
        Objects.requireNonNull(message, "Message cannot be null");
        if (context == null || context.isEmpty()) {
            return message;
        }
        return message + formatContext(context);
    }

    /**
     * Converts the supplied context map into a sanitised key-value representation suitable for logs.
     *
     * @param context the context map
     * @return formatted context string beginning with a leading space
     */
    @NotNull
    public static String formatContext(@NotNull final Map<String, ?> context) {
        Objects.requireNonNull(context, "Context cannot be null");
        if (context.isEmpty()) {
            return "";
        }
        final StringJoiner joiner = new StringJoiner(", ", " context={", "}");
        for (final Map.Entry<String, ?> entry : context.entrySet()) {
            final String key = sanitiseKey(entry.getKey());
            final String value = sanitiseValue(entry.getValue());
            joiner.add(key + "=" + value);
        }
        return " " + joiner;
    }

    /**
     * Produces a stable anonymised identifier suitable for INFO/WARN log levels.
     *
     * @param input the sensitive identifier to hash
     * @return anonymised identifier string
     */
    @NotNull
    public static String anonymize(@NotNull final CharSequence input) {
        Objects.requireNonNull(input, "Input cannot be null");
        try {
            final MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            final byte[] hashed = digest.digest(input.toString().getBytes(StandardCharsets.UTF_8));
            final String hex = HEX_FORMAT.formatHex(hashed);
            return hex.substring(0, Math.min(12, hex.length())).toLowerCase(Locale.ROOT);
        } catch (final NoSuchAlgorithmException ignored) {
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * Produces a stable anonymised identifier for UUID inputs.
     *
     * @param uuid the UUID to anonymise
     * @return anonymised identifier string
     */
    @NotNull
    public static String anonymize(@NotNull final UUID uuid) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        return anonymize(uuid.toString());
    }

    /**
     * Logs a warning when sanitisation fails unexpectedly.
     */
    private static void logSanitisationWarning(@NotNull final String message, @NotNull final Exception exception) {
        final Logger logger = getLogger(TranslationLogger.class);
        logger.log(Level.FINE, message, exception);
    }

    @NotNull
    private static String sanitiseKey(@Nullable final String rawKey) {
        final String key = rawKey == null ? "unknown" : rawKey.trim();
        if (ALLOWED_KEY_PATTERN.matcher(key).matches()) {
            return key;
        }
        final StringBuilder builder = new StringBuilder(key.length());
        for (final char c : key.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    @NotNull
    private static String sanitiseValue(@Nullable final Object value) {
        if (value == null) {
            return "null";
        }
        try {
            final String asString = String.valueOf(value);
            if (asString.length() <= MAX_VALUE_LENGTH) {
                return asString;
            }
            return asString.substring(0, MAX_VALUE_LENGTH - 3) + "...";
        } catch (final Exception exception) {
            logSanitisationWarning("Failed to sanitise log value", exception);
            return "<error>";
        }
    }
}
