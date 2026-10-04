package com.gymmanager.dto;

import lombok.Builder;

/** Minimal {id, fullName} reference to the AppUser (TRAINER) teaching a course. */
@Builder
public record TrainerRefDTO(Long id, String fullName) {}
