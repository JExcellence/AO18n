# R18n Translation System - Usage Guide

A comprehensive internationalization (i18n) library for Minecraft plugins supporting versions 1.8-1.21+.

## Table of Contents

1. [Quick Start](#quick-start)
2. [R18nManager API](#r18nmanager-api)
3. [Configuration Options](#configuration-options)
4. [Translation Caching](#translation-caching)
5. [Player Locale Storage](#player-locale-storage)
6. [Translation Files](#translation-files)
7. [JSON Translation Support](#json-translation-support)
8. [Plural Support](#plural-support)
9. [File Watcher (Hot Reload)](#file-watcher-hot-reload)
10. [Translation Export](#translation-export)
11. [Translation Metrics](#translation-metrics)
12. [Missing Key Handler](#missing-key-handler)
13. [I18n Fluent Builder](#i18n-fluent-builder)
14. [Version Detection](#version-detection)
15. [PR18n Command](#pr18n-command)
16. [Permissions](#permissions)
17. [Best Practices](#best-practices)

---

## Quick Start

```java
public class MyPlugin extends JavaPlugin {
    private R18nManager r18n;

    @Override
    public void onEnable() {
        r18n = R18nManager.builder(this)
            .defaultLocale("en_US")
            .supportedLocales("en_US", "de_DE", "es_ES", "fr_FR", "ja_JP")
            .enableKeyValidation(true)
            .enablePlaceholderAPI(true)
            .build();

        r18n.initialize().thenRun(() -> {
            getLogger().info("R18n initialized successfully!");
            
            // Register command
            PR18nCommand command = new PR18nCommand(this, r18n);
            getCommand("r18n").setExecutor(command);
            getCommand("r18n").setTabCompleter(command);
        });
    }

    @Override
    public void onDisable() {
        if (r18n != null) {
            r18n.shutdown();
        }
    }

    // Send a message to a player
    public void welcomePlayer(Player player) {
        r18n.message("welcome.message")
            .placeholder("player", player.getName())
            .placeholder("server", getServer().getName())
            .withPrefix()
            .send(player);
    }
}
```

---

## R18nManager API

The `R18nManager` provides a modern builder pattern with async support.

### Builder Configuration

```java
R18nManager r18n = R18nManager.builder(plugin)
    .defaultLocale("en_US")                    // Default fallback locale
    .supportedLocales("en_US", "de_DE", "es_ES") // Supported locales
    .enableKeyValidation(true)                  // Validate keys on load
    .enablePlaceholderAPI(true)                 // PlaceholderAPI integration
    .translationDirectory("translations")       // Custom directory name
    .build();
```

### Async Initialization

```java
r18n.initialize().thenRun(() -> {
    // R18n is ready to use
    getLogger().info("Loaded " + r18n.getTranslationLoader().getTotalKeyCount() + " keys");
});
```

### Sending Messages

```java
// Simple message
r18n.message("welcome.message")
    .placeholder("player", player.getName())
    .send(player);

// With prefix
r18n.message("error.permission")
    .placeholder("permission", "admin.reload")
    .withPrefix()
    .send(player);

// Broadcast to all players
r18n.message("server.announcement")
    .placeholder("message", "Server restarting in 5 minutes!")
    .broadcast();

// Send to console
r18n.message("server.startup")
    .console();

// Explicit locale override
r18n.message("welcome.message")
    .locale("de_DE")
    .send(player);
```

### Getting Components

```java
// Get as Component
Component component = r18n.message("item.name")
    .placeholder("item", "Diamond Sword")
    .toComponent(player);

// Get as List<Component> for multi-line
List<Component> lines = r18n.message("item.lore")
    .placeholder("damage", 10)
    .toComponents(player);

// Get as plain String
String text = r18n.message("placeholder.value")
    .toString(player);
```

### Hot Reload

```java
r18n.reload().thenRun(() -> {
    getLogger().info("Translations reloaded!");
});
```

### Accessing Components

```java
// Get configuration
R18nConfiguration config = r18n.getConfiguration();

// Get translation loader
TranslationLoader loader = r18n.getTranslationLoader();
Set<String> locales = loader.getLoadedLocales();
int keyCount = loader.getTotalKeyCount();

// Get version detector
VersionDetector detector = r18n.getVersionDetector();

// Get message sender
VersionedMessageSender sender = r18n.getMessageSender();

// Get audiences (Adventure)
BukkitAudiences audiences = r18n.getAudiences();

// Get locale storage
LocaleStorage storage = r18n.getLocaleStorage();

// Get cache statistics (if caching enabled)
CacheStats stats = r18n.getCacheStats();

// Get translation metrics (if metrics enabled)
TranslationMetrics metrics = r18n.getMetrics();
```

---

## Configuration Options

The `R18nConfiguration` record provides comprehensive configuration options:

```java
R18nManager r18n = R18nManager.builder(plugin)
    // Basic settings
    .defaultLocale("en_US")
    .supportedLocales("en_US", "de_DE", "es_ES")
    .translationDirectory("translations")
    .enableKeyValidation(true)
    .enablePlaceholderAPI(true)
    
    // Caching settings
    .configuration(R18nConfiguration.defaultConfiguration()
        .withCacheEnabled(true)
        .withCacheMaxSize(1000)
        .withCacheExpireMinutes(30))
    
    // File watcher for hot reload
    .enableFileWatcher(true)
    
    // Metrics collection
    .configuration(R18nConfiguration.defaultConfiguration()
        .withMetricsEnabled(true))
    
    // Custom missing key handler
    .configuration(R18nConfiguration.defaultConfiguration()
        .withMissingKeyHandler((key, locale, placeholders) -> 
            "<red>Translation missing: " + key + "</red>"))
    
    .build();
```

### Configuration Builder

You can also use the configuration builder directly:

```java
R18nConfiguration config = R18nConfiguration.defaultConfiguration().toBuilder()
    .defaultLocale("en_US")
    .supportedLocales("en_US", "de_DE", "es_ES")
    .enableCache(true)
    .cacheMaxSize(2000)
    .cacheExpireMinutes(60)
    .enableFileWatcher(true)
    .enableMetrics(true)
    .onMissingKey((key, locale, placeholders) -> null) // Suppress missing key messages
    .build();

R18nManager r18n = R18nManager.builder(plugin)
    .configuration(config)
    .build();
```

---

## Translation Caching

R18n uses Caffeine cache to store parsed MiniMessage Components for improved performance.

### Configuration

```java
R18nManager r18n = R18nManager.builder(plugin)
    .configuration(R18nConfiguration.defaultConfiguration()
        .withCacheEnabled(true)      // Enable caching (default: true)
        .withCacheMaxSize(1000)      // Max cached entries (default: 1000)
        .withCacheExpireMinutes(30)) // Expiration time (default: 30 minutes)
    .build();
```

### Cache Statistics

```java
// Get cache statistics
CacheStats stats = r18n.getCacheStats();
if (stats != null) {
    long hitCount = stats.hitCount();
    long missCount = stats.missCount();
    double hitRate = stats.hitRate();
    
    getLogger().info("Cache hit rate: " + (hitRate * 100) + "%");
}
```

### Cache Invalidation

The cache is automatically invalidated when translations are reloaded:

```java
r18n.reload().thenRun(() -> {
    // Cache is automatically cleared on reload
    getLogger().info("Translations reloaded, cache cleared!");
});
```

---

## Player Locale Storage

R18n supports persistent player locale preferences through the `LocaleStorage` interface.

### In-Memory Storage (Default)

```java
// Default behavior - locales stored in memory only
R18nManager r18n = R18nManager.builder(plugin)
    .defaultLocale("en_US")
    .build();
```

### Database Storage with JEHibernate

```java
// Using JEHibernate for persistent storage
R18nManager r18n = R18nManager.builder(plugin)
    .defaultLocale("en_US")
    .withDatabaseStorage(entityManagerFactory)
    .build();

// Or set EMF first, then enable database storage
R18nManager r18n = R18nManager.builder(plugin)
    .entityManagerFactory(emf)
    .withDatabaseStorage()
    .build();
```

### Custom Storage Implementation

```java
public class RedisLocaleStorage implements LocaleStorage {
    private final JedisPool jedisPool;
    
    @Override
    public Optional<String> getLocale(UUID playerId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String locale = jedis.get("locale:" + playerId);
            return Optional.ofNullable(locale);
        }
    }
    
    @Override
    public void setLocale(UUID playerId, String locale) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("locale:" + playerId, locale);
        }
    }
    
    @Override
    public void removeLocale(UUID playerId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del("locale:" + playerId);
        }
    }
    
    @Override
    public void clearAll() {
        // Implementation
    }
}

// Use custom storage
R18nManager r18n = R18nManager.builder(plugin)
    .localeStorage(new RedisLocaleStorage(jedisPool))
    .build();
```

### Using Locale Storage

```java
LocaleStorage storage = r18n.getLocaleStorage();

// Set player's preferred locale
storage.setLocale(player.getUniqueId(), "de_DE");

// Get player's locale
Optional<String> locale = storage.getLocale(player.getUniqueId());

// Remove player's locale preference
storage.removeLocale(player.getUniqueId());
```

---

## Translation Files

### Directory Structure

```
plugins/YourPlugin/
└── translations/
    ├── translation.yml    # Configuration (optional)
    ├── en_US.yml          # English (US)
    ├── en_US.json         # English (US) - JSON format
    ├── de_DE.yml          # German (Germany)
    ├── es_ES.yml          # Spanish (Spain)
    ├── fr_FR.yml          # French (France)
    └── ja_JP.yml          # Japanese
```

### Configuration File (translation.yml)

```yaml
# Default language (fallback when a translation is missing)
defaultLanguage: "en_US"

# List of supported languages (full locale codes)
supportedLanguages:
  - "en_US"
  - "de_DE"
  - "es_ES"
  - "fr_FR"
  - "ja_JP"
  - "zh_CN"
  - "ko_KR"

# Advanced configuration options
settings:
  validateKeys: true
  placeholderAPI: true
  legacyColors: true
  debug: false
  cacheTranslations: true
```

### Translation File Format (en_US.yml)

```yaml
# Prefix for all messages
prefix: "<gold>[MyPlugin]</gold> "

# Welcome messages
welcome:
  message: "<green>Welcome to the server, {player}!</green>"
  first-join:
    - "<gold>Welcome, {player}!</gold>"
    - "<gray>This is your first time here!</gray>"
    - "<yellow>Type /help to get started.</yellow>"

# Error messages
error:
  permission: "<red>You don't have permission: {permission}</red>"
  not-found: "<red>Could not find: {item}</red>"

# Item formatting
items:
  sword:
    name: "<red>Flame Sword</red>"
    lore:
      - "<gray>A legendary weapon</gray>"
      - "<gray>forged in dragon fire.</gray>"
      - ""
      - "<yellow>Damage: {damage}</yellow>"
      - "<yellow>Durability: {durability}</yellow>"

# Statistics
stats:
  display: "<gray>Kills: <green>{kills}</green> | Deaths: <red>{deaths}</red> | K/D: <yellow>{kdr}</yellow></gray>"
```

### Placeholder Formats

Both formats are supported:
- `{placeholder}` - Brace format
- `%placeholder%` - Percent format

### MiniMessage Formatting

Full MiniMessage support including:
- Colors: `<red>`, `<green>`, `<#FF5500>`
- Gradients: `<gradient:#FF0000:#00FF00>text</gradient>`
- Decorations: `<bold>`, `<italic>`, `<underlined>`
- Click events: `<click:run_command:/help>Click here</click>`
- Hover events: `<hover:show_text:'Tooltip'>Hover me</hover>`

### Legacy Color Codes

Legacy codes are automatically converted:
- `&a` → `<green>`
- `§c` → `<red>`
- `&l` → `<bold>`

---

## JSON Translation Support

R18n supports JSON files alongside YAML for translations.

### JSON File Format (en_US.json)

```json
{
  "prefix": "<gold>[MyPlugin]</gold> ",
  "welcome": {
    "message": "<green>Welcome to the server, {player}!</green>",
    "first-join": [
      "<gold>Welcome, {player}!</gold>",
      "<gray>This is your first time here!</gray>",
      "<yellow>Type /help to get started.</yellow>"
    ]
  },
  "error": {
    "permission": "<red>You don't have permission: {permission}</red>",
    "not-found": "<red>Could not find: {item}</red>"
  },
  "items": {
    "sword": {
      "name": "<red>Flame Sword</red>",
      "lore": [
        "<gray>A legendary weapon</gray>",
        "<gray>forged in dragon fire.</gray>",
        "",
        "<yellow>Damage: {damage}</yellow>",
        "<yellow>Durability: {durability}</yellow>"
      ]
    }
  }
}
```

### Features

- Nested objects are flattened to dot-notation keys (e.g., `welcome.message`)
- Arrays are supported for multi-line messages
- Jackson is used for JSON parsing
- When both YAML and JSON files exist for the same locale, YAML takes precedence
- JSON files are automatically extracted from plugin JAR alongside YAML files

---

## Plural Support

R18n supports ICU-style plural rules for grammatically correct translations.

### Translation File Format

```yaml
# Plural forms using suffixes
items:
  count:
    one: "You have {count} item"
    other: "You have {count} items"

# For languages with more complex rules (e.g., Russian)
messages:
  count:
    one: "У вас {count} сообщение"      # 1, 21, 31...
    few: "У вас {count} сообщения"      # 2-4, 22-24...
    many: "У вас {count} сообщений"     # 0, 5-20, 25-30...
    other: "У вас {count} сообщений"    # Fallback

# Arabic with all six forms
items:
  count:
    zero: "لا توجد عناصر"
    one: "عنصر واحد"
    two: "عنصران"
    few: "{count} عناصر"
    many: "{count} عنصرًا"
    other: "{count} عنصر"
```

### Using Plural Support

```java
// Use count() method for plural-aware messages
r18n.message("items.count")
    .count("count", itemCount)
    .send(player);

// The system automatically selects the correct plural form
// based on the count value and player's locale
```

### Supported Plural Categories

| Category | Description | Example Languages |
|----------|-------------|-------------------|
| `zero` | Zero items | Arabic |
| `one` | Singular | English, German, Spanish |
| `two` | Dual | Arabic, Welsh |
| `few` | Few items | Russian, Polish, Arabic |
| `many` | Many items | Russian, Polish, Arabic |
| `other` | Default/plural | All languages |

### Supported Languages

| Language | Plural Forms |
|----------|--------------|
| English, German | one, other |
| French | one (0-1), other |
| Spanish, Italian, Portuguese | one, other |
| Russian, Ukrainian | one, few, many |
| Polish | one, few, many |
| Arabic | zero, one, two, few, many, other |
| Chinese, Japanese, Korean | other (no plural forms) |

---

## File Watcher (Hot Reload)

R18n can automatically reload translations when files change during development.

### Configuration

```java
R18nManager r18n = R18nManager.builder(plugin)
    .defaultLocale("en_US")
    .enableFileWatcher(true)  // Enable hot reload
    .build();
```

### Features

- Watches the translations directory for file changes
- Automatically reloads when files are created, modified, or deleted
- Includes debouncing to prevent rapid reloads
- Runs on a separate daemon thread
- Automatically stopped on shutdown

### Supported Events

| Event | Action |
|-------|--------|
| File modified | Reload all translations |
| File created | Load new translations |
| File deleted | Remove translations |

### Notes

- Recommended for development environments only
- Default: disabled (`watchFiles: false`)
- Changes are detected within 2 seconds
- Cache is automatically invalidated on reload

---

## Translation Export

Export translations to various formats for backup or external translation services.

### Programmatic Export

```java
// Export to CSV
Path csvPath = plugin.getDataFolder().toPath().resolve("translations.csv");
r18n.exportTranslations(csvPath, TranslationExportService.ExportFormat.CSV);

// Export to JSON
Path jsonPath = plugin.getDataFolder().toPath().resolve("translations.json");
r18n.exportTranslations(jsonPath, TranslationExportService.ExportFormat.JSON);

// Export to YAML
Path yamlPath = plugin.getDataFolder().toPath().resolve("translations.yml");
r18n.exportTranslations(yamlPath, TranslationExportService.ExportFormat.YAML);
```

### Command Export

```
/r18n export csv
/r18n export json
/r18n export yaml
```

### Export Formats

#### CSV Format
```csv
key,locale,value
"welcome.message","en_US","Welcome to the server, {player}!"
"welcome.message","de_DE","Willkommen auf dem Server, {player}!"
"error.permission","en_US","You don't have permission: {permission}"
```

#### JSON Format
```json
{
  "en_US": {
    "welcome.message": "Welcome to the server, {player}!",
    "error.permission": "You don't have permission: {permission}"
  },
  "de_DE": {
    "welcome.message": "Willkommen auf dem Server, {player}!"
  }
}
```

#### YAML Format
```yaml
en_US:
  welcome.message: "Welcome to the server, {player}!"
  error.permission: "You don't have permission: {permission}"
de_DE:
  welcome.message: "Willkommen auf dem Server, {player}!"
```

---

## Translation Metrics

Track translation usage statistics for monitoring and optimization.

### Configuration

```java
R18nManager r18n = R18nManager.builder(plugin)
    .configuration(R18nConfiguration.defaultConfiguration()
        .withMetricsEnabled(true))
    .build();
```

### Accessing Metrics

```java
TranslationMetrics metrics = r18n.getMetrics();
if (metrics != null) {
    // Total requests
    long totalRequests = metrics.getTotalRequests();
    
    // Unique keys used
    int uniqueKeys = metrics.getUniqueKeyCount();
    
    // Missing key occurrences
    long missingOccurrences = metrics.getTotalMissingKeyOccurrences();
    int uniqueMissing = metrics.getUniqueMissingKeyCount();
    
    // Top used keys
    List<Map.Entry<String, Long>> topKeys = metrics.getMostUsedKeys(10);
    for (Map.Entry<String, Long> entry : topKeys) {
        getLogger().info(entry.getKey() + ": " + entry.getValue() + " uses");
    }
    
    // Locale distribution
    Map<String, Long> localeDistribution = metrics.getLocaleDistribution();
    
    // Missing key details
    Map<String, Long> missingKeys = metrics.getMissingKeyOccurrences();
    
    // Reset metrics
    metrics.reset();
}
```

### Metrics Command

```
/r18n metrics
```

Displays:
- Total translation requests
- Unique keys used
- Missing key occurrences
- Top 5 most used keys
- Locale distribution
- Most frequent missing keys

---

## Missing Key Handler

Customize what happens when a translation key is not found.

### Default Behavior

By default, missing keys display: `<gold>Missing: <red>{key}</red></gold>`

### Custom Handler

```java
R18nManager r18n = R18nManager.builder(plugin)
    .configuration(R18nConfiguration.defaultConfiguration()
        .withMissingKeyHandler((key, locale, placeholders) -> {
            // Return custom message
            return "<red>Translation not found: " + key + "</red>";
        }))
    .build();
```

### Suppress Missing Key Messages

```java
R18nManager r18n = R18nManager.builder(plugin)
    .configuration(R18nConfiguration.defaultConfiguration()
        .withMissingKeyHandler((key, locale, placeholders) -> {
            // Return null to suppress the message entirely
            return null;
        }))
    .build();
```

### Log Missing Keys

```java
R18nManager r18n = R18nManager.builder(plugin)
    .configuration(R18nConfiguration.defaultConfiguration()
        .withMissingKeyHandler((key, locale, placeholders) -> {
            // Log the missing key
            plugin.getLogger().warning("Missing translation: " + key + " for locale: " + locale);
            // Return fallback message
            return "<gray>[" + key + "]</gray>";
        }))
    .build();
```

### Handler Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `key` | String | The missing translation key |
| `locale` | String | The locale that was being used |
| `placeholders` | Map<String, Object> | The placeholders that were provided |

---

## I18n Fluent Builder

The `I18n` class provides a simple fluent API that works with R18nManager.

### Basic Usage

```java
// Send to player
new I18n.Builder("welcome.message", player)
    .withPlaceholder("player", player.getName())
    .includePrefix()
    .build()
    .sendMessage();

// Send to console
new I18n.Builder("server.startup")
    .build()
    .sendMessage();
```

### Multiple Placeholders

```java
new I18n.Builder("stats.display", player)
    .withPlaceholder("kills", 100)
    .withPlaceholder("deaths", 50)
    .withPlaceholder("kdr", 2.0)
    .build()
    .sendMessage();

// Or using a map
Map<String, Object> placeholders = Map.of(
    "kills", 100,
    "deaths", 50,
    "kdr", 2.0
);

new I18n.Builder("stats.display", player)
    .withPlaceholders(placeholders)
    .build()
    .sendMessage();
```

### Getting Components

```java
I18n i18n = new I18n.Builder("item.name", player)
    .withPlaceholder("item", "Diamond Sword")
    .build();

// Get single component
Component component = i18n.component();

// Get list of components (multi-line)
List<Component> lines = i18n.children();
```

---

## Version Detection

The `VersionDetector` automatically detects server type and version.

### Supported Servers

| Server Type | Native Adventure | Detection |
|-------------|------------------|-----------|
| Paper       | ✓                | `com.destroystokyo.paper.ParticleBuilder` |
| Purpur      | ✓                | `org.purpurmc.purpur.PurpurConfig` |
| Folia       | ✓                | `io.papermc.paper.threadedregions.RegionizedServer` |
| Spigot      | ✗ (Platform)     | `org.spigotmc.SpigotConfig` |
| Bukkit      | ✗ (Platform)     | Fallback |

### Usage

```java
VersionDetector detector = r18n.getVersionDetector();

// Check server type
if (detector.isPaper()) {
    // Paper-specific code
}

// Check version
if (detector.isModern()) {
    // 1.13+ features
}

if (detector.isVersionAtLeast("v1_16_R1")) {
    // 1.16+ features
}

// Get environment summary
String summary = detector.getEnvironmentSummary();
// "Paper v1_20_R3 (MC: 1.20.4, Modern: Yes, Adventure: Native)"
```

---

## PR18n Command

The `/r18n` command provides administrative functionality.

### Subcommands

| Command | Description | Permission |
|---------|-------------|------------|
| `/r18n reload` | Reload translation files | `r18n.command.reload` |
| `/r18n missing` | View missing keys browser | `r18n.command.missing` |
| `/r18n missing <locale>` | View missing keys for locale | `r18n.command.missing` |
| `/r18n missing <locale> <page>` | Paginated missing keys | `r18n.command.missing` |
| `/r18n export <format>` | Export translations | `r18n.command.export` |
| `/r18n metrics` | View translation metrics | `r18n.command.metrics` |
| `/r18n help` | Show help | `r18n.command.help` |

### Features

- Gradient UI: Modern MiniMessage gradients for visual appeal
- Interactive Locale Selection: Click to view missing keys
- Status Indicators: ✓ (complete), ⚠ (minor issues), ✗ (needs attention)
- Pagination: Navigate through missing keys
- Click-to-Copy: Copy translation keys to clipboard
- Hover Details: View key information on hover
- Console Support: All commands work from console with plain text output

### Registering the Command

```java
// In your plugin.yml
commands:
  r18n:
    description: R18n translation management
    usage: /<command> [reload|missing|export|metrics|help]
    permission: r18n.command

// In your onEnable()
PR18nCommand command = new PR18nCommand(this, r18nManager);
getCommand("r18n").setExecutor(command);
getCommand("r18n").setTabCompleter(command);
```

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `r18n.command` | Base command access | op |
| `r18n.command.help` | View help | op |
| `r18n.command.reload` | Reload translations | op |
| `r18n.command.missing` | View missing keys | op |
| `r18n.command.export` | Export translations | op |
| `r18n.command.metrics` | View metrics | op |

---

## Best Practices

1. **Use Full Locale Codes**: Use `en_US`, `de_DE`, `es_ES` format for proper regional support
2. **Organize Keys**: Use dot notation for namespacing (`welcome.message`, `error.permission`)
3. **Provide Fallbacks**: Always have translations in the default locale
4. **Use MiniMessage**: Leverage gradients and modern formatting
5. **Validate Keys**: Enable key validation during development
6. **Async Loading**: Use `initialize()` for non-blocking startup
7. **Clean Shutdown**: Call `shutdown()` in `onDisable()`
8. **Register Commands**: Register PR18nCommand for admin functionality
9. **Enable Caching**: Keep caching enabled for production (default)
10. **Use File Watcher**: Enable during development for faster iteration
11. **Monitor Metrics**: Enable metrics to identify missing or unused translations
12. **Persist Locales**: Use database storage for persistent player preferences
13. **Handle Missing Keys**: Configure a custom handler for better UX
14. **Support Plurals**: Use plural forms for grammatically correct translations

---

## Support

For issues and feature requests, please open an issue on the project repository.
