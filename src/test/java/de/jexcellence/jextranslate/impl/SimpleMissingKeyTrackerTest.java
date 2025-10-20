package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.MissingKeyTracker;
import de.jexcellence.jextranslate.api.TranslationKey;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleMissingKeyTrackerTest {

    @Test
    void trackMultipleKeysAcrossLocalesMaintainsSnapshots() {
        final SimpleMissingKeyTracker tracker = new SimpleMissingKeyTracker();
        final Locale us = Locale.US;
        final Locale fr = Locale.FRANCE;
        final TranslationKey greeting = TranslationKey.of("messages", "greeting");
        final TranslationKey farewell = TranslationKey.of("messages", "farewell");
        final TranslationKey quest = TranslationKey.of("quests", "start");

        tracker.trackMissing(greeting, us);
        tracker.trackMissing(farewell, us);
        tracker.trackMissing(farewell, us);
        tracker.trackMissing(greeting, fr);
        tracker.trackMissing(quest, fr);

        assertEquals(2, tracker.getMissingCount(us), "US locale should retain unique keys");
        assertEquals(2, tracker.getMissingCount(fr), "FR locale should retain unique keys");
        assertEquals(4, tracker.getTotalMissingCount(), "Total missing count should sum across locales");
        assertEquals(Set.of(us, fr), tracker.getLocalesWithMissingKeys(), "All locales should be reported");
        assertTrue(tracker.isMissing(greeting, us), "Tracked key should be marked missing for US");
        assertTrue(tracker.isMissing(farewell, us), "Tracked key should be marked missing for US");
        assertTrue(tracker.isMissing(greeting, fr), "Tracked key should be marked missing for FR");
        assertTrue(tracker.isMissing(quest, fr), "Tracked key should be marked missing for FR");

        final Map<Locale, Set<TranslationKey>> snapshot = tracker.getAllMissingKeys();
        assertEquals(Set.of(greeting, farewell), snapshot.get(us), "Snapshot should capture US keys");
        assertEquals(Set.of(greeting, quest), snapshot.get(fr), "Snapshot should capture FR keys");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(Locale.CANADA, Set.of()),
            "Snapshot map should be immutable");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(us).add(quest),
            "Snapshot set should be immutable");

        tracker.markResolved(farewell, us);
        assertFalse(tracker.isMissing(farewell, us), "Tracker should reflect removal after markResolved");
        assertTrue(snapshot.get(us).contains(farewell), "Snapshot should remain unchanged after tracker mutation");
    }

    @Test
    void markResolvedAndClearOperationsMutateState() {
        final SimpleMissingKeyTracker tracker = new SimpleMissingKeyTracker();
        final Locale us = Locale.US;
        final Locale fr = Locale.FRANCE;
        final TranslationKey greeting = TranslationKey.of("messages", "greeting");
        final TranslationKey farewell = TranslationKey.of("messages", "farewell");

        tracker.trackMissing(greeting, us);
        tracker.trackMissing(farewell, fr);

        assertTrue(tracker.markResolved(greeting, us), "markResolved should remove the key when present");
        assertFalse(tracker.isMissing(greeting, us), "Key should no longer be missing after resolution");
        assertEquals(0, tracker.getMissingCount(us), "Locale count should drop to zero after resolution");
        assertFalse(tracker.markResolved(greeting, us), "markResolved should return false when nothing changes");

        tracker.trackMissing(greeting, us);
        tracker.trackMissing(farewell, fr);
        assertEquals(1, tracker.clearMissing(us), "clearMissing should remove all locale keys");
        assertEquals(0, tracker.getMissingCount(us), "Locale should have no missing keys after clear");

        tracker.trackMissing(greeting, us);
        tracker.trackMissing(farewell, fr);
        assertEquals(2, tracker.clearAllMissing(), "clearAllMissing should report the total removed keys");
        assertEquals(0, tracker.getTotalMissingCount(), "Tracker should be empty after clearAllMissing");
        assertTrue(tracker.getAllMissingKeys().isEmpty(), "All locales should be cleared");

        tracker.setEnabled(false);
        assertFalse(tracker.isEnabled(), "Tracker should report disabled state");
        tracker.trackMissing(greeting, us);
        assertEquals(0, tracker.getTotalMissingCount(), "Tracking should be ignored when disabled");
        assertTrue(tracker.getAllMissingKeys().isEmpty(), "No keys should be recorded while disabled");
    }

    @Test
    void statisticsSnapshotReflectsLiveDataAndTimestamps() {
        final SimpleMissingKeyTracker tracker = new SimpleMissingKeyTracker();
        final MissingKeyTracker.Statistics statistics = tracker.getStatistics();

        assertEquals(0, statistics.getTotalTrackingEvents(), "No events should be recorded initially");
        assertEquals(0, statistics.getUniqueMissingCount(), "No unique keys should be present initially");
        assertEquals(0, statistics.getAffectedLocaleCount(), "No locales should be affected initially");
        assertNull(statistics.getLastTrackingTime(), "Last tracking time should be null before tracking");
        assertNotNull(statistics.getTrackingStartTime(), "Start time should be initialized");

        final TranslationKey greeting = TranslationKey.of("messages", "greeting");
        final TranslationKey farewell = TranslationKey.of("messages", "farewell");
        final Locale us = Locale.US;
        final Locale fr = Locale.FRANCE;

        tracker.trackMissing(greeting, us);
        assertEquals(1, statistics.getTotalTrackingEvents(), "Statistics should update after tracking");
        assertEquals(1, statistics.getUniqueMissingCount(), "Unique key count should reflect tracked key");
        assertEquals(1, statistics.getAffectedLocaleCount(), "Affected locale count should update");
        final LocalDateTime firstTracking = statistics.getLastTrackingTime();
        assertNotNull(firstTracking, "Tracking should set the last tracking timestamp");
        assertSame(greeting, statistics.getMostFrequentMissing(), "Most frequent key should match tracked key");
        assertSame(us, statistics.getLocaleWithMostMissing(), "Locale with most missing keys should be US");

        tracker.trackMissing(greeting, us);
        tracker.trackMissing(farewell, fr);
        tracker.trackMissing(greeting, fr);

        assertEquals(4, statistics.getTotalTrackingEvents(), "All tracking events should be counted");
        assertEquals(2, statistics.getUniqueMissingCount(), "Unique keys should de-duplicate across locales");
        assertEquals(2, statistics.getAffectedLocaleCount(), "Both locales should be represented");
        assertSame(greeting, statistics.getMostFrequentMissing(), "Most frequent key should remain greeting");
        assertSame(fr, statistics.getLocaleWithMostMissing(), "France should have the most missing keys");

        final LocalDateTime latestTracking = statistics.getLastTrackingTime();
        assertNotNull(latestTracking, "Latest tracking timestamp should be available");
        assertTrue(!latestTracking.isBefore(firstTracking), "Tracking timestamps should not move backwards");
        assertSame(statistics.getTrackingStartTime(), tracker.getTrackingStartTime(),
            "Statistics start time should mirror tracker start time");
    }
}

