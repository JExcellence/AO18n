# JExTranslate

A modern, fluent i18n (internationalization) API for Spigot/Bukkit/Paper environments with full support for MiniMessage, placeholders, and interactive components.

## Features

- 🎨 **MiniMessage Support** - Full Kyori Adventure integration for rich text formatting
- 🌍 **Automatic Locale Detection** - Supports modern Paper API and legacy Bukkit methods
- 📦 **Flexible Storage** - YAML, JSON, or custom repository implementations
- 🔒 **Type-Safe Keys** - Compile-time validation of translation keys
- 🔄 **Placeholder System** - Rich placeholder support with formatting options
- ⚡ **Async Operations** - Non-blocking translation loading and formatting
- 🎯 **Prefix Management** - Automatic prefix handling for consistent messaging
- 🖱️ **Interactive Components** - Click events, hover text, and more via MiniMessage

## Quick Start

### 1. Add Dependency

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation("de.jexcellence.translate:jextranslate:3.0.0")
}
```

**Gradle (Groovy):**
```groovy
dependencies {
    implementation 'de.jexcellence.translate:jextranslate:3.0.0'
}
```

**Maven:**
```xml
<dependency>
    <groupId>de.jexcellence.translate</groupId>
    <artifactId>jextranslate</artifactId>
    <version>3.0.0</version>
</dependency>
```

### 2. Initialize

```java
@Override
public void onEnable() {
    // Create translation repository
    Path translationsDir = getDataFolder().toPath().resolve("translations");
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
}
```

### 3. Create Translation Files

**translations/en.yml:**
```yaml
prefix: "<gold>[MyPlugin]</gold> "
welcome:
  message: "<green>Welcome, {player}!</green>"
coins:
  balance: "You have <gold>{amount}</gold> coins"
```

**translations/de.yml:**
```yaml
prefix: "<gold>[MeinPlugin]</gold> "
welcome:
  message: "<green>Willkommen, {player}!</green>"
coins:
  balance: "Du hast <gold>{amount}</gold> Münzen"
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
```

## Documentation

For comprehensive documentation, see [API_DOCUMENTATION.md](API_DOCUMENTATION.md)

## Examples

### Basic Translation
```java
TranslationService.create(TranslationKey.of("welcome.message"), player)
    .with("player", player.getName())
    .send();
```

### Multiple Placeholders
```java
TranslationService.create(TranslationKey.of("teleport.success"), player)
    .withPrefix()
    .with("world", worldName)
    .with("x", x)
    .with("y", y)
    .with("z", z)
    .send();
```

### Action Bar
```java
TranslationService.create(TranslationKey.of("combat.cooldown"), player)
    .with("seconds", remainingSeconds)
    .sendActionBar();
```

### Title
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

### Interactive Components
```yaml
# translations/en.yml
help:
  button: "<click:run_command:/help><hover:show_text:'Click for help'><green>[?]</green></hover></click>"
  link: "<click:open_url:https://example.com><blue><u>Visit our website</u></blue></click>"
```

## Architecture

### Core Components

- **TranslationService** - Main entry point for creating and sending messages
- **TranslationKey** - Type-safe, immutable translation key representation
- **Placeholder** - Type-safe placeholder system with formatting support
- **TranslatedMessage** - Immutable formatted message with multiple output formats
- **TranslationRepository** - Interface for loading and managing translations
- **MessageFormatter** - Interface for formatting messages with placeholders
- **LocaleResolver** - Interface for resolving player locales

### Implementations

- **YamlTranslationRepository** - YAML-based translation storage
- **MiniMessageFormatter** - MiniMessage-based formatting
- **LocaleResolverProvider** - Auto-detecting locale resolver factory

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

## Requirements

- Java 17+
- Spigot/Bukkit/Paper 1.16+
- Kyori Adventure API

## License

MIT License - See LICENSE file for details

## Support

For issues, questions, or contributions, please visit the project repository.
