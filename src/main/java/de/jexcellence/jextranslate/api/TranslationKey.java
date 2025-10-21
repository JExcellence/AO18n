package de.jexcellence.jextranslate.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable value object describing the hierarchical path to a translation entry inside a
 * {@link TranslationRepository}. Keys follow a lower-case dotted convention to align with YAML resource structures and
 * MiniMessage placeholder usage.
 *
 * <p>Utility methods on this record assist with navigating nested namespaces used by repository implementations, such as
 * {@link de.jexcellence.jextranslate.impl.YamlTranslationRepository}, ensuring builders and repositories reference keys
 * consistently.</p>
 *
 * <p>Construction validates the key pattern to prevent invalid repository lookups and accidental collisions.</p>
 *
 * @param key the normalized dotted path identifying a translation
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.1
 */
public record TranslationKey(@NotNull String key) {

    private static final String SEPARATOR = ".";
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-z0-9._-]+$");

    /**
     * Creates a new {@link TranslationKey} enforcing the repository naming rules.
     *
     * @param key the dotted key path referencing a translation entry
     * @throws NullPointerException     if the key is {@code null}
     * @throws IllegalArgumentException if the key violates repository naming constraints
     */
    public TranslationKey {
        Objects.requireNonNull(key, "Key cannot be null");
        validateKey(key);
    }

    /**
     * Creates a translation key from the provided string.
     *
     * @param key the dotted key path referencing a translation entry
     * @return the validated {@link TranslationKey}
     */
    @NotNull
    public static TranslationKey of(@NotNull final String key) {
        return new TranslationKey(key);
    }

    /**
     * Creates a translation key by joining multiple segments with the repository separator.
     *
     * @param segments path segments that should be joined using {@code .}
     * @return the resulting {@link TranslationKey}
     * @throws IllegalArgumentException if no segments are provided
     */
    @NotNull
    public static TranslationKey of(@NotNull final String... segments) {
        if (segments.length == 0) {
            throw new IllegalArgumentException("At least one segment is required");
        }
        return new TranslationKey(String.join(SEPARATOR, segments));
    }

    /**
     * Validates the supplied key against repository naming rules.
     *
     * @param key the key candidate to validate
     */
    private static void validateKey(@NotNull final String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be empty");
        }
        if (key.startsWith(SEPARATOR) || key.endsWith(SEPARATOR)) {
            throw new IllegalArgumentException("Key cannot start or end with separator: " + key);
        }
        if (key.contains("..")) {
            throw new IllegalArgumentException("Key cannot contain consecutive separators: " + key);
        }
        if (!VALID_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                "Key contains invalid characters (only lowercase letters, numbers, dots, hyphens, underscores): " + key
            );
        }
    }

    /**
     * Creates a child key by appending a new segment.
     *
     * @param segment the additional segment to append
     * @return a new {@link TranslationKey} combining the current path with the supplied segment
     */
    @NotNull
    public TranslationKey child(@NotNull final String segment) {
        Objects.requireNonNull(segment, "Segment cannot be null");
        return new TranslationKey(this.key + SEPARATOR + segment);
    }

    /**
     * Resolves the parent key, if available.
     *
     * @return the parent {@link TranslationKey} or {@code null} when at the root level
     */
    @Nullable
    public TranslationKey parent() {
        final int lastDot = this.key.lastIndexOf(SEPARATOR);
        return lastDot == -1 ? null : new TranslationKey(this.key.substring(0, lastDot));
    }

    /**
     * Returns the final segment of the key, often used for display purposes or fallback resolution.
     *
     * @return the terminal segment of the translation path
     */
    @NotNull
    public String lastSegment() {
        final int lastDot = this.key.lastIndexOf(SEPARATOR);
        return lastDot == -1 ? this.key : this.key.substring(lastDot + 1);
    }

    /**
     * Determines if the key begins with the provided string prefix.
     *
     * @param prefix the prefix to evaluate
     * @return {@code true} when the key starts with the prefix
     */
    public boolean startsWith(@NotNull final String prefix) {
        Objects.requireNonNull(prefix, "Prefix cannot be null");
        return this.key.startsWith(prefix);
    }

    /**
     * Determines if the key begins with another translation key.
     *
     * @param prefix the prefix key to evaluate
     * @return {@code true} when this key starts with the supplied key
     */
    public boolean startsWith(@NotNull final TranslationKey prefix) {
        Objects.requireNonNull(prefix, "Prefix cannot be null");
        return startsWith(prefix.key());
    }

    /**
     * Calculates the depth of the key within the repository tree.
     *
     * @return the number of segments represented by this key
     */
    public int depth() {
        return (int) this.key.chars().filter(ch -> ch == '.').count() + 1;
    }

    /**
     * Determines if the key is at the root level (contains no separators).
     *
     * @return {@code true} when the key is a top-level entry
     */
    public boolean isRoot() {
        return !this.key.contains(SEPARATOR);
    }

    @Override
    public String toString() {
        return this.key;
    }
}
