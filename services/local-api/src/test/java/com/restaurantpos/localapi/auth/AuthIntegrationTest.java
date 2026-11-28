package com.restaurantpos.localapi.auth;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.auth.AuthDtos.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "pos.security.local-token=integration-test-token")
@Transactional
class AuthIntegrationTest {
    @Autowired private AuthService auth;

    @Test
    void firstRunCreatesOwnerAndDisablesBootstrapAfterwards() {
        assertFalse(auth.setupStatus().initialized());

        AuthSession initial = auth.bootstrap(new BootstrapRequest(
            "owner_" + java.util.UUID.randomUUID().toString().substring(0, 8),
            "Restaurant Owner",
            "A-Strong-Initial-Password-2026"
        ));
        assertEquals("OWNER", initial.role());
        assertNotNull(initial.accessToken());
        assertFalse(initial.accessToken().isBlank());
        assertTrue(auth.setupStatus().initialized());
        assertNotNull(auth.authenticate(initial.accessToken()));

        assertThrows(PosException.class, () -> auth.bootstrap(new BootstrapRequest(
            "another.owner", "Second Owner", "Another-Strong-Password-2026"
        )));
    }

    @Test
    void loginIssuesRevocableTwelveHourSession() {
        String username = "login_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        auth.bootstrap(new BootstrapRequest(username, "Test Owner", "A-Strong-Password-2026"));

        AuthSession login = auth.login(new LoginRequest(username.toUpperCase(), "A-Strong-Password-2026"));
        assertEquals("OWNER", login.role());
        assertTrue(login.expiresAt().isAfter(java.time.OffsetDateTime.now().plusHours(11)));
        assertNotNull(auth.authenticate(login.accessToken()));

        auth.logout(login.accessToken());
        assertNull(auth.authenticate(login.accessToken()));
    }

    @Test
    void failedPasswordsAreRateLimitedAndPersisted() {
        String username = "lock_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        auth.bootstrap(new BootstrapRequest(username, "Locked Owner", "A-Strong-Password-2026"));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThrows(PosException.class, () ->
                auth.login(new LoginRequest(username, "Incorrect-Password-2026"))
            );
        }
        assertThrows(PosException.class, () ->
            auth.login(new LoginRequest(username, "Incorrect-Password-2026"))
        );
        assertThrows(PosException.class, () ->
            auth.login(new LoginRequest(username, "A-Strong-Password-2026"))
        );
    }

    @Test
    void ownerCanCreateAndDeactivateCashierAccount() {
        auth.bootstrap(new BootstrapRequest(
            "admin_" + java.util.UUID.randomUUID().toString().substring(0, 8),
            "Admin Owner",
            "A-Strong-Password-2026"
        ));

        StaffView staff = auth.createStaff(new CreateStaffRequest(
            "cashier_" + java.util.UUID.randomUUID().toString().substring(0, 8),
            "Front Cashier",
            "A-Temporary-Password-2026",
            StaffRole.CASHIER
        ));
        assertEquals("CASHIER", staff.role());
        assertTrue(staff.active());
        auth.deactivateStaff(staff.userId());
        assertFalse(auth.listStaff().stream().filter(x -> x.userId().equals(staff.userId())).findFirst().orElseThrow().active());
    }
}
