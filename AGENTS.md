# Agent Guidelines for `JExTranslate`

> **Note:** Read the root `AGENTS.md` for repository-wide workflow, commit, and testing guidelines before following the module-specific notes below.

## Initialization Requirements
- Always document and enforce the prerequisite call to `TranslationService.configure(...)` before any repository/formatter/resolver access. Treat this as a mandatory bootstrap step for every entry point, including tests and utilities.
- When updating usage docs or examples, describe the locale resolution cascade and cache behaviour:
  - Locale selection must follow: explicit override → resolved player locale → service default.
  - Mention that resolved locales are cached per-player/profile and refreshed on service reload.
  - Note that caches must be cleared whenever repository contents, formatter implementations, or resolver strategies change (e.g., during reload commands or hot-swap testing).

## Placeholder and Formatting Conventions
- Preserve the fluent placeholder API patterns: use immutable builders, never reuse mutable state across threads, and chain `.with(...)` calls on fresh message instances.
- Explain how to select or override message prefixes and ensure authors reference MiniMessage-compatible markup. Document that MiniMessage formatting requires fully validated Adventure components and that placeholders must be wrapped in `{placeholder}` tokens compatible with MiniMessage escaping rules.

## Repository Synchronisation
- Cross-reference the module README when documenting storage expectations, especially YAML layout, repository initialization, and formatter/resolver setup. Keep examples aligned with the README so repository, formatter, and resolver configuration steps remain synchronized across docs and code samples.
- When cache invalidation or storage location changes are introduced, describe the required repository reload/initialization steps to keep the README, formatters, and locale resolvers in sync.
