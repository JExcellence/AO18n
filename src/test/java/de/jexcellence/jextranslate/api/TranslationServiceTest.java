package de.jexcellence.jextranslate.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranslationServiceTest {

    private static final TranslationKey KEY = TranslationKey.of("greeting");
    private static final TranslationKey CUSTOM_PREFIX_KEY = TranslationKey.of("custom-prefix");
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Locale PLAYER_LOCALE = Locale.US;
    private static final Locale DEFAULT_RESOLVER_LOCALE = Locale.GERMANY;
    private static final Locale REPOSITORY_DEFAULT_LOCALE = Locale.ENGLISH;

    @Mock
    private TranslationRepository repository;

    @Mock
    private MessageFormatter formatter;

    @Mock
    private LocaleResolver localeResolver;

    @Mock
    private Player player;

    @BeforeEach
    void setUp() {
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.getName()).thenReturn("TestPlayer");
        lenient().when(localeResolver.resolveLocale(player)).thenReturn(Optional.of(PLAYER_LOCALE));
        when(localeResolver.getDefaultLocale()).thenReturn(DEFAULT_RESOLVER_LOCALE);
        when(repository.getDefaultLocale()).thenReturn(REPOSITORY_DEFAULT_LOCALE);
        lenient().when(formatter.formatComponent(anyString(), anyList(), any(Locale.class)))
            .thenAnswer(invocation -> Component.text((String) invocation.getArgument(0)));

        TranslationService.configure(new TranslationService.ServiceConfiguration(repository, formatter, localeResolver));
        TranslationService.clearLocaleCache();
    }

    @AfterEach
    void tearDown() throws Exception {
        TranslationService.clearLocaleCache();
        final Field configurationField = TranslationService.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        configurationField.set(null, null);
    }

    @Test
    void createCachesResolvedLocale() {
        TranslationService.create(KEY, player);
        TranslationService.create(KEY, player);

        verify(localeResolver, times(1)).resolveLocale(player);
    }

    @Test
    void createFreshForcesLocaleReevaluation() {
        TranslationService.create(KEY, player);
        TranslationService.createFresh(KEY, player);

        verify(localeResolver, times(2)).resolveLocale(player);
    }

    @Test
    void createWithExplicitLocaleBypassesResolver() {
        TranslationService.create(KEY, player, Locale.ITALIAN);

        verify(localeResolver, never()).resolveLocale(player);
    }

    @Test
    void createFallsBackToResolverDefaultWhenAbsent() {
        when(localeResolver.resolveLocale(player)).thenReturn(Optional.empty());
        when(repository.getTranslation(KEY, DEFAULT_RESOLVER_LOCALE)).thenReturn(Optional.of("default-template"));

        TranslationService.create(KEY, player).build();

        verify(localeResolver).getDefaultLocale();
        verify(repository).getTranslation(KEY, DEFAULT_RESOLVER_LOCALE);
    }

    @Test
    void createFallsBackToRepositoryDefaultOnResolverFailure() {
        when(localeResolver.resolveLocale(player)).thenThrow(new IllegalStateException("resolver failed"));
        when(repository.getTranslation(KEY, REPOSITORY_DEFAULT_LOCALE)).thenReturn(Optional.of("repo-template"));

        TranslationService.create(KEY, player).build();

        verify(repository).getTranslation(KEY, REPOSITORY_DEFAULT_LOCALE);
    }

    @Test
    void createThrowsWhenServiceUnconfigured() throws Exception {
        resetConfiguration();

        final IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> TranslationService.create(KEY, player)
        );
        assertTrue(exception.getMessage().contains("TranslationService not configured"));
    }

    @Test
    void buildReturnsFallbackWhenTranslationMissing() {
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.empty());

        final TranslatedMessage message = TranslationService.create(KEY, player).build();

        assertEquals(KEY.key(), message.asPlainText());
    }

    @Test
    void buildIncludesDefaultPrefixWhenRequested() {
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("main-template"));
        when(repository.getTranslation(TranslationKey.of("prefix"), PLAYER_LOCALE))
            .thenReturn(Optional.of("prefix-template"));
        when(formatter.formatComponent(eq("main-template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Main"));
        when(formatter.formatComponent(eq("prefix-template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("[P] "));

        final TranslatedMessage message = TranslationService.create(KEY, player)
            .withPrefix()
            .build();

        assertEquals("[P] Main", message.asPlainText());
    }

    @Test
    void buildUsesCustomPrefixKeyWhenProvided() {
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("main-template"));
        when(repository.getTranslation(CUSTOM_PREFIX_KEY, PLAYER_LOCALE)).thenReturn(Optional.of("custom-prefix"));
        when(formatter.formatComponent(eq("main-template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Body"));
        when(formatter.formatComponent(eq("custom-prefix"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("<C>"));

        final TranslatedMessage message = TranslationService.create(KEY, player)
            .withPrefix(CUSTOM_PREFIX_KEY)
            .build();

        assertEquals("<C>Body", message.asPlainText());
    }

    @Test
    void buildSkipsPrefixWhenTranslationMissing() {
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("main-template"));
        when(repository.getTranslation(TranslationKey.of("prefix"), PLAYER_LOCALE)).thenReturn(Optional.empty());
        when(formatter.formatComponent(eq("main-template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Main"));

        final TranslatedMessage message = TranslationService.create(KEY, player)
            .withPrefix()
            .build();

        assertEquals("Main", message.asPlainText());
    }

    @Test
    void buildContinuesWhenPrefixLookupFails() {
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("main-template"));
        when(repository.getTranslation(TranslationKey.of("prefix"), PLAYER_LOCALE))
            .thenThrow(new RuntimeException("prefix failure"));
        when(formatter.formatComponent(eq("main-template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Main"));

        final TranslatedMessage message = TranslationService.create(KEY, player)
            .withPrefix()
            .build();

        assertEquals("Main", message.asPlainText());
    }

    @Test
    void placeholderFactoryHandlesSupportedTypes() {
        final LocalDateTime dateTime = LocalDateTime.of(2024, 3, 4, 5, 6);
        final Component component = Component.text("Component");
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("template"));
        when(formatter.formatComponent(eq("template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Result"));

        TranslationService.create(KEY, player)
            .with("nullValue", null)
            .with("stringValue", "value")
            .with("numberValue", 7)
            .with("componentValue", component)
            .with("timeValue", dateTime)
            .build();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<Placeholder>> placeholderCaptor = ArgumentCaptor.forClass(List.class);
        verify(formatter).formatComponent(eq("template"), placeholderCaptor.capture(), eq(PLAYER_LOCALE));
        final List<Placeholder> placeholders = placeholderCaptor.getValue();

        assertEquals(5, placeholders.size());
        assertEquals(Placeholder.PlaceholderType.TEXT, placeholders.get(0).type());
        assertTrue(placeholders.get(0).asText().isEmpty());
        assertEquals(Placeholder.PlaceholderType.TEXT, placeholders.get(1).type());
        assertEquals("value", placeholders.get(1).asText());
        assertEquals(Placeholder.PlaceholderType.NUMBER, placeholders.get(2).type());
        assertEquals("7", placeholders.get(2).asText());
        assertEquals(Placeholder.PlaceholderType.RICH_TEXT, placeholders.get(3).type());
        assertEquals("Component", placeholders.get(3).asText());
        assertEquals(Placeholder.PlaceholderType.DATE_TIME, placeholders.get(4).type());
        assertEquals(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(dateTime), placeholders.get(4).asText());
    }

    @Test
    void placeholderOverloadRetainsProvidedInstance() {
        final Placeholder custom = Placeholder.of("custom", "value");
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("template"));
        when(formatter.formatComponent(eq("template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Result"));

        TranslationService.create(KEY, player)
            .with(custom)
            .build();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<Placeholder>> placeholderCaptor = ArgumentCaptor.forClass(List.class);
        verify(formatter).formatComponent(eq("template"), placeholderCaptor.capture(), eq(PLAYER_LOCALE));
        final List<Placeholder> placeholders = placeholderCaptor.getValue();

        assertEquals(1, placeholders.size());
        assertSame(custom, placeholders.get(0));
    }

    @Test
    void withAllMergesPlaceholderMap() {
        final Map<String, Object> placeholdersMap = new LinkedHashMap<>();
        placeholdersMap.put("second", "two");
        placeholdersMap.put("third", 3);
        final Placeholder first = Placeholder.of("first", "one");
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("template"));
        when(formatter.formatComponent(eq("template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Result"));

        TranslationService.create(KEY, player)
            .with(first)
            .withAll(placeholdersMap)
            .build();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<Placeholder>> placeholderCaptor = ArgumentCaptor.forClass(List.class);
        verify(formatter).formatComponent(eq("template"), placeholderCaptor.capture(), eq(PLAYER_LOCALE));
        final List<Placeholder> placeholders = placeholderCaptor.getValue();

        assertEquals(3, placeholders.size());
        assertSame(first, placeholders.get(0));
        assertEquals("two", placeholders.get(1).asText());
        assertEquals(Placeholder.PlaceholderType.NUMBER, placeholders.get(2).type());
        assertEquals("3", placeholders.get(2).asText());
    }

    @Test
    void buildAsyncDelegatesToBuild() {
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("template"));
        when(formatter.formatComponent(eq("template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Async"));

        final CompletableFuture<TranslatedMessage> future = TranslationService.create(KEY, player).buildAsync();
        final TranslatedMessage message = future.join();

        assertEquals("Async", message.asPlainText());
    }

    @Test
    void sendDispatchesToPlayer() {
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("template"));
        when(formatter.formatComponent(eq("template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Body"));

        TranslationService.create(KEY, player).send();

        final ArgumentCaptor<Component> componentCaptor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(componentCaptor.capture());
        assertEquals("Body", PlainTextComponentSerializer.plainText().serialize(componentCaptor.getValue()));
    }

    @Test
    void sendActionBarDispatchesToPlayer() {
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("template"));
        when(formatter.formatComponent(eq("template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Bar"));

        TranslationService.create(KEY, player).sendActionBar();

        final ArgumentCaptor<Component> componentCaptor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendActionBar(componentCaptor.capture());
        assertEquals("Bar", PlainTextComponentSerializer.plainText().serialize(componentCaptor.getValue()));
    }

    @Test
    void sendTitleDispatchesToPlayer() {
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("template"));
        when(formatter.formatComponent(eq("template"), anyList(), eq(PLAYER_LOCALE)))
            .thenReturn(Component.text("Title"));

        TranslationService.create(KEY, player).sendTitle();

        final ArgumentCaptor<Title> titleCaptor = ArgumentCaptor.forClass(Title.class);
        verify(player).showTitle(titleCaptor.capture());
        assertEquals("Title", PlainTextComponentSerializer.plainText().serialize(titleCaptor.getValue().title()));
    }

    @Test
    void buildReturnsFallbackMessageWhenFormatterFails() {
        when(repository.getTranslation(KEY, PLAYER_LOCALE)).thenReturn(Optional.of("template"));
        when(formatter.formatComponent(eq("template"), anyList(), eq(PLAYER_LOCALE)))
            .thenThrow(new RuntimeException(
                "fail",
                new MessageFormatter.FormattingException("fail", "template", List.of())
            ));

        final TranslatedMessage message = TranslationService.create(KEY, player).build();

        assertTrue(message.asPlainText().startsWith("[Error: RuntimeException]"));
        assertTrue(message.asPlainText().endsWith(KEY.key()));
    }

    private void resetConfiguration() throws Exception {
        final Field configurationField = TranslationService.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        configurationField.set(null, null);
    }
}
