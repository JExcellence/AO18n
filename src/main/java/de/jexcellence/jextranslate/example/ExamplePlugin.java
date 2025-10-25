package de.jexcellence.jextranslate.example;

import de.jexcellence.jextranslate.api.LocaleResolver;
import de.jexcellence.jextranslate.api.MessageFormatter;
import de.jexcellence.jextranslate.api.TranslationKey;
import de.jexcellence.jextranslate.api.TranslationRepository;
import de.jexcellence.jextranslate.api.TranslationService;
import de.jexcellence.jextranslate.impl.LocaleResolverProvider;
import de.jexcellence.jextranslate.impl.MiniMessageFormatter;
import de.jexcellence.jextranslate.impl.YamlTranslationRepository;
import de.jexcellence.jextranslate.util.DebugUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Example Bukkit plugin demonstrating how to configure {@link TranslationService} with the YAML repository and
 * MiniMessage formatter. Highlights locale resolution behaviour, MiniMessage placeholder usage, and cache invalidation
 * patterns described in the module documentation.
 *
 * <p>Refer to this class when bootstrapping new plugins to ensure {@link TranslationService#configure(TranslationService.ServiceConfiguration)}
 * is invoked before any translations are resolved.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.1
 */
public class ExamplePlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeTranslationService();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ExamplePlugin enabled with JExTranslate!");
    }

    /**
     * Initializes the translation service using {@link YamlTranslationRepository}, {@link MiniMessageFormatter}, and the
     * auto-detecting {@link LocaleResolver}. This helper assumes {@link #saveDefaultConfig()} has already provisioned the
     * plugin data folder and must be invoked before any other component attempts to resolve translations.
     *
     * <p>The resulting {@link TranslationService} configuration becomes globally accessible, ensuring every subsequent call
     * to {@link TranslationService#create(TranslationKey, Player)} or
     * {@link TranslationService#createFresh(TranslationKey, Player)} operates on the same repository, formatter, and
     * locale resolver.</p>
     */
    private void initializeTranslationService() {
        final Path translationsDir = getDataFolder().toPath().resolve("translations");

        final TranslationRepository repository = YamlTranslationRepository.create(
            translationsDir,
            Locale.ENGLISH
        );

        final MessageFormatter formatter = new MiniMessageFormatter();

        final LocaleResolver localeResolver = LocaleResolverProvider.createAutoDetecting(Locale.ENGLISH);

        TranslationService.configure(new TranslationService.ServiceConfiguration(
            repository,
            formatter,
            localeResolver
        ));

        getLogger().info("Translation service initialized");
        getLogger().info("Available locales: " + repository.getAvailableLocales());
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        if (player.hasPlayedBefore()) {
            TranslationService.create(TranslationKey.of("welcome.returning"), player)
                .withPrefix()
                .with("player", player.getName())
                .with("online", getServer().getOnlinePlayers().size())
                .send();
        } else {
            TranslationService.create(TranslationKey.of("welcome.first-join"), player)
                .withPrefix()
                .with("player", player.getName())
                .send();

            TranslationService.create(TranslationKey.of("welcome.tutorial"), player)
                .sendTitle();
        }
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String label, @NotNull final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "coins" -> handleCoinsCommand(player, args);
            case "lang" -> handleLangCommand(player, args);
            case "translatedebug" -> handleDebugCommand(player, args);
            case "translatereload" -> handleReloadCommand(player);
            default -> {
                return false;
            }
        }

        return true;
    }

    private void handleCoinsCommand(final Player player, final String[] args) {
        final int coins = 1000;

        TranslationService.create(TranslationKey.of("coins.balance"), player)
            .withPrefix()
            .with("amount", coins)
            .send();

        TranslationService.create(TranslationKey.of("coins.info"), player)
            .with("player", player.getName())
            .with("coins", coins)
            .sendActionBar();
    }

    private void handleLangCommand(final Player player, final String[] args) {
        if (args.length == 0) {
            final LocaleResolver resolver = TranslationService.getConfiguration().localeResolver();
            final Locale currentLocale = resolver.resolveLocale(player).orElse(resolver.getDefaultLocale());

            TranslationService.create(TranslationKey.of("lang.current"), player)
                .withPrefix()
                .with("locale", currentLocale.toString())
                .send();

            final TranslationRepository repository = TranslationService.getConfiguration().repository();
            TranslationService.create(TranslationKey.of("lang.available"), player)
                .with("locales", repository.getAvailableLocales().toString())
                .send();
            return;
        }

        final String localeString = args[0];
        final Locale newLocale = parseLocale(localeString);

        final LocaleResolver resolver = TranslationService.getConfiguration().localeResolver();
        if (resolver.setPlayerLocale(player, newLocale)) {
            TranslationService.clearLocaleCache(player);

            TranslationService.create(TranslationKey.of("lang.changed"), player)
                .withPrefix()
                .with("locale", newLocale.toString())
                .send();
        } else {
            TranslationService.create(TranslationKey.of("lang.error"), player)
                .withPrefix()
                .send();
        }
    }

    private void handleDebugCommand(final Player player, final String[] args) {
        if (args.length == 0) {
            player.sendMessage("Usage: /translatedebug <key>");
            return;
        }

        final String key = args[0];
        final String debugInfo = DebugUtils.debugTranslation(key, player);
        player.sendMessage(debugInfo);
    }

    /**
     * Reloads the configured {@link TranslationRepository} and clears cached locale resolutions so newly loaded bundles are
     * immediately visible to players. Cache invalidation is mandatory because locale detection results are memoized by the
     * service to minimize lookups across join events.
     *
     * @param player the operator invoking the reload command
     */
    private void handleReloadCommand(final Player player) {
        TranslationService.create(TranslationKey.of("reload.starting"), player)
            .withPrefix()
            .send();

        final TranslationRepository repository = TranslationService.getConfiguration().repository();
        repository.reload().thenRun(() -> {
            TranslationService.clearLocaleCache();

            TranslationService.create(TranslationKey.of("reload.complete"), player)
                .withPrefix()
                .with("locales", repository.getAvailableLocales().size())
                .with("keys", repository.getAllAvailableKeys().size())
                .send();
        });
    }

    @NotNull
    private Locale parseLocale(@NotNull final String localeString) {
        final String[] parts = localeString.split("_");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return Locale.ENGLISH;
        }
        if (parts.length == 1) {
            return new Locale(parts[0]);
        } else if (parts.length == 2) {
            return new Locale(parts[0], parts[1]);
        } else if (parts.length >= 3) {
            return new Locale(parts[0], parts[1], parts[2]);
        }
        return Locale.ENGLISH;
    }
}
