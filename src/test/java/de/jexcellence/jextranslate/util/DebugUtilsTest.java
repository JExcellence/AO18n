package de.jexcellence.jextranslate.util;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.jexcellence.jextranslate.api.LocaleResolver;
import de.jexcellence.jextranslate.api.MessageFormatter;
import de.jexcellence.jextranslate.api.TranslationKey;
import de.jexcellence.jextranslate.api.TranslationRepository;
import de.jexcellence.jextranslate.api.TranslationService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DebugUtilsTest {

    private ServerMock server;
    private PlayerMock player;
    private TranslationRepository repository;
    private TranslationRepository.RepositoryMetadata metadata;
    private LocaleResolver resolver;
    private MessageFormatter formatter;

    @BeforeEach
    void setUp() {
        this.server = MockBukkit.mock();
        this.player = new PlayerMock(this.server, "Debugger");

        this.repository = mock(TranslationRepository.class);
        this.metadata = mock(TranslationRepository.RepositoryMetadata.class);
        this.resolver = mock(LocaleResolver.class);
        this.formatter = mock(MessageFormatter.class);

        when(this.resolver.resolveLocale(any(Player.class))).thenReturn(Optional.of(Locale.ENGLISH));
        when(this.resolver.getDefaultLocale()).thenReturn(Locale.ENGLISH);

        when(this.repository.getDefaultLocale()).thenReturn(Locale.ENGLISH);
        when(this.repository.getAvailableLocales()).thenReturn(Set.of(Locale.ENGLISH));
        when(this.repository.getAllAvailableKeys()).thenReturn(Set.of(TranslationKey.of("greeting")));
        when(this.repository.reload()).thenReturn(CompletableFuture.completedFuture(null));
        when(this.repository.getMetadata()).thenReturn(this.metadata);

        when(this.metadata.getType()).thenReturn("memory");
        when(this.metadata.getSource()).thenReturn("unit-test");
        when(this.metadata.getLastModified()).thenReturn(123L);
        when(this.metadata.getTotalTranslations()).thenReturn(1);

        TranslationService.configure(new TranslationService.ServiceConfiguration(
            this.repository,
            this.formatter,
            this.resolver
        ));
    }

    @AfterEach
    void tearDown() throws Exception {
        MockBukkit.unmock();
        TranslationService.clearLocaleCache();
        resetConfiguration();
    }

    @Test
    void debugTranslationIncludesLocaleAndTranslationDetails() {
        final Locale resolvedLocale = new Locale("en", "US");
        when(this.resolver.resolveLocale(this.player)).thenReturn(Optional.of(resolvedLocale));
        when(this.formatter.formatComponent(eq("Hello!"), anyList(), eq(resolvedLocale)))
            .thenReturn(Component.text("Hello!"));

        final TranslationKey key = TranslationKey.of("greeting");
        when(this.repository.getTranslation(eq(key), eq(resolvedLocale))).thenReturn(Optional.of("Hello!"));

        final String report = DebugUtils.debugTranslation("greeting", this.player);

        assertTrue(report.contains("Key: greeting"));
        assertTrue(report.contains("Player: " + this.player.getName()));
        assertTrue(report.contains("Player Locale: en_US"));
        assertTrue(report.contains("Resolved Locale: en_US"));
        assertTrue(report.contains("Default Locale: en"));
        assertTrue(report.contains("Translation Found: true"));
        assertTrue(report.contains("Translation Value: 'Hello!'"));
        assertTrue(report.contains("Message Text: 'Hello!'"));
        assertTrue(report.contains("=== End Debug Report ==="));
    }

    @Test
    void debugTranslationProvidesFallbackInformationWhenMissing() {
        final Locale resolvedLocale = new Locale("fr", "CA");
        when(this.resolver.resolveLocale(this.player)).thenReturn(Optional.of(resolvedLocale));

        final TranslationKey key = TranslationKey.of("missing.key");
        when(this.repository.getTranslation(eq(key), eq(resolvedLocale))).thenReturn(Optional.empty());
        when(this.repository.getTranslation(eq(key), eq(new Locale("fr")))).thenReturn(Optional.of("Salut!"));
        when(this.repository.getTranslation(eq(key), eq(Locale.ENGLISH))).thenReturn(Optional.of("Hello!"));

        final String report = DebugUtils.debugTranslation("missing.key", this.player);

        assertTrue(report.contains("Translation Found: false"));
        assertTrue(report.contains("Translation Value: NOT_FOUND"));
        assertTrue(report.contains("Language-only Fallback (fr): true"));
        assertTrue(report.contains("Fallback Value: 'Salut!'"));
        assertTrue(report.contains("Default Locale Fallback (en): true"));
        assertTrue(report.contains("Default Value: 'Hello!'"));
        assertTrue(report.contains("Message Text: 'missing.key'"));
    }

    @Test
    void debugTranslationReportsUnconfiguredService() throws Exception {
        resetConfiguration();

        final String report = DebugUtils.debugTranslation("greeting", this.player);

        assertTrue(report.contains("ERROR: TranslationService not configured!"));
    }

    @Test
    void compareTranslationsOutputsDifferences() {
        final Locale resolvedLocale = Locale.ENGLISH;
        when(this.resolver.resolveLocale(this.player)).thenReturn(Optional.of(resolvedLocale));

        final TranslationKey firstKey = TranslationKey.of("first");
        final TranslationKey secondKey = TranslationKey.of("second");

        when(this.repository.getTranslation(eq(firstKey), eq(resolvedLocale))).thenReturn(Optional.of("First value"));
        when(this.repository.getTranslation(eq(secondKey), eq(resolvedLocale))).thenReturn(Optional.of("Second value"));

        when(this.formatter.formatComponent(eq("First value"), anyList(), eq(resolvedLocale)))
            .thenReturn(Component.text("First value"));
        when(this.formatter.formatComponent(eq("Second value"), anyList(), eq(resolvedLocale)))
            .thenReturn(Component.text("Second value"));

        final String comparison = DebugUtils.compareTranslations("first", "second", this.player);

        assertTrue(comparison.contains("Message 1 Text: 'First value'"));
        assertTrue(comparison.contains("Message 2 Text: 'Second value'"));
        assertTrue(comparison.contains("Texts Equal: false"));
    }

    @Test
    void forceRefreshClearsSpecificPlayerAndReloadsRepository() {
        final String status = DebugUtils.forceRefresh(this.player);

        assertTrue(status.contains("Cleared locale cache for player: " + this.player.getName()));
        assertTrue(status.contains("Reloaded translation repository"));
        assertTrue(status.contains("Refresh completed successfully"));
        verify(this.repository).reload();
    }

    @Test
    void forceRefreshClearsAllPlayersWhenNoPlayerProvided() {
        final String status = DebugUtils.forceRefresh(null);

        assertTrue(status.contains("Cleared locale cache for all players"));
        assertTrue(status.contains("Reloaded translation repository"));
        assertTrue(status.contains("Refresh completed successfully"));
        verify(this.repository).reload();
    }

    @Test
    void getSystemStatusReportsConfiguredDetails() {
        when(this.repository.getAvailableLocales()).thenReturn(Set.of(Locale.ENGLISH, Locale.GERMAN));
        when(this.repository.getAllAvailableKeys()).thenReturn(Set.of(TranslationKey.of("a"), TranslationKey.of("b")));

        final String status = DebugUtils.getSystemStatus();

        assertTrue(status.contains("Service: CONFIGURED"));
        assertTrue(status.contains("Default Locale: en"));
        assertTrue(status.contains("Available Locales: [en, de]"));
        assertTrue(status.contains("Total Keys: 2"));
        assertTrue(status.contains("Repository Type: memory"));
        assertTrue(status.contains("Repository Source: unit-test"));
        assertTrue(status.contains("Last Modified: 123"));
        assertTrue(status.contains("Total Translations: 1"));
    }

    @Test
    void getSystemStatusReportsUnconfiguredService() throws Exception {
        resetConfiguration();

        final String status = DebugUtils.getSystemStatus();

        assertTrue(status.contains("Service: NOT_CONFIGURED"));
    }

    private static void resetConfiguration() throws Exception {
        final Field configuration = TranslationService.class.getDeclaredField("configuration");
        configuration.setAccessible(true);
        configuration.set(null, null);
    }
}
