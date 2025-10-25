package de.jexcellence.jextranslate.example;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginManagerMock;
import de.jexcellence.jextranslate.api.LocaleResolver;
import de.jexcellence.jextranslate.api.MessageFormatter;
import de.jexcellence.jextranslate.api.TranslationKey;
import de.jexcellence.jextranslate.api.TranslationRepository;
import de.jexcellence.jextranslate.api.TranslationService;
import de.jexcellence.jextranslate.impl.MiniMessageFormatter;
import de.jexcellence.jextranslate.impl.YamlTranslationRepository;
import de.jexcellence.jextranslate.util.DebugUtils;
import org.bukkit.command.Command;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ExamplePluginTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private PluginManagerMock pluginManager;

    @BeforeEach
    void setUp() {
        this.server = MockBukkit.mock();
        this.pluginManager = this.server.getPluginManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        resetTranslationServiceConfiguration();
    }

    @Test
    void onEnableInitializesTranslationService() throws Exception {
        final ExamplePlugin original = loadPlugin();
        final ExamplePlugin pluginSpy = spy(original);
        copyJavaPluginState(original, pluginSpy);

        final Path pluginConfig = this.tempDir.resolve("config.yml");
        Files.createDirectories(this.tempDir);
        Files.createFile(pluginConfig);

        setField(JavaPlugin.class, pluginSpy, "dataFolder", this.tempDir.toFile());
        setField(JavaPlugin.class, pluginSpy, "configFile", pluginConfig.toFile());
        setField(JavaPlugin.class, pluginSpy, "isEnabled", true);

        final ServerMock serverSpy = spy(this.server);
        final PluginManagerMock managerSpy = spy(this.pluginManager);
        doReturn(managerSpy).when(serverSpy).getPluginManager();
        setField(JavaPlugin.class, pluginSpy, "server", serverSpy);

        final AtomicReference<TranslationService.ServiceConfiguration> capturedConfig = new AtomicReference<>();

        try (MockedStatic<TranslationService> translationService = mockStatic(TranslationService.class)) {
            translationService.when(() -> TranslationService.configure(any(TranslationService.ServiceConfiguration.class)))
                .thenAnswer(invocation -> {
                    capturedConfig.set(invocation.getArgument(0));
                    return null;
                });

            pluginSpy.onEnable();

            verify(pluginSpy).saveDefaultConfig();
            verify(managerSpy).registerEvents(pluginSpy, pluginSpy);
            translationService.verify(() -> TranslationService.configure(any(TranslationService.ServiceConfiguration.class)));
        }

        final TranslationService.ServiceConfiguration configuration = capturedConfig.get();
        assertNotNull(configuration, "Translation service configuration should be supplied");
        assertTrue(configuration.repository() instanceof YamlTranslationRepository, "Repository should use YAML implementation");
        assertTrue(configuration.formatter() instanceof MiniMessageFormatter, "Formatter should be MiniMessage based");
        assertEquals(Locale.ENGLISH, configuration.repository().getDefaultLocale(), "Default locale should be English");
        assertEquals(Locale.ENGLISH, configuration.localeResolver().getDefaultLocale(), "Locale resolver should default to English");
        assertEquals(this.tempDir.resolve("translations"),
            getPrivatePath(configuration.repository(), "translationsDirectory"),
            "Translations should be stored in the plugin data directory"
        );
    }

    @Test
    void parseLocaleHandlesVariants() throws Exception {
        final ExamplePlugin plugin = allocateExamplePlugin();
        final Method parseLocale = ExamplePlugin.class.getDeclaredMethod("parseLocale", String.class);
        parseLocale.setAccessible(true);

        final Locale onePart = (Locale) parseLocale.invoke(plugin, "en");
        final Locale twoPart = (Locale) parseLocale.invoke(plugin, "en_US");
        final Locale threePart = (Locale) parseLocale.invoke(plugin, "en_US_POSIX");
        final Locale fallback = (Locale) parseLocale.invoke(plugin, "");

        assertEquals(new Locale("en"), onePart);
        assertEquals(new Locale("en", "US"), twoPart);
        assertEquals(new Locale("en", "US", "POSIX"), threePart);
        assertEquals(Locale.ENGLISH, fallback);
    }

    @Test
    void joinEventForReturningPlayerUsesPrefixedMessage() {
        final ExamplePlugin plugin = loadPlugin();
        final PlayerMock player = spy(new PlayerMock(this.server, "Veteran"));
        doReturn(true).when(player).hasPlayedBefore();

        final TranslationService returningChain = translationChain();

        try (MockedStatic<TranslationService> translationService = mockStatic(TranslationService.class)) {
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("welcome.returning")), eq(player)))
                .thenReturn(returningChain);

            final PlayerJoinEvent event = mock(PlayerJoinEvent.class);
            doReturn(player).when(event).getPlayer();

            plugin.onPlayerJoin(event);

            verify(returningChain).withPrefix();
            verify(returningChain).with("player", player.getName());
            verify(returningChain).with("online", this.server.getOnlinePlayers().size());
            verify(returningChain).send();
            translationService.verify(() -> TranslationService.create(eq(TranslationKey.of("welcome.returning")), eq(player)));
            translationService.verifyNoMoreInteractions();
        }
    }

    @Test
    void joinEventForFirstTimePlayerSendsTutorial() {
        final ExamplePlugin plugin = loadPlugin();
        final PlayerMock player = spy(new PlayerMock(this.server, "Newcomer"));
        doReturn(false).when(player).hasPlayedBefore();

        final TranslationService firstJoinChain = translationChain();
        final TranslationService tutorialChain = translationChain();

        try (MockedStatic<TranslationService> translationService = mockStatic(TranslationService.class)) {
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("welcome.first-join")), eq(player)))
                .thenReturn(firstJoinChain);
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("welcome.tutorial")), eq(player)))
                .thenReturn(tutorialChain);

            final PlayerJoinEvent event = mock(PlayerJoinEvent.class);
            doReturn(player).when(event).getPlayer();

            plugin.onPlayerJoin(event);

            verify(firstJoinChain).withPrefix();
            verify(firstJoinChain).with("player", player.getName());
            verify(firstJoinChain).send();
            verify(tutorialChain).sendTitle();
        }
    }

    @Test
    void coinsCommandUsesActionBarAndChatChains() {
        final ExamplePlugin plugin = loadPlugin();
        final PlayerMock player = new PlayerMock(this.server, "Banker");

        final TranslationService balanceChain = translationChain();
        final TranslationService infoChain = translationChain();

        final Command command = mock(Command.class);
        doReturn("coins").when(command).getName();

        try (MockedStatic<TranslationService> translationService = mockStatic(TranslationService.class)) {
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("coins.balance")), eq(player)))
                .thenReturn(balanceChain);
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("coins.info")), eq(player)))
                .thenReturn(infoChain);

            plugin.onCommand(player, command, "coins", new String[0]);

            verify(balanceChain).withPrefix();
            verify(balanceChain).with("amount", 1000);
            verify(balanceChain).send();

            verify(infoChain).with("player", player.getName());
            verify(infoChain).with("coins", 1000);
            verify(infoChain).sendActionBar();
        }
    }

    @Test
    void langCommandReportsCurrentLocale() {
        final ExamplePlugin plugin = loadPlugin();
        final PlayerMock player = new PlayerMock(this.server, "Polyglot");

        final TranslationService currentChain = translationChain();
        final TranslationService availableChain = translationChain();

        final TranslationRepository repository = mock(TranslationRepository.class);
        final LocaleResolver resolver = mock(LocaleResolver.class);
        final MessageFormatter formatter = mock(MessageFormatter.class);

        doReturn(Optional.of(Locale.GERMAN)).when(resolver).resolveLocale(player);
        doReturn(Locale.ENGLISH).when(resolver).getDefaultLocale();
        doReturn(Set.of(Locale.ENGLISH, Locale.GERMAN)).when(repository).getAvailableLocales();

        final TranslationService.ServiceConfiguration configuration = new TranslationService.ServiceConfiguration(repository, formatter, resolver);

        final Command command = mock(Command.class);
        doReturn("lang").when(command).getName();

        try (MockedStatic<TranslationService> translationService = mockStatic(TranslationService.class)) {
            translationService.when(TranslationService::getConfiguration).thenReturn(configuration);
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("lang.current")), eq(player)))
                .thenReturn(currentChain);
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("lang.available")), eq(player)))
                .thenReturn(availableChain);

            plugin.onCommand(player, command, "lang", new String[0]);

            verify(currentChain).withPrefix();
            verify(currentChain).with("locale", "de");
            verify(currentChain).send();

            verify(availableChain).with("locales", repository.getAvailableLocales().toString());
            verify(availableChain).send();
        }
    }

    @Test
    void langCommandChangesLocaleClearsCache() {
        final ExamplePlugin plugin = loadPlugin();
        final PlayerMock player = new PlayerMock(this.server, "Translator");

        final TranslationService changedChain = translationChain();

        final TranslationRepository repository = mock(TranslationRepository.class);
        final LocaleResolver resolver = mock(LocaleResolver.class);
        final MessageFormatter formatter = mock(MessageFormatter.class);

        doReturn(true).when(resolver).setPlayerLocale(eq(player), any(Locale.class));
        doReturn(Locale.ENGLISH).when(resolver).getDefaultLocale();

        final TranslationService.ServiceConfiguration configuration = new TranslationService.ServiceConfiguration(repository, formatter, resolver);

        final Command command = mock(Command.class);
        doReturn("lang").when(command).getName();

        try (MockedStatic<TranslationService> translationService = mockStatic(TranslationService.class)) {
            translationService.when(TranslationService::getConfiguration).thenReturn(configuration);
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("lang.changed")), eq(player)))
                .thenReturn(changedChain);

            plugin.onCommand(player, command, "lang", new String[]{"fr_FR"});

            verify(resolver).setPlayerLocale(player, new Locale("fr", "FR"));
            translationService.verify(() -> TranslationService.clearLocaleCache(player));
            verify(changedChain).withPrefix();
            verify(changedChain).with("locale", new Locale("fr", "FR").toString());
            verify(changedChain).send();
        }
    }

    @Test
    void langCommandFailureUsesErrorMessage() {
        final ExamplePlugin plugin = loadPlugin();
        final PlayerMock player = new PlayerMock(this.server, "Skeptic");

        final TranslationService errorChain = translationChain();

        final TranslationRepository repository = mock(TranslationRepository.class);
        final LocaleResolver resolver = mock(LocaleResolver.class);
        final MessageFormatter formatter = mock(MessageFormatter.class);

        doReturn(false).when(resolver).setPlayerLocale(eq(player), any(Locale.class));
        doReturn(Locale.ENGLISH).when(resolver).getDefaultLocale();

        final TranslationService.ServiceConfiguration configuration = new TranslationService.ServiceConfiguration(repository, formatter, resolver);

        final Command command = mock(Command.class);
        doReturn("lang").when(command).getName();

        try (MockedStatic<TranslationService> translationService = mockStatic(TranslationService.class)) {
            translationService.when(TranslationService::getConfiguration).thenReturn(configuration);
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("lang.error")), eq(player)))
                .thenReturn(errorChain);

            plugin.onCommand(player, command, "lang", new String[]{"jp"});

            verify(resolver).setPlayerLocale(player, new Locale("jp"));
            verify(errorChain).withPrefix();
            verify(errorChain).send();
        }
    }

    @Test
    void translateDebugCommandUsesDebugUtils() {
        final ExamplePlugin plugin = loadPlugin();
        final PlayerMock player = new PlayerMock(this.server, "Debugger");

        final Command command = mock(Command.class);
        doReturn("translatedebug").when(command).getName();

        try (MockedStatic<DebugUtils> debugUtils = mockStatic(DebugUtils.class)) {
            debugUtils.when(() -> DebugUtils.debugTranslation(eq("greeting"), eq(player)))
                .thenReturn("Debug Info");

            plugin.onCommand(player, command, "translatedebug", new String[]{"greeting"});

            assertEquals("Debug Info", player.nextMessage());
            debugUtils.verify(() -> DebugUtils.debugTranslation("greeting", player));
        }
    }

    @Test
    void translateReloadCommandClearsCachesAndReportsTotals() {
        final ExamplePlugin plugin = loadPlugin();
        final PlayerMock player = new PlayerMock(this.server, "Admin");

        final TranslationService startingChain = translationChain();
        final TranslationService completeChain = translationChain();

        final TranslationRepository repository = mock(TranslationRepository.class);
        final LocaleResolver resolver = mock(LocaleResolver.class);
        final MessageFormatter formatter = mock(MessageFormatter.class);

        doReturn(Locale.ENGLISH).when(resolver).getDefaultLocale();
        doReturn(Set.of(Locale.ENGLISH, Locale.GERMAN)).when(repository).getAvailableLocales();
        doReturn(Set.of(TranslationKey.of("alpha"), TranslationKey.of("beta"))).when(repository).getAllAvailableKeys();
        doReturn(CompletableFuture.completedFuture(null)).when(repository).reload();

        final TranslationService.ServiceConfiguration configuration = new TranslationService.ServiceConfiguration(repository, formatter, resolver);

        final Command command = mock(Command.class);
        doReturn("translatereload").when(command).getName();

        try (MockedStatic<TranslationService> translationService = mockStatic(TranslationService.class)) {
            translationService.when(TranslationService::getConfiguration).thenReturn(configuration);
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("reload.starting")), eq(player)))
                .thenReturn(startingChain);
            translationService.when(() -> TranslationService.create(eq(TranslationKey.of("reload.complete")), eq(player)))
                .thenReturn(completeChain);

            plugin.onCommand(player, command, "translatereload", new String[0]);

            verify(startingChain).withPrefix();
            verify(startingChain).send();
            verify(repository).reload();
            translationService.verify(TranslationService::clearLocaleCache);
            verify(completeChain).withPrefix();
            verify(completeChain).with("locales", repository.getAvailableLocales().size());
            verify(completeChain).with("keys", repository.getAllAvailableKeys().size());
            verify(completeChain).send();
        }
    }

    private ExamplePlugin loadPlugin() {
        final PluginDescriptionFile description = new PluginDescriptionFile("ExamplePlugin", "1.0.0", ExamplePlugin.class.getName());
        return (ExamplePlugin) this.pluginManager.loadPlugin(ExamplePlugin.class, description, new Object[0]);
    }

    private static void copyJavaPluginState(@NotNull final JavaPlugin source, @NotNull final JavaPlugin target) throws IllegalAccessException {
        Class<?> type = JavaPlugin.class;
        while (type != null && JavaPlugin.class.isAssignableFrom(type)) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                field.set(target, field.get(source));
            }
            type = type.getSuperclass();
        }
    }

    private static void setField(@NotNull final Class<?> owningClass, @NotNull final Object target, @NotNull final String fieldName, final Object value) throws NoSuchFieldException, IllegalAccessException {
        final Field field = owningClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static TranslationService translationChain() {
        final TranslationService chain = mock(TranslationService.class);
        doReturn(chain).when(chain).withPrefix();
        doReturn(chain).when(chain).with(anyString(), any());
        return chain;
    }

    private static Path getPrivatePath(final Object target, final String fieldName) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((Path) field.get(target));
    }

    private static ExamplePlugin allocateExamplePlugin() throws Exception {
        final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        final Unsafe unsafe = (Unsafe) unsafeField.get(null);
        return (ExamplePlugin) unsafe.allocateInstance(ExamplePlugin.class);
    }

    private static void resetTranslationServiceConfiguration() {
        final TranslationRepository repository = mock(TranslationRepository.class);
        doReturn(Locale.ENGLISH).when(repository).getDefaultLocale();
        doReturn(Set.of()).when(repository).getAvailableLocales();

        final LocaleResolver resolver = mock(LocaleResolver.class);
        doReturn(Optional.of(Locale.ENGLISH)).when(resolver).resolveLocale(any());
        doReturn(Locale.ENGLISH).when(resolver).getDefaultLocale();

        final MessageFormatter formatter = mock(MessageFormatter.class);

        TranslationService.configure(new TranslationService.ServiceConfiguration(repository, formatter, resolver));
        TranslationService.clearLocaleCache();
    }
}
