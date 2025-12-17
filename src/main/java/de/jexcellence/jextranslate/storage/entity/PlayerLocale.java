package de.jexcellence.jextranslate.storage.entity;

import de.jexcellence.hibernate.entity.BaseEntity;
import jakarta.persistence.*;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for storing player locale preferences in a database.
 *
 * <p>This entity extends {@link BaseEntity} from JEHibernate to inherit
 * standard entity functionality including ID generation and lifecycle management.</p>
 *
 * @author JExcellence
 * @version 4.0.0
 * @since 4.0.0
 */
@Entity
@Table(name = "r18n_player_locale")
public class PlayerLocale extends BaseEntity {

    @Column(name = "unique_id", unique = true, nullable = false)
    private UUID uniqueId;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    @Column(name = "locale_updated_at")
    private Instant localeUpdatedAt;

    /**
     * Default constructor required by JPA.
     */
    public PlayerLocale() {
    }

    /**
     * Creates a new PlayerLocale instance.
     *
     * @param uniqueId the player's unique identifier
     * @param locale   the locale code
     */
    public PlayerLocale(@NotNull UUID uniqueId, @NotNull String locale) {
        this.uniqueId = uniqueId;
        this.locale = locale;
        this.localeUpdatedAt = Instant.now();
    }

    /**
     * Gets the player's unique identifier.
     *
     * @return the player UUID
     */
    @NotNull
    public UUID getUniqueId() {
        return uniqueId;
    }

    /**
     * Sets the player's unique identifier.
     *
     * @param uniqueId the player UUID
     */
    public void setUniqueId(@NotNull UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * Gets the locale code.
     *
     * @return the locale code
     */
    @NotNull
    public String getLocale() {
        return locale;
    }

    /**
     * Sets the locale code.
     *
     * @param locale the locale code
     */
    public void setLocale(@NotNull String locale) {
        this.locale = locale;
    }

    /**
     * Gets the last locale update timestamp.
     *
     * @return the update timestamp
     */
    public Instant getLocaleUpdatedAt() {
        return localeUpdatedAt;
    }

    /**
     * Sets the last locale update timestamp.
     *
     * @param localeUpdatedAt the update timestamp
     */
    public void setLocaleUpdatedAt(Instant localeUpdatedAt) {
        this.localeUpdatedAt = localeUpdatedAt;
    }

    @PrePersist
    @PreUpdate
    protected void onLocaleUpdate() {
        this.localeUpdatedAt = Instant.now();
    }
}
