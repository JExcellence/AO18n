# JExTranslate

A modern, server-version-aware internationalisation (i18n) library for Spigot / Bukkit / Paper / Folia Minecraft plugins. Supports Minecraft 1.8 through the latest versions with automatic feature detection, full MiniMessage formatting, legacy `&` colour codes, Bedrock Edition compatibility, and hot-reload.

---

## Table of Contents

1. [Installation](#installation)
2. [Quick Start](#quick-start)
3. [Translation File Format](#translation-file-format)
   - [File naming](#file-naming)
   - [YAML structure and key naming](#yaml-structure-and-key-naming)
   - [Values: strings and lists](#values-strings-and-lists)
   - [Placeholders](#placeholders)
   - [MiniMessage formatting](#minimessage-formatting)
   - [Legacy colour codes](#legacy-colour-codes)
   - [Special key: prefix](#special-key-prefix)
   - [Plural forms](#plural-forms)
4. [Configuration Reference](#configuration-reference)
   - [R18nManager.Builder](#r18nmanagerbuilder)
   - [R18nConfiguration.Builder](#r18nconfigurationbuilder)
5. [Sending Messages](#sending-messages)
6. [Locale Resolution](#locale-resolution)
7. [Hot Reload](#hot-reload)
8. [Bedrock Edition Support](#bedrock-edition-support)
9. [Key Validation and Missing Keys](#key-validation-and-missing-keys)
10. [Cache and Metrics Monitoring](#cache-and-metrics-monitoring)
11. [Export](#export)
12. [Admin Command](#admin-command)
13. [Migration from I18n to MessageBuilder](#migration-from-i18n-to-messagebuilder)
14. [Complete YAML Example](#complete-yaml-example)

---

## Installation

Add the dependency to your plugin's `build.gradle.kts`:

```kotlin
dependencies {
    compileOnly("de.jexcellence.translate:jextranslate:3.0.0")
}
```

> JExTranslate is a `compileOnly` dependency — it is provided at runtime by the server via JExDependency. The resulting JAR is ~160 KB with zero bundled runtime dependencies.

---

## Quick Start

### 1. Initialise R18nManager in `onEnable`

```java
private R18nManager r18n;

@Override
public void onEnable() {
    r18n = R18nManager.builder(this)
        .defaultLocale("en_US")
        .supportedLocales("en_US", "de_DE", "fr_FR")
        .enableKeyValidation(true)
        .build();

    r18n.initialize().thenRun(() -> {
        getLogger().info("Translations loaded!");
    }).exceptionally(ex -> {
        getLogger().severe("Translation loading failed: " + ex.getMessage());
        return null;
    });
}
```

### 2. Shut down in `onDisable`

```java
@Override
public void onDisable() {
    if (r18n != null) {
        r18n.shutdown();
    }
}
```

### 3. Send a message

```java
// Concise form (recommended)
r18n.msg("welcome.player").with("player", player.getName()).send(player);

// Equivalent verbose form
r18n.message("welcome.player")
    .placeholder("player", player.getName())
    .send(player);
```

---

## Translation File Format

### File naming

Place translation files inside your plugin's resources at:

```
src/main/resources/translations/<locale>.yml
src/main/resources/translations/<locale>.json   # JSON also supported
```

| Locale code | Language |
|-------------|----------|
| `en_US`     | English (US) |
| `en_GB`     | English (UK) |
| `de_DE`     | German |
| `fr_FR`     | French |
| `es_ES`     | Spanish |
| `pt_BR`     | Portuguese (Brazil) |
| `ru_RU`     | Russian |
| `zh_CN`     | Chinese (Simplified) |
| `ja_JP`     | Japanese |
| `ko_KR`     | Korean |

Any locale code is supported — add the file and it is loaded automatically.

Both YAML (`.yml` / `.yaml`) and JSON (`.json`) files are supported. If both exist for the same locale, **YAML takes precedence** and the JSON file is skipped. YAML is recommended for human-edited files; JSON is useful for machine-generated translations or CI pipelines.

On first server start the files are extracted from the plugin JAR into `plugins/<YourPlugin>/translations/`. Players may then edit the files and reload without restarting.

---

### YAML structure and key naming

Keys are **dot-separated** and map directly to the nested YAML structure.

```yaml
welcome:
  player: "Welcome, {player}!"
  server: "You joined {server}."

error:
  no_permission: "<red>You do not have permission.</red>"
  unknown_command: "<red>Unknown command. Use /help.</red>"
```

The keys above resolve as:
- `welcome.player`
- `welcome.server`
- `error.no_permission`
- `error.unknown_command`

**Rules for key names:**
- Use lowercase letters, digits, underscores, and dots only: `[a-z0-9_.]`
- Each segment separated by `.` corresponds to one YAML nesting level
- Do **not** start or end a key with a dot
- Do **not** use consecutive dots

---

### Values: strings and lists

A translation value may be a single string or a YAML list (for multi-line messages, e.g. lore):

```yaml
# Single-line message
join_message: "<green>Welcome back, {player}!</green>"

# Multi-line message (displayed as multiple chat lines or item lore)
help_menu:
  - "<gold>--- Help ---</gold>"
  - "<yellow>/spawn</yellow> <gray>- Teleport to spawn</gray>"
  - "<yellow>/home</yellow> <gray>- Teleport to your home</gray>"
  - "<yellow>/balance</yellow> <gray>- Check your balance</gray>"
```

Retrieve multi-line messages:

```java
List<Component> lore = r18n.msg("help_menu").components(player);
List<String> loreStrings = r18n.msg("help_menu").texts(player);
```

---

### Placeholders

Placeholders are surrounded by curly braces: `{name}`.

```yaml
welcome:
  player: "Hello, {player}! You have {coins} coins."
```

```java
r18n.msg("welcome.player")
    .with("player", player.getName())
    .with("coins", account.getBalance())
    .send(player);
```

Both `{name}` and `%name%` syntax are recognised in translation values and replaced identically.

**Important:** Placeholder *values* are automatically escaped so they cannot inject MiniMessage tags. A player named `<red>Griefer</red>` will appear as literal text, not red.

---

### MiniMessage formatting

JExTranslate uses [Adventure MiniMessage](https://docs.advntr.dev/minimessage/format.html) as its native format. MiniMessage tags are fully supported:

```yaml
messages:
  welcome:     "<green>Welcome, <bold>{player}</bold>!</green>"
  warning:     "<yellow><b>Warning:</b></yellow> <gray>Your balance is low.</gray>"
  clickable:   "<click:run_command:'/spawn'><aqua>[Teleport to Spawn]</aqua></click>"
  gradient:    "<gradient:red:gold>Server is restarting!</gradient>"
  rainbow:     "<rainbow>Have a colourful day!</rainbow>"
```

Full MiniMessage tag reference: https://docs.advntr.dev/minimessage/format.html

---

### Legacy colour codes

If `legacyColorSupport` is enabled (default: `true`), the `&` character is treated as a colour code:

```yaml
messages:
  old_style: "&aGreen &bAqua &cRed &lBold"
```

Legacy codes are automatically converted to MiniMessage before parsing, so they work alongside MiniMessage tags.

| Code | Effect |
|------|--------|
| `&0`-`&9`, `&a`-`&f` | Colours |
| `&l` | Bold |
| `&o` | Italic |
| `&n` | Underline |
| `&m` | Strikethrough |
| `&k` | Obfuscated |
| `&r` | Reset |

---

### Special key: `prefix`

The `prefix` key is reserved. When `.prefix()` (or `.withPrefix()`) is called on a `MessageBuilder`, the resolved `prefix` value is prepended to the message.

```yaml
prefix: "<dark_gray>[<gold>MyPlugin</gold>]</dark_gray> "

errors:
  no_permission: "<red>You lack permission.</red>"
```

```java
r18n.msg("errors.no_permission").prefix().send(player);
// Displays: [MyPlugin] You lack permission.
```

---

### Plural forms

Append `.zero`, `.one`, `.two`, `.few`, `.many`, or `.other` as sub-keys to provide locale-aware plural forms.

```yaml
items:
  count:
    one:   "You have {count} item."
    other: "You have {count} items."
```

```java
r18n.msg("items.count")
    .count("count", itemList.size())
    .send(player);
```

The library selects the correct form based on the player's locale using ICU-compatible plural rules. If the specific form is not found, it falls back to `.other`, then to the base key.

---

## Configuration Reference

### R18nManager.Builder

The simplest way to configure JExTranslate. Covers the most common options:

| Builder method | Default | Description |
|---|---|---|
| `.defaultLocale(String)` | `"en_US"` | Fallback locale when a key is missing for the player's locale |
| `.supportedLocales(String...)` | `{"en_US"}` | Locales to load; empty set or `.autoDetectLocales()` loads all files found |
| `.autoDetectLocales()` | - | Load every translation file present in the directory |
| `.translationDirectory(String)` | `"translations"` | Sub-directory inside the plugin data folder |
| `.enableKeyValidation(boolean)` | `true` | Warn on startup about keys missing from non-default locales |
| `.enablePlaceholderAPI(boolean)` | `false` | Integrate with PlaceholderAPI (requires PAPI installed) |
| `.enableFileWatcher(boolean)` | `false` | Hot-reload translation files on change |
| `.configuration(R18nConfiguration)` | - | Supply a fully-built configuration object for fine-grained control |

### R18nConfiguration.Builder

For fine-grained control over caching, Bedrock formatting, metrics, and missing key handling, use `R18nConfiguration.Builder` directly:

```java
R18nConfiguration config = new R18nConfiguration.Builder()
    .defaultLocale("en_US")
    .supportedLocales("en_US", "de_DE")
    .legacyColorSupport(true)
    .enableCache(true)
    .cacheMaxSize(2000)
    .cacheExpireMinutes(60)
    .enableMetrics(true)
    .bedrockSupportEnabled(true)
    .hexColorFallback(HexColorFallback.NEAREST_LEGACY)
    .bedrockFormatMode(BedrockFormatMode.CONSERVATIVE)
    .onMissingKey((key, locale, placeholders) ->
        "<red>Missing translation: " + key + "</red>")
    .build();

R18nManager r18n = R18nManager.builder(plugin)
    .configuration(config)
    .build();
```

| Builder method | Default | Description |
|---|---|---|
| `.defaultLocale(String)` | `"en_US"` | Fallback locale |
| `.supportedLocales(String...)` | `{"en_US"}` | Set of locales to load |
| `.autoDetectLocales()` | - | Load all files found (ignores supported set) |
| `.translationDirectory(String)` | `"translations"` | Directory name for translation files |
| `.keyValidationEnabled(boolean)` | `true` | Validate keys across locales on startup |
| `.placeholderAPIEnabled(boolean)` | `false` | PlaceholderAPI integration |
| `.legacyColorSupport(boolean)` | `true` | Support `&` colour codes alongside MiniMessage |
| `.debugMode(boolean)` | `false` | Enable debug logging |
| `.enableCache(boolean)` | `true` | Cache parsed MiniMessage Components (Caffeine) |
| `.cacheMaxSize(int)` | `1000` | Maximum cache entries |
| `.cacheExpireMinutes(int)` | `30` | Cache TTL in minutes |
| `.enableFileWatcher(boolean)` | `false` | Watch files for hot reload |
| `.enableMetrics(boolean)` | `false` | Collect translation usage metrics |
| `.onMissingKey(MissingKeyHandler)` | Shows `Missing: <key>` | Custom handler for missing keys |
| `.bedrockSupportEnabled(boolean)` | `true` | Auto-detect Geyser/Floodgate Bedrock players |
| `.hexColorFallback(HexColorFallback)` | `NEAREST_LEGACY` | How hex colours are downgraded for Bedrock |
| `.bedrockFormatMode(BedrockFormatMode)` | `CONSERVATIVE` | What to strip for Bedrock (click/hover/fonts) |

Existing configurations can also be modified immutably using `with*` methods (e.g., `config.withCacheMaxSize(2000)`), which internally delegate to the Builder.

---

## Sending Messages

`MessageBuilder` (returned by `r18n.msg(key)` or `r18n.message(key)`) provides all sending and conversion methods. All builder methods (`with`, `prefix`, `locale`, `count`) return the builder for chaining.

```java
// -- Send ------------------------------------------------------------------
r18n.msg("welcome.player").with("player", name).send(player);             // to Player
r18n.msg("reload.success").send(sender);                                   // to CommandSender
r18n.msg("announcement").send(audience);                                   // to Adventure Audience
r18n.msg("server.restart").with("time", "5 minutes").broadcast();          // to all online players

// -- Console (with locale control) -----------------------------------------
r18n.msg("startup.complete").console();                                    // default locale
r18n.msg("startup.complete").console("de_DE");                             // explicit locale

// -- Bedrock ---------------------------------------------------------------
r18n.msg("welcome").with("player", name).sendBedrock(player);             // force Bedrock-safe send
String bedrockText   = r18n.msg("item.name").toBedrockString(player);     // Bedrock-safe legacy string
List<String> bdLines = r18n.msg("item.lore").toBedrockStrings(player);    // multi-line Bedrock strings
String plainText     = r18n.msg("form.title").plain(player);              // plain text (no formatting)
boolean isBedrock    = r18n.msg("any").isBedrockPlayer(player);           // check detection

// -- Get as Component / String ---------------------------------------------
Component title      = r18n.msg("gui.shop_title").component(player);      // single Component
List<Component> lore = r18n.msg("item.lore").components(player);          // multi-line Components
String text          = r18n.msg("balance").text(player);                  // formatted string
List<String> lines   = r18n.msg("help.commands").texts(player);           // multi-line strings
String plain         = r18n.msg("mail.subject").plain(player);            // plain text, no formatting

// -- Other -----------------------------------------------------------------
r18n.msg("admin.report").locale("en_US").send(adminPlayer);               // force locale
r18n.msg("greeting").prefix().send(player);                               // prepend configured prefix
if (r18n.msg("optional.feature").exists(player)) { ... }                  // check key existence
```

### Method aliases (concise vs verbose)

| Concise | Verbose equivalent | Returns |
|---|---|---|
| `msg(key)` | `message(key)` | `MessageBuilder` |
| `.with(k, v)` | `.placeholder(k, v)` | `MessageBuilder` |
| `.prefix()` | `.withPrefix()` | `MessageBuilder` |
| `.component(player)` | `.toComponent(player)` | `Component` |
| `.components(player)` | `.toComponents(player)` | `List<Component>` |
| `.text(player)` | `.toString(player)` | `String` |
| `.texts(player)` | `.toStrings(player)` | `List<String>` |
| `.plain(player)` | `.toPlainString(player)` | `String` |

Both forms are fully supported. Use whichever style you prefer.

---

## Locale Resolution

The locale for each message is determined in this order:

1. Explicitly set via `.locale("de_DE")` on the builder
2. Player's client locale from `Player.getLocale()`, normalised and matched against supported locales
3. Default locale from configuration

If a key is not found for the resolved locale, the system falls back to the default locale automatically.

---

## Hot Reload

Enable the file watcher to auto-reload translations when files change on disk:

```java
R18nManager.builder(plugin)
    .enableFileWatcher(true)
    .build();
```

Or reload programmatically (e.g., from a `/reload` command):

```java
r18n.reload().thenRun(() -> sender.sendMessage("Translations reloaded!"));
```

The file watcher runs on a daemon thread and monitors the translation directory for file creation, modification, and deletion events.

---

## Bedrock Edition Support

JExTranslate automatically detects Geyser/Floodgate Bedrock players via the `BedrockDetectionCache`. When `send(player)` is called and the player is detected as Bedrock, unsupported features (click events, hover events, custom fonts) are stripped automatically while colours and text formatting are preserved.

You can also force Bedrock-safe sending or retrieve Bedrock-compatible strings explicitly:

```java
// Auto-detected (transparent -- send() checks internally)
r18n.msg("welcome").with("player", name).send(player);

// Forced Bedrock output (bypass detection, always strip unsupported features)
r18n.msg("welcome").with("player", name).sendBedrock(player);

// Get a Bedrock-compatible legacy string (for custom UI / forms)
String bedrockText = r18n.msg("item.name").toBedrockString(player);

// Get multiple Bedrock-compatible strings (for lore / multi-line)
List<String> bedrockLore = r18n.msg("item.lore").toBedrockStrings(player);

// Get plain text with all formatting removed (for Bedrock forms)
String plainTitle = r18n.msg("form.title").plain(player);

// Check detection
if (r18n.msg("any").isBedrockPlayer(player)) {
    // Bedrock-specific logic
}
```

Configuration options:

```java
new R18nConfiguration.Builder()
    .bedrockSupportEnabled(true)                           // enable auto-detection (default: true)
    .hexColorFallback(HexColorFallback.NEAREST_LEGACY)     // downgrade hex to nearest &-code
    .bedrockFormatMode(BedrockFormatMode.CONSERVATIVE)     // strip click/hover/fonts
    .build();
```

---

## Key Validation and Missing Keys

When `enableKeyValidation(true)` is set, R18nManager logs any keys that exist in the default locale but are absent from other loaded locales. This makes it easy to spot translation gaps after adding new features.

Customise what happens when a key is not found at all:

```java
new R18nConfiguration.Builder()
    .onMissingKey((key, locale, placeholders) -> {
        // Return a fallback message, or null to send nothing
        return "<red>[Missing: " + key + "]</red>";
    })
    .build();
```

The default handler returns `<gold>Missing: <red>{key}</red></gold>` so missing keys are visible during development.

---

## Cache and Metrics Monitoring

JExTranslate uses [Caffeine](https://github.com/ben-manes/caffeine) to cache parsed MiniMessage `Component` objects, avoiding repeated parsing for frequently used keys.

**Cache configuration:**

```java
new R18nConfiguration.Builder()
    .enableCache(true)       // default: true
    .cacheMaxSize(2000)      // default: 1000
    .cacheExpireMinutes(60)  // default: 30
    .build();
```

**Reading cache stats programmatically:**

```java
var stats = r18n.getCacheStats();
if (stats != null) {
    double hitRate = stats.hitRate();
    long evictions = stats.evictionCount();
}
```

**Translation metrics** (when `enableMetrics(true)` is set):

```java
var metrics = r18n.getMetrics();
if (metrics != null) {
    // Access translation usage statistics
}
```

Both cache stats and metrics are also available via the `/r18n metrics` admin command.

---

## Export

Export all loaded translations to CSV, JSON, or YAML for external tools, review, or CI pipelines:

```java
// Programmatic export
r18n.exportTranslations(
    Path.of("translations-export.json"),
    TranslationExportService.ExportFormat.JSON
);
```

Supported formats:

| Format | Description |
|---|---|
| `CSV` | Key, locale, value columns — easy to import into spreadsheets |
| `JSON` | Flat key-value structure per locale — for external translation tools |
| `YAML` | YAML format — for manual review or re-import |

Export is also available via the admin command: `/r18n export <csv|json|yaml>`.

---

## Admin Command

Register the built-in `/r18n` management command in your `plugin.yml`:

```yaml
commands:
  r18n:
    description: R18n translation management
    usage: /r18n [reload|missing|export|metrics]
    permission: r18n.admin
```

Then call:

```java
r18n.registerCommand(); // registers as "/r18n"
// or
r18n.registerCommand("translate"); // custom command name
```

Subcommands:

| Subcommand | Description |
|---|---|
| `/r18n reload` | Reload all translation files |
| `/r18n missing <locale>` | List keys missing for a locale |
| `/r18n export <csv\|json\|yaml>` | Export all translations |
| `/r18n metrics` | Show cache hit rate and usage statistics |

---

## Migration from I18n to MessageBuilder

The legacy `I18n` class (and its inner `Builder`) are **deprecated since v3.0.0** and will be removed in a future version. Migrate to `MessageBuilder` via `r18n.msg()`:

| Before (I18n) | After (MessageBuilder) |
|---|---|
| `new I18n.Builder("key", player).build().sendMessage()` | `r18n.msg("key").send(player)` |
| `new I18n.Builder("key", player).withPlaceholder("k", v).build().sendMessage()` | `r18n.msg("key").with("k", v).send(player)` |
| `new I18n.Builder("key", player).includePrefix().build().sendMessage()` | `r18n.msg("key").prefix().send(player)` |
| `new I18n.Builder("key", player).build().component()` | `r18n.msg("key").component(player)` |
| `new I18n.Builder("key", player).build().children()` | `r18n.msg("key").components(player)` |
| `new I18n.Builder("key").build().sendMessage()` | `r18n.msg("key").console()` |

**Key differences:**
- No `.build()` step needed — `MessageBuilder` methods send or return values directly
- Player is passed to `send()` / `component()` instead of the constructor
- Console messages use `.console()` instead of omitting the player from the constructor
- Locale override, plural support, and Bedrock sending are only available in `MessageBuilder`

---

## Complete YAML Example

`src/main/resources/translations/en_US.yml`

```yaml
# Plugin prefix -- prepended when .prefix() is called
prefix: "<dark_gray>[<gold>MyPlugin</gold>]</dark_gray> "

# --- General ----------------------------------------------------------------
general:
  no_permission:  "<red>You do not have permission to do that.</red>"
  player_only:    "<red>This command can only be used by players.</red>"
  unknown_error:  "<red>An unexpected error occurred. Please contact an administrator.</red>"

# --- Welcome -----------------------------------------------------------------
welcome:
  join:    "<green>Welcome back, <bold>{player}</bold>!</green>"
  first:   "<green>Welcome to the server, <bold>{player}</bold>! Type <yellow>/help</yellow> to get started.</green>"
  leave:   "<gray>{player} has left the server.</gray>"

# --- Economy -----------------------------------------------------------------
economy:
  balance:       "Your balance: <gold>{balance} coins</gold>"
  insufficient:  "<red>Insufficient funds. You need <gold>{needed}</gold> but have <gold>{balance}</gold>.</red>"
  received:      "<green>You received <gold>{amount} coins</gold> from <white>{sender}</white>.</green>"
  sent:          "<green>You sent <gold>{amount} coins</gold> to <white>{target}</white>.</green>"

# --- Items (with plurals) ----------------------------------------------------
items:
  count:
    zero:  "You have no items."
    one:   "You have <gold>{count}</gold> item."
    other: "You have <gold>{count}</gold> items."

# --- Help menu (multi-line) --------------------------------------------------
help:
  header:
    - "<gold>======= <white>Help</white> =======</gold>"
    - "<gray>Available commands:</gray>"
  commands:
    - "<yellow>/balance</yellow> <dark_gray>-</dark_gray> <gray>Check your balance</gray>"
    - "<yellow>/pay <player> <amount></yellow> <dark_gray>-</dark_gray> <gray>Send coins</gray>"
    - "<yellow>/shop</yellow> <dark_gray>-</dark_gray> <gray>Open the shop</gray>"
  footer:
    - "<gold>====================</gold>"

# --- GUI titles ---------------------------------------------------------------
gui:
  shop_title:      "<dark_gray>Shop</dark_gray>"
  inventory_title: "<dark_gray>{player}'s Inventory</dark_gray>"

# --- Reload (used by admin command) -------------------------------------------
r18n:
  reload:
    success: "<green>Translations reloaded successfully!</green>"
    failure: "<red>Failed to reload translations. Check console for errors.</red>"
  missing:
    none:   "<green>No missing translation keys found!</green>"
    header: "<yellow>Found {count} missing keys for locale '{locale}':</yellow>"
    key:    "<red> - {key}</red>"
  key:
    missing: "<gold>Missing key: <red>{key}</red></gold>"
```

`src/main/resources/translations/de_DE.yml`

```yaml
prefix: "<dark_gray>[<gold>MyPlugin</gold>]</dark_gray> "

general:
  no_permission: "<red>Du hast keine Berechtigung dafur.</red>"
  player_only:   "<red>Dieser Befehl kann nur von Spielern verwendet werden.</red>"
  unknown_error: "<red>Ein unerwarteter Fehler ist aufgetreten.</red>"

welcome:
  join:  "<green>Willkommen zuruck, <bold>{player}</bold>!</green>"
  first: "<green>Willkommen auf dem Server, <bold>{player}</bold>! Gib <yellow>/help</yellow> ein, um loszulegen.</green>"
  leave: "<gray>{player} hat den Server verlassen.</gray>"

economy:
  balance:      "Dein Kontostand: <gold>{balance} Munzen</gold>"
  insufficient: "<red>Unzureichendes Guthaben. Du benotigst <gold>{needed}</gold>, hast aber nur <gold>{balance}</gold>.</red>"
  received:     "<green>Du hast <gold>{amount} Munzen</gold> von <white>{sender}</white> erhalten.</green>"
  sent:         "<green>Du hast <gold>{amount} Munzen</gold> an <white>{target}</white> gesendet.</green>"

items:
  count:
    zero:  "Du hast keine Gegenstande."
    one:   "Du hast <gold>{count}</gold> Gegenstand."
    other: "Du hast <gold>{count}</gold> Gegenstande."
```

---

## Notes for AI Translation Key Generation

When generating or extending translation files for JExTranslate, follow these rules:

1. **Use only dot-notation keys**: `section.subsection.key_name` — no spaces, no uppercase in key names.
2. **Placeholders use `{name}` syntax**: Always curly braces. The key name must match exactly what is passed via `.with("name", value)` or `.placeholder("name", value)` in Java.
3. **MiniMessage tags are native**: Use `<green>`, `<bold>`, `<gradient:red:gold>`, `<click:run_command:'/cmd'>`, etc. No `§` characters.
4. **Legacy `&` codes also work** if `legacyColorSupport` is enabled, but prefer MiniMessage.
5. **Quote values that start with YAML special characters**: Characters `:`, `{`, `[`, `&`, `*`, `#`, `?`, `|`, `-`, `<`, `>`, `=`, `!` all require quoting. Since most MiniMessage values start with `<`, **always quote values containing MiniMessage tags**. Use double quotes for strings with placeholders (`"<green>{player}</green>"`).
6. **Lists use YAML sequence syntax** (`- "line one"`), not joined strings.
7. **The `prefix` key is special** — define it at the top level of each locale file.
8. **Plural keys** are sub-keys ending in `.zero`, `.one`, `.two`, `.few`, `.many`, `.other`.
9. **Every locale file must have the same set of keys** as the default locale. Missing keys fall back to the default locale silently.
10. **File encoding must be UTF-8** without BOM.
11. **Both YAML and JSON are supported**: YAML is preferred for human-edited files. If both exist for a locale, YAML takes precedence.
