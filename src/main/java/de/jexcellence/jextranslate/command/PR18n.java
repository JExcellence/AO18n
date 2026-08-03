package de.jexcellence.jextranslate.command;

import com.raindropcentral.commands.BukkitCommand;
import com.raindropcentral.commands.utility.Command;
import de.jexcellence.evaluable.section.CommandSection;
import de.jexcellence.jextranslate.R18nManager;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Command-framework bridge that exposes JExTranslate administration commands in host plugins.
 *
 * <p>The bridge delegates execution and tab completion to {@link PR18nCommand} while preserving
 * namespaced command labels for each hosting plugin, such as {@code rdq:r18n}.</p>
 *
 * @author JExcellence
 * @version 2.1.0
 * @since 2.1.0
 */
@Command
@SuppressWarnings("unused")
public final class PR18n extends BukkitCommand {

    private static final List<String> SUBCOMMANDS = List.of("reload", "missing", "export", "metrics", "help");
    private static final Set<String> SUBCOMMAND_SET = Set.copyOf(SUBCOMMANDS);

    private final JavaPlugin loadedPlugin;

    /**
     * Creates a command bridge for the hosting plugin.
     *
     * @param commandSection mapped command configuration section
     * @param loadedPlugin host plugin instance
     */
    public PR18n(
            final @NotNull CommandSection commandSection,
            final @NotNull JavaPlugin loadedPlugin
    ) {
        super(commandSection);
        this.loadedPlugin = loadedPlugin;
    }

    @Override
    protected void onInvocation(
            final @NotNull CommandSender sender,
            final @NotNull String alias,
            final @NotNull String[] args
    ) {
        final PR18nCommand delegate = this.createDelegate(sender, true);
        if (delegate == null) {
            return;
        }
        delegate.onCommand(sender, this, alias, args);
    }

    @Override
    protected @NotNull List<String> onTabCompletion(
            final @NotNull CommandSender sender,
            final @NotNull String alias,
            final @NotNull String[] args
    ) {
        if (args.length == 1) {
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            final List<String> suggestions = new ArrayList<>();
            for (String subcommand : SUBCOMMANDS) {
                if (subcommand.startsWith(prefix)) {
                    suggestions.add(subcommand);
                }
            }
            return suggestions;
        }

        if (args.length > 1 && !SUBCOMMAND_SET.contains(args[0].toLowerCase(Locale.ROOT))) {
            return List.of();
        }

        final PR18nCommand delegate = this.createDelegate(sender, false);
        if (delegate == null) {
            return List.of();
        }
        return delegate.onTabComplete(sender, this, alias, args);
    }

    private @Nullable PR18nCommand createDelegate(
            final @NotNull CommandSender sender,
            final boolean notifySender
    ) {
        final R18nManager manager = R18nManager.getInstance();
        if (manager == null || !manager.isInitialized()) {
            if (notifySender) {
                sender.sendMessage("[R18n] Translation manager is still starting. Try again shortly.");
            }
            return null;
        }
        return new PR18nCommand(this.loadedPlugin, manager);
    }
}
