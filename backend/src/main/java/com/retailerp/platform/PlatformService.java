package com.retailerp.platform;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.common.NotFoundException;
import com.retailerp.security.JwtService;
import com.retailerp.subscription.Module;

/**
 * Cross-tenant operations. RLS hides other tenants from the app role, so these go through the SECURITY DEFINER
 * functions of migration V7. Only reachable from {@link PlatformController} (PLATFORM_ADMIN only).
 */
@Service
public class PlatformService {

    private final PlatformAdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;
    private final String dummyHash;

    public PlatformService(PlatformAdminRepository admins, PasswordEncoder passwordEncoder, JwtService jwtService, JdbcTemplate jdbc) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jdbc = jdbc;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public record LoginRequest(@jakarta.validation.constraints.NotBlank String email, @jakarta.validation.constraints.NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, String email, String fullName) {
    }

    public record TenantRow(UUID id, String name, Instant createdAt, String planCode, String trialPlanCode, Instant trialEndsAt,
            boolean trialActive, List<String> addons, long activeUsers, long activeWarehouses) {
    }

    public record SubscriptionUpdate(@jakarta.validation.constraints.NotBlank String planCode, Instant trialEndsAt, Set<Module> addons) {
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        var admin = admins.findByEmailIgnoreCase(request.email().trim());
        boolean matches = passwordEncoder.matches(request.password(), admin.map(PlatformAdmin::getPasswordHash).orElse(dummyHash));
        if (admin.isEmpty() || !matches) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        JwtService.IssuedToken token = jwtService.issuePlatform(admin.get().getId(), admin.get().getEmail(), admin.get().getFullName());
        return new LoginResponse(token.token(), token.expiresAt(), admin.get().getEmail(), admin.get().getFullName());
    }

    public List<TenantRow> tenants() {
        Instant now = Instant.now();
        return jdbc.query("SELECT * FROM platform_tenants()", (rs, i) -> {
            Instant trialEnds = toInstant(rs.getObject("trial_ends_at", OffsetDateTime.class));
            String trialPlan = rs.getString("trial_plan_code");
            return new TenantRow(rs.getObject("id", UUID.class), rs.getString("name"),
                    toInstant(rs.getObject("created_at", OffsetDateTime.class)), rs.getString("plan_code"), trialPlan, trialEnds,
                    trialPlan != null && trialEnds != null && trialEnds.isAfter(now), strings(rs.getArray("addons")),
                    rs.getLong("user_count"), rs.getLong("warehouse_count"));
        });
    }

    public TenantRow updateSubscription(UUID tenantId, SubscriptionUpdate update) {
        String[] addons = (update.addons() == null ? Set.<Module>of() : update.addons()).stream().map(Enum::name).toArray(String[]::new);
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (var statement = connection.prepareStatement("SELECT platform_set_subscription(?, ?, ?, ?)")) {
                    statement.setObject(1, tenantId);
                    statement.setString(2, update.planCode());
                    statement.setObject(3, update.trialEndsAt() == null ? null : update.trialEndsAt().atOffset(ZoneOffset.UTC));
                    statement.setArray(4, connection.createArrayOf("varchar", addons));
                    statement.execute();
                }
                return null;
            });
        } catch (org.springframework.dao.DataAccessException e) {
            if (e.getMessage() != null && e.getMessage().contains("Unknown tenant")) {
                throw new NotFoundException("Tenant", tenantId);
            }
            if (e instanceof DataIntegrityViolationException) {
                throw new IllegalArgumentException("Unknown plan: " + update.planCode());
            }
            throw e;
        }
        return tenants().stream().filter(t -> t.id().equals(tenantId)).findFirst().orElseThrow(() -> new NotFoundException("Tenant", tenantId));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
