package de.jexcellence.jextranslate.command;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.jexcellence.jextranslate.api.LocaleResolver;
import de.jexcellence.jextranslate.api.MissingKeyTracker;
import de.jexcellence.jextranslate.api.TranslationKey;
import de.jexcellence.jextranslate.api.TranslationRepository;
import de.jexcellence.jextranslate.api.TranslationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class TranslationCommandTest {

    private static final String PERMISSION_BASE = "jextranslate.admin";
    private static final String PERMISSION_MISSING = PERMISSION_BASE + ".missing";
    private static final String PERMISSION_ADD = PERMISSION_BASE + ".add";
    private static final String PERMISSION_STATS = PERMISSION_BASE + ".stats";
    private static final String PERMISSION_RELOAD = PERMISSION_BASE + ".reload";
    private static final String PERMISSION_BACKUP = PERMISSION_BASE + ".backup";

    private ServerMock server;
    private JavaPlugin plugin;
    private MissingKeyTracker missingKeyTracker;
    private TranslationRepository repository;
    private LocaleResolver localeResolver;
    private TranslationCommand translationCommand;
    private MockedStatic<Bukkit> bukkitStatic;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() {
        this.server = MockBukkit.mock();
        this.plugin = MockBukkit.load(TestTranslationPlugin.class);

        this.missingKeyTracker = mock(MissingKeyTracker.class);
        this.repository = mock(TranslationRepository.class);
        this.localeResolver = mock(LocaleResolver.class);

        when(this.localeResolver.resolveLocale(any(Player.class))).thenReturn(Optional.of(Locale.ENGLISH));
        when(this.localeResolver.getDefaultLocale()).thenReturn(Locale.ENGLISH);

        this.scheduler = mock(BukkitScheduler.class);
        this.bukkitStatic = mockStaticScheduler(this.scheduler);

        this.translationCommand = new TranslationCommand(this.plugin, this.missingKeyTracker, this.repository, this.localeResolver);
    }

    @AfterEach
    void tearDown() {
        this.bukkitStatic.close();
        MockBukkit.unmock();
    }

    @Test
    void missingSubCommandRequiresPermission() {
        final PlayerMock player = this.server.addPlayer("Missingless");
        player.addAttachment(this.plugin, PERMISSION_MISSING, false);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"missing"});

        final Component message = player.nextComponentMessage();
        assertEquals("You don't have permission to view missing keys.", plain(message));
    }

    @Test
    void missingConsoleFallbackListsKeys() {
        final ConsoleCommandSenderMock console = this.server.getConsoleSender();
        final Set<TranslationKey> keys = new LinkedHashSet<>();
        keys.add(TranslationKey.of("alpha"));
        keys.add(TranslationKey.of("beta"));

        when(this.missingKeyTracker.getMissingKeys(new Locale("de", "DE"))).thenReturn(keys);

        this.translationCommand.onCommand(console, dummyCommand(), "translate", new String[]{"missing", "de_DE"});

        assertEquals("Missing keys for locale de_DE (2 total):", nextMessage(console));
        assertEquals("  - alpha", nextMessage(console));
        assertEquals("  - beta", nextMessage(console));
    }

    @Test
    void missingConsoleFallbackHandlesEmptyResult() {
        final ConsoleCommandSenderMock console = this.server.getConsoleSender();
        when(this.missingKeyTracker.getMissingKeys(Locale.ENGLISH)).thenReturn(Collections.emptySet());

        this.translationCommand.onCommand(console, dummyCommand(), "translate", new String[]{"missing"});

        assertEquals("No missing keys found for locale: en", nextMessage(console));
    }

    @Test
    void missingPlayerViewOpensGuiAndNavigatesPages() {
        final PlayerMock player = grantPermission(this.server.addPlayer("Navigator"), PERMISSION_MISSING);
        final Set<TranslationKey> keys = IntStream.range(0, 50)
            .mapToObj(index -> TranslationKey.of("key." + index))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        when(this.missingKeyTracker.getMissingKeys(Locale.ENGLISH)).thenAnswer(invocation -> new LinkedHashSet<>(keys));

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"missing"});

        InventoryView view = player.getOpenInventory();
        assertNotNull(view, "The GUI should open for players with permission");

        Map<UUID, ?> guiSessions = getGuiSessions();
        assertTrue(guiSessions.containsKey(player.getUniqueId()), "GUI session should be tracked");
        final Object initialSession = guiSessions.get(player.getUniqueId());
        assertNotNull(initialSession);
        @SuppressWarnings("unchecked")
        final List<TranslationKey> initialKeys = (List<TranslationKey>) invokeRecordAccessor(initialSession, "keys", List.class);
        assertNotNull(initialKeys);
        assertEquals(50, initialKeys.size());

        final ItemStack firstPageItem = view.getTopInventory().getItem(0);
        assertNotNull(firstPageItem);
        assertEquals("key.0", plain(firstPageItem.getItemMeta().displayName()));

        final ItemStack nextArrow = view.getTopInventory().getItem(53);
        assertNotNull(nextArrow, "Expected navigation arrow on the first page");

        invokeHandleGuiClick(player, initialSession, 53, nextArrow);

        view = player.getOpenInventory();
        assertNotNull(view);
        guiSessions = getGuiSessions();
        final Object nextSession = guiSessions.get(player.getUniqueId());
        assertNotNull(nextSession);
        @SuppressWarnings("unchecked")
        final List<TranslationKey> nextKeys = (List<TranslationKey>) invokeRecordAccessor(nextSession, "keys", List.class);
        final int nextPage = invokeRecordAccessor(nextSession, "page", Integer.class);
        final ItemStack secondPageItem = view.getTopInventory().getItem(0);
        assertNotNull(secondPageItem);
        assertEquals(nextKeys.get(nextPage * 45).key(), plain(secondPageItem.getItemMeta().displayName()));

        final ItemStack previousArrow = view.getTopInventory().getItem(45);
        assertNotNull(previousArrow, "Expected back arrow on the second page");

        invokeHandleGuiClick(player, nextSession, 45, previousArrow);

        view = player.getOpenInventory();
        assertNotNull(view);
        guiSessions = getGuiSessions();
        final Object finalSession = guiSessions.get(player.getUniqueId());
        assertNotNull(finalSession);
        final ItemStack firstReturnItem = view.getTopInventory().getItem(0);
        assertNotNull(firstReturnItem);
        assertEquals(initialKeys.get(0).key(), plain(firstReturnItem.getItemMeta().displayName()));
    }

    @Test
    void clickingKeyStartsChatSessionAndClosesGui() {
        final PlayerMock player = grantPermission(this.server.addPlayer("KeyCollector"), PERMISSION_MISSING);
        final TranslationKey key = TranslationKey.of("example.key");
        when(this.missingKeyTracker.getMissingKeys(Locale.ENGLISH)).thenReturn(new LinkedHashSet<>(Set.of(key)));

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"missing"});

        final InventoryView view = player.getOpenInventory();
        assertNotNull(view);

        final Map<UUID, ?> guiSessions = getGuiSessions();
        final Object guiSession = guiSessions.get(player.getUniqueId());
        assertNotNull(guiSession);

        final ItemStack keyItem = view.getTopInventory().getItem(0);
        assertNotNull(keyItem, "Missing key item should populate the GUI");

        invokeHandleGuiClick(player, guiSession, 0, keyItem);

        final Map<UUID, ?> chatSessions = getChatSessions();
        final Object session = chatSessions.get(player.getUniqueId());
        assertNotNull(session, "Chat session should be registered");

        assertEquals("example.key", invokeRecordAccessor(session, "key", TranslationKey.class).key());
        assertEquals(Locale.ENGLISH, invokeRecordAccessor(session, "locale", Locale.class));
        verify(this.localeResolver, times(2)).resolveLocale(player);

        assertTrue(getGuiSessions().containsKey(player.getUniqueId()));
    }

    @Test
    void closingInventoryClearsGuiSession() {
        final PlayerMock player = grantPermission(this.server.addPlayer("Closer"), PERMISSION_MISSING);
        final TranslationKey key = TranslationKey.of("example.key");
        when(this.missingKeyTracker.getMissingKeys(Locale.ENGLISH)).thenReturn(new LinkedHashSet<>(Set.of(key)));

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"missing"});

        final InventoryView view = player.getOpenInventory();
        assertNotNull(view);

        final Map<UUID, ?> guiSessions = getGuiSessions();
        assertTrue(guiSessions.containsKey(player.getUniqueId()));

        player.closeInventory();

        final InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        final InventoryView mockView = mock(InventoryView.class);
        when(mockView.getTitle()).thenReturn("Missing Keys - en (1/1)");
        when(event.getView()).thenReturn(mockView);

        this.translationCommand.onInventoryClose(event);

        assertFalse(getGuiSessions().containsKey(player.getUniqueId()), "GUI session should be removed after closing inventory");
    }

    @Test
    void addSubCommandRequiresPermission() {
        final PlayerMock player = this.server.addPlayer("Adder");
        player.addAttachment(this.plugin, PERMISSION_ADD, false);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"add", "quest.title"});

        assertEquals("You don't have permission to add translations.", nextMessage(player));
    }

    @Test
    void addSubCommandValidatesArguments() {
        final PlayerMock player = grantPermission(this.server.addPlayer("Usage"), PERMISSION_ADD);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"add"});

        assertEquals("Usage: /translate add <key>", nextMessage(player));
    }

    @Test
    void addSubCommandStartsChatSession() {
        final PlayerMock player = grantPermission(this.server.addPlayer("Starter"), PERMISSION_ADD);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"add", "quest.title"});

        final String addWizard = nextMessage(player);
        assertNotNull(addWizard);
        assertTrue(addWizard.contains("Translation Wizard"));

        final Object session = getChatSessions().get(player.getUniqueId());
        assertNotNull(session);
        assertEquals("quest.title", invokeRecordAccessor(session, "key", TranslationKey.class).key());
    }

    @Test
    void chatSessionCancellationNotifiesPlayer() {
        final PlayerMock player = grantPermission(this.server.addPlayer("Canceller"), PERMISSION_ADD);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"add", "quest.title"});
        nextMessage(player);

        final AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "cancel", new HashSet<>());

        this.translationCommand.onPlayerChat(event);

        assertTrue(event.isCancelled());
        assertEquals("Translation wizard cancelled.", nextMessage(player));
        assertFalse(getChatSessions().containsKey(player.getUniqueId()));
    }

    @Test
    void chatSessionCompletionMarksResolvedAndResponds() {
        final PlayerMock player = grantPermission(this.server.addPlayer("Completer"), PERMISSION_ADD);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"add", "quest.title"});
        nextMessage(player);

        final AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "Translated Value", new HashSet<>());

        this.translationCommand.onPlayerChat(event);

        verify(this.missingKeyTracker).markResolved(TranslationKey.of("quest.title"), Locale.ENGLISH);
        assertTrue(event.isCancelled());
        final String success = nextMessage(player);
        assertNotNull(success);
        assertTrue(success.contains("Translation added successfully!"));
        assertTrue(success.contains("Translated Value"));
        assertFalse(getChatSessions().containsKey(player.getUniqueId()));
    }

    @Test
    void chatSessionHandlesFailuresGracefully() {
        final PlayerMock player = grantPermission(this.server.addPlayer("Failer"), PERMISSION_ADD);

        doThrow(new IllegalStateException("boom"))
            .when(this.missingKeyTracker)
            .markResolved(TranslationKey.of("quest.title"), Locale.ENGLISH);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"add", "quest.title"});
        player.nextComponentMessage();

        final AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "Value", new HashSet<>());
        this.translationCommand.onPlayerChat(event);

        final String message = nextMessage(player);
        assertNotNull(message);
        assertTrue(message.contains("Failed to save translation"));
    }

    @Test
    void statsSubCommandRequiresPermission() {
        final PlayerMock player = this.server.addPlayer("Statistician");
        player.addAttachment(this.plugin, PERMISSION_STATS, false);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"stats"});

        assertEquals("You don't have permission to view statistics.", nextMessage(player));
    }

    @Test
    void statsSubCommandOutputsTrackerStatistics() {
        final PlayerMock player = grantPermission(this.server.addPlayer("Stats"), PERMISSION_STATS);

        final MissingKeyTracker.Statistics statistics = mock(MissingKeyTracker.Statistics.class);
        when(statistics.getTotalTrackingEvents()).thenReturn(5L);
        when(statistics.getUniqueMissingCount()).thenReturn(3);
        when(statistics.getAffectedLocaleCount()).thenReturn(2);
        when(statistics.getMostFrequentMissing()).thenReturn(TranslationKey.of("quest.title"));
        when(statistics.getLocaleWithMostMissing()).thenReturn(Locale.GERMANY);

        when(this.missingKeyTracker.getStatistics()).thenReturn(statistics);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"stats"});

        final String output = nextMessage(player);
        assertTrue(output.contains("=== Translation Statistics ==="));
        assertTrue(output.contains("5"));
        assertTrue(output.contains("quest.title"));
        assertTrue(output.contains("de_DE"));
    }

    @Test
    void reloadSubCommandTriggersRepositoryReload() {
        final PlayerMock player = grantPermission(this.server.addPlayer("Reloader"), PERMISSION_RELOAD);

        final CompletableFuture<Void> reloadFuture = new CompletableFuture<>();
        when(this.repository.reload()).thenReturn(reloadFuture);

        try (MockedStatic<TranslationService> serviceStatic = org.mockito.Mockito.mockStatic(TranslationService.class)) {
            this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"reload"});

            reloadFuture.complete(null);

            serviceStatic.verify(TranslationService::clearLocaleCache);
        }

        final List<String> messages = drainMessages(player);
        assertTrue(messages.get(0).contains("Reloading translations"));
        assertTrue(messages.get(1).contains("Translations reloaded"));
    }

    @Test
    void reloadSubCommandReportsFailures() {
        final PlayerMock player = grantPermission(this.server.addPlayer("ReloadFail"), PERMISSION_RELOAD);

        final CompletableFuture<Void> reloadFuture = new CompletableFuture<>();
        when(this.repository.reload()).thenReturn(reloadFuture);

        try (MockedStatic<TranslationService> serviceStatic = org.mockito.Mockito.mockStatic(TranslationService.class)) {
            this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"reload"});

            reloadFuture.completeExceptionally(new IllegalStateException("nope"));

            serviceStatic.verifyNoInteractions();
        }

        final List<String> messages = drainMessages(player);
        assertTrue(messages.get(1).contains("Failed to reload translations"));
    }

    @Test
    void backupSubCommandRunsAsynchronousTask() {
        final PlayerMock player = grantPermission(this.server.addPlayer("Backer"), PERMISSION_BACKUP);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"backup", "es_ES"});

        verify(this.scheduler).runTaskAsynchronously(eq(this.plugin), any(Runnable.class));

        final List<String> messages = drainMessages(player);
        assertTrue(messages.get(0).contains("Creating backup for locale: es_ES"));
        assertTrue(messages.get(1).contains("Backup created successfully"));
    }

    @Test
    void backupSubCommandRequiresPermission() {
        final PlayerMock player = this.server.addPlayer("NoBackup");
        player.addAttachment(this.plugin, PERMISSION_BACKUP, false);

        this.translationCommand.onCommand(player, dummyCommand(), "translate", new String[]{"backup"});

        assertEquals("You don't have permission to create backups.", nextMessage(player));
        verifyNoInteractions(this.scheduler);
    }

    @Test
    void infoSubCommandOutputsMetadata() {
        final ConsoleCommandSenderMock console = this.server.getConsoleSender();

        final TranslationRepository.RepositoryMetadata metadata = mock(TranslationRepository.RepositoryMetadata.class);
        when(metadata.getType()).thenReturn("yaml");
        when(metadata.getSource()).thenReturn("translations");
        when(metadata.getTotalTranslations()).thenReturn(42);
        when(this.repository.getMetadata()).thenReturn(metadata);
        when(this.repository.getAvailableLocales()).thenReturn(Set.of(Locale.ENGLISH, Locale.GERMAN));

        this.translationCommand.onCommand(console, dummyCommand(), "translate", new String[]{"info"});

        final String output = nextMessage(console);
        assertTrue(output.contains("Translation System Info"));
        assertTrue(output.contains("yaml"));
        assertTrue(output.contains("42"));
    }

    @Test
    void onTabCompleteSuggestsSubCommandsAndLocales() {
        final PlayerMock player = this.server.addPlayer("Tabber");
        when(this.missingKeyTracker.getLocalesWithMissingKeys()).thenReturn(Set.of(Locale.ENGLISH, Locale.GERMANY));

        final List<String> rootSuggestions = this.translationCommand.onTabComplete(player, dummyCommand(), "translate", new String[]{""});
        assertTrue(rootSuggestions.contains("missing"));
        assertTrue(rootSuggestions.contains("backup"));

        final List<String> filtered = this.translationCommand.onTabComplete(player, dummyCommand(), "translate", new String[]{"m"});
        assertEquals(List.of("missing"), filtered);

        final List<String> locales = this.translationCommand.onTabComplete(player, dummyCommand(), "translate", new String[]{"missing", ""});
        assertTrue(locales.contains("en"));
        assertTrue(locales.contains("de_DE"));
    }

    @Test
    void parseLocaleSupportsLanguageAndCountryVariants() {
        assertEquals(new Locale("pt", "BR"), invokeParseLocale("pt-BR"));
        assertEquals(new Locale("fr"), invokeParseLocale("fr"));
        assertEquals(Locale.ENGLISH, invokeParseLocale(null));
    }

    private static Command dummyCommand() {
        return mock(Command.class);
    }

    private PlayerMock grantPermission(@NotNull final PlayerMock player, @NotNull final String permission) {
        player.addAttachment(this.plugin, permission, true);
        return player;
    }

    private static MockedStatic<Bukkit> mockStaticScheduler(@NotNull final BukkitScheduler scheduler) {
        final MockedStatic<Bukkit> mocked = org.mockito.Mockito.mockStatic(Bukkit.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
        final AtomicInteger taskIds = new AtomicInteger();

        final AnsweringTaskFactory factory = new AnsweringTaskFactory(taskIds);

        lenient().when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(factory);
        lenient().when(scheduler.runTaskAsynchronously(any(Plugin.class), any(Runnable.class))).thenAnswer(factory);

        mocked.when(Bukkit::getScheduler).thenReturn(scheduler);
        return mocked;
    }

    private static String plain(@NotNull final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static String nextMessage(@NotNull final PlayerMock player) {
        try {
            final Component component = player.nextComponentMessage();
            if (component != null) {
                return plain(component);
            }
        } catch (final NoSuchElementException ignored) {
            return null;
        }
        try {
            return player.nextMessage();
        } catch (final NoSuchElementException ignored) {
            return null;
        }
    }

    private static String nextMessage(@NotNull final ConsoleCommandSenderMock console) {
        try {
            final Component component = console.nextComponentMessage();
            if (component != null) {
                return plain(component);
            }
        } catch (final NoSuchElementException ignored) {
            return null;
        }
        try {
            return console.nextMessage();
        } catch (final NoSuchElementException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, ?> getGuiSessions() {
        return (Map<UUID, ?>) readField("activeGuiSessions");
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, ?> getChatSessions() {
        return (Map<UUID, ?>) readField("activeChatSessions");
    }

    private Object readField(@NotNull final String name) {
        try {
            final Field field = TranslationCommand.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(this.translationCommand);
        } catch (final IllegalAccessException | NoSuchFieldException exception) {
            fail(exception);
            return null;
        }
    }

    private static <T> T invokeRecordAccessor(@NotNull final Object record, @NotNull final String accessor, @NotNull final Class<T> type) {
        try {
            final Method method = record.getClass().getDeclaredMethod(accessor);
            method.setAccessible(true);
            return type.cast(method.invoke(record));
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            fail(exception);
            return null;
        }
    }

    private void invokeHandleGuiClick(@NotNull final Player player, @NotNull final Object session, final int slot, @NotNull final ItemStack item) {
        try {
            final Method method = TranslationCommand.class.getDeclaredMethod(
                "handleGuiClick",
                Player.class,
                session.getClass(),
                int.class,
                ItemStack.class
            );
            method.setAccessible(true);
            method.invoke(this.translationCommand, player, session, slot, item);
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            fail(exception);
        }
    }

    private Locale invokeParseLocale(final String value) {
        try {
            final Method method = TranslationCommand.class.getDeclaredMethod("parseLocale", String.class);
            method.setAccessible(true);
            return (Locale) method.invoke(this.translationCommand, value);
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            fail(exception);
            return Locale.ENGLISH;
        }
    }

    private static List<String> drainMessages(@NotNull final PlayerMock player) {
        final List<String> messages = new ArrayList<>();
        while (true) {
            final String message = nextMessage(player);
            if (message == null) {
                break;
            }
            messages.add(message);
        }
        return messages;
    }

    private static class AnsweringTaskFactory implements org.mockito.stubbing.Answer<BukkitTask> {

        private final AtomicInteger counter;

        AnsweringTaskFactory(@NotNull final AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public BukkitTask answer(final org.mockito.invocation.InvocationOnMock invocation) {
            final Runnable runnable = invocation.getArgument(1);
            runnable.run();
            final Plugin owner = invocation.getArgument(0);
            return new ImmediateTask(owner, this.counter.incrementAndGet());
        }
    }

    private static final class ImmediateTask implements BukkitTask {

        private final Plugin owner;
        private final int id;

        private ImmediateTask(@NotNull final Plugin owner, final int id) {
            this.owner = owner;
            this.id = id;
        }

        @Override
        public int getTaskId() {
            return this.id;
        }

        @Override
        public Plugin getOwner() {
            return this.owner;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void cancel() {
            // no-op for synchronous execution
        }
    }

}
