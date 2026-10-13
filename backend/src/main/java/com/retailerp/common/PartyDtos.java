package com.retailerp.common;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PartyDtos {

    private PartyDtos() {
    }

    public record PartyRequest(
            @NotBlank @Size(max = 200) String name,
            @Email @Size(max = 320) String email,
            @Size(max = 50) String phone,
            @Size(max = 2000) String address,
            @Size(max = 5000) String notes,
            Boolean active) {
    }

    public record PartyResponse(UUID id, String name, String email, String phone, String address, String notes, boolean active,
            Instant createdAt) {

        public static PartyResponse from(Party p) {
            return new PartyResponse(p.getId(), p.getName(), p.getEmail(), p.getPhone(), p.getAddress(), p.getNotes(), p.isActive(),
                    p.getCreatedAt());
        }
    }
}
