package com.retailerp.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.NotFoundException;
import com.retailerp.security.AuthDtos.AuthResponse;
import com.retailerp.security.AuthDtos.LoginRequest;
import com.retailerp.security.AuthDtos.MeResponse;
import com.retailerp.security.AuthDtos.RegisterRequest;
import com.retailerp.security.JwtService.AuthenticatedUser;
import com.retailerp.tenancy.TenantContext;
import com.retailerp.tenant.Tenant;
import com.retailerp.tenant.TenantRepository;
import com.retailerp.user.Role;
import com.retailerp.user.User;
import com.retailerp.user.UserRepository;

@Service
public class AuthService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Compared against when the email doesn't exist, so "unknown email" and "wrong password" take the same time. */
    private final String dummyHash;

    public AuthService(JdbcTemplate jdbc, TransactionTemplate tx, TenantRepository tenants, UserRepository users,
            PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** Creates a new tenant (business) and its first ADMIN user, then logs them in. */
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (findLoginRow(email).isPresent()) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        UUID tenantId = UUID.randomUUID();
        // The tenant must be "current" BEFORE the transaction starts, so the connection is tagged with it and the
        // RLS WITH CHECK clause accepts the new rows.
        AuthenticatedUser user = TenantContext.callAs(tenantId, () -> tx.execute(status -> {
            Tenant tenant = tenants.save(new Tenant(tenantId, request.companyName().trim()));
            User admin = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName().trim(), Role.ADMIN));
            return new AuthenticatedUser(admin.getId(), tenant.getId(), admin.getEmail(), admin.getFullName(), admin.getRole());
        }));
        return respond(user, request.companyName().trim());
    }

    public AuthResponse login(LoginRequest request) {
        Optional<LoginRow> row = findLoginRow(request.email().trim());
        String hash = row.map(LoginRow::passwordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (row.isEmpty() || !matches || !row.get().enabled()) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        LoginRow r = row.get();
        AuthenticatedUser user = new AuthenticatedUser(r.id(), r.tenantId(), r.email(), r.fullName(), r.role());
        String tenantName = TenantContext.callAs(r.tenantId(),
                () -> tx.execute(status -> tenants.findById(r.tenantId()).map(Tenant::getName).orElse("")));
        return respond(user, tenantName);
    }

    /** Runs inside a request, where TenantContext was set from the JWT. */
    public MeResponse me(UUID userId) {
        return tx.execute(status -> {
            User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
            Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow(() -> new NotFoundException("Tenant", user.getTenantId()));
            return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), tenant.getId(), tenant.getName());
        });
    }

    private AuthResponse respond(AuthenticatedUser user, String tenantName) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.token(), token.expiresAt(),
                new MeResponse(user.userId(), user.email(), user.fullName(), user.role(), user.tenantId(), tenantName));
    }

    /** Cross-tenant lookup through the SECURITY DEFINER function (RLS would otherwise hide every user). */
    private Optional<LoginRow> findLoginRow(String email) {
        return jdbc.query("SELECT * FROM auth_find_user_by_email(?)", (rs, i) -> new LoginRow(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("enabled")), email).stream().findFirst();
    }

    private record LoginRow(UUID id, UUID tenantId, String email, String passwordHash, String fullName, Role role, boolean enabled) {
    }
}
