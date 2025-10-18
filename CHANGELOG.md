# Changelog

All notable changes to JExTranslate will be documented in this file.

## [3.0.0] - 2025-01-10

### Complete Rewrite

This version represents a complete modernization and rewrite of the i18n API with better naming, cleaner code, and modern Java features.

### Added

#### Core API
- `TranslationService` - New main entry point replacing `I18n`
- `TranslationKey` - Type-safe translation keys replacing `MessageKey`
- `TranslatedMessage` - Immutable message result replacing `Message`
- `LocaleResolver` - Simplified locale resolution replacing `PlayerLocaleService`
- `Placeholder` - Sealed interface with type-safe implementations
- `MissingKeyTracker` - Interface for tracking missing translations

#### Implementations
- `MiniMessageFormatter` - MiniMessage-based message formatting
- `YamlTranslationRepository` - YAML file-based translation storage
- `LocaleResolverProvider` - Auto-detecting locale resolver factory
- `SimpleMissingKeyTracker` - Basic missing key tracking implementation

#### Utilities
- `DebugUtils` - Comprehensive debugging utilities
  - Translation debugging
  - Translation comparison
  - System status reporting
  - Force refresh capability

#### Features
- Full async support with `CompletableFuture`
- Automatic locale detection (Paper/Bukkit/Fallback)
- Locale caching for performance
- Translation fallback chain
- Prefix management
- Action bar support
- Title support
- Multi-line message support
- Interactive MiniMessage components
- Repository event listeners
- Template validation

### Changed

#### Naming Improvements
- `I18n` → `TranslationService`
- `MessageKey` → `TranslationKey`
- `Message` → `TranslatedMessage`
- `PlayerLocaleService` → `LocaleResolver`
- `includePrefix()` → `withPrefix()`
- `withPlaceholder()` → `with()`
- `sendMessage()` → `send()`

#### API Improvements
- Fluent builder pattern throughout
- Better method naming consistency
- Cleaner, more intuitive API
- Reduced verbosity
- Better type safety with sealed interfaces
- Comprehensive null safety annotations

#### Code Quality
- No inline comments (self-documenting code)
- Proper use of `final` keyword
- Records for immutable data
- Modern Java 17 features
- Better separation of concerns
- Interface-based design

### Removed

- Inline comments (replaced with clear naming)
- Redundant methods
- Legacy compatibility layers
- Reflection-based version detection (replaced with method availability checks)

### Technical Improvements

#### Architecture
- Repository pattern for data access
- Strategy pattern for formatting
- Provider pattern for auto-detection
- Builder pattern for message creation
- Immutable objects for thread safety

#### Performance
- Locale caching
- Translation caching
- Async operations
- Efficient fallback chain
- Minimal object allocation

#### Thread Safety
- All API classes are thread-safe
- Concurrent collections where needed
- Immutable message objects
- Atomic operations for tracking

### Documentation

- Comprehensive API documentation (API_DOCUMENTATION.md)
- Quick start guide (README.md)
- Project summary (PROJECT_SUMMARY.md)
- Example plugin with usage demonstrations
- Example translation files (en, de, es)
- Migration guide from old API

### Dependencies

- Updated to Java 17
- Kyori Adventure API 4.16.0
- SnakeYAML 2.2
- JetBrains Annotations 24.1.0

### Migration

See API_DOCUMENTATION.md for detailed migration guide from version 2.x to 3.0.

#### Quick Migration Example

**Before (2.x):**
```java
I18n.create(MessageKey.of("welcome.message"), player)
    .includePrefix()
    .withPlaceholder("name", player.getName())
    .sendMessage();
```

**After (3.0):**
```java
TranslationService.create(TranslationKey.of("welcome.message"), player)
    .withPrefix()
    .with("name", player.getName())
    .send();
```

---

## [2.0.0] - Previous Version

### Features
- Basic i18n support
- YAML translation files
- Placeholder support
- Locale detection
- Prefix handling

### Known Issues
- Verbose API
- Inconsistent naming
- Limited async support
- Complex configuration

---

## Future Roadmap

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
