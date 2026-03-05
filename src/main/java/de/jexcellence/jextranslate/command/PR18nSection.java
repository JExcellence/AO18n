package de.jexcellence.jextranslate.command;

import de.jexcellence.evaluable.section.ACommandSection;
import de.jexcellence.gpeee.interpreter.EvaluationEnvironmentBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Command section model for {@code pr18n}.
 *
 * @author JExcellence
 * @version 2.1.0
 * @since 2.1.0
 */
@SuppressWarnings("unused")
public final class PR18nSection extends ACommandSection {

    private static final String COMMAND_NAME = "pr18n";

    /**
     * Creates a section model for the {@code pr18n} command.
     *
     * @param environmentBuilder expression environment used by command configuration
     */
    public PR18nSection(final @NotNull EvaluationEnvironmentBuilder environmentBuilder) {
        super(COMMAND_NAME, environmentBuilder);
    }
}
