package com.gymmanager.dto;

import lombok.Builder;

/** Minimal {id, name} reference, used to embed a discipline or course in a parent DTO. */
@Builder
public record NamedRefDTO(Long id, String name) {}
