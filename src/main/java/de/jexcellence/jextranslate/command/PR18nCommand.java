package de.jexcellence.jextranslate.command;

import de.jexcellence.jextranslate.R18nManager;
import de.jexcellence.jextranslate.core.TranslationExportService;
import de.jexcellence.jextranslate.core.TranslationLoader;
import de.jexcellence.jextranslate.core.TranslationMetrics;
import de.jexcellence.jextranslate.core.VersionedMessageSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Enhanced command handler for the "r18n" command with modern MiniMessage gradients.
 *
 * <p>This command supports the following subcommands with enhanced visual presentation:</p>
 * <ul>
 *   <li><strong>reload</strong> - Reloads all translation files at runtime with visual feedback</li>
 *   <li><strong>missing</strong> - Displays missing translation keys with modern interactive pagination</li>
 * </ul>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public class PR18nCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "missing", "export", "metrics", "help");
    private static final List<String> EXPORT_FORMATS = List.of("csv", "json", "yaml");
    private static final int KEYS_PER_PAGE = 12;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin loadedPlugin;
    private final R18nManager r18nManager;
    private final VersionedMessageSender messageSender;
    private final TranslationExportService exportService;
    private String commandPath;

    /**
     * Executes PR18nCommand.
     */
    public PR18nCommand(@NotNull JavaPlugin loadedPlugin, @NotNull R18nManager r18nManager) {
        this.loadedPlugin = loadedPlugin;
        this.r18nManager = r18nManager;
        this.messageSender = r18nManager.getMessageSender();
        this.exportService = new TranslationExportService();
        this.commandPath = "/" + loadedPlugin.getName().toLowerCase(Locale.ROOT) + ":r18n";
    }

    /**
     * Executes onCommand.
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        this.commandPath = "/" + label.toLowerCase(Locale.ROOT);
        boolean isPlayer = sender instanceof Player;
        Player player = isPlayer ? (Player) sender : null;

        if (args.length == 3 && "missing".equalsIgnoreCase(args[0])) {
            if (isPlayer) {
                handlePaginationRouting(player, args[1], args[2]);
            } else {
                handleConsoleMissingSubcommand(sender, args);
            }
            return true;
        }

        if (args.length == 0) {
            if (isPlayer) {
                sendEnhancedUsageMessage(player);
            } else {
                sendConsoleUsageMessage(sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload" -> {
                if (isPlayer) {
                    handleReloadSubcommand(player);
                } else {
                    handleConsoleReloadSubcommand(sender);
                }
            }
            case "missing" -> {
                if (isPlayer) {
                    handleMissingSubcommand(player, args);
                } else {
                    handleConsoleMissingSubcommand(sender, args);
                }
            }
            case "export" -> handleExportSubcommand(sender, args, isPlayer, player);
            case "metrics" -> handleMetricsSubcommand(sender, isPlayer, player);
            default -> {
                if (isPlayer) {
                    sendEnhancedUsageMessage(player);
                } else {
                    sendConsoleUsageMessage(sender);
                }
            }
        }

        return true;
    }

    private void handlePaginationRouting(@NotNull Player player, @NotNull String localeArg, @NotNull String pageArg) {
        if (!player.hasPermission(ER18nPermission.MISSING.getFallbackNode())) {
            return;
        }
        try {
            int page = Integer.parseInt(pageArg);
            displayMissingKeysForLocale(player, localeArg, page);
        } catch (NumberFormatException e) {
            sendEnhancedUsageMessage(player);
        }
    }

    private void handleReloadSubcommand(@NotNull Player player) {
        if (!player.hasPermission(ER18nPermission.RELOAD.getFallbackNode())) {
            sendNoPermission(player);
            return;
        }
        reloadTranslationsWithEnhancedFeedback(player);
    }

    private void handleMissingSubcommand(@NotNull Player player, @NotNull String[] args) {
        if (!player.hasPermission(ER18nPermission.MISSING.getFallbackNode())) {
            sendNoPermission(player);
            return;
        }

        if (args.length > 1) {
            int page = args.length > 2 ? parsePageNumber(args[2]) : 1;
            displayMissingKeysForLocale(player, args[1], page);
        } else {
            displayEnhancedLocaleSelection(player);
        }
    }

    private int parsePageNumber(String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void sendNoPermission(@NotNull Player player) {
        Component msg = MINI_MESSAGE.deserialize("<red>You don't have permission to use this command.</red>");
        if (messageSender != null) messageSender.sendMessage(player, msg);
    }

    // ==================== Export Command Handlers ====================

    private void handleExportSubcommand(@NotNull CommandSender sender, @NotNull String[] args,
                                         boolean isPlayer, @Nullable Player player) {
        if (!sender.hasPermission(ER18nPermission.EXPORT.getFallbackNode())) {
            if (isPlayer && player != null) {
                sendNoPermission(player);
            } else {
                sender.sendMessage("[R18n] You don't have permission to use this command.");
            }
            return;
        }

        if (args.length < 2) {
            sendExportUsage(sender, isPlayer, player);
            return;
        }

        String formatArg = args[1].toUpperCase();
        Optional<TranslationExportService.ExportFormat> formatOpt = TranslationExportService.parseFormat(formatArg);

        if (formatOpt.isEmpty()) {
            if (isPlayer && player != null) {
                Component errorMsg = MINI_MESSAGE.deserialize(
                        "<red>Invalid export format: <white>" + args[1] + "</white></red>\n" +
                        "<gray>Valid formats: <yellow>csv</yellow>, <yellow>json</yellow>, <yellow>yaml</yellow></gray>");
                if (messageSender != null) messageSender.sendMessage(player, errorMsg);
            } else {
                sender.sendMessage("[R18n] Invalid export format: " + args[1]);
                sender.sendMessage("[R18n] Valid formats: csv, json, yaml");
            }
            return;
        }

        TranslationExportService.ExportFormat format = formatOpt.get();
        performExport(sender, format, isPlayer, player);
    }

    private void sendExportUsage(@NotNull CommandSender sender, boolean isPlayer, @Nullable Player player) {
        if (isPlayer && player != null) {
            Component usage = MINI_MESSAGE.deserialize(
                    "<gradient:#f39c12:#e67e22>📤 Export Usage</gradient>\n" +
                    "<gray>Usage: <yellow>" + commandPath + " export <format></yellow></gray>\n" +
                    "<gray>Formats: <green>csv</green>, <green>json</green>, <green>yaml</green></gray>");
            if (messageSender != null) messageSender.sendMessage(player, usage);
        } else {
            sender.sendMessage("[R18n] Usage: " + commandPath + " export <format>");
            sender.sendMessage("[R18n] Formats: csv, json, yaml");
        }
    }

    private void performExport(@NotNull CommandSender sender, @NotNull TranslationExportService.ExportFormat format,
                                boolean isPlayer, @Nullable Player player) {
        // Generate filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "translations_" + timestamp + TranslationExportService.getFileExtension(format);
        Path exportPath = loadedPlugin.getDataFolder().toPath().resolve(fileName);

        // Get all translations
        Map<String, Map<String, List<String>>> translations = r18nManager.getTranslationLoader().getAllTranslations();

        if (isPlayer && player != null) {
            Component exportingMsg = MINI_MESSAGE.deserialize(
                    "<gradient:#f39c12:#e67e22>📤 Exporting translations to " + format + " format...</gradient>");
            if (messageSender != null) messageSender.sendMessage(player, exportingMsg);
        } else {
            sender.sendMessage("[R18n] Exporting translations to " + format + " format...");
        }

        try {
            exportService.export(exportPath, format, translations);

            int keyCount = translations.size();
            Set<String> locales = new HashSet<>();
            for (Map<String, List<String>> localeMap : translations.values()) {
                locales.addAll(localeMap.keySet());
            }

            if (isPlayer && player != null) {
                Component successMsg = MINI_MESSAGE.deserialize(
                        "<gradient:#2ecc71:#27ae60>✓ Export Complete!</gradient>\n" +
                        "<gray>Exported <white>" + keyCount + "</white> keys across <white>" + locales.size() + "</white> locales</gray>\n" +
                        "<gray>File: <yellow>" + fileName + "</yellow></gray>");
                if (messageSender != null) messageSender.sendMessage(player, successMsg);
            } else {
                sender.sendMessage("[R18n] Export Complete!");
                sender.sendMessage("[R18n] Exported " + keyCount + " keys across " + locales.size() + " locales");
                sender.sendMessage("[R18n] File: " + exportPath.toAbsolutePath());
            }
        } catch (IOException e) {
            loadedPlugin.getLogger().log(Level.SEVERE, "Failed to export translations", e);

            if (isPlayer && player != null) {
                Component errorMsg = MINI_MESSAGE.deserialize(
                        "<gradient:#e74c3c:#c0392b>✗ Export Failed!</gradient>\n" +
                        "<gray>An error occurred while exporting translations.</gray>\n" +
                        "<gray>Check the console for details.</gray>");
                if (messageSender != null) messageSender.sendMessage(player, errorMsg);
            } else {
                sender.sendMessage("[R18n] Export Failed!");
                sender.sendMessage("[R18n] Error: " + e.getMessage());
            }
        }
    }

    // ==================== Metrics Command Handlers ====================

    private void handleMetricsSubcommand(@NotNull CommandSender sender, boolean isPlayer, @Nullable Player player) {
        if (!sender.hasPermission(ER18nPermission.METRICS.getFallbackNode())) {
            if (isPlayer && player != null) {
                sendNoPermission(player);
            } else {
                sender.sendMessage("[R18n] You don't have permission to use this command.");
            }
            return;
        }

        TranslationMetrics metrics = r18nManager.getMetrics();
        if (metrics == null) {
            if (isPlayer && player != null) {
                Component msg = MINI_MESSAGE.deserialize(
                        "<gradient:#e74c3c:#c0392b>✗ Metrics Disabled</gradient>\n" +
                        "<gray>Translation metrics are not enabled in the configuration.</gray>\n" +
                        "<gray>Enable metrics with <yellow>metricsEnabled: true</yellow> in your config.</gray>");
                if (messageSender != null) messageSender.sendMessage(player, msg);
            } else {
                sender.sendMessage("[R18n] Metrics are not enabled in the configuration.");
                sender.sendMessage("[R18n] Enable metrics with metricsEnabled: true");
            }
            return;
        }

        if (isPlayer && player != null) {
            displayPlayerMetrics(player, metrics);
        } else {
            displayConsoleMetrics(sender, metrics);
        }
    }

    private void displayPlayerMetrics(@NotNull Player player, @NotNull TranslationMetrics metrics) {
        Component headerLine = MINI_MESSAGE.deserialize("<gradient:#3498db:#2980b9>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>");
        Component title = MINI_MESSAGE.deserialize("<gradient:#3498db:#2980b9>📊 Translation Metrics</gradient>");

        if (messageSender != null) {
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, headerLine);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, title);
            messageSender.sendMessage(player, Component.empty());
        }

        // Summary stats
        Component summaryTitle = MINI_MESSAGE.deserialize("<gradient:#9b59b6:#8e44ad>📈 Summary</gradient>");
        Component totalRequests = MINI_MESSAGE.deserialize("<dark_gray>▪ <gray>Total Requests: <white>" + metrics.getTotalRequests() + "</white></gray>");
        Component uniqueKeys = MINI_MESSAGE.deserialize("<dark_gray>▪ <gray>Unique Keys Used: <white>" + metrics.getUniqueKeyCount() + "</white></gray>");
        Component missingOccurrences = MINI_MESSAGE.deserialize("<dark_gray>▪ <gray>Missing Key Occurrences: <red>" + metrics.getTotalMissingKeyOccurrences() + "</red></gray>");
        Component uniqueMissing = MINI_MESSAGE.deserialize("<dark_gray>▪ <gray>Unique Missing Keys: <red>" + metrics.getUniqueMissingKeyCount() + "</red></gray>");

        if (messageSender != null) {
            messageSender.sendMessage(player, summaryTitle);
            messageSender.sendMessage(player, totalRequests);
            messageSender.sendMessage(player, uniqueKeys);
            messageSender.sendMessage(player, missingOccurrences);
            messageSender.sendMessage(player, uniqueMissing);
            messageSender.sendMessage(player, Component.empty());
        }

        // Top used keys
        List<Map.Entry<String, Long>> topKeys = metrics.getMostUsedKeys(5);
        if (!topKeys.isEmpty()) {
            Component topKeysTitle = MINI_MESSAGE.deserialize("<gradient:#2ecc71:#27ae60>🔑 Top Used Keys</gradient>");
            if (messageSender != null) messageSender.sendMessage(player, topKeysTitle);
            
            for (int i = 0; i < topKeys.size(); i++) {
                Map.Entry<String, Long> entry = topKeys.get(i);
                Component keyLine = MINI_MESSAGE.deserialize("<dark_gray>" + (i + 1) + ". <gray>" + entry.getKey() + " <dark_gray>- <white>" + entry.getValue() + "</white> uses</gray>");
                if (messageSender != null) messageSender.sendMessage(player, keyLine);
            }
            if (messageSender != null) messageSender.sendMessage(player, Component.empty());
        }

        // Locale distribution
        Map<String, Long> localeDistribution = metrics.getLocaleDistribution();
        if (!localeDistribution.isEmpty()) {
            Component localeTitle = MINI_MESSAGE.deserialize("<gradient:#f39c12:#e67e22>🌐 Locale Distribution</gradient>");
            if (messageSender != null) messageSender.sendMessage(player, localeTitle);
            
            localeDistribution.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(entry -> {
                        Component localeLine = MINI_MESSAGE.deserialize("<dark_gray>▪ <gray>" + entry.getKey().toUpperCase() + ": <white>" + entry.getValue() + "</white> requests</gray>");
                        if (messageSender != null) messageSender.sendMessage(player, localeLine);
                    });
            if (messageSender != null) messageSender.sendMessage(player, Component.empty());
        }

        // Missing keys
        Map<String, Long> missingKeys = metrics.getMissingKeyOccurrences();
        if (!missingKeys.isEmpty()) {
            Component missingTitle = MINI_MESSAGE.deserialize("<gradient:#e74c3c:#c0392b>⚠ Most Frequent Missing Keys</gradient>");
            if (messageSender != null) messageSender.sendMessage(player, missingTitle);
            
            missingKeys.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(entry -> {
                        Component missingLine = MINI_MESSAGE.deserialize("<dark_gray>▪ <red>" + entry.getKey() + "</red> <dark_gray>- <white>" + entry.getValue() + "</white> times</gray>");
                        if (messageSender != null) messageSender.sendMessage(player, missingLine);
                    });
        }

        if (messageSender != null) {
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, headerLine);
            messageSender.sendMessage(player, Component.empty());
        }
    }

    private void displayConsoleMetrics(@NotNull CommandSender sender, @NotNull TranslationMetrics metrics) {
        sender.sendMessage("========== Translation Metrics ==========");
        sender.sendMessage("");
        sender.sendMessage("--- Summary ---");
        sender.sendMessage("Total Requests: " + metrics.getTotalRequests());
        sender.sendMessage("Unique Keys Used: " + metrics.getUniqueKeyCount());
        sender.sendMessage("Missing Key Occurrences: " + metrics.getTotalMissingKeyOccurrences());
        sender.sendMessage("Unique Missing Keys: " + metrics.getUniqueMissingKeyCount());
        sender.sendMessage("");

        // Top used keys
        List<Map.Entry<String, Long>> topKeys = metrics.getMostUsedKeys(5);
        if (!topKeys.isEmpty()) {
            sender.sendMessage("--- Top Used Keys ---");
            for (int i = 0; i < topKeys.size(); i++) {
                Map.Entry<String, Long> entry = topKeys.get(i);
                sender.sendMessage((i + 1) + ". " + entry.getKey() + " - " + entry.getValue() + " uses");
            }
            sender.sendMessage("");
        }

        // Locale distribution
        Map<String, Long> localeDistribution = metrics.getLocaleDistribution();
        if (!localeDistribution.isEmpty()) {
            sender.sendMessage("--- Locale Distribution ---");
            localeDistribution.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(entry -> sender.sendMessage("  " + entry.getKey().toUpperCase() + ": " + entry.getValue() + " requests"));
            sender.sendMessage("");
        }

        // Missing keys
        Map<String, Long> missingKeys = metrics.getMissingKeyOccurrences();
        if (!missingKeys.isEmpty()) {
            sender.sendMessage("--- Most Frequent Missing Keys ---");
            missingKeys.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(entry -> sender.sendMessage("  " + entry.getKey() + " - " + entry.getValue() + " times"));
        }

        sender.sendMessage("=========================================");
    }

    private void displayEnhancedLocaleSelection(@NotNull Player player) {
        Set<String> availableLocales = getAvailableLocales();

        Component headerLine = MINI_MESSAGE.deserialize("<gradient:#9b59b6:#e74c3c>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>");
        Component title = MINI_MESSAGE.deserialize("<gradient:#9b59b6:#8e44ad>🌐 Translation Keys Browser</gradient>");
        Component subtitle = MINI_MESSAGE.deserialize("<gray>Select a locale to analyze missing translation keys</gray>");
        Component instructions = MINI_MESSAGE.deserialize("<dark_gray>▪ <yellow>Click</yellow> <dark_gray>»</dark_gray> <gray>View missing keys for locale</gray>");

        if (messageSender != null) {
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, headerLine);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, title);
            messageSender.sendMessage(player, subtitle);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, instructions);
            messageSender.sendMessage(player, Component.empty());
        }

        sendLocaleButtonsGrid(player, availableLocales);

        if (messageSender != null) {
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, headerLine);
            messageSender.sendMessage(player, Component.empty());
        }
    }

    private void sendLocaleButtonsGrid(@NotNull Player player, @NotNull Set<String> locales) {
        List<String> localeList = new ArrayList<>(locales);
        int buttonsPerRow = 4;

        for (int i = 0; i < localeList.size(); i += buttonsPerRow) {
            Component row = Component.text(" ");
            for (int j = 0; j < buttonsPerRow && (i + j) < localeList.size(); j++) {
                String locale = localeList.get(i + j);
                Set<String> missingKeys = findMissingKeysForLocale(locale);
                if (j > 0) row = row.append(Component.text(" "));
                Component localeButton = createEnhancedLocaleButton(locale, missingKeys.size());
                row = row.append(localeButton);
            }
            if (messageSender != null) messageSender.sendMessage(player, row);
        }
    }

    private Component createEnhancedLocaleButton(@NotNull String locale, int missingCount) {
        String buttonColor = missingCount == 0 ? "#2ecc71" : missingCount < 10 ? "#f39c12" : "#e74c3c";
        String statusIcon = missingCount == 0 ? "✓" : missingCount < 10 ? "⚠" : "✗";
        String darkerShade = getDarkerShade(buttonColor);

        Component button = MINI_MESSAGE.deserialize("<gradient:" + buttonColor + ":" + darkerShade + ">[" + statusIcon + " " + locale.toUpperCase() + "]</gradient>");

        String statusColor = missingCount == 0 ? "green" : missingCount < 10 ? "yellow" : "red";
        String statusText = missingCount == 0 ? "<green>Complete</green>" : missingCount < 10 ? "<yellow>Minor Issues</yellow>" : "<red>Needs Attention</red>";

        Component hoverText = MINI_MESSAGE.deserialize(
                "<gradient:#9b59b6:#8e44ad>Locale Information</gradient>\n\n" +
                        "<dark_gray>▪ <gray>Language: <white>" + locale.toUpperCase() + "</white></gray>\n" +
                        "<dark_gray>▪ <gray>Missing Keys: <" + statusColor + ">" + missingCount + "</" + statusColor + "></gray>\n" +
                        "<dark_gray>▪ <gray>Status: " + statusText + "</gray>\n\n" +
                        "<gradient:#f1c40f:#f39c12>Click</gradient> <dark_gray>»</dark_gray> <gray>View missing keys</gray>"
        );

        return button.hoverEvent(HoverEvent.showText(hoverText))
                .clickEvent(ClickEvent.runCommand(commandPath + " missing " + locale + " 1"));
    }

    private String getDarkerShade(@NotNull String hexColor) {
        return switch (hexColor) {
            case "#2ecc71" -> "#27ae60";
            case "#f39c12" -> "#e67e22";
            case "#e74c3c" -> "#c0392b";
            default -> "#7f8c8d";
        };
    }

    private void displayMissingKeysForLocale(@NotNull Player player, @NotNull String locale, int page) {
        Set<String> missingKeys = findMissingKeysForLocale(locale);

        if (missingKeys.isEmpty()) {
            sendNoMissingKeysMessage(player, locale);
            return;
        }

        List<String> keysList = new ArrayList<>(missingKeys);
        int totalPages = (int) Math.ceil((double) keysList.size() / KEYS_PER_PAGE);
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int startIndex = (currentPage - 1) * KEYS_PER_PAGE;
        int endIndex = Math.min(startIndex + KEYS_PER_PAGE, keysList.size());

        sendEnhancedMissingKeysHeader(player, locale, currentPage, totalPages, missingKeys.size());
        sendMissingKeysList(player, keysList, startIndex, endIndex);
        sendEnhancedNavigationBar(player, locale, currentPage, totalPages);
    }

    private void sendEnhancedMissingKeysHeader(@NotNull Player player, @NotNull String locale,
                                                int currentPage, int totalPages, int totalKeys) {
        Component headerLine = MINI_MESSAGE.deserialize("<gradient:#e74c3c:#c0392b>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>");
        Component title = MINI_MESSAGE.deserialize("<gradient:#e74c3c:#c0392b>🔍 Missing Keys Analysis</gradient> <dark_gray>»</dark_gray> <gradient:#ecf0f1:#bdc3c7>" + locale.toUpperCase() + "</gradient>");
        Component pageInfo = MINI_MESSAGE.deserialize("<gray>Page <gradient:#f39c12:#e67e22>" + currentPage + "</gradient><gray>/<gradient:#f39c12:#e67e22>" + totalPages + "</gradient> <dark_gray>•</dark_gray> <gradient:#e74c3c:#c0392b>" + totalKeys + "</gradient> <gray>missing keys</gray>");
        Component instructions = MINI_MESSAGE.deserialize("<dark_gray>▪ <yellow>Click</yellow> <dark_gray>»</dark_gray> <gray>Copy key to clipboard</gray>");

        if (messageSender != null) {
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, headerLine);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, title);
            messageSender.sendMessage(player, pageInfo);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, instructions);
            messageSender.sendMessage(player, Component.empty());
        }
    }

    private void sendMissingKeysList(@NotNull Player player, @NotNull List<String> keysList, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            String key = keysList.get(i);
            Component keyComponent = Component.text("")
                    .append(Component.text(" "))
                    .append(MINI_MESSAGE.deserialize("<gradient:#e74c3c:#c0392b>✗</gradient>"))
                    .append(Component.text(" "))
                    .append(MINI_MESSAGE.deserialize("<gradient:#ecf0f1:#95a5a6>" + key + "</gradient>")
                            .hoverEvent(HoverEvent.showText(MINI_MESSAGE.deserialize(
                                    "<gradient:#9b59b6:#8e44ad>Translation Key Details</gradient>\n\n" +
                                            "<dark_gray>▪ <gray>Key: <gradient:#ecf0f1:#bdc3c7>" + key + "</gradient></gray>\n" +
                                            "<dark_gray>▪ <gray>Status: <gradient:#e74c3c:#c0392b>Missing</gradient></gray>\n\n" +
                                            "<gradient:#f1c40f:#f39c12>Click</gradient> <dark_gray>»</dark_gray> <gray>Copy to clipboard</gray>"
                            )))
                            .clickEvent(ClickEvent.copyToClipboard(key)));

            if (messageSender != null) messageSender.sendMessage(player, keyComponent);
        }
    }

    private void sendEnhancedNavigationBar(@NotNull Player player, @NotNull String locale, int currentPage, int totalPages) {
        if (messageSender != null) messageSender.sendMessage(player, Component.empty());

        Component navigation = Component.text(" ");

        if (currentPage > 1) {
            Component prevButton = MINI_MESSAGE.deserialize("<gradient:#2ecc71:#27ae60>[← Previous]</gradient>")
                    .hoverEvent(HoverEvent.showText(MINI_MESSAGE.deserialize("<gradient:#2ecc71:#27ae60>Go to page " + (currentPage - 1) + "</gradient>")))
                    .clickEvent(ClickEvent.runCommand(commandPath + " missing " + locale + " " + (currentPage - 1)));
            navigation = navigation.append(prevButton);
        } else {
            navigation = navigation.append(MINI_MESSAGE.deserialize("<dark_gray>[← Previous]</dark_gray>"));
        }

        navigation = navigation.append(Component.text(" "))
                .append(MINI_MESSAGE.deserialize("<gradient:#9b59b6:#8e44ad>[Page " + currentPage + "/" + totalPages + "]</gradient>"))
                .append(Component.text(" "));

        if (currentPage < totalPages) {
            Component nextButton = MINI_MESSAGE.deserialize("<gradient:#2ecc71:#27ae60>[Next →]</gradient>")
                    .hoverEvent(HoverEvent.showText(MINI_MESSAGE.deserialize("<gradient:#2ecc71:#27ae60>Go to page " + (currentPage + 1) + "</gradient>")))
                    .clickEvent(ClickEvent.runCommand(commandPath + " missing " + locale + " " + (currentPage + 1)));
            navigation = navigation.append(nextButton);
        } else {
            navigation = navigation.append(MINI_MESSAGE.deserialize("<dark_gray>[Next →]</dark_gray>"));
        }

        navigation = navigation.append(Component.text(" "))
                .append(MINI_MESSAGE.deserialize("<gradient:#3498db:#2980b9>[← Back to Locales]</gradient>")
                        .hoverEvent(HoverEvent.showText(MINI_MESSAGE.deserialize("<gradient:#3498db:#2980b9>Return to locale selection</gradient>")))
                        .clickEvent(ClickEvent.runCommand(commandPath + " missing")));

        if (messageSender != null) {
            messageSender.sendMessage(player, navigation);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, MINI_MESSAGE.deserialize("<gradient:#e74c3c:#c0392b>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>"));
            messageSender.sendMessage(player, Component.empty());
        }
    }

    private void sendNoMissingKeysMessage(@NotNull Player player, @NotNull String locale) {
        Component headerLine = MINI_MESSAGE.deserialize("<gradient:#2ecc71:#27ae60>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>");
        Component successMessage = MINI_MESSAGE.deserialize(
                "<gradient:#2ecc71:#27ae60>✓ Perfect Translation Coverage</gradient>\n\n" +
                        "<gray>No missing translation keys found for locale: <gradient:#ecf0f1:#bdc3c7>" + locale.toUpperCase() + "</gradient></gray>\n" +
                        "<gray>All translations are complete and ready for use!</gray>"
        );
        Component backButton = MINI_MESSAGE.deserialize("<gradient:#3498db:#2980b9>[← Back to Locales]</gradient>")
                .hoverEvent(HoverEvent.showText(MINI_MESSAGE.deserialize("<gradient:#3498db:#2980b9>Return to locale selection</gradient>")))
                .clickEvent(ClickEvent.runCommand(commandPath + " missing"));

        if (messageSender != null) {
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, headerLine);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, successMessage);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, Component.text(" ").append(backButton));
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, headerLine);
            messageSender.sendMessage(player, Component.empty());
        }
    }

    private void sendEnhancedUsageMessage(@NotNull Player player) {
        Component headerLine = MINI_MESSAGE.deserialize("<gradient:#9b59b6:#8e44ad>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>");
        Component title = MINI_MESSAGE.deserialize("<gradient:#9b59b6:#8e44ad>🛠 R18n Administration Commands</gradient>");
        Component reloadCommand = MINI_MESSAGE.deserialize("<dark_gray>▪ <gradient:#2ecc71:#27ae60>" + commandPath + " reload</gradient> <dark_gray>»</dark_gray> <gray>Reload translation files</gray>");
        Component missingCommand = MINI_MESSAGE.deserialize("<dark_gray>▪ <gradient:#e74c3c:#c0392b>" + commandPath + " missing</gradient> <dark_gray>»</dark_gray> <gray>Analyze missing translation keys</gray>");
        Component exportCommand = MINI_MESSAGE.deserialize("<dark_gray>▪ <gradient:#f39c12:#e67e22>" + commandPath + " export <format></gradient> <dark_gray>»</dark_gray> <gray>Export translations (csv/json/yaml)</gray>");
        Component metricsCommand = MINI_MESSAGE.deserialize("<dark_gray>▪ <gradient:#3498db:#2980b9>" + commandPath + " metrics</gradient> <dark_gray>»</dark_gray> <gray>View translation usage statistics</gray>");

        if (messageSender != null) {
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, headerLine);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, title);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, reloadCommand);
            messageSender.sendMessage(player, missingCommand);
            messageSender.sendMessage(player, exportCommand);
            messageSender.sendMessage(player, metricsCommand);
            messageSender.sendMessage(player, Component.empty());
            messageSender.sendMessage(player, headerLine);
            messageSender.sendMessage(player, Component.empty());
        }
    }

    private void reloadTranslationsWithEnhancedFeedback(@NotNull Player player) {
        Component loadingMessage = MINI_MESSAGE.deserialize("<gradient:#f39c12:#e67e22>🔄 Reloading translation files...</gradient>");
        if (messageSender != null) messageSender.sendMessage(player, loadingMessage);

        r18nManager.reload().thenRun(() -> {
            TranslationLoader loader = r18nManager.getTranslationLoader();
            int localeCount = loader.getLoadedLocales().size();
            int keyCount = loader.getTotalKeyCount();
            
            Component successMessage = MINI_MESSAGE.deserialize(
                    "<gradient:#2ecc71:#27ae60>✓ Translation Reload Complete</gradient>\n" +
                            "<gray>Loaded <white>" + localeCount + "</white> locales with <white>" + keyCount + "</white> translation keys!</gray>"
            );
            if (messageSender != null) messageSender.sendMessage(player, successMessage);
        }).exceptionally(ex -> {
            Component failureMessage = MINI_MESSAGE.deserialize(
                    "<gradient:#e74c3c:#c0392b>✗ Translation Reload Failed</gradient>\n" +
                            "<gray>An error occurred while reloading translation files.</gray>\n" +
                            "<gray>Check the console for detailed error information.</gray>"
            );
            if (messageSender != null) messageSender.sendMessage(player, failureMessage);
            loadedPlugin.getLogger().log(Level.WARNING, "Failed to load translations", ex);
            return null;
        });
    }

    // ==================== Console Command Handlers ====================

    private void handleConsoleReloadSubcommand(@NotNull CommandSender sender) {
        if (!sender.hasPermission(ER18nPermission.RELOAD.getFallbackNode())) {
            sender.sendMessage("[R18n] You don't have permission to use this command.");
            return;
        }
        reloadTranslationsForConsole(sender);
    }

    private void reloadTranslationsForConsole(@NotNull CommandSender sender) {
        sender.sendMessage("[R18n] Reloading translation files...");

        r18nManager.reload().thenRun(() -> {
            TranslationLoader loader = r18nManager.getTranslationLoader();
            int localeCount = loader.getLoadedLocales().size();
            int keyCount = loader.getTotalKeyCount();
            
            sender.sendMessage("[R18n] Translation Reload Complete!");
            sender.sendMessage("[R18n] Loaded " + localeCount + " locales with " + keyCount + " translation keys.");
        }).exceptionally(ex -> {
            sender.sendMessage("[R18n] Translation Reload Failed!");
            sender.sendMessage("[R18n] Check the console for detailed error information.");
            loadedPlugin.getLogger().log(Level.WARNING, "Failed to load translations", ex);
            return null;
        });
    }

    private void sendConsoleUsageMessage(@NotNull CommandSender sender) {
        sender.sendMessage("========== R18n Administration Commands ==========");
        sender.sendMessage(commandPath + " reload - Reload translation files");
        sender.sendMessage(commandPath + " missing [locale] [page] - Analyze missing translation keys");
        sender.sendMessage(commandPath + " export <format> - Export translations (csv/json/yaml)");
        sender.sendMessage(commandPath + " metrics - View translation usage statistics");
        sender.sendMessage("==================================================");
    }

    private void handleConsoleMissingSubcommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(ER18nPermission.MISSING.getFallbackNode())) {
            sender.sendMessage("[R18n] You don't have permission to use this command.");
            return;
        }

        if (args.length > 1) {
            int page = args.length > 2 ? parsePageNumber(args[2]) : 1;
            displayConsoleMissingKeysForLocale(sender, args[1], page);
        } else {
            displayConsoleLocaleSelection(sender);
        }
    }

    private void displayConsoleLocaleSelection(@NotNull CommandSender sender) {
        Set<String> availableLocales = getAvailableLocales();

        sender.sendMessage("========== Translation Keys Browser ==========");
        sender.sendMessage("Available locales:");
        sender.sendMessage("");

        for (String locale : availableLocales) {
            Set<String> missingKeys = findMissingKeysForLocale(locale);
            String status = missingKeys.isEmpty() ? "[OK]" : "[" + missingKeys.size() + " missing]";
            sender.sendMessage("  " + locale.toUpperCase() + " " + status);
        }

        sender.sendMessage("");
        sender.sendMessage("Use: " + commandPath + " missing <locale> [page] to view missing keys");
        sender.sendMessage("==============================================");
    }

    private void displayConsoleMissingKeysForLocale(@NotNull CommandSender sender, @NotNull String locale, int page) {
        Set<String> missingKeys = findMissingKeysForLocale(locale);

        if (missingKeys.isEmpty()) {
            sender.sendMessage("========== Missing Keys: " + locale.toUpperCase() + " ==========");
            sender.sendMessage("No missing translation keys found!");
            sender.sendMessage("All translations are complete for this locale.");
            sender.sendMessage("==============================================");
            return;
        }

        List<String> keysList = new ArrayList<>(missingKeys);
        int totalPages = (int) Math.ceil((double) keysList.size() / KEYS_PER_PAGE);
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int startIndex = (currentPage - 1) * KEYS_PER_PAGE;
        int endIndex = Math.min(startIndex + KEYS_PER_PAGE, keysList.size());

        sender.sendMessage("========== Missing Keys: " + locale.toUpperCase() + " ==========");
        sender.sendMessage("Page " + currentPage + "/" + totalPages + " | Total: " + missingKeys.size() + " missing keys");
        sender.sendMessage("");

        for (int i = startIndex; i < endIndex; i++) {
            sender.sendMessage("  [X] " + keysList.get(i));
        }

        sender.sendMessage("");
        sendConsolePaginationInfo(sender, locale, currentPage, totalPages);
        sender.sendMessage("==============================================");
    }

    private void sendConsolePaginationInfo(@NotNull CommandSender sender, @NotNull String locale, int currentPage, int totalPages) {
        StringBuilder navInfo = new StringBuilder("Navigation: ");
        
        if (currentPage > 1) {
            navInfo.append(commandPath).append(" missing ").append(locale).append(" ").append(currentPage - 1).append(" (prev)");
        }
        
        if (currentPage > 1 && currentPage < totalPages) {
            navInfo.append(" | ");
        }
        
        if (currentPage < totalPages) {
            navInfo.append(commandPath).append(" missing ").append(locale).append(" ").append(currentPage + 1).append(" (next)");
        }
        
        if (currentPage == 1 && currentPage == totalPages) {
            navInfo.append("Single page");
        }
        
        sender.sendMessage(navInfo.toString());
    }

    private Set<String> getAvailableLocales() {
        return r18nManager.getTranslationLoader().getLoadedLocales().stream()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> findMissingKeysForLocale(@NotNull String locale) {
        return r18nManager.getTranslationLoader().getMissingKeys(locale).stream()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Executes onTabComplete.
     */
    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(subcommand -> subcommand.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && "missing".equalsIgnoreCase(args[0])) {
            return getAvailableLocales().stream()
                    .filter(locale -> locale.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && "export".equalsIgnoreCase(args[0])) {
            return EXPORT_FORMATS.stream()
                    .filter(format -> format.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && "missing".equalsIgnoreCase(args[0])) {
            return List.of("1", "2", "3", "4", "5");
        }

        return List.of();
    }
}
