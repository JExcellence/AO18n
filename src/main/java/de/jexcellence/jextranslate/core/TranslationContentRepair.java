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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class TranslationContentRepair {

    private static final Map<Character, Byte> WINDOWS_1252_BYTE_MAPPING = createWindows1252ByteMapping();

    private TranslationContentRepair() {
    }

    @NotNull
    static Optional<String> repairCorruptedUtf8Content(@NotNull String content) {
        String normalizedContent = stripUtf8Bom(content);
        byte[] recoveredBytes = recoverSingleByteMojibake(normalizedContent);
        if (recoveredBytes.length == 0) {
            return Optional.empty();
        }

        String repairedContent;
        try {
            repairedContent = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(recoveredBytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            return Optional.empty();
        }

        if (normalizedContent.equals(repairedContent) || containsYamlDisallowedCharacters(repairedContent)) {
            return Optional.empty();
        }

        return Optional.of(repairedContent);
    }

    @NotNull
    static String stripUtf8Bom(@NotNull String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }

    private static boolean containsYamlDisallowedCharacters(@NotNull String content) {
        return content.chars().anyMatch(codePoint ->
                (codePoint >= 0x00 && codePoint <= 0x08)
                        || codePoint == 0x0B
                        || codePoint == 0x0C
                        || (codePoint >= 0x0E && codePoint <= 0x1F)
                        || (codePoint >= 0x7F && codePoint <= 0x84)
                        || (codePoint >= 0x86 && codePoint <= 0x9F)
        );
    }

    @NotNull
    private static byte[] recoverSingleByteMojibake(@NotNull String content) {
        byte[] recoveredBytes = new byte[content.length()];
        int index = 0;

        for (int offset = 0; offset < content.length(); offset++) {
            char character = content.charAt(offset);
            Byte mappedByte = mapSingleByteCharacter(character);
            if (mappedByte == null) {
                return new byte[0];
            }
            recoveredBytes[index++] = mappedByte;
        }

        return index == recoveredBytes.length ? recoveredBytes : Arrays.copyOf(recoveredBytes, index);
    }

    @Nullable
    private static Byte mapSingleByteCharacter(char character) {
        if (character <= 0x00FF) {
            return (byte) character;
        }
        return WINDOWS_1252_BYTE_MAPPING.get(character);
    }

    @NotNull
    private static Map<Character, Byte> createWindows1252ByteMapping() {
        Map<Character, Byte> mapping = new HashMap<>();
        mapping.put('\u20AC', (byte) 0x80);
        mapping.put('\u201A', (byte) 0x82);
        mapping.put('\u0192', (byte) 0x83);
        mapping.put('\u201E', (byte) 0x84);
        mapping.put('\u2026', (byte) 0x85);
        mapping.put('\u2020', (byte) 0x86);
        mapping.put('\u2021', (byte) 0x87);
        mapping.put('\u02C6', (byte) 0x88);
        mapping.put('\u2030', (byte) 0x89);
        mapping.put('\u0160', (byte) 0x8A);
        mapping.put('\u2039', (byte) 0x8B);
        mapping.put('\u0152', (byte) 0x8C);
        mapping.put('\u017D', (byte) 0x8E);
        mapping.put('\u2018', (byte) 0x91);
        mapping.put('\u2019', (byte) 0x92);
        mapping.put('\u201C', (byte) 0x93);
        mapping.put('\u201D', (byte) 0x94);
        mapping.put('\u2022', (byte) 0x95);
        mapping.put('\u2013', (byte) 0x96);
        mapping.put('\u2014', (byte) 0x97);
        mapping.put('\u02DC', (byte) 0x98);
        mapping.put('\u2122', (byte) 0x99);
        mapping.put('\u0161', (byte) 0x9A);
        mapping.put('\u203A', (byte) 0x9B);
        mapping.put('\u0153', (byte) 0x9C);
        mapping.put('\u017E', (byte) 0x9E);
        mapping.put('\u0178', (byte) 0x9F);
        return Map.copyOf(mapping);
    }
}
