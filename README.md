# JExTranslate

**Modern, fluent i18n (internationalization) API for Minecraft plugins**

JExTranslate provides a comprehensive, type-safe translation system for Spigot/Bukkit/Paper plugins with full MiniMessage support, automatic locale detection, and a clean, fluent API.

## ✨ Features

- 🎨 **MiniMessage Support** - Full Kyori Adventure integration for rich text formatting
- 🌍 **Automatic Locale Detection** - Supports modern Paper API and legacy Bukkit methods
- 📦 **Flexible Storage** - YAML-based with support for custom repository implementations
- 🔒 **Type-Safe Keys** - Compile-time validation of translation keys
- 🔄 **Rich Placeholder System** - Support for text, numbers, dates, components, and custom formatters
- ⚡ **Async Operations** - Non-blocking translation loading and formatting
- 🎯 **Prefix Management** - Automatic prefix handling for consistent messaging
- 🖱️ **Interactive Components** - Click events, hover text, and more via MiniMessage
- 🔍 **Missing Key Tracking** - Track and report missing translations
- 🎭 **Multiple Output Formats** - Chat, action bar, titles, and plain text
- 🌐 **Fallback Chain** - Graceful degradation from specific locale → language → default
- 🧵 **Thread-Safe** - All operations are thread-safe and immutable

## 📋 Requirements

- **Java 17+**
- **Spigot/Bukkit/Paper 1.16+**
- **Kyori Adventure API 4.16.0+**

## 🚀 Quick Start

### 1. Add Dependency

**Gradle (Kotlin DSL):**
```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("de.jexcellence.translate:jextranslate:3.0.0")
}
```

**Gradle (Groovy):**
```groovy
repositories {
    mavenCentral()
    maven { url 'https://repo.papermc.io/repository/maven-public/' }
}

dependencies {
    implementation 'de.jexcellence.translate:jextranslate:3.0.0'
}
```

**Maven:**
```xml
<repositories>
  <repository>
    <id>papermc</id>
    <url>https://repo.papermc.io/repository/maven-public/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>de.jexcellence.translate</groupId>
    <artifactId>jextranslate</artifactId>
    <version>3.0.0</version>
  </dependency>
</dependencies>
```

### 2. Initialize in Your Plugin

```java
public final class MyPlugin extends JavaPlugin {
    
    @Override
    public void onEnable() {
        // Create translation repository
        Path translationsDir = getDataFolder().toPath().resolve("translations");
        TranslationRepository repository = YamlTranslationRepository.create(
            translationsDir,
            Locale.ENGLISH  // Default fallback locale
        );

        // Create message formatter (MiniMessage)
        MessageFormatter formatter = new MiniMessageFormatter();

        // Create locale resolver (auto-detects Paper/Bukkit)
        LocaleResolver localeResolver = LocaleResolverProvider.createAutoDetecting(Locale.ENGLISH);

        // Configure the translation service
        TranslationService.configure(new TranslationService.ServiceConfiguration(
            repository,
            formatter,
            localeResolver
        ));
        
        getLogger().info("JExTranslate initialized successfully!");
    }
}
```

### 3. Create Translation Files

Create YAML files in `plugins/YourPlugin/translations/`:

**translations/en.yml:**
```yaml
prefix: "<gold>[MyPlugin]</gold> "

welcome:
  message: "<green>Welcome, {player}!</green>"
  first-join: "<yellow>This is your first time joining!</yellow>"

coins:
  balance: "You have <gold>{amount}</gold> coins"
  insufficient: "<red>You need {required} coins but only have {current}</red>"

teleport:
  success: "<green>Teleported to {world} at {x}, {y}, {z}</green>"

combat:
  cooldown: "<red>Cooldown: {seconds}s</red>"

event:
  victory: "<gold><bold>VICTORY!</bold></gold> Team {team} wins!"
```

**translations/de.yml:**
```yaml
prefix: "<gold>[MeinPlugin]</gold> "

welcome:
  message: "<green>Willkommen, {player}!</green>"
  first-join: "<yellow>Dies ist dein erster Besuch!</yellow>"

coins:
  balance: "Du hast <gold>{amount}</gold> Münzen"
  insufficient: "<red>Du brauchst {required} Münzen, hast aber nur {current}</red>"

teleport:
  success: "<green>Teleportiert nach {world} bei {x}, {y}, {z}</green>"

combat:
  cooldown: "<red>Abklingzeit: {seconds}s</red>"

event:
  victory: "<gold><bold>SIEG!</bold></gold> Team {team} gewinnt!"
```

**translations/es.yml:**
```yaml
prefix: "<gold>[MiPlugin]</gold> "

welcome:
  message: "<green>¡Bienvenido, {player}!</green>"
  first-join: "<yellow>¡Esta es tu primera vez aquí!</yellow>"

coins:
  balance: "Tienes <gold>{amount}</gold> monedas"
  insufficient: "<red>Necesitas {required} monedas pero solo tienes {current}</red>"
```

### 4. Send Messages

```java
// Simple message
TranslationService.create(TranslationKey.of("welcome.message"), player)
    .with("player", player.getName())
    .send();

// With prefix
TranslationService.create(TranslationKey.of("coins.balance"), player)
    .withPrefix()
    .with("amount", playerCoins)
    .send();

// Multiple placeholders
TranslationService.create(TranslationKey.of("teleport.success"), player)
    .withPrefix()
    .with("world", worldName)
    .with("x", x)
    .with("y", y)
    .with("z", z)
    .send();
```

## 📖 Usage Examples

### Basic Translation

```java
TranslationService.create(TranslationKey.of("welcome.message"), player)
    .with("player", player.getName())
    .send();
```

### With Prefix

```java
TranslationService.create(TranslationKey.of("error.no-permission"), player)
    .withPrefix()
    .send();
```

### Custom Prefix

```java
TranslationService.create(TranslationKey.of("admin.broadcast"), player)
    .withPrefix(TranslationKey.of("prefix.admin"))
    .with("message", broadcastText)
    .send();
```

### Multiple Placeholders

```java
TranslationService.create(TranslationKey.of("coins.insufficient"), player)
    .withPrefix()
    .with("required", 1000)
    .with("current", playerCoins)
    .send();
```

### Formatted Numbers

```java
NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);

TranslationService.create(TranslationKey.of("shop.purchase"), player)
    .withPrefix()
    .with(Placeholder.of("item", itemName))
    .with(Placeholder.of("price", price, currencyFormat))
    .send();
```

### Rich Text Components

```java
Component itemComponent = Component.text(itemName)
    .color(NamedTextColor.GOLD)
    .hoverEvent(HoverEvent.showText(Component.text("Click to view")));

TranslationService.create(TranslationKey.of("inventory.item-received"), player)
    .with(Placeholder.of("item", itemComponent))
    .send();
```

### Action Bar Messages

```java
TranslationService.create(TranslationKey.of("combat.cooldown"), player)
    .with("seconds", remainingSeconds)
    .sendActionBar();
```

### Title Messages

```java
TranslationService.create(TranslationKey.of("event.victory"), player)
    .with("team", teamName)
    .sendTitle();
```

### Async Translation

```java
TranslationService.create(TranslationKey.of("complex.message"), player)
    .with("data", expensiveData)
    .buildAsync()
    .thenAccept(message -> message.sendTo(player))
    .exceptionally(throwable -> {
        plugin.getLogger().severe("Translation failed: " + throwable.getMessage());
        return null;
    });
```

### Multi-line Messages (Item Lore)

**translations/en.yml:**
```yaml
item:
  sword:
    lore:
      - "<gray>Damage: <red>{damage}</red></gray>"
      - "<gray>Durability: <green>{durability}</green></gray>"
      - ""
      - "<gold>Legendary Sword</gold>"
```

```java
TranslatedMessage loreMessage = TranslationService.create(
    TranslationKey.of("item.sword.lore"),
    player
)
    .with("damage", swordDamage)
    .with("durability", swordDurability)
    .build();

List<Component> loreLines = loreMessage.splitLines();
itemMeta.lore(loreLines);
```

### Interactive Components

**translations/en.yml:**
```yaml
gui:
  help:
    button: "<click:run_command:/help><hover:show_text:'Click for help'><green>[?]</green></hover></click>"
  
  link: "<click:open_url:https://example.com><blue><u>Visit our website</u></blue></click>"
  
  suggest: "<click:suggest_command:/msg {player} ><gray>Click to message {player}</gray></click>"

# Gradients and colors
welcome:
  fancy: "<gradient:gold:yellow>Welcome to the server!</gradient>"

# Decorations
announcement: "<bold><red>IMPORTANT:</red></bold> <white>{message}</white>"
```

### Explicit Locale

```java
// Send message in specific language regardless of player's locale
TranslationService.create(
    TranslationKey.of("announcement.global"),
    player,
    Locale.ENGLISH
)
    .with("message", announcementText)
    .send();
```

### Locale Management

```java
// Get player's current locale
LocaleResolver resolver = TranslationService.getConfiguration().localeResolver();
Optional<Locale> playerLocale = resolver.resolveLocale(player);

// Set player's preferred locale
resolver.setPlayerLocale(player, Locale.GERMAN);

// Clear player's preference (revert to auto-detection)
resolver.clearPlayerLocale(player);

// Clear locale cache for fresh detection
TranslationService.clearLocaleCache(player);
```

### Repository Management

```java
TranslationRepository repository = TranslationService.getConfiguration().repository();

// Reload translations
repository.reload().thenRun(() -> {
    plugin.getLogger().info("Translations reloaded successfully!");
});

// Get available locales
Set<Locale> locales = repository.getAvailableLocales();

// Get all translation keys
Set<TranslationKey> keys = repository.getAllAvailableKeys();

// Check if translation exists
boolean exists = repository.hasTranslation(
    TranslationKey.of("some.key"),
    Locale.ENGLISH
);
```

## 🏗️ Architecture

### Core Components

#### TranslationService
Main entry point for creating and sending translated messages. Provides fluent builder API.

**Key Methods:**
- `create(TranslationKey, Player)` - Create translation instance
- `withPrefix()` - Add prefix to message
- `with(String, Object)` - Add placeholder
- `send()` - Send to player's chat
- `sendActionBar()` - Send to action bar
- `sendTitle()` - Send as title
- `build()` - Build TranslatedMessage
- `buildAsync()` - Build asynchronously

#### TranslationKey
Type-safe, immutable translation key representation.

**Features:**
- Hierarchical dot-separated structure
- Validation on creation
- Parent/child relationships
- Utility methods for key manipulation

**Example:**
```java
TranslationKey key = TranslationKey.of("gui.buttons.next");
key.lastSegment();  // "next"
key.parent();       // TranslationKey.of("gui.buttons")
key.depth();        // 3
```

#### Placeholder
Sealed interface with type-safe implementations for different value types.

**Types:**
- `TextPlaceholder` - String values
- `NumberPlaceholder` - Numeric values with optional formatting
- `ComponentPlaceholder` - Rich text components
- `FormattedPlaceholder` - Custom formatted values

**Example:**
```java
Placeholder.of("player", playerName)
Placeholder.of("amount", 1000)
Placeholder.of("price", 99.99, currencyFormat)
Placeholder.of("item", component)
```

#### TranslatedMessage
Immutable representation of a formatted translation.

**Methods:**
- `asPlainText()` - Get as plain text
- `asLegacyText()` - Get as legacy formatted text
- `sendTo(Player)` - Send to player
- `sendActionBar(Player)` - Send as action bar
- `sendTitle(Player)` - Send as title
- `splitLines()` - Split into multiple lines
- `isEmpty()` - Check if empty
- `contains(String)` - Check if contains text

#### TranslationRepository
Interface for loading and managing translations.

**Implementations:**
- `YamlTranslationRepository` - YAML file-based storage

**Methods:**
- `getTranslation(TranslationKey, Locale)` - Get translation
- `getTranslationAsync(...)` - Get asynchronously
- `hasTranslation(...)` - Check existence
- `getAvailableLocales()` - Get supported locales
- `reload()` - Reload translations
- `addListener(RepositoryListener)` - Listen for events

#### MessageFormatter
Interface for formatting messages with placeholders.

**Implementations:**
- `MiniMessageFormatter` - MiniMessage-based formatting

**Methods:**
- `formatText(String, List<Placeholder>, Locale)` - Format as text
- `formatComponent(String, List<Placeholder>, Locale)` - Format as component
- `validateTemplate(String)` - Validate template syntax

#### LocaleResolver
Interface for resolving player locales.

**Factory:**
- `LocaleResolverProvider.createAutoDetecting(Locale)` - Auto-detects best API

**Methods:**
- `resolveLocale(Player)` - Get player's locale
- `setPlayerLocale(Player, Locale)` - Set preference
- `clearPlayerLocale(Player)` - Clear preference
- `getDefaultLocale()` - Get fallback locale

### Translation Fallback Chain

1. **Exact locale**: e.g., `en_US`
2. **Language only**: e.g., `en`
3. **Default locale**: Configured fallback
4. **Key name**: If all else fails, return the key itself

#### Fresh Translation Builders

Use `TranslationService.createFresh(...)` whenever you require a builder that ignores any cached placeholder state and
forces the service to resolve templates, prefixes, and locale metadata from the latest repository contents. This is
particularly useful in administrative tooling that assembles different placeholder combinations for the same key within
one tick. Standard `create(...)` calls remain efficient for single-use message dispatch, but `createFresh(...)`
guarantees a pristine builder every time it is invoked.

#### Repository Reloads and Cache Clearing

Reloading a `TranslationRepository` does not automatically invalidate cached locale resolutions. Always follow any
`repository.reload()` invocation with the appropriate cache purge:

- `TranslationService.clearLocaleCache()` to flush the entire locale cache after global reloads.
- `TranslationService.clearLocaleCache(Player)` to refresh a specific player's cached locale when toggling language
  preferences.

Operators should document this requirement in scripts and commands—forgetting to clear caches will leave players stuck
with stale translations until they reconnect or their cache entry expires.

#### MiniMessage Placeholder Syntax

MiniMessage placeholders must be wrapped in `{placeholder}` tokens. Common patterns include:

```yaml
coins:
  balance: "<gray>You have <gold>{amount}</gold> coins</gray>"

welcome:
  returning: "<green>Welcome back, {player}!</green>"

announcement:
  broadcast: "<bold><red>ALERT:</red></bold> <white>{message}</white>"
```

- Escape MiniMessage control characters inside placeholders with single quotes, for example
  `<hover:show_text:'Click to open {menu}'><green>{label}</green></hover>`.
- Keep placeholders immutable—each chained `.with(...)` call should operate on a fresh message instance as described in
  the builder section above.
- Validate complex templates through `MessageFormatter#validateTemplate(String)` so malformed MiniMessage markup never
  reaches players.

### YAML File Structure

**Simple keys:**
```yaml
simple-message: "Hello, world!"
```

**Nested keys:**
```yaml
gui:
  buttons:
    next: "Next Page"
    previous: "Previous Page"
```

**Multi-line (lists):**
```yaml
help:
  commands:
    - "Available commands:"
    - "/help - Show this help"
    - "/info - Show server info"
```

**With placeholders:**
```yaml
welcome: "Hello, {player}! You have {coins} coins."
```

**With MiniMessage:**
```yaml
error: "<red><bold>ERROR:</bold></red> <white>{message}</white>"
```

**Interactive:**
```yaml
button: "<click:run_command:/help><hover:show_text:'Click'><green>[?]</green></hover></click>"
```

## 🔧 Advanced Features

### Custom Date/Time Formatting

```java
DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

TranslationService.create(TranslationKey.of("event.scheduled"), player)
    .with(Placeholder.of("time", eventTime, dateFormatter))
    .send();
```

### Custom Object Formatting

```java
Placeholder.of("player", playerObject, p -> 
    p.getName() + " (Level " + p.getLevel() + ")"
);
```

### Hierarchical Keys

```java
TranslationKey base = TranslationKey.of("gui");
TranslationKey buttons = base.child("buttons");
TranslationKey nextButton = buttons.child("next");

// Equivalent to: TranslationKey.of("gui.buttons.next")
```

### Repository Listeners

```java
repository.addListener(new TranslationRepository.RepositoryListener() {
    @Override
    public void onReload(TranslationRepository repo) {
        plugin.getLogger().info("Translations reloaded");
    }

    @Override
    public void onError(TranslationRepository repo, Throwable error) {
        plugin.getLogger().severe("Translation error: " + error.getMessage());
    }
});
```

### Missing Key Tracking

```java
// Track missing translations
MissingKeyTracker tracker = new SimpleMissingKeyTracker();

// Get missing keys report
Map<Locale, Set<TranslationKey>> missingKeys = tracker.getMissingKeys();

// Clear tracking
tracker.clear();
```

## 🐛 Troubleshooting

### No translations loaded

**Problem:** Translations not found or loaded

**Solutions:**
- Verify YAML files exist in `plugins/YourPlugin/translations/`
- Check file names match locale codes (e.g., `en.yml`, `de.yml`)
- Ensure YAML syntax is valid
- Check plugin logs for errors during initialization

### Placeholders not replaced

**Problem:** Placeholders appear as `{key}` in messages

**Solutions:**
- Verify placeholder keys match exactly (case-sensitive)
- Ensure you're calling `.with("key", value)` before `.send()`
- Check YAML file has correct placeholder syntax: `{key}`

### MiniMessage not formatting

**Problem:** MiniMessage tags appear as plain text

**Solutions:**
- Verify you're using `MiniMessageFormatter`
- Check MiniMessage syntax is correct
- Ensure Adventure API is properly shaded/included
- Test with simple tags like `<red>text</red>`

### Wrong locale displayed

**Problem:** Player sees wrong language

**Solutions:**
- Clear locale cache: `TranslationService.clearLocaleCache(player)`
- Check player's client language settings
- Verify translation file exists for that locale
- Check fallback chain is working (specific → language → default)

### Performance issues

**Problem:** Translations are slow

**Solutions:**
- Use async operations: `.buildAsync()`
- Enable locale caching (enabled by default)
- Preload translations during plugin initialization
- Avoid creating new TranslationService instances repeatedly

## 📚 Best Practices

### 1. Use Hierarchical Keys

```java
// ✅ Good
TranslationKey.of("gui.shop.purchase.success")
TranslationKey.of("gui.shop.purchase.failure")

// ❌ Avoid
TranslationKey.of("shop_purchase_success")
```

### 2. Consistent Prefix Usage

```java
// Always use prefix for user-facing messages
TranslationService.create(key, player)
    .withPrefix()
    .send();

// Skip prefix for GUI text, action bars, titles
TranslationService.create(key, player)
    .sendActionBar();
```

### 3. Descriptive Placeholder Names

```yaml
# ✅ Good
message: "Welcome, {player_name}! You have {coin_count} coins."

# ❌ Avoid
message: "Welcome, {p}! You have {x} coins."
```

### 4. Organize Translation Files

```
translations/
  en.yml          # English (default)
  de.yml          # German
  es.yml          # Spanish
  fr.yml          # French
  ...
```

### 5. Handle Missing Translations

```java
Optional<String> translation = repository.getTranslation(key, locale);
if (translation.isEmpty()) {
    plugin.getLogger().warning("Missing translation: " + key + " for locale: " + locale);
}
```

### 6. Reload Command

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("reloadtranslations")) {
        TranslationRepository repository = TranslationService.getConfiguration().repository();
        repository.reload().thenRun(() -> {
            TranslationService.clearLocaleCache();
            sender.sendMessage("Translations reloaded successfully and caches cleared!");
        });
        return true;
    }
    return false;
}
```

### 7. Validate Templates

```java
MessageFormatter formatter = TranslationService.getConfiguration().formatter();
MessageFormatter.ValidationResult result = formatter.validateTemplate(template);

if (!result.isValid()) {
    for (String error : result.getErrors()) {
        plugin.getLogger().warning("Template validation error: " + error);
    }
}
```

### 8. Use Async for Expensive Operations

```java
// ✅ Good for complex formatting
TranslationService.create(key, player)
    .with("expensive", computeExpensiveValue())
    .buildAsync()
    .thenAccept(message -> message.sendTo(player));

// ✅ Good for simple messages
TranslationService.create(key, player)
    .with("simple", value)
    .send();
```

## 🔄 Migration from Old API (2.x → 3.0)

### API Changes

| Old (2.x) | New (3.0) |
|-----------|-----------|
| `I18n` | `TranslationService` |
| `MessageKey` | `TranslationKey` |
| `Message` | `TranslatedMessage` |
| `PlayerLocaleService` | `LocaleResolver` |
| `withPrefix()` | `withPrefix()` |
| `with()` | `with()` |
| `sendMessage()` | `send()` |

### Code Examples

**Before (2.x):**
```java
I18n.create(MessageKey.of("welcome.message"), player)
    .withPrefix()
    .with("name", player.getName())
    .send();
```

**After (3.0):**
```java
TranslationService.create(TranslationKey.of("welcome.message"), player)
    .withPrefix()
    .with("name", player.getName())
    .send();
```

### Breaking Changes

- Renamed all core classes for clarity
- Simplified method names
- Removed inline comments (self-documenting code)
- Updated to Java 17 minimum
- Changed package structure

### Migration Steps

1. Update dependency to 3.0.0
2. Find and replace class names
3. Update method calls
4. Recompile and test
5. Update translation files if needed

## 📦 Building from Source

```bash
# Clone the repository
git clone https://github.com/jexcellence/jextranslate.git
cd jextranslate

# Build the project
./gradlew build

# Run tests
./gradlew test

# Generate Javadoc
./gradlew javadoc

# Publish to local Maven
./gradlew publishToMavenLocal

# Create API JAR
./gradlew apiJar
```

**Build outputs:**
- `build/libs/jextranslate-3.0.0.jar` - Main JAR
- `build/libs/jextranslate-3.0.0-sources.jar` - Sources
- `build/libs/jextranslate-3.0.0-javadoc.jar` - Javadoc

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests if applicable
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Code Style

- Use Java 17 features
- Follow existing naming conventions
- Add `final` to all appropriate variables
- Use records for immutable data classes
- No inline comments (self-documenting code)
- Add comprehensive Javadoc

## 📄 License

MIT License - See [LICENSE](LICENSE) file for details.

## 🔗 Links

- **Group ID:** `de.jexcellence.translate`
- **Artifact ID:** `jextranslate`
- **Version:** `3.0.0`
- **Java Version:** 17
- **Paper API:** 1.20.4-R0.1-SNAPSHOT

## 💡 Tips

- Always initialize TranslationService in `onEnable()`
- Use hierarchical keys for better organization
- Enable locale caching for better performance
- Use async operations for complex translations
- Provide fallback translations in default locale
- Test with multiple locales during development
- Use MiniMessage for rich formatting
- Track missing keys during development

## ⚠️ Important Notes

- JExTranslate requires Java 17+ due to modern language features
- All API classes are thread-safe and immutable
- Locale detection works best on Paper (legacy Bukkit support included)
- Translation files are cached - use `reload()` to refresh
- MiniMessage formatting requires proper Adventure API setup
- Placeholder keys are case-sensitive

## 🎯 Roadmap

### Planned for 3.1.0
- JSON translation repository
- Database translation repository
- PlaceholderAPI integration
- Hot-reload support

### Planned for 3.2.0
- Translation editor GUI
- Translation validation tools
- Coverage reports
- A/B testing support

### Planned for 4.0.0
- Remote translation repository (HTTP API)
- Translation versioning
- Advanced caching strategies
- Performance optimizations

---

**Made with ❤️ by JExcellence**
