package com.gymmanager.controller;

import com.gymmanager.dto.ComuneDTO;
import com.gymmanager.service.ComuniService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Reference data: the list of Italian comuni for the address autocomplete.
 * Cached aggressively client-side — the data changes a handful of times a year.
 */
@RestController
@RequestMapping("/api/v1/comuni")
@RequiredArgsConstructor
public class ComuniController {

    private final ComuniService comuniService;

    @GetMapping
    public ResponseEntity<List<ComuneDTO>> all() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(comuniService.all());
    }
}
