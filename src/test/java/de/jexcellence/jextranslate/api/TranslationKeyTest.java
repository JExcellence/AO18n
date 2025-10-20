package de.jexcellence.jextranslate.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationKeyTest {

    @Test
    void ofStringCreatesValidatedKey() {
        final TranslationKey key = TranslationKey.of("messages.greeting.hello");

        assertEquals("messages.greeting.hello", key.key(), "Key should match the supplied value");
    }

    @Test
    void ofSegmentsJoinsSegmentsWithSeparator() {
        final TranslationKey key = TranslationKey.of("messages", "greeting", "hello");

        assertEquals("messages.greeting.hello", key.key(), "Segments should be joined with dots");
    }

    @Test
    void ofSegmentsRejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, TranslationKey::of, "Empty segments array should be rejected");
    }

    @Test
    void factoriesRejectNullKeys() {
        assertThrows(NullPointerException.class, () -> TranslationKey.of((String) null), "Null key should be rejected");
    }

    @Test
    void factoriesRejectInvalidKeys() {
        for (final String invalid : List.of("", ".leading", "trailing.", "double..dot", "invalid!")) {
            assertThrows(IllegalArgumentException.class, () -> TranslationKey.of(invalid), () -> "Invalid key should be rejected: " + invalid);
        }
    }

    @Test
    void childAppendsSegment() {
        final TranslationKey parent = TranslationKey.of("messages.greeting");
        final TranslationKey child = parent.child("hello");

        assertEquals("messages.greeting.hello", child.key(), "Child key should append segment");
        assertEquals(parent, child.parent(), "Parent should be the original key");
    }

    @Test
    void childRejectsNullSegment() {
        final TranslationKey key = TranslationKey.of("messages");

        assertThrows(NullPointerException.class, () -> key.child(null), "Null child segment should be rejected");
    }

    @Test
    void childRejectsInvalidSegment() {
        final TranslationKey key = TranslationKey.of("messages");

        assertThrows(IllegalArgumentException.class, () -> key.child("Invalid"), "Invalid child segment should be rejected");
    }

    @Test
    void parentNavigatesThroughHierarchy() {
        final TranslationKey key = TranslationKey.of("a.b.c");

        final TranslationKey parent = key.parent();
        final TranslationKey root = parent != null ? parent.parent() : null;

        assertEquals(TranslationKey.of("a.b"), parent, "First parent should drop the last segment");
        assertEquals(TranslationKey.of("a"), root, "Second parent should reach the root key");
        assertNull(root != null ? root.parent() : null, "Root parent should be null");
    }

    @Test
    void parentOfRootIsNull() {
        final TranslationKey key = TranslationKey.of("root");

        assertNull(key.parent(), "Root keys should not have a parent");
    }

    @Test
    void lastSegmentReturnsTerminalPart() {
        final TranslationKey key = TranslationKey.of("messages.greeting.hello");
        final TranslationKey root = TranslationKey.of("root");

        assertEquals("hello", key.lastSegment(), "Last segment should match the final path segment");
        assertEquals("root", root.lastSegment(), "Root key last segment should be the key itself");
    }

    @Test
    void startsWithSupportsStringAndKeyPrefixes() {
        final TranslationKey key = TranslationKey.of("messages.greeting.hello");
        final TranslationKey prefix = TranslationKey.of("messages.greeting");

        assertTrue(key.startsWith("messages"), "Key should start with string prefix");
        assertTrue(key.startsWith(prefix), "Key should start with key prefix");
        assertFalse(key.startsWith("other"), "Key should not match unrelated prefix");
        assertFalse(key.startsWith(TranslationKey.of("messages.goodbye")), "Key should not match different key prefix");
    }

    @Test
    void startsWithRejectsNullPrefix() {
        final TranslationKey key = TranslationKey.of("messages");

        assertThrows(NullPointerException.class, () -> key.startsWith((String) null), "Null string prefix should be rejected");
        assertThrows(NullPointerException.class, () -> key.startsWith((TranslationKey) null), "Null key prefix should be rejected");
    }

    @Test
    void depthCountsSegments() {
        final TranslationKey root = TranslationKey.of("root");
        final TranslationKey nested = TranslationKey.of("a.b.c.d");

        assertEquals(1, root.depth(), "Root depth should be one");
        assertEquals(4, nested.depth(), "Nested depth should equal number of segments");
    }

    @Test
    void isRootReflectsSeparatorPresence() {
        final TranslationKey root = TranslationKey.of("root");
        final TranslationKey nested = TranslationKey.of("a.b");

        assertTrue(root.isRoot(), "Single segment keys should be root");
        assertFalse(nested.isRoot(), "Keys with separators should not be root");
    }

    @Test
    void toStringReturnsKey() {
        final TranslationKey key = TranslationKey.of("messages.greeting.hello");

        assertEquals("messages.greeting.hello", key.toString(), "toString should return the key value");
    }
}
