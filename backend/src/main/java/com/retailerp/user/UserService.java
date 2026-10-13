package com.retailerp.user;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.NotFoundException;
import com.retailerp.user.UserDtos.CreateUserRequest;
import com.retailerp.user.UserDtos.UpdateUserRequest;
import com.retailerp.user.UserDtos.UserResponse;

/**
 * {@code @Transactional} on the class: every public method runs in a DB transaction (Spring wraps this bean in a
 * proxy that begins/commits/rolls back around each call). readOnly=true is a hint for read-only methods.
 */
@Service
@Transactional
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByOrderByFullNameAsc().stream().map(UserResponse::from).toList();
    }

    public UserResponse create(CreateUserRequest request) {
        User user = new User(request.email(), passwordEncoder.encode(request.password()), request.fullName().trim(), request.role());
        // saveAndFlush: hit the unique email index now, so a duplicate becomes a 409 here, not at commit time.
        return UserResponse.from(users.saveAndFlush(user));
    }

    public UserResponse update(UUID id, UpdateUserRequest request, UUID currentUserId) {
        User user = get(id);
        boolean losesAdmin = user.getRole() == Role.ADMIN && user.isEnabled() && (request.role() != Role.ADMIN || !request.enabled());
        if (losesAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("A tenant must keep at least one enabled ADMIN.");
        }
        if (id.equals(currentUserId) && !request.enabled()) {
            throw new BusinessRuleException("You can't disable your own account.");
        }
        user.update(request.fullName().trim(), request.role(), request.enabled());
        return UserResponse.from(user);
    }

    public void resetPassword(UUID id, String password) {
        get(id).changePassword(passwordEncoder.encode(password));
    }

    private User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }
}
