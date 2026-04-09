package de.jexcellence.jextranslate.core;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Enhanced version detection system for Minecraft servers.
 *
 * <p>This class provides comprehensive detection of server types, versions,
 * and capabilities to ensure optimal compatibility across all supported
 * Minecraft versions (1.8-1.21+).</p>
 *
 * <p><strong>Supported Server Types:</strong></p>
 * <ul>
 *   <li><strong>Paper</strong> - Modern Paper servers with native Adventure support</li>
 *   <li><strong>Purpur</strong> - Purpur servers (Paper fork)</li>
 *   <li><strong>Folia</strong> - Folia servers (experimental Paper fork)</li>
 *   <li><strong>Spigot</strong> - Spigot servers requiring Adventure platform</li>
 *   <li><strong>Bukkit</strong> - CraftBukkit servers requiring Adventure platform</li>
 * </ul>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class VersionDetector {

    private static final Logger LOGGER = Logger.getLogger(VersionDetector.class.getName());

    private final boolean isPaper;
    private final boolean isPurpur;
    private final boolean isFolia;
    private final boolean isSpigot;
    private final boolean isBukkit;
    private final String serverVersion;
    private final String minecraftVersion;
    private final boolean isModern;
    private final ServerType serverType;

    /**
     * Server type enumeration.
     */
    public enum ServerType {
        PAPER("Paper"),
        PURPUR("Purpur"),
        FOLIA("Folia"),
        SPIGOT("Spigot"),
        BUKKIT("Bukkit");

        private final String displayName;

        ServerType(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Gets displayName.
         */
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Creates a new version detector and performs detection.
     */
    public VersionDetector() {
        LOGGER.info("Detecting server environment...");

        // Detect server type (order matters - most specific first)
        this.isFolia = detectFolia();
        this.isPurpur = !isFolia && detectPurpur();
        this.isPaper = !isFolia && !isPurpur && detectPaper();
        this.isSpigot = !isPaper && !isPurpur && !isFolia && detectSpigot();
        this.isBukkit = !isPaper && !isPurpur && !isFolia && !isSpigot;

        // Determine server type enum
        if (isFolia) {
            this.serverType = ServerType.FOLIA;
        } else if (isPurpur) {
            this.serverType = ServerType.PURPUR;
        } else if (isPaper) {
            this.serverType = ServerType.PAPER;
        } else if (isSpigot) {
            this.serverType = ServerType.SPIGOT;
        } else {
            this.serverType = ServerType.BUKKIT;
        }

        // Detect version information
        this.minecraftVersion = detectMinecraftVersion();
        this.serverVersion = detectServerVersion();
        this.isModern = detectModernVersion();
    }


    /**
     * Detects if the server is running Folia.
     */
    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Detects if the server is running Purpur.
     */
    private boolean detectPurpur() {
        try {
            Class.forName("org.purpurmc.purpur.PurpurConfig");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Detects if the server is running Paper.
     */
    private boolean detectPaper() {
        try {
            Class.forName("com.destroystokyo.paper.ParticleBuilder");
            return true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("io.papermc.paper.configuration.Configuration");
                return true;
            } catch (ClassNotFoundException e2) {
                return false;
            }
        }
    }

    /**
     * Detects if the server is running Spigot.
     */
    private boolean detectSpigot() {
        try {
            Class.forName("org.spigotmc.SpigotConfig");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Detects the Minecraft version.
     */
    @NotNull
    private String detectMinecraftVersion() {
        try {
            return Bukkit.getVersion();
        } catch (Exception e) {
            LOGGER.warning("Failed to detect Minecraft version: " + e.getMessage());
            return "unknown";
        }
    }

    /**
     * Detects the server version (NMS version).
     */
    @NotNull
    private String detectServerVersion() {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = packageName.split("\\.");
            if (parts.length > 3) {
                return parts[3]; // e.g., "v1_20_R3"
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to detect server version: " + e.getMessage());
        }
        return "unknown";
    }

    /**
     * Detects if the server is running a modern version (1.13+).
     */
    private boolean detectModernVersion() {
        if ("unknown".equals(serverVersion)) {
            return true; // Assume modern for safety
        }
        try {
            // Parse version like "v1_20_R3" -> major=1, minor=20
            String[] parts = serverVersion.substring(1).split("_");
            if (parts.length >= 2) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                return major > 1 || (major == 1 && minor >= 13);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to parse server version for modernity check: " + e.getMessage());
        }
        return true; // Assume modern for safety
    }

    /**
     * Checks if the server has native Adventure support.
     *
     * @return true if the server has native Adventure support
     */
    public boolean hasNativeAdventure() {
        return isPaper || isPurpur || isFolia;
    }

    /**
     * Checks if Adventure platform is required.
     *
     * @return true if Adventure platform is required
     */
    public boolean requiresAdventurePlatform() {
        return !hasNativeAdventure();
    }

    /**
     * Checks if the server supports modern features (1.13+).
     *
     * @return true if the server supports modern features
     */
    public boolean isModern() {
        return isModern;
    }

    /**
     * Checks if the server supports legacy features (pre-1.13).
     *
     * @return true if the server supports legacy features
     */
    public boolean isLegacy() {
        return !isModern;
    }

    /**
     * Gets the server type.
     *
     * @return the server type
     */
    @NotNull
    public ServerType getServerType() {
        return serverType;
    }

    /**
     * Gets the Minecraft version string.
     *
     * @return the Minecraft version
     */
    @NotNull
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    /**
     * Gets the server version (NMS version).
     *
     * @return the server version
     */
    @NotNull
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * Checks if the server is Paper.
     *
     * @return true if Paper
     */
    public boolean isPaper() {
        return isPaper;
    }

    /**
     * Checks if the server is Purpur.
     *
     * @return true if Purpur
     */
    public boolean isPurpur() {
        return isPurpur;
    }

    /**
     * Checks if the server is Folia.
     *
     * @return true if Folia
     */
    public boolean isFolia() {
        return isFolia;
    }

    /**
     * Checks if the server is Spigot.
     *
     * @return true if Spigot
     */
    public boolean isSpigot() {
        return isSpigot;
    }

    /**
     * Checks if the server is Bukkit.
     *
     * @return true if Bukkit
     */
    public boolean isBukkit() {
        return isBukkit;
    }

    /**
     * Checks if a specific class exists.
     *
     * @param className the class name to check
     * @return true if the class exists
     */
    public boolean hasClass(@NotNull String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if the current version is at least the specified version.
     *
     * @param targetVersion the target version (e.g., "v1_16_R1")
     * @return true if current version >= target version
     */
    public boolean isVersionAtLeast(@NotNull String targetVersion) {
        if ("unknown".equals(serverVersion)) {
            return false;
        }
        try {
            return compareVersions(serverVersion, targetVersion) >= 0;
        } catch (Exception e) {
            LOGGER.warning("Failed to compare versions: " + e.getMessage());
            return false;
        }
    }

    /**
     * Compares two version strings.
     *
     * @param version1 the first version
     * @param version2 the second version
     * @return negative if version1 < version2, 0 if equal, positive if version1 > version2
     */
    private int compareVersions(@NotNull String version1, @NotNull String version2) {
        int[] v1 = parseVersion(version1);
        int[] v2 = parseVersion(version2);

        if (v1[0] != v2[0]) {
            return Integer.compare(v1[0], v2[0]);
        }
        if (v1[1] != v2[1]) {
            return Integer.compare(v1[1], v2[1]);
        }
        return Integer.compare(v1[2], v2[2]);
    }

    /**
     * Parses a version string into [major, minor, revision].
     *
     * @param version the version string (e.g., "v1_20_R3")
     * @return array of [major, minor, revision]
     */
    private int[] parseVersion(@NotNull String version) {
        String clean = version.startsWith("v") ? version.substring(1) : version;
        String[] parts = clean.split("_");

        int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int revision = 0;

        if (parts.length > 2) {
            String revPart = parts[2];
            if (revPart.startsWith("R")) {
                revision = Integer.parseInt(revPart.substring(1));
            } else {
                revision = Integer.parseInt(revPart);
            }
        }
        return new int[]{major, minor, revision};
    }

    /**
     * Gets a summary of the environment.
     *
     * @return environment summary string
     */
    @NotNull
    public String getEnvironmentSummary() {
        return String.format("%s %s (MC: %s, Modern: %s, Adventure: %s)",
                serverType.getDisplayName(),
                serverVersion,
                minecraftVersion,
                isModern ? "Yes" : "No",
                hasNativeAdventure() ? "Native" : "Platform");
    }
}
