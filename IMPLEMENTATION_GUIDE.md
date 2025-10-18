## JExTranslate Implementation Guide

This document provides a comprehensive guide for implementing the remaining classes based on the modernization patterns established.

## Completed Files

### Core API (Already Created)
- ✅ `TranslationKey.java` - Type-safe translation keys
- ✅ `Placeholder.java` - Sealed placeholder types
- ✅ `TranslationRepository.java` - Repository interface
- ✅ `MessageFormatter.java` - Formatter interface
- ✅ `LocaleResolver.java` - Locale resolution
- ✅ `TranslatedMessage.java` - Immutable message result
- ✅ `TranslationService.java` - Main API entry point
- ✅ `MissingKeyTracker.java` - Missing key tracking

### Implementations (Already Created)
- ✅ `MiniMessageFormatter.java` - Basic MiniMessage formatting
- ✅ `LocaleResolverProvider.java` - Auto-detecting resolver
- ✅ `YamlTranslationRepository.java` - YAML storage
- ✅ `SimpleMissingKeyTracker.java` - Key tracking
- ✅ `HybridMessageFormatter.java` - Enhanced formatter

### Commands (Already Created)
- ✅ `TranslationCommand.java` - Admin command system

### Utilities (Already Created)
- ✅ `DebugUtils.java` - Debugging tools

## Remaining Files to Implement

### 1. Enhanced YAML Repository (`impl/EnhancedYamlRepository.java`)

**Purpose**: Better YAML handling with comments, multi-line support, and cleaner structure

**Key Features**:
- Preserve comments in YAML files
- Handle multi-line strings elegantly
- Support for YAML lists as lore
- Better error messages
- Atomic file operations

**Implementation Pattern**:
```java
package de.jexcellence.jextranslate.impl;

public class EnhancedYamlRepository implements TranslationRepository {
    private final Path translationsDirectory;
    private final Map<Locale, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final List<RepositoryListener> listeners = new CopyOnWriteArrayList<>();
    private Locale defaultLocale;
    
    // Constructor, getTranslation, reload, etc.
    // Use SnakeYAML with custom representers for better formatting
    // Implement comment preservation
    // Handle lists as newline-separated strings
}
```

### 2. YAML Translation Writer (`impl/YamlTranslationWriter.java`)

**Purpose**: Safe writing of translations to YAML files

**Key Features**:
- Non-destructive writes
- Backup creation
- Validation
- Thread-safe operations

**Implementation Pattern**:
```java
package de.jexcellence.jextranslate.impl;

public class YamlTranslationWriter {
    private final Path translationsDirectory;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final WriterConfig config;
    
    public void addTranslation(Locale locale, TranslationKey key, String value) throws IOException {
        lock.writeLock().lock();
        try {
            // Create backup if enabled
            // Load existing YAML
            // Add/update key
            // Write atomically
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public Path createBackup(Locale locale) throws IOException {
        // Create timestamped backup
    }
    
    public ValidationResult validateValue(String value) {
        // Check for unsafe characters
        // Validate YAML compatibility
    }
}
```

### 3. Tracking Repository Decorator (`impl/TrackingRepositoryDecorator.java`)

**Purpose**: Add missing key tracking to any repository

**Implementation Pattern**:
```java
package de.jexcellence.jextranslate.impl;

public class TrackingRepositoryDecorator implements TranslationRepository {
    private final TranslationRepository delegate;
    private final MissingKeyTracker tracker;
    
    @Override
    public Optional<String> getTranslation(TranslationKey key, Locale locale) {
        final Optional<String> translation = delegate.getTranslation(key, locale);
        if (translation.isEmpty()) {
            tracker.trackMissing(key, locale);
        }
        return translation;
    }
    
    // Delegate all other methods
    // Track missing keys transparently
}
```

## Modernization Patterns

### 1. Naming Conventions

**Old → New**:
- `I18n` → `TranslationService`
- `MessageKey` → `TranslationKey`
- `PlayerLocaleService` → `LocaleResolver`
- `includePrefix()` → `withPrefix()`
- `withPlaceholder()` → `with()`
- `sendMessage()` → `send()`

### 2. Code Style

**Use**:
- `final` for all local variables and parameters
- Records for immutable data classes
- Sealed interfaces for type hierarchies
- `@NotNull`/`@Nullable` annotations
- Switch expressions instead of statements
- No inline comments (self-documenting code)

**Example**:
```java
// ❌ Old style
public String format(String template, List<Placeholder> placeholders) {
    String result = template; // Process template
    for (Placeholder p : placeholders) {
        result = result.replace("{" + p.key() + "}", p.value());
    }
    return result;
}

// ✅ New style
@NotNull
public String format(@NotNull final String template, @NotNull final List<Placeholder> placeholders) {
    String result = template;
    for (final Placeholder placeholder : placeholders) {
        final String key = "{" + placeholder.key() + "}";
        result = result.replace(key, placeholder.asText());
    }
    return result;
}
```

### 3. Async Operations

**Pattern**:
```java
@NotNull
public CompletableFuture<Void> reload() {
    return CompletableFuture.runAsync(() -> {
        try {
            // Perform reload
            notifyListeners();
        } catch (final Exception exception) {
            LOGGER.log(Level.SEVERE, "Reload failed", exception);
            notifyError(exception);
        }
    });
}
```

### 4. Builder Pattern

**Pattern**:
```java
public class TranslationService {
    private final TranslationKey key;
    private final Player player;
    private final List<Placeholder> placeholders;
    
    private TranslationService(...) {
        // Private constructor
    }
    
    @NotNull
    public static TranslationService create(@NotNull final TranslationKey key, @NotNull final Player player) {
        return new TranslationService(key, player, List.of());
    }
    
    @NotNull
    public TranslationService with(@NotNull final String key, @Nullable final Object value) {
        final List<Placeholder> newPlaceholders = new ArrayList<>(this.placeholders);
        newPlaceholders.add(createPlaceholder(key, value));
        return new TranslationService(this.key, this.player, newPlaceholders);
    }
}
```

### 5. Error Handling

**Pattern**:
```java
try {
    // Operation
} catch (final Exception exception) {
    LOGGER.log(Level.WARNING, "Operation failed", exception);
    return fallbackValue();
}
```

## YAML File Structure

### Simple Keys
```yaml
prefix: "<gold>[MyPlugin]</gold> "
welcome: "<green>Welcome, {player}!</green>"
```

### Nested Keys
```yaml
gui:
  buttons:
    next: "Next Page"
    previous: "Previous Page"
```

### Multi-line (Lists)
```yaml
help:
  commands:
    - "Available commands:"
    - "/help - Show this help"
    - "/info - Show server info"
```

### With Placeholders
```yaml
coins:
  balance: "You have <gold>{amount}</gold> coins"
  insufficient: "<red>You need {required} but only have {current}</red>"
```

### Interactive Components
```yaml
buttons:
  help: "<click:run_command:/help><hover:show_text:'Click for help'><green>[?]</green></hover></click>"
  link: "<click:open_url:https://example.com><blue><u>Visit Website</u></blue></click>"
```

## Testing Strategy

### Unit Tests
```java
@Test
public void testTranslationKeyValidation() {
    assertThrows(IllegalArgumentException.class, () -> TranslationKey.of(""));
    assertThrows(IllegalArgumentException.class, () -> TranslationKey.of(".invalid"));
    assertDoesNotThrow(() -> TranslationKey.of("valid.key"));
}

@Test
public void testPlaceholderReplacement() {
    final String template = "Hello, {player}!";
    final List<Placeholder> placeholders = List.of(Placeholder.of("player", "Steve"));
    final String result = formatter.formatText(template, placeholders, Locale.ENGLISH);
    assertEquals("Hello, Steve!", result);
}
```

### Integration Tests
```java
@Test
public void testFullTranslationFlow() {
    // Setup repository
    // Create translation service
    // Build message
    // Verify output
}
```

## Performance Considerations

### 1. Caching
- Cache locale resolutions
- Cache MessageFormat instances
- Cache translation lookups

### 2. Async Operations
- Use `CompletableFuture` for I/O operations
- Don't block main thread
- Use thread pools appropriately

### 3. Memory Management
- Use weak references for caches where appropriate
- Clear caches on reload
- Limit cache sizes

## Migration Checklist

- [ ] Update all class names to new conventions
- [ ] Replace inline comments with clear naming
- [ ] Add `final` to all appropriate variables
- [ ] Use records for data classes
- [ ] Implement sealed interfaces where appropriate
- [ ] Add `@NotNull`/`@Nullable` annotations
- [ ] Convert to switch expressions
- [ ] Implement async operations
- [ ] Add comprehensive error handling
- [ ] Update documentation
- [ ] Write unit tests
- [ ] Update examples

## Command System

### Permissions
```yaml
permissions:
  jextranslate.admin:
    description: "Access to all translation commands"
    default: op
    children:
      jextranslate.admin.missing: true
      jextranslate.admin.add: true
      jextranslate.admin.stats: true
      jextranslate.admin.reload: true
      jextranslate.admin.backup: true
```

### Commands
```yaml
commands:
  translate:
    description: "Translation management commands"
    usage: "/translate <missing|add|stats|reload|backup|info>"
    permission: jextranslate.admin
    aliases: [trans, i18n]
```

## Best Practices Summary

1. **Always use `final`** for immutability
2. **Prefer records** for data classes
3. **Use sealed interfaces** for type hierarchies
4. **Annotate nullability** with `@NotNull`/`@Nullable`
5. **No inline comments** - use clear naming
6. **Async for I/O** - use `CompletableFuture`
7. **Builder pattern** for complex objects
8. **Validate inputs** early and clearly
9. **Log appropriately** - use levels correctly
10. **Test thoroughly** - unit and integration tests

## Next Steps

1. Implement remaining repository classes
2. Create comprehensive test suite
3. Add example plugins
4. Write migration guide
5. Create performance benchmarks
6. Add CI/CD pipeline
7. Publish to Maven Central

## Support

For questions or issues:
- GitHub Issues: https://github.com/jexcellence/jextranslate/issues
- Documentation: See API_DOCUMENTATION.md
- Examples: See example/ directory
