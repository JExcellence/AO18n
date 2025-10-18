/**
 * Deployment and contribution checklist for the {@code de.jexcellence.jextranslate} package.
 *
 * <h2>Repository layout</h2>
 * <ul>
 *     <li>Verify the YAML repository structure matches the expectations in the README sections
 *     {@code "### 3. Create Translation Files"} and {@code "### Repository Management"},
 *     updating both whenever directories, default locales, or storage locations change.</li>
 *     <li>Keep the build and distribution instructions in {@code "## 📦 Building from Source"}
 *     synchronized with any alterations to module boundaries or artifact outputs.</li>
 * </ul>
 *
 * <h2>Formatter alignment</h2>
 * <ul>
 *     <li>Align formatter behaviour and placeholder usage with the examples in
 *     {@code "### 2. Initialize in Your Plugin"} and {@code "### 4. Send Messages"},
 *     reflecting updates to formatter implementations or resolver defaults.</li>
 *     <li>Mirror any formatter validation or prefix changes in the README guidance under
 *     {@code "### 7. Validate Templates"} and {@code "### 2. Consistent Prefix Usage"} so code
 *     and documentation remain consistent.</li>
 * </ul>
 *
 * <h2>Cache and repository reset triggers</h2>
 * <ul>
 *     <li>Clear locale caches via {@code TranslationService.clearLocaleCache(...)} or perform a
 *     repository reload whenever YAML repositories are edited, new locales are introduced, or
 *     formatter/resolver strategies change, as highlighted in {@code "### Repository Management"}
 *     and {@code "### 6. Reload Command"}.</li>
 *     <li>During deployments, force cache invalidation after swapping storage backends, migrating
 *     translation keys, or updating MiniMessage formatter versions to honour the reminders in
 *     {@code "## ⚠️ Important Notes"}.</li>
 * </ul>
 */
package de.jexcellence.jextranslate;
