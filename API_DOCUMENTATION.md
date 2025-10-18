# JExTranslate API Documentation

A modern, fluent i18n (internationalization) API for Spigot/Bukkit/Paper environments with full support for MiniMessage, placeholders, and interactive components.

## Table of Contents

1. [Overview](#overview)
2. [Core Concepts](#core-concepts)
3. [Quick Start](#quick-start)
4. [API Components](#api-components)
5. [Usage Examples](#usage-examples)
6. [Advanced Features](#advanced-features)
7. [Implementation Details](#implementation-details)
8. [Best Practices](#best-practices)

---

## Overview

JExTranslate provides a comprehensive solution for handling translations in Minecraft plugins with:

- **Fluent Builder API**: Chain methods for readable, maintainable code
- **MiniMessage Support**: Full Kyori Adventure integration for rich text formatting
- **Automatic Locale Detection**: Supports modern Paper API and legacy Bukkit methods
- **Flexible Storage**: YAML, JSON, or custom repository implementations
- **Type-Safe Keys**: Compile-time validation of translation keys
- **Placeholder System**: Rich placeholder support with formatting options
- **Async Operations**: Non-blocking translation loading and formatting
- **Prefix Management**: Automatic prefix handling for consistent messaging
- **Interactive Components**: Click events, hover text, and more via MiniMessage

---

## Core Concepts

### Translation Keys

Translation keys are strongly-typed, immutable identifiers for translations:

```java
TranslationKey key = TranslationKey.of("welcome.message");
TranslationKey nested = TranslationKey.of("gui", "buttons", "next");
```

**Key Rules:**
- Lowercase letters, numbers, dots, hyphens, underscores only
- Dot-separated hierarchical structure
- Cannot start/end with dots or contain consecutive dots

### Placeholders

Placeholders are type-safe value substitutions in translations:

```java
Placeholder.of("player", playerName)           // Text
Placeholder.of("amount", 1000)                  // Number
Placeholder.of("coins", 1000, numberFormat)     // Formatted number
Placeholder.of("message", component)            // Rich text component
Placeholder.of("time", LocalDateTime.now())     // Date/time
Placeholder.of("custom", value, formatter)      // Custom formatter
```

### Locales

Locales determine which language translations to use:

- Automatically detected from player's client settings
- Fallback chain: specific locale → language-only → default locale
- Cacheable for performance
- Manually overridable per player

---

## Quick Start

### 1. Setup

```java
// Create translation repository
Path translationsDir = plugin.getDataFolder().toPath().resolve("translations");
TranslationRepository repository = YamlTranslationRepository.create(
    translationsDir,
    Locale.ENGLISH
);

// Create message formatter
MessageFormatter formatter = new MiniMessageFormatter();

// Create locale resolver
LocaleResolver localeResolver = LocaleResolverProvider.createAutoDetecting(Locale.ENGLISH);

// Configure the service
TranslationService.configure(new TranslationService.ServiceConfiguration(
    repository,
    formatter,
    localeResolver
));
```

### 2. Create Translation Files

**translations/en.yml:**
```yaml
prefix: "<gold>[MyPlugin]</gold> "
welcome:
  message: "<green>Welcome, {player}!</green>"
  first-join: "<yellow>This is your first time joining!</yellow>"
coins:
  balance: "You have <gold>{amount}</gold> coins"
  insufficient: "<red>You need {required} coins but only have {current}</red>"
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
```

### 3. Send Messages

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
TranslationService.create(TranslationKey.of("coins.insufficient"), player)
    .withPrefix()
    .with("required", 1000)
    .with("current", playerCoins)
    .send();
```

---

## API Components

### TranslationService

The main entry point for creating and sending translated messages.

**Methods:**

```java
// Create service instance
static TranslationService create(TranslationKey key, Player player)
static TranslationService create(TranslationKey key, Player player, Locale locale)
static TranslationService createFresh(TranslationKey key, Player player)

// Configure service
static void configure(ServiceConfiguration config)
static ServiceConfiguration getConfiguration()

// Cache management
static void clearLocaleCache()
static void clearLocaleCache(Player player)

// Builder methods
TranslationService withPrefix()
TranslationService withPrefix(TranslationKey prefixKey)
TranslationService with(String key, Object value)
TranslationService with(Placeholder placeholder)
TranslationService withAll(Map<String, Object> placeholders)

// Build and send
TranslatedMessage build()
CompletableFuture<TranslatedMessage> buildAsync()
void send()
void sendActionBar()
void sendTitle()
```

### TranslatedMessage

Immutable representation of a formatted translation.

**Methods:**

```java
// Text representations
String asPlainText()
String asLegacyText()

// Send to player
void sendTo(Player player)
void sendActionBar(Player player)
void sendTitle(Player player)
void sendTitle(Player player, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut)

// Utility methods
boolean isEmpty()
int length()
boolean contains(String text)
List<Component> splitLines()

// Transformations
TranslatedMessage withKey(TranslationKey newKey)
TranslatedMessage append(Component other)
TranslatedMessage prepend(Component other)

// Debugging
String toDebugString()
```

### TranslationRepository

Interface for loading and managing translations.

**Implementations:**
- `YamlTranslationRepository`: Load from YAML files

**Methods:**

```java
Optional<String> getTranslation(TranslationKey key, Locale locale)
CompletableFuture<Optional<String>> getTranslationAsync(TranslationKey key, Locale locale)
boolean hasTranslation(TranslationKey key, Locale locale)

Set<Locale> getAvailableLocales()
Locale getDefaultLocale()
void setDefaultLocale(Locale locale)

Set<TranslationKey> getAvailableKeys(Locale locale)
Set<TranslationKey> getAllAvailableKeys()

CompletableFuture<Void> reload()

void addListener(RepositoryListener listener)
void removeListener(RepositoryListener listener)

RepositoryMetadata getMetadata()
```

### MessageFormatter

Interface for formatting messages with placeholders.

**Implementations:**
- `MiniMessageFormatter`: MiniMessage-based formatting with placeholder support

**Methods:**

```java
String formatText(String template, List<Placeholder> placeholders, Locale locale)
Component formatComponent(String template, List<Placeholder> placeholders, Locale locale)
ValidationResult validateTemplate(String template)

FormattingStrategy getStrategy()
void setStrategy(FormattingStrategy strategy)
```

### LocaleResolver

Interface for resolving player locales.

**Factory:**
- `LocaleResolverProvider.createAutoDetecting(Locale defaultLocale)`: Auto-detects best available API

**Methods:**

```java
Optional<Locale> resolveLocale(Player player)
Locale getDefaultLocale()
void setDefaultLocale(Locale locale)

boolean setPlayerLocale(Player player, Locale locale)
boolean clearPlayerLocale(Player player)
boolean supportsLocaleStorage()
```

---

## Usage Examples

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
Map<String, Object> placeholders = Map.of(
    "player", player.getName(),
    "world", player.getWorld().getName(),
    "x", player.getLocation().getBlockX(),
    "y", player.getLocation().getBlockY(),
    "z", player.getLocation().getBlockZ()
);

TranslationService.create(TranslationKey.of("teleport.success"), player)
    .withPrefix()
    .withAll(placeholders)
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
    .thenAccept(message -> message.sendTo(player));
```

### Multi-line Messages (Lore)

```yaml
# translations/en.yml
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
    plugin.getLogger().info("Translations reloaded");
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

---

## Advanced Features

### Custom Formatters

```java
// Custom date formatter
DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

TranslationService.create(TranslationKey.of("event.scheduled"), player)
    .with(Placeholder.of("time", eventTime, dateFormatter))
    .send();

// Custom object formatter
Placeholder.of("player", playerObject, p -> p.getName() + " (" + p.getLevel() + ")");
```

### MiniMessage Features

```yaml
# Interactive components in translations
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

### Hierarchical Keys

```java
TranslationKey base = TranslationKey.of("gui");
TranslationKey buttons = base.child("buttons");
TranslationKey nextButton = buttons.child("next");

// Equivalent to: TranslationKey.of("gui.buttons.next")
```

### Key Utilities

```java
TranslationKey key = TranslationKey.of("gui.buttons.next");

key.lastSegment();  // "next"
key.parent();       // TranslationKey.of("gui.buttons")
key.depth();        // 3
key.isRoot();       // false
key.startsWith("gui");  // true
```

---

## Implementation Details

### Locale Resolution Strategy

1. **Check stored preference**: If player has manually set locale
2. **Detect client locale**: Use Paper's `player.locale()` or Bukkit's `player.getLocale()`
3. **Fallback to default**: Use configured default locale

### Translation Fallback Chain

1. **Exact locale**: e.g., `en_US`
2. **Language only**: e.g., `en`
3. **Default locale**: Configured fallback
4. **Key name**: If all else fails, return the key itself

### YAML File Structure

```yaml
# Flat keys
simple-message: "Hello, world!"

# Nested keys
gui:
  buttons:
    next: "Next Page"
    previous: "Previous Page"
  
# Multi-line (list becomes newline-separated)
help:
  commands:
    - "Available commands:"
    - "/help - Show this help"
    - "/info - Show server info"

# With placeholders
welcome: "Hello, {player}! You have {coins} coins."

# With MiniMessage formatting
error: "<red><bold>ERROR:</bold></red> <white>{message}</white>"
```

### Performance Considerations

- **Locale caching**: Player locales are cached to avoid repeated lookups
- **Translation caching**: Repository implementations should cache loaded translations
- **Async operations**: Use `buildAsync()` for expensive operations
- **Immutability**: All core classes are immutable for thread safety

### Thread Safety

- All API classes are thread-safe
- Repository implementations must be thread-safe
- Locale cache uses `ConcurrentHashMap`
- Builder pattern creates new instances (immutable)

---

## Best Practices

### 1. Use Hierarchical Keys

```java
// Good
TranslationKey.of("gui.shop.purchase.success")
TranslationKey.of("gui.shop.purchase.failure")

// Avoid
TranslationKey.of("shop_purchase_success")
TranslationKey.of("shop_purchase_failure")
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

### 3. Placeholder Naming

```yaml
# Good: descriptive, lowercase, underscores
message: "Welcome, {player_name}! You have {coin_count} coins."

# Avoid: unclear, mixed case
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
// Repository returns Optional - handle gracefully
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
            sender.sendMessage("Translations reloaded successfully!");
        });
        return true;
    }
    return false;
}
```

### 7. Validation

```java
// Validate templates during plugin initialization
MessageFormatter formatter = TranslationService.getConfiguration().formatter();
MessageFormatter.ValidationResult result = formatter.validateTemplate(template);

if (!result.isValid()) {
    for (String error : result.getErrors()) {
        plugin.getLogger().warning("Template validation error: " + error);
    }
}
```

### 8. Testing

```java
// Use explicit locales for testing
@Test
public void testGermanTranslation() {
    TranslatedMessage message = TranslationService.create(
        TranslationKey.of("test.message"),
        mockPlayer,
        Locale.GERMAN
    )
        .with("value", 42)
        .build();
    
    assertEquals("Testnachricht: 42", message.asPlainText());
}
```

---

## Migration from Old API

### Old Code

```java
I18n.create(MessageKey.of("welcome.message"), player)
    .includePrefix()
    .withPlaceholder("name", player.getName())
    .sendMessage();
```

### New Code

```java
TranslationService.create(TranslationKey.of("welcome.message"), player)
    .withPrefix()
    .with("name", player.getName())
    .send();
```

### Key Changes

- `I18n` → `TranslationService`
- `MessageKey` → `TranslationKey`
- `includePrefix()` → `withPrefix()`
- `withPlaceholder()` → `with()`
- `sendMessage()` → `send()`
- `Message` → `TranslatedMessage`
- `PlayerLocaleService` → `LocaleResolver`

---

## License

This API is part of the JExTranslate project.

## Support

For issues, questions, or contributions, please visit the project repository.
