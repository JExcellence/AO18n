package de.jexcellence.jextranslate.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissingKeyTrackerTest {

    private static final TranslationKey SAMPLE_KEY = TranslationKey.of("example.key");
    private static final Locale SAMPLE_LOCALE = Locale.US;

    @Test
    void trackMissingWithContextDelegatesToTwoArgumentMethod() {
        final RecordingMissingKeyTracker tracker = new RecordingMissingKeyTracker();

        tracker.trackMissing(SAMPLE_KEY, SAMPLE_LOCALE, "command:example");

        assertEquals(1, tracker.getInvocations().size(), "Expected a single tracking invocation");
        final Invocation invocation = tracker.getInvocations().get(0);
        assertEquals(SAMPLE_KEY, invocation.key(), "Delegated key should match the input key");
        assertEquals(SAMPLE_LOCALE, invocation.locale(), "Delegated locale should match the input locale");
    }

    @Test
    void trackMissingWithNullContextStillDelegates() {
        final RecordingMissingKeyTracker tracker = new RecordingMissingKeyTracker();

        tracker.trackMissing(SAMPLE_KEY, SAMPLE_LOCALE, null);

        assertEquals(1, tracker.getInvocations().size(), "Expected a single tracking invocation");
        final Invocation invocation = tracker.getInvocations().get(0);
        assertEquals(SAMPLE_KEY, invocation.key(), "Delegated key should match the input key");
        assertEquals(SAMPLE_LOCALE, invocation.locale(), "Delegated locale should match the input locale");
    }

    private static final class RecordingMissingKeyTracker implements MissingKeyTracker {

        private final List<Invocation> invocations = new ArrayList<>();

        @Override
        public void trackMissing(final TranslationKey key, final Locale locale) {
            this.invocations.add(new Invocation(key, locale));
        }

        List<Invocation> getInvocations() {
            return this.invocations;
        }

        @Override
        public Set<TranslationKey> getMissingKeys(final Locale locale) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Locale, Set<TranslationKey>> getAllMissingKeys() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getTotalMissingCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMissingCount(final Locale locale) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Locale> getLocalesWithMissingKeys() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isMissing(final TranslationKey key, final Locale locale) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markResolved(final TranslationKey key, final Locale locale) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int clearMissing(final Locale locale) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int clearAllMissing() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalDateTime getTrackingStartTime() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEnabled() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setEnabled(final boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Statistics getStatistics() {
            throw new UnsupportedOperationException();
        }
    }

    private record Invocation(TranslationKey key, Locale locale) {
    }
}
