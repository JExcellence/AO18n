# JExTranslate

YAML-driven internationalization for Bukkit / Spigot / Paper / Folia plugins. MiniMessage native, legacy `&` codes alongside, per-player locale resolution, hot reload, Bedrock-aware sending, and a plural-form selector — packed into a `~160 KB` JAR with zero bundled runtime dependencies.

Originally built for the [JExSuite](https://github.com/JExcellence/JExSuite) plugin family, JExTranslate is consumable as a standalone library. It runs on Minecraft 1.8 through 1.21+ with feature detection at startup, so the same plugin JAR works across the whole Bukkit-API span without compile-time version flags.

```
plugins/MyPlugin/translations/
├── en_US.yml       <- default
├── de_DE.yml
└── fr_FR.yml
```

```yaml
# en_US.yml
prefix: "<dark_gray>[<gold>MyPlugin</gold>]</dark_gray> "

welcome:
  join: "<green>Welcome back, <bold>{player}</bold>!</green>"

balance:
  message: "Your balance: <gold>{balance} coins</gold>"
```

```java
r18n.msg("welcome.join")
    .with("player", player.getName())
    .prefix()
    .send(player);
// → [MyPlugin] Welcome back, **SaltyFeaRz**!
```

---

## Contents

- [Install](#install)
- [Quick start](#quick-start)
- [Translation files](#translation-files)
- [Sending messages](#sending-messages)
- [Locale resolution](#locale-resolution)
- [Plurals](#plurals)
- [Bedrock](#bedrock)
- [Hot reload](#hot-reload)
- [Configuration reference](#configuration-reference)
- [Admin command](#admin-command)
- [Migration from `I18n`](#migration-from-i18n)
- [Building from source](#building-from-source)
- [License & credits](#license--credits)

---

## Install

```kotlin
// build.gradle.kts
repositories {
    maven("https://maven.pkg.github.com/JExcellence/JExSuite") {
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.key").orNull
        }
    }
}

dependencies {
    compileOnly("de.jexcellence.translate:jextranslate:3.0.0")
}
```

`compileOnly` because JExTranslate is loaded at runtime by the server (typically via [JExDependency](https://github.com/JExcellence/JExSuite) or shaded into your plugin JAR — see [Building from source](#building-from-source)).

Requires **Java 21+** and Bukkit API 1.8 or newer.

---

## Quick start

### `onEnable`

```java
public final class MyPlugin extends JavaPlugin {

    private R18nManager r18n;

    @Override
    public void onEnable() {
        r18n = R18nManager.builder(this)
                .defaultLocale("en_US")
                .supportedLocales("en_US", "de_DE", "fr_FR")
                .enableKeyValidation(true)
                .enableFileWatcher(true)   // hot reload while server is running
                .build();

        r18n.initialize()
                .thenRun(() -> getLogger().info("Translations loaded"))
                .exceptionally(ex -> {
                    getLogger().log(Level.SEVERE, "Translation init failed", ex);
                    return null;
                });
    }

    @Override
    public void onDisable() {
        if (r18n != null) r18n.shutdown();
    }
}
```

### Send messages

```java
// Send to a player — locale resolved from Player.getLocale()
r18n.msg("welcome.join").with("player", player.getName()).send(player);

// Add the configured plugin prefix
r18n.msg("error.no_permission").prefix().send(player);

// Console (uses the default locale)
r18n.msg("startup.complete").console();

// Broadcast to every online player (each gets their own locale)
r18n.msg("server.restart").with("seconds", 30).broadcast();

// Force a specific locale
r18n.msg("admin.report").locale("en_US").send(adminPlayer);
```

### Get a `Component` instead of sending

```java
Component title       = r18n.msg("gui.shop_title").component(player);
List<Component> lore  = r18n.msg("item.lore").components(player);
String plain          = r18n.msg("mail.subject").plain(player);   // no formatting
```

---

## Translation files

Files live at `plugins/<YourPlugin>/translations/<locale>.yml` and are auto-extracted from the plugin JAR on first start. Players (or you) can edit them in place; with `enableFileWatcher(true)` the changes pick up without a restart.

### Key naming

Keys are dot-separated and map directly to YAML nesting. Use `[a-z0-9_.]` only.

```yaml
welcome:
  player:  "Hello, {player}!"
  server:  "You joined {server}."

error:
  no_permission:    "<red>You don't have permission.</red>"
  unknown_command:  "<red>Unknown command. Try /help.</red>"
```

→ `welcome.player`, `welcome.server`, `error.no_permission`, `error.unknown_command`.

### Values

A value is either a string or a list of strings. Lists become multi-line chat output or item lore.

```yaml
join_message: "<green>Welcome back, {player}!</green>"

help_menu:
  - "<gold>--- Help ---</gold>"
  - "<yellow>/spawn</yellow> <gray>- teleport to spawn</gray>"
  - "<yellow>/balance</yellow> <gray>- check your balance</gray>"
```

```java
r18n.msg("help_menu").send(player);                  // sends each line
List<Component> lore = r18n.msg("help_menu").components(player);
```

### Placeholders

Curly braces. Both `{name}` and `%name%` are recognised.

```yaml
balance.line: "Your balance: <gold>{balance} coins</gold>"
```

```java
r18n.msg("balance.line").with("balance", account.getBalance()).send(player);
```

Placeholder *values* are MiniMessage-escaped automatically — a player named `<red>Griefer</red>` cannot hijack your message into a red one.

### Formatting

[MiniMessage](https://docs.advntr.dev/minimessage/format.html) is the native format:

```yaml
fancy:
  click:    "<click:run_command:'/spawn'><aqua>[Click to teleport]</aqua></click>"
  gradient: "<gradient:red:gold>Server is restarting!</gradient>"
  rainbow:  "<rainbow>Have a colourful day!</rainbow>"
```

Legacy `&` codes also work and are converted to MiniMessage at parse time:

```yaml
old: "&aGreen &bAqua &cRed &lBold"
```

Disable with `.legacyColorSupport(false)` if you want strict MiniMessage only.

### Special key: `prefix`

Define `prefix` once at the top of each locale file. Calling `.prefix()` on a `MessageBuilder` prepends it to whatever you're sending.

```yaml
prefix: "<dark_gray>[<gold>MyPlugin</gold>]</dark_gray> "
```

```java
r18n.msg("error.no_permission").prefix().send(player);
// → [MyPlugin] You don't have permission.
```

---

## Sending messages

Every send / convert method lives on `MessageBuilder`, returned by `r18n.msg(key)`.

| Method | Purpose |
|---|---|
| `.with(name, value)` | Add a placeholder |
| `.locale(code)` | Force a specific locale (overrides player's locale) |
| `.prefix()` | Prepend the configured `prefix` value |
| `.count(name, n)` | Pluralised placeholder — drives the `.zero` / `.one` / `.other` selector |
| `.send(target)` | Send to a `Player`, `CommandSender`, or Adventure `Audience` |
| `.broadcast()` | Send to every online player (each in their own locale) |
| `.console()` / `.console(locale)` | Send to the console |
| `.sendBedrock(player)` | Force Bedrock-safe send (strip click/hover/fonts, downgrade hex) |
| `.component(player)` | Returns the rendered Adventure `Component` |
| `.components(player)` | Returns `List<Component>` for multi-line values |
| `.text(player)` | Returns the rendered string |
| `.texts(player)` | Returns `List<String>` for multi-line values |
| `.plain(player)` | Plain text — no formatting (useful for Bedrock forms) |
| `.toBedrockString(player)` | Bedrock-compatible legacy `§`-string |
| `.toBedrockStrings(player)` | Multi-line Bedrock-compatible strings |
| `.exists(player)` | `true` if the key resolves for the player's locale |

Verbose aliases are also available — `message(key)` for `msg(key)`, `placeholder(k, v)` for `with(k, v)`, `withPrefix()` for `prefix()`, `toComponent(p)` for `component(p)`, etc. Use whichever style fits your codebase.

---

## Locale resolution

Resolved per-message, in this order:

1. Explicit `.locale("...")` on the builder
2. `Player.getLocale()`, normalised and matched against the configured supported locales
3. The default locale

If a key is missing in the resolved locale, the lookup falls back to the default locale automatically. If it's missing there too, `onMissingKey` runs — by default that surfaces a bright `Missing: <key>` line so gaps are visible during development.

---

## Plurals

Append `.zero` / `.one` / `.two` / `.few` / `.many` / `.other` to a key:

```yaml
items:
  count:
    zero:  "You have no items."
    one:   "You have <gold>{count}</gold> item."
    other: "You have <gold>{count}</gold> items."
```

```java
r18n.msg("items.count").count("count", inventory.size()).send(player);
```

The selector follows ICU CLDR rules per locale — Russian, Polish, Arabic, etc. all get correct forms. If a specific form isn't defined, it falls through to `.other`, then to the base key.

---

## Bedrock

Geyser / Floodgate Bedrock players are auto-detected. When you `send(player)` a message that uses click events, hover events, or custom fonts, those features are automatically stripped for Bedrock players while colours and formatting are preserved. Java players still see the full message.

You can also bypass detection or get a Bedrock-formatted string explicitly:

```java
r18n.msg("welcome").with("player", name).sendBedrock(player);    // force Bedrock formatting
String bedrockText      = r18n.msg("item.name").toBedrockString(player);
List<String> bedrockLore = r18n.msg("item.lore").toBedrockStrings(player);

if (r18n.msg("any.key").isBedrockPlayer(player)) {
    // Bedrock-only branch
}
```

Two knobs control the conversion:

```java
new R18nConfiguration.Builder()
    .hexColorFallback(HexColorFallback.NEAREST_LEGACY)   // hex → nearest &-code
    .bedrockFormatMode(BedrockFormatMode.CONSERVATIVE)   // strip click/hover/fonts
    .build();
```

---

## Hot reload

```java
R18nManager.builder(plugin).enableFileWatcher(true).build();
```

A daemon thread watches `plugins/<YourPlugin>/translations/` for create / modify / delete events and reloads the parsed translations in-memory. Or trigger reloads from code:

```java
r18n.reload().thenRun(() -> sender.sendMessage("Translations reloaded"));
```

Invalid YAML / JSON during a reload is logged and the previous in-memory state is preserved — broken edits don't take down the plugin.

---

## Configuration reference

### `R18nManager.Builder` — common case

| Method | Default | What it does |
|---|---|---|
| `.defaultLocale(String)` | `en_US` | Fallback locale when the player's locale isn't loaded |
| `.supportedLocales(String...)` | `{ en_US }` | Locales to load. Empty set or `.autoDetectLocales()` loads every file in the dir |
| `.autoDetectLocales()` | — | Load whatever's in the translation directory, no whitelist |
| `.translationDirectory(String)` | `translations` | Folder name inside the plugin's data folder |
| `.enableKeyValidation(boolean)` | `true` | Log keys missing from non-default locales on startup |
| `.enablePlaceholderAPI(boolean)` | `false` | Resolve `%placeholder%` via PlaceholderAPI before MiniMessage parsing |
| `.enableFileWatcher(boolean)` | `false` | Hot reload on file change |
| `.configuration(R18nConfiguration)` | — | Pass a hand-built configuration for fine control |

### `R18nConfiguration.Builder` — fine control

For caching, missing-key handling, Bedrock formatting, metrics, etc. Pass to `.configuration(...)` on the manager builder.

| Method | Default | Description |
|---|---|---|
| `.legacyColorSupport(boolean)` | `true` | Honour `&a`-style colour codes alongside MiniMessage |
| `.debugMode(boolean)` | `false` | Verbose logging through the load + lookup pipeline |
| `.enableCache(boolean)` | `true` | Cache parsed `Component` objects |
| `.cacheMaxSize(int)` | `1000` | Cache capacity |
| `.cacheExpireMinutes(int)` | `30` | Cache TTL |
| `.enableMetrics(boolean)` | `false` | Track translation usage — surfaces via `/r18n metrics` |
| `.bedrockSupportEnabled(boolean)` | `true` | Auto-detect Bedrock players via Geyser / Floodgate |
| `.hexColorFallback(HexColorFallback)` | `NEAREST_LEGACY` | How `<#aabbcc>` hex colours are downgraded for Bedrock |
| `.bedrockFormatMode(BedrockFormatMode)` | `CONSERVATIVE` | What to strip for Bedrock (click / hover / fonts / etc.) |
| `.onMissingKey((key, locale, placeholders) -> String)` | Renders `Missing: <key>` | Customise behaviour for unresolvable keys |

```java
var config = new R18nConfiguration.Builder()
        .defaultLocale("en_US")
        .supportedLocales("en_US", "de_DE")
        .cacheMaxSize(2000)
        .cacheExpireMinutes(60)
        .onMissingKey((key, locale, placeholders) ->
                "<red>[Missing: " + key + "]</red>")
        .build();

var r18n = R18nManager.builder(plugin).configuration(config).build();
```

---

## Admin command

A built-in `/r18n` command with `reload`, `missing`, `export`, and `metrics` subcommands ships with the library. To register it:

```yaml
# plugin.yml
commands:
  r18n:
    description: R18n translation management
    usage: /r18n [reload|missing|export|metrics]
    permission: r18n.admin
```

```java
r18n.registerCommand();              // /r18n
r18n.registerCommand("translate");   // custom alias
```

| Subcommand | Effect |
|---|---|
| `/r18n reload` | Reload all translation files |
| `/r18n missing <locale>` | List keys present in default locale but missing from `<locale>` |
| `/r18n export <csv\|json\|yaml>` | Export every loaded translation to a file in the plugin data folder |
| `/r18n metrics` | Cache hit rate + per-key usage counts (requires `enableMetrics(true)`) |

Programmatic export:

```java
r18n.exportTranslations(
    Path.of("plugins/MyPlugin/translations-export.json"),
    TranslationExportService.ExportFormat.JSON
);
```

---

## Migration from `I18n`

The legacy `I18n` / `I18n.Builder` API is deprecated since 3.0.0 and slated for removal. The replacement is `r18n.msg(key)` / `MessageBuilder`.

| Old | New |
|---|---|
| `new I18n.Builder("k", player).build().sendMessage()` | `r18n.msg("k").send(player)` |
| `…withPlaceholder("p", v)…` | `…with("p", v)…` |
| `…includePrefix()…` | `…prefix()…` |
| `…build().component()` | `r18n.msg("k").component(player)` |
| `new I18n.Builder("k").build().sendMessage()` (console) | `r18n.msg("k").console()` |

Differences worth knowing:
- No `.build()` step — `MessageBuilder` methods send / convert directly
- The player goes to `send(player)` / `component(player)`, not the constructor
- Locale override (`.locale("…")`), plural support (`.count(…)`), and Bedrock methods only exist on the new API

---

## Building from source

JExTranslate is part of the JExSuite monorepo:

```bash
git clone https://github.com/JExcellence/JExSuite.git
cd JExSuite
./gradlew :JExTranslate:build
./gradlew :JExTranslate:publishToMavenLocal
```

The built artifact lands at `JExTranslate/build/libs/jextranslate-3.0.0.jar`.

To consume from your own plugin's build script while developing:

```kotlin
// build.gradle.kts
repositories { mavenLocal() }
dependencies { compileOnly("de.jexcellence.translate:jextranslate:3.0.0") }
```

If you want to ship JExTranslate inside your plugin JAR rather than rely on JExDependency, swap `compileOnly` for `implementation` and add a Shadow plugin relocation so the `de.jexcellence.jextranslate` package doesn't collide with other plugins doing the same:

```kotlin
import com.gradleup.shadow.tasks.ShadowJar

tasks.named<ShadowJar>("shadowJar") {
    relocate("de.jexcellence.jextranslate", "myplugin.shaded.jextranslate")
}
```

---

## License & credits

Author: **JExcellence** — https://jexcellence.de
Adventure / MiniMessage: https://docs.advntr.dev
Bedrock detection: Geyser / Floodgate (optional soft-dep)
Cache: Caffeine

Part of the [JExSuite](https://github.com/JExcellence/JExSuite) plugin ecosystem.
