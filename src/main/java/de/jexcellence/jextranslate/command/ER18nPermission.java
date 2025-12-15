package de.jexcellence.jextranslate.command;

import org.jetbrains.annotations.NotNull;

/**
 * Enum representing the available permissions for the R18n plugin commands.
 *
 * <p>Each permission node consists of an internal name and a fallback node string,
 * which can be used for permission checks and compatibility with different permission systems.</p>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public enum ER18nPermission {

    COMMAND("command", "r18n.command"),
    HELP("commandHelp", "r18n.command.help"),
    RELOAD("commandReload", "r18n.command.reload"),
    MISSING("commandMissing", "r18n.command.missing"),
    EXPORT("commandExport", "r18n.command.export"),
    METRICS("commandMetrics", "r18n.command.metrics");

    private final String internalName;
    private final String fallbackNode;

    ER18nPermission(@NotNull String internalName, @NotNull String fallbackNode) {
        this.internalName = internalName;
        this.fallbackNode = fallbackNode;
    }

    /**
     * Gets the internal name of the permission node.
     *
     * @return the internal name as a String
     */
    public String getInternalName() {
        return this.internalName;
    }

    /**
     * Gets the fallback node string for the permission.
     *
     * @return the fallback node as a String
     */
    public String getFallbackNode() {
        return this.fallbackNode;
    }
}
