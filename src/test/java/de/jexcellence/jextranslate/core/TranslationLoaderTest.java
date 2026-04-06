/*
 * Copyright (c) 2021-2026 Antimatter Zone LLC. All rights reserved.
 *
 * This source code is proprietary and confidential to Antimatter Zone LLC.
 * Unauthorized copying, modification, distribution, display, performance,
 * publication, sublicensing, or creation of derivative works is prohibited
 * without prior written permission from Antimatter Zone LLC, except to the
 * extent permitted by applicable United States law.
 *
 * This notice is intended to preserve all rights and remedies available under
 * the laws of the State of Washington and the United States of America.
 */
package de.jexcellence.jextranslate.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TranslationLoader")
class TranslationLoaderTest {

    @Test
    @DisplayName("should recover mojibake YAML content into valid UTF-8")
    @SuppressWarnings("unchecked")
    void shouldRecoverMojibakeYamlContentIntoValidUtf8() {
        String corruptedYaml = "\uFEFFtitle: \"\u00E3\u0081\u00AE\u00E6\u2014\u00A5\u00E6\u0153\u00AC\u00E8\u00AA\u017E\"\n"
                + "message: \"<green>RaindropCentral \u00E3\u0081\u00AB\u00E6\u017D\u00A5\u00E7\u00B6\u0161\u00E4\u00B8\u00AD...</green>\"\n";

        Optional<String> repaired = TranslationContentRepair.repairCorruptedUtf8Content(corruptedYaml);

        assertTrue(repaired.isPresent());

        Map<String, Object> yaml = new Yaml().load(repaired.orElseThrow());
        assertEquals("\u306E\u65E5\u672C\u8A9E", yaml.get("title"));
        assertEquals("<green>RaindropCentral \u306B\u63A5\u7D9A\u4E2D...</green>", yaml.get("message"));
    }

    @Test
    @DisplayName("should leave healthy UTF-8 content unchanged")
    void shouldLeaveHealthyUtf8ContentUnchanged() {
        String healthyYaml = "title: \"\u306E\u65E5\u672C\u8A9E \uD83D\uDE00\"\n"
                + "message: \"<green>RaindropCentral \u306B\u63A5\u7D9A\u4E2D...</green>\"\n";

        Optional<String> repaired = TranslationContentRepair.repairCorruptedUtf8Content(healthyYaml);

        assertTrue(repaired.isEmpty());
    }
}
