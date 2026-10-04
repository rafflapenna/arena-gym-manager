package com.gymmanager.dto;

import java.util.List;

/**
 * A single Italian comune as served to the frontend address autocomplete.
 *
 * @param nome            comune name
 * @param sigla           2-letter province code (e.g. "BT")
 * @param provincia       full province name
 * @param regione         region name
 * @param cap             one or more postal codes
 * @param codiceCatastale 4-character cadastral code (e.g. "A001"), null if the source omits it
 */
public record ComuneDTO(
        String nome,
        String sigla,
        String provincia,
        String regione,
        List<String> cap,
        String codiceCatastale
) {}
