package de.jexcellence.jextranslate.command;

import de.jexcellence.jextranslate.api.LocaleResolver;
import de.jexcellence.jextranslate.api.MessageFormatter;
import de.jexcellence.jextranslate.api.MissingKeyTracker;
import de.jexcellence.jextranslate.api.TranslationKey;
import de.jexcellence.jextranslate.api.TranslationRepository;
import de.jexcellence.jextranslate.api.TranslationService;
import de.jexcellence.jextranslate.util.TranslationBackupService;
import de.jexcellence.jextranslate.util.TranslationLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Administrative command that exposes translation management functionality, including listing missing keys, adding new
 * translations, viewing statistics, and reloading the configured {@link TranslationRepository}. Integrates tightly with
 * {@link TranslationService}, {@link MessageFormatter}, and {@link LocaleResolver} to ensure changes propagate through
 * the entire translation pipeline.
 *
 * <p>Designed for Paper/Spigot environments and demonstrates MiniMessage placeholder usage consistent with the module's
 * documentation.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.3
 */
public class TranslationCommand implements CommandExecutor, TabCompleter, Listener {

    private static final Logger LOGGER = TranslationLogger.getLogger(TranslationCommand.class);
    private static final String YAML_EXTENSION = ".yml";
    private static final String PERMISSION_BASE = "jextranslate.admin";
    private static final String PERMISSION_MISSING = PERMISSION_BASE + ".missing";
    private static final String PERMISSION_ADD = PERMISSION_BASE + ".add";
    private static final String PERMISSION_STATS = PERMISSION_BASE + ".stats";
    private static final String PERMISSION_RELOAD = PERMISSION_BASE + ".reload";
    private static final String PERMISSION_BACKUP = PERMISSION_BASE + ".backup";
    private static final int ITEMS_PER_PAGE = 45;
    private static final String GUI_TITLE_PREFIX = "Missing Keys - ";

    private final JavaPlugin plugin;
    private final MissingKeyTracker missingKeyTracker;
    private final TranslationRepository repository;
    private final LocaleResolver localeResolver;
    private final Map<UUID, ChatSession> activeChatSessions = new ConcurrentHashMap<>();
    private final Map<UUID, GuiSession> activeGuiSessions = new ConcurrentHashMap<>();

    /**
     * Creates a new translation command using the supplied service collaborators. Event listeners are automatically
     * registered for inventory and chat handling.
     *
     * @param plugin            the owning plugin instance
     * @param missingKeyTracker tracker used to populate missing key views
     * @param repository        repository providing translations and metadata
     * @param localeResolver    resolver used for player locale detection and overrides
     */
    public TranslationCommand(
        @NotNull final JavaPlugin plugin,
        @NotNull final MissingKeyTracker missingKeyTracker,
        @NotNull final TranslationRepository repository,
        @NotNull final LocaleResolver localeResolver
    ) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.missingKeyTracker = Objects.requireNonNull(missingKeyTracker, "Missing key tracker cannot be null");
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.localeResolver = Objects.requireNonNull(localeResolver, "Locale resolver cannot be null");

        Bukkit.getPluginManager().registerEvents(this, plugin);
        LOGGER.info(() -> TranslationLogger.message(
                "TranslationCommand initialized",
                Map.of("plugin", plugin.getName())
        ));
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String label, @NotNull final String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        final String subCommand = args[0].toLowerCase();
        return switch (subCommand) {
            case "missing" -> handleMissing(sender, args);
            case "add" -> handleAdd(sender, args);
            case "stats" -> handleStats(sender);
            case "reload" -> handleReload(sender);
            case "backup" -> handleBackup(sender, args);
            case "info" -> handleInfo(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    /**
     * Handles the {@code /translate missing} sub-command, opening a GUI or printing to console depending on sender type.
     *
     * @param sender command executor
     * @param args   command arguments
     * @return {@code true} once the command is processed
     */
    private boolean handleMissing(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!sender.hasPermission(PERMISSION_MISSING)) {
            sender.sendMessage(Component.text("You don't have permission to view missing keys.").color(NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            handleMissingConsole(sender, args);
            return true;
        }

        final Locale targetLocale = determineTargetLocale(player, args, 1);
        openMissingKeysGui(player, targetLocale, 0);
        return true;
    }

    private void handleMissingConsole(@NotNull final CommandSender sender, @NotNull final String[] args) {
        final Locale targetLocale = args.length > 1 ? parseLocale(args[1]) : Locale.ENGLISH;
        final Set<TranslationKey> missingKeys = missingKeyTracker.getMissingKeys(targetLocale);

        if (missingKeys.isEmpty()) {
            sender.sendMessage("No missing keys found for locale: " + targetLocale);
            return;
        }

        sender.sendMessage("Missing keys for locale " + targetLocale + " (" + missingKeys.size() + " total):");
        for (final TranslationKey key : missingKeys) {
            sender.sendMessage("  - " + key.key());
        }
    }

    /**
     * Handles the {@code /translate add} sub-command, launching a chat-based translation wizard.
     *
     * @param sender command executor
     * @param args   command arguments
     * @return {@code true} once the command is processed
     */
    private boolean handleAdd(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!sender.hasPermission(PERMISSION_ADD)) {
            sender.sendMessage(Component.text("You don't have permission to add translations.").color(NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /translate add <key>").color(NamedTextColor.RED));
            return true;
        }

        final String keyString = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        final TranslationKey key = TranslationKey.of(keyString);
        startChatSession(player, key);
        return true;
    }

    /**
     * Handles the {@code /translate stats} sub-command, printing tracker metrics using Adventure components.
     *
     * @param sender command executor
     * @return {@code true} once the command is processed
     */
    private boolean handleStats(@NotNull final CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_STATS)) {
            sender.sendMessage(Component.text("You don't have permission to view statistics.").color(NamedTextColor.RED));
            return true;
        }

        final MissingKeyTracker.Statistics stats = missingKeyTracker.getStatistics();
        final Component message = Component.text()
            .append(Component.text("=== Translation Statistics ===").color(NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Tracking Events: ").color(NamedTextColor.GRAY))
            .append(Component.text(stats.getTotalTrackingEvents()).color(NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Unique Missing Keys: ").color(NamedTextColor.GRAY))
            .append(Component.text(stats.getUniqueMissingCount()).color(NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Affected Locales: ").color(NamedTextColor.GRAY))
            .append(Component.text(stats.getAffectedLocaleCount()).color(NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Most Frequent Key: ").color(NamedTextColor.GRAY))
            .append(Component.text(stats.getMostFrequentMissing() != null ? stats.getMostFrequentMissing().key() : "None").color(NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Top Locale: ").color(NamedTextColor.GRAY))
            .append(Component.text(stats.getLocaleWithMostMissing() != null ? stats.getLocaleWithMostMissing().toString() : "None").color(NamedTextColor.WHITE))
            .build();

        sender.sendMessage(message);
        return true;
    }

    /**
     * Handles the {@code /translate reload} sub-command, triggering repository reload and cache invalidation. Locale
     * resolutions are cached by {@link TranslationService}, so clearing them after a reload is essential to prevent
     * players from seeing stale translations.
     *
     * @param sender command executor
     * @return {@code true} once the command is processed
     */
    private boolean handleReload(@NotNull final CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sender.sendMessage(Component.text("You don't have permission to reload translations.").color(NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("Reloading translations...").color(NamedTextColor.YELLOW));

        repository.reload().thenRun(() -> {
            // Always flush cached locale resolutions so freshly loaded bundles take effect immediately.
            TranslationService.clearLocaleCache();
            sender.sendMessage(Component.text("Translations reloaded and caches cleared!").color(NamedTextColor.GREEN));
        }).exceptionally(throwable -> {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Failed to reload translations",
                            Map.of("subcommand", "reload")
                    ),
                    throwable
            );
            sender.sendMessage(Component.text("Failed to reload translations: " + throwable.getMessage()).color(NamedTextColor.RED));
            return null;
        });

        return true;
    }

    /**
     * Handles the {@code /translate backup} sub-command, creating timestamped copies of translation files for one or
     * more locales using {@link TranslationBackupService}.
     *
     * @param sender command executor
     * @param args   command arguments
     * @return {@code true} once the command is processed
     */
    private boolean handleBackup(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!sender.hasPermission(PERMISSION_BACKUP)) {
            sender.sendMessage(Component.text("You don't have permission to create backups.").color(NamedTextColor.RED));
            return true;
        }

        final boolean backupAll = args.length > 1 && "all".equalsIgnoreCase(args[1]);
        final List<Locale> localesToBackup = new ArrayList<>();

        if (backupAll) {
            localesToBackup.addAll(repository.getAvailableLocales());
        } else {
            final Locale locale = args.length > 1 ? parseLocale(args[1]) : repository.getDefaultLocale();
            localesToBackup.add(locale);
        }

        if (localesToBackup.isEmpty()) {
            sender.sendMessage(Component.text("No locales available for backup.").color(NamedTextColor.RED));
            return true;
        }

        final TranslationRepository.RepositoryMetadata metadata = repository.getMetadata();
        if (!"yaml".equalsIgnoreCase(metadata.getType())) {
            sender.sendMessage(Component.text("Manual backups are only supported for YAML repositories.").color(NamedTextColor.RED));
            return true;
        }

        final String directoryProperty = metadata.getProperty("directory");
        if (directoryProperty == null || directoryProperty.isEmpty()) {
            sender.sendMessage(Component.text("Translation repository directory is not available.").color(NamedTextColor.RED));
            return true;
        }

        final Path translationsPath;
        try {
            translationsPath = Path.of(directoryProperty);
        } catch (final Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Invalid translation directory for manual backup",
                            Map.of("directory", directoryProperty)
                    ),
                    exception
            );
            sender.sendMessage(Component.text("Failed to resolve translation directory. See console for details.").color(NamedTextColor.RED));
            return true;
        }

        localesToBackup.sort(Comparator.comparing(this::toLocaleTag));
        final List<Locale> locales = List.copyOf(localesToBackup);
        final String descriptor = backupAll ? "all locales" : toLocaleTag(locales.get(0));

        sender.sendMessage(Component.text("Preparing backup for " + descriptor + "...").color(NamedTextColor.YELLOW));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final TranslationBackupService backupService = new TranslationBackupService(translationsPath);
            final List<Component> resultLines = new ArrayList<>();

            for (final Locale locale : locales) {
                final Path localeFile = resolveLocaleFile(translationsPath, locale);
                try {
                    final Optional<Path> backupPath = backupService.createBackup(localeFile, "manual-command");
                    if (backupPath.isPresent()) {
                        final Path created = backupPath.get();
                        LOGGER.info(() -> TranslationLogger.message(
                                "Manual translation backup created",
                                Map.of(
                                        "locale", toLocaleTag(locale),
                                        "source", localeFile.toAbsolutePath().toString(),
                                        "backup", created.toAbsolutePath().toString()
                                )
                        ));
                        resultLines.add(Component.text()
                                .append(Component.text("✔ ").color(NamedTextColor.GREEN))
                                .append(Component.text(toLocaleTag(locale) + " -> ").color(NamedTextColor.GRAY))
                                .append(Component.text(created.getFileName().toString()).color(NamedTextColor.WHITE))
                                .build());
                    } else {
                        resultLines.add(Component.text()
                                .append(Component.text("✖ ").color(NamedTextColor.RED))
                                .append(Component.text(toLocaleTag(locale) + " - source file not found").color(NamedTextColor.GRAY))
                                .build());
                    }
                } catch (final IOException exception) {
                    LOGGER.log(
                            Level.WARNING,
                            TranslationLogger.message(
                                    "Manual translation backup failed",
                                    Map.of(
                                            "locale", toLocaleTag(locale),
                                            "file", localeFile.toAbsolutePath().toString()
                                    )
                            ),
                            exception
                    );
                    resultLines.add(Component.text()
                            .append(Component.text("✖ ").color(NamedTextColor.RED))
                            .append(Component.text(toLocaleTag(locale) + " - error: " + exception.getMessage()).color(NamedTextColor.GRAY))
                            .build());
                }
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (resultLines.isEmpty()) {
                    sender.sendMessage(Component.text("No translation files were eligible for backup.").color(NamedTextColor.RED));
                    return;
                }

                sender.sendMessage(Component.text("=== Backup Results ===").color(NamedTextColor.GOLD));
                resultLines.forEach(sender::sendMessage);
            });
        });

        return true;
    }

    /**
     * Handles the {@code /translate info} sub-command, outputting repository metadata.
     *
     * @param sender command executor
     * @return {@code true} once the command is processed
     */
    private boolean handleInfo(@NotNull final CommandSender sender) {
        final TranslationRepository.RepositoryMetadata metadata = repository.getMetadata();
        final Component message = Component.text()
            .append(Component.text("=== Translation System Info ===").color(NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Repository Type: ").color(NamedTextColor.GRAY))
            .append(Component.text(metadata.getType()).color(NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Source: ").color(NamedTextColor.GRAY))
            .append(Component.text(metadata.getSource()).color(NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Available Locales: ").color(NamedTextColor.GRAY))
            .append(Component.text(repository.getAvailableLocales().toString()).color(NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Total Translations: ").color(NamedTextColor.GRAY))
            .append(Component.text(metadata.getTotalTranslations()).color(NamedTextColor.WHITE))
            .build();

        sender.sendMessage(message);
        return true;
    }

    private void openMissingKeysGui(@NotNull final Player player, @NotNull final Locale locale, final int page) {
        final Set<TranslationKey> missingKeys = missingKeyTracker.getMissingKeys(locale);

        if (missingKeys.isEmpty()) {
            player.sendMessage(Component.text("No missing keys found for locale: " + locale).color(NamedTextColor.GREEN));
            return;
        }

        final List<TranslationKey> keyList = new ArrayList<>(missingKeys);
        keyList.sort(Comparator.comparing(TranslationKey::key));

        final int totalPages = (int) Math.ceil((double) keyList.size() / ITEMS_PER_PAGE);
        final int startIndex = page * ITEMS_PER_PAGE;
        final int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, keyList.size());

        final String title = GUI_TITLE_PREFIX + locale + " (" + (page + 1) + "/" + totalPages + ")";
        final Inventory inventory = Bukkit.createInventory(null, 54, title);

        for (int i = startIndex; i < endIndex; i++) {
            final TranslationKey key = keyList.get(i);
            final ItemStack item = createMissingKeyItem(key);
            inventory.setItem(i - startIndex, item);
        }

        addNavigationItems(inventory, page, totalPages, locale);
        activeGuiSessions.put(player.getUniqueId(), new GuiSession(locale, page, keyList));
        player.openInventory(inventory);
    }

    @NotNull
    private ItemStack createMissingKeyItem(@NotNull final TranslationKey key) {
        final ItemStack item = new ItemStack(Material.PAPER);
        final ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(key.key())
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                Component.text("Click to add translation")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("Key: " + key.key())
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));

            item.setItemMeta(meta);
        }

        return item;
    }

    private void addNavigationItems(@NotNull final Inventory inventory, final int currentPage, final int totalPages, @NotNull final Locale locale) {
        if (currentPage > 0) {
            final ItemStack prevItem = new ItemStack(Material.ARROW);
            final ItemMeta prevMeta = prevItem.getItemMeta();
            if (prevMeta != null) {
                prevMeta.displayName(Component.text("Previous Page")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
                prevItem.setItemMeta(prevMeta);
            }
            inventory.setItem(45, prevItem);
        }

        if (currentPage < totalPages - 1) {
            final ItemStack nextItem = new ItemStack(Material.ARROW);
            final ItemMeta nextMeta = nextItem.getItemMeta();
            if (nextMeta != null) {
                nextMeta.displayName(Component.text("Next Page")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
                nextItem.setItemMeta(nextMeta);
            }
            inventory.setItem(53, nextItem);
        }

        final ItemStack infoItem = new ItemStack(Material.BOOK);
        final ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(Component.text("Locale: " + locale)
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

            infoMeta.lore(List.of(
                Component.text("Page " + (currentPage + 1) + " of " + totalPages)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("Click keys to add translations")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));

            infoItem.setItemMeta(infoMeta);
        }
        inventory.setItem(49, infoItem);
    }

    private void startChatSession(@NotNull final Player player, @NotNull final TranslationKey key) {
        final Locale playerLocale = localeResolver.resolveLocale(player).orElse(Locale.ENGLISH);
        final ChatSession session = new ChatSession(key, playerLocale);
        activeChatSessions.put(player.getUniqueId(), session);

        final Component message = Component.text()
            .append(Component.text("=== Translation Wizard ===").color(NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Key: ").color(NamedTextColor.GRAY))
            .append(Component.text(key.key()).color(NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("Locale: ").color(NamedTextColor.GRAY))
            .append(Component.text(playerLocale.toString()).color(NamedTextColor.AQUA))
            .append(Component.newline())
            .append(Component.text("Please enter the translation (or 'cancel' to abort):").color(NamedTextColor.WHITE))
            .build();

        player.sendMessage(message);
    }

    @EventHandler
    public void onInventoryClick(@NotNull final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE_PREFIX)) {
            return;
        }

        event.setCancelled(true);

        final GuiSession session = activeGuiSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        final ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        handleGuiClick(player, session, event.getSlot(), clicked);
    }

    @EventHandler
    public void onInventoryClose(@NotNull final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        final String title = event.getView().getTitle();
        if (title.startsWith(GUI_TITLE_PREFIX)) {
            activeGuiSessions.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerChat(@NotNull final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final ChatSession session = activeChatSessions.get(player.getUniqueId());

        if (session == null) {
            return;
        }

        event.setCancelled(true);
        final String message = event.getMessage().trim();

        if ("cancel".equalsIgnoreCase(message)) {
            activeChatSessions.remove(player.getUniqueId());
            player.sendMessage(Component.text("Translation wizard cancelled.").color(NamedTextColor.YELLOW));
            return;
        }

        handleChatInput(player, session, message);
    }

    private void handleGuiClick(@NotNull final Player player, @NotNull final GuiSession session, final int slot, @NotNull final ItemStack item) {
        if (slot == 45) {
            openMissingKeysGui(player, session.locale, session.page - 1);
            return;
        }

        if (slot == 53) {
            openMissingKeysGui(player, session.locale, session.page + 1);
            return;
        }

        if (slot == 49) {
            return;
        }

        if (slot < ITEMS_PER_PAGE) {
            final int keyIndex = session.page * ITEMS_PER_PAGE + slot;
            if (keyIndex < session.keys.size()) {
                final TranslationKey key = session.keys.get(keyIndex);
                player.closeInventory();
                startChatSession(player, key);
            }
        }
    }

    private void handleChatInput(@NotNull final Player player, @NotNull final ChatSession session, @NotNull final String input) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                missingKeyTracker.markResolved(session.key, session.locale);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    activeChatSessions.remove(player.getUniqueId());

                    final Component successMessage = Component.text()
                        .append(Component.text("Translation added successfully!").color(NamedTextColor.GREEN))
                        .append(Component.newline())
                        .append(Component.text("Key: ").color(NamedTextColor.GRAY))
                        .append(Component.text(session.key.key()).color(NamedTextColor.YELLOW))
                        .append(Component.newline())
                        .append(Component.text("Value: ").color(NamedTextColor.GRAY))
                        .append(Component.text(input).color(NamedTextColor.WHITE))
                        .build();

                    player.sendMessage(successMessage);
                });
            } catch (final Exception exception) {
                LOGGER.log(
                        Level.WARNING,
                        TranslationLogger.message(
                                "Failed to save translation",
                                Map.of(
                                        "player", TranslationLogger.anonymize(player.getUniqueId()),
                                        "key", session.key.key(),
                                        "locale", session.locale.toString()
                                )
                        ),
                        exception
                );
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Failed to save translation: " + exception.getMessage()).color(NamedTextColor.RED));
                });
            }
        });
    }

    private void sendUsage(@NotNull final CommandSender sender) {
        final Component usage = Component.text()
            .append(Component.text("=== Translation Command Usage ===").color(NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("/translate missing [locale]").color(NamedTextColor.YELLOW))
            .append(Component.text(" - View missing keys").color(NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/translate add <key>").color(NamedTextColor.YELLOW))
            .append(Component.text(" - Add a translation").color(NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/translate stats").color(NamedTextColor.YELLOW))
            .append(Component.text(" - View statistics").color(NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/translate reload").color(NamedTextColor.YELLOW))
            .append(Component.text(" - Reload translations").color(NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/translate backup [locale|all]").color(NamedTextColor.YELLOW))
            .append(Component.text(" - Create translation backups").color(NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/translate info").color(NamedTextColor.YELLOW))
            .append(Component.text(" - System information").color(NamedTextColor.GRAY))
            .build();

        sender.sendMessage(usage);
    }

    @NotNull
    private Path resolveLocaleFile(@NotNull final Path directory, @NotNull final Locale locale) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");
        return directory.resolve(toLocaleTag(locale) + YAML_EXTENSION);
    }

    @NotNull
    private String toLocaleTag(@NotNull final Locale locale) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        // Prefer Locale fields to construct a stable, filesystem-safe tag using underscores
        final String language = locale.getLanguage();
        final String country = locale.getCountry();
        final String variant = locale.getVariant();

        if (!language.isEmpty() && !country.isEmpty() && !variant.isEmpty()) {
            return (language.toLowerCase(Locale.ROOT) + "_" + country.toUpperCase(Locale.ROOT) + "_" + variant);
        }
        if (!language.isEmpty() && !country.isEmpty()) {
            return (language.toLowerCase(Locale.ROOT) + "_" + country.toUpperCase(Locale.ROOT));
        }
        if (!language.isEmpty()) {
            return language.toLowerCase(Locale.ROOT);
        }

        // Fallback to repository default, normalized the same way
        final Locale defaultLocale = repository.getDefaultLocale();
        final String defLang = defaultLocale.getLanguage();
        final String defCountry = defaultLocale.getCountry();
        final String defVariant = defaultLocale.getVariant();
        if (!defLang.isEmpty() && !defCountry.isEmpty() && !defVariant.isEmpty()) {
            return (defLang.toLowerCase(Locale.ROOT) + "_" + defCountry.toUpperCase(Locale.ROOT) + "_" + defVariant);
        }
        if (!defLang.isEmpty() && !defCountry.isEmpty()) {
            return (defLang.toLowerCase(Locale.ROOT) + "_" + defCountry.toUpperCase(Locale.ROOT));
        }
        return defLang.isEmpty() ? "en" : defLang.toLowerCase(Locale.ROOT);
    }

    @NotNull
    private Locale determineTargetLocale(@NotNull final Player player, @NotNull final String[] args, final int argIndex) {
        if (args.length > argIndex) {
            return parseLocale(args[argIndex]);
        }
        return localeResolver.resolveLocale(player).orElse(Locale.ENGLISH);
    }

    @NotNull
    private Locale parseLocale(@NotNull final String localeString) {
        try {
            final String[] parts = localeString.replace('-', '_').split("_");
            if (parts.length == 1) {
                return new Locale(parts[0].toLowerCase());
            } else if (parts.length >= 2) {
                return new Locale(parts[0].toLowerCase(), parts[1].toUpperCase());
            }
        } catch (final Exception exception) {
            LOGGER.fine(() -> TranslationLogger.message(
                    "Failed to parse locale",
                    Map.of("input", localeString)
            ));
        }
        return Locale.ENGLISH;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String alias, @NotNull final String[] args) {
        if (args.length == 1) {
            return List.of("missing", "add", "stats", "reload", "backup", "info").stream()
                .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && "missing".equalsIgnoreCase(args[0])) {
            return missingKeyTracker.getLocalesWithMissingKeys().stream()
                .map(Locale::toString)
                .filter(locale -> locale.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && "backup".equalsIgnoreCase(args[0])) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            final List<String> suggestions = new ArrayList<>();
            suggestions.add("all");
            repository.getAvailableLocales().stream()
                .map(this::toLocaleTag)
                .forEach(suggestions::add);
            return suggestions.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private record GuiSession(@NotNull Locale locale, int page, @NotNull List<TranslationKey> keys) {}

    private record ChatSession(@NotNull TranslationKey key, @NotNull Locale locale) {}
}
