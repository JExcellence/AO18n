package de.jexcellence.jextranslate.storage.repository;

import de.jexcellence.jextranslate.storage.entity.PlayerLocale;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link PlayerLocale} entities.
 *
 * <p>This repository provides CRUD operations for player locale preferences
 * using JPA. It can be used directly or through {@link de.jexcellence.jextranslate.storage.DatabaseLocaleStorage}.</p>
 *
 * @author JExcellence
 * @version 4.0.0
 * @since 4.0.0
 */
public class PlayerLocaleRepository {

    private final EntityManagerFactory entityManagerFactory;

    /**
     * Creates a new PlayerLocaleRepository.
     *
     * @param entityManagerFactory the JPA entity manager factory
     */
    public PlayerLocaleRepository(@NotNull EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    /**
     * Finds a player locale by player UUID.
     *
     * @param playerId the player's unique identifier
     * @return an Optional containing the PlayerLocale if found
     */
    @NotNull
    public Optional<PlayerLocale> findByUniqueId(@NotNull UUID playerId) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            TypedQuery<PlayerLocale> query = em.createQuery(
                    "SELECT p FROM PlayerLocale p WHERE p.uniqueId = :uniqueId",
                    PlayerLocale.class
            );
            query.setParameter("uniqueId", playerId);
            return query.getResultStream().findFirst();
        } finally {
            em.close();
        }
    }

    /**
     * Finds all player locales.
     *
     * @return a list of all PlayerLocale entities
     */
    @NotNull
    public List<PlayerLocale> findAll() {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            return em.createQuery("SELECT p FROM PlayerLocale p", PlayerLocale.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Saves a new player locale.
     *
     * @param playerLocale the entity to save
     * @return the saved entity
     */
    @NotNull
    public PlayerLocale save(@NotNull PlayerLocale playerLocale) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(playerLocale);
            em.getTransaction().commit();
            return playerLocale;
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
     * Updates an existing player locale.
     *
     * @param playerLocale the entity to update
     * @return the updated entity
     */
    @NotNull
    public PlayerLocale update(@NotNull PlayerLocale playerLocale) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            PlayerLocale merged = em.merge(playerLocale);
            em.getTransaction().commit();
            return merged;
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
     * Deletes a player locale by player UUID.
     *
     * @param playerId the player's unique identifier
     * @return true if an entity was deleted
     */
    public boolean deleteByUniqueId(@NotNull UUID playerId) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            int deleted = em.createQuery(
                    "DELETE FROM PlayerLocale p WHERE p.uniqueId = :uniqueId"
            ).setParameter("uniqueId", playerId).executeUpdate();
            em.getTransaction().commit();
            return deleted > 0;
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
     * Deletes all player locales.
     *
     * @return the number of deleted entities
     */
    public int deleteAll() {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            int deleted = em.createQuery("DELETE FROM PlayerLocale").executeUpdate();
            em.getTransaction().commit();
            return deleted;
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
     * Gets the entity manager factory.
     *
     * @return the entity manager factory
     */
    @NotNull
    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }
}
