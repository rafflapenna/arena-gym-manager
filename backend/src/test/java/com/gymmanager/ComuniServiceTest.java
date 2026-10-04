package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.dto.ComuneDTO;
import com.gymmanager.service.ComuniService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context, no Testcontainers): {@link ComuniService} only needs an
 * {@link ObjectMapper}, and {@code seedFromBundle} reads the bundled resource synchronously.
 */
class ComuniServiceTest {

    @Test
    void seedFromBundle_parsesCodiceCatastaleFromTheBundledSeed() {
        ComuniService service = new ComuniService(new ObjectMapper());
        service.seedFromBundle();

        List<ComuneDTO> comuni = service.all();
        assertThat(comuni).isNotEmpty();

        ComuneDTO abanoTerme = comuni.stream()
                .filter(c -> "Abano Terme".equals(c.nome()))
                .findFirst().orElseThrow();
        assertThat(abanoTerme.codiceCatastale()).isEqualTo("A001");
    }
}
