package de.jexcellence.jextranslate.storage;

import de.jexcellence.jextranslate.storage.entity.PlayerLocale;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database-backed implementation of {@link LocaleStorage} using JPA.
 *
 * <p>This implementation persists player locale preferences to a database,
 * allowing preferences to survive server restarts. It includes an in-memory
 * cache layer for improved performance.</p>
 *
 * <p>This implementation is thread-safe using ConcurrentHashMap for caching
 * and proper transaction management for database operations.</p>
 *
 * @author JExcellence
 * @version 4.0.0
 * @since 4.0.0
 */
public class DatabaseLocaleStorage implements LocaleStorage {

    private final EntityManagerFactory entityManagerFactory;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();

    /**
     * Creates a new DatabaseLocaleStorage.
     *
     * @param entityManagerFactory the JPA entity manager factory
     */
    public DatabaseLocaleStorage(@NotNull EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    @NotNull
    public Optional<String> getLocale(@NotNull UUID playerId) {
        // Check cache first
        String cached = cache.get(playerId);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Query database
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            TypedQuery<PlayerLocale> query = em.createQuery(
                    "SELECT p FROM PlayerLocale p WHERE p.uniqueId = :uniqueId",
                    PlayerLocale.class
            );
            query.setParameter("uniqueId", playerId);
            
            return query.getResultStream()
                    .findFirst()
                    .map(playerLocale -> {
                        cache.put(playerId, playerLocale.getLocale());
                        return playerLocale.getLocale();
                    });
        } finally {
            em.close();
        }
    }

    @Override
    public void setLocale(@NotNull UUID playerId, @NotNull String locale) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            
            TypedQuery<PlayerLocale> query = em.createQuery(
                    "SELECT p FROM PlayerLocale p WHERE p.uniqueId = :uniqueId",
                    PlayerLocale.class
            );
            query.setParameter("uniqueId", playerId);
            
            Optional<PlayerLocale> existing = query.getResultStream().findFirst();
            
            if (existing.isPresent()) {
                PlayerLocale playerLocale = existing.get();
                playerLocale.setLocale(locale);
                em.merge(playerLocale);
            } else {
                PlayerLocale playerLocale = new PlayerLocale(playerId, locale);
                em.persist(playerLocale);
            }
            
            em.getTransaction().commit();
            cache.put(playerId, locale);
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public void removeLocale(@NotNull UUID playerId) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            
            int deleted = em.createQuery(
                    "DELETE FROM PlayerLocale p WHERE p.uniqueId = :uniqueId"
            ).setParameter("uniqueId", playerId).executeUpdate();
            
            em.getTransaction().commit();
            
            if (deleted > 0) {
                cache.remove(playerId);
            }
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public void clearAll() {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            em.createQuery("DELETE FROM PlayerLocale").executeUpdate();
            em.getTransaction().commit();
            cache.clear();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    /**
     * Clears the in-memory cache without affecting the database.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Gets the number of cached entries.
     *
     * @return the cache size
     */
    public int getCacheSize() {
        return cache.size();
    }
}
