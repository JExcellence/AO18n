package de.jexcellence.jextranslate.core;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * File watcher for translation files that triggers automatic reloads on changes.
 *
 * <p>This class monitors the translations directory for file changes (create, modify, delete)
 * and triggers a reload callback when translation files are modified. It includes debouncing
 * to prevent rapid reloads when multiple files change in quick succession.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Watches for ENTRY_CREATE, ENTRY_MODIFY, and ENTRY_DELETE events</li>
 *   <li>Filters for translation file extensions (.yml, .yaml, .json)</li>
 *   <li>Debounces rapid changes to prevent excessive reloads</li>
 *   <li>Runs on a separate daemon thread</li>
 * </ul>
 *
 * @author JExcellence
 * @version 4.0.0
 * @since 4.0.0
 */
public final class TranslationFileWatcher implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(TranslationFileWatcher.class.getName());
    
    /**
     * Default debounce delay in milliseconds to prevent rapid reloads.
     */
    private static final long DEFAULT_DEBOUNCE_MS = 500;

    private final WatchService watchService;
    private final Path translationsDir;
    private final Runnable onReload;
    private final long debounceMs;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong lastReloadTime = new AtomicLong(0);

    /**
     * Creates a new TranslationFileWatcher.
     *
     * @param translationsDir the directory containing translation files
     * @param onReload        the callback to invoke when translations should be reloaded
     * @throws IOException if the watch service cannot be created or the directory cannot be registered
     */
    public TranslationFileWatcher(@NotNull Path translationsDir, @NotNull Runnable onReload) throws IOException {
        this(translationsDir, onReload, DEFAULT_DEBOUNCE_MS);
    }

    /**
     * Creates a new TranslationFileWatcher with custom debounce delay.
     *
     * @param translationsDir the directory containing translation files
     * @param onReload        the callback to invoke when translations should be reloaded
     * @param debounceMs      the debounce delay in milliseconds
     * @throws IOException if the watch service cannot be created or the directory cannot be registered
     */
    public TranslationFileWatcher(@NotNull Path translationsDir, @NotNull Runnable onReload, long debounceMs) 
            throws IOException {
        this.translationsDir = translationsDir;
        this.onReload = onReload;
        this.debounceMs = debounceMs;
        this.watchService = FileSystems.getDefault().newWatchService();
        
        // Register for create, modify, and delete events
        translationsDir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
        );
        
        LOGGER.info("File watcher registered for directory: " + translationsDir);
    }

    /**
     * Executes run.
     */
    @Override
    public void run() {
        LOGGER.info("Translation file watcher started");
        
        while (running.get()) {
            try {
                // Poll with timeout to allow checking the running flag
                WatchKey key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                
                if (key == null) {
                    continue;
                }
                
                boolean shouldReload = false;
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    // Handle overflow events
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        LOGGER.warning("File watcher overflow event - some events may have been lost");
                        shouldReload = true;
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();
                    
                    if (isTranslationFile(fileName.toString())) {
                        LOGGER.info(String.format("Translation file %s: %s", 
                                getEventTypeName(kind), fileName));
                        shouldReload = true;
                    }
                }
                
                // Reset the key to receive further events
                boolean valid = key.reset();
                if (!valid) {
                    LOGGER.warning("Watch key is no longer valid - directory may have been deleted");
                    break;
                }
                
                // Trigger reload with debouncing
                if (shouldReload) {
                    triggerReloadWithDebounce();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.info("File watcher interrupted");
                break;
            } catch (ClosedWatchServiceException e) {
                LOGGER.info("Watch service closed");
                break;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in file watcher loop", e);
            }
        }
        
        LOGGER.info("Translation file watcher stopped");
    }

    /**
     * Stops the file watcher.
     *
     * <p>This method signals the watcher to stop and closes the watch service.
     * The watcher thread will terminate after the current poll timeout.</p>
     */
    public void stop() {
        running.set(false);
        try {
            watchService.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing watch service", e);
        }
    }

    /**
     * Checks if the watcher is currently running.
     *
     * @return true if the watcher is running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Triggers a reload with debouncing to prevent rapid reloads.
     */
    private void triggerReloadWithDebounce() {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastReloadTime.get();
        
        if (currentTime - lastTime >= debounceMs) {
            if (lastReloadTime.compareAndSet(lastTime, currentTime)) {
                LOGGER.info("Triggering translation reload due to file changes");
                try {
                    onReload.run();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error during translation reload callback", e);
                }
            }
        } else {
            LOGGER.fine("Debouncing reload - too soon since last reload");
        }
    }

    /**
     * Checks if a file name represents a translation file.
     *
     * @param fileName the file name to check
     * @return true if the file is a translation file (.yml, .yaml, or .json)
     */
    private boolean isTranslationFile(@NotNull String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".yml") || lowerName.endsWith(".yaml") || lowerName.endsWith(".json");
    }

    /**
     * Gets a human-readable name for a watch event kind.
     *
     * @param kind the event kind
     * @return a human-readable name
     */
    @NotNull
    private String getEventTypeName(@NotNull WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return "created";
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return "modified";
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return "deleted";
        }
        return "changed";
    }
}
