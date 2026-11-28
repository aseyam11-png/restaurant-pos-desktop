package com.restaurantpos.localapi.auth;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantpos.localapi.auth.AuthDtos.*;
import com.restaurantpos.localapi.cash.CashShiftDtos.OpenShiftRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "pos.security.local-token=integration-test-token")
@AutoConfigureMockMvc
@Transactional
class UserAuthorizationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AuthService auth;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void allBusinessRequestsRequireALocalUserSession() throws Exception {
        mvc.perform(get("/api/v1/catalog/products")
            .header("X-POS-Local-Token", "integration-test-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void cashierCanReadCatalogButCannotManagePurchasesOrUsers() throws Exception {
        String ownerName = "owner_" + UUID.randomUUID().toString().substring(0, 8);
        auth.bootstrap(new BootstrapRequest(ownerName, "Owner", "A-Strong-Password-2026"));
        String cashierName = "cashier_" + UUID.randomUUID().toString().substring(0, 8);
        auth.createStaff(new CreateStaffRequest(
            cashierName, "Front Cashier", "A-Temporary-Password-2026", StaffRole.CASHIER
        ));
        String token = auth.login(new LoginRequest(cashierName, "A-Temporary-Password-2026")).accessToken();

        mvc.perform(get("/api/v1/catalog/products")
            .header("X-POS-Local-Token", "integration-test-token")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/users")
            .header("X-POS-Local-Token", "integration-test-token")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/purchases")
            .header("X-POS-Local-Token", "integration-test-token")
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void businessActorComesFromTheAuthenticatedSessionNotTheRequestBody() throws Exception {
        auth.bootstrap(new BootstrapRequest(
            "owner_" + UUID.randomUUID().toString().substring(0, 8),
            "Trusted Owner",
            "A-Strong-Password-2026"
        ));
        String cashierName = "drawer_" + UUID.randomUUID().toString().substring(0, 8);
        auth.createStaff(new CreateStaffRequest(
            cashierName, "Authenticated Cashier", "A-Temporary-Password-2026", StaffRole.CASHIER
        ));
        String token = auth.login(new LoginRequest(cashierName, "A-Temporary-Password-2026")).accessToken();

        mvc.perform(post("/api/v1/cash-shifts/open")
            .header("X-POS-Local-Token", "integration-test-token")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new OpenShiftRequest(new BigDecimal("25.00"), "Forged Owner"))))
            .andExpect(status().isOk());

        String openedBy = jdbc.queryForObject(
            "SELECT opened_by FROM cash_shifts ORDER BY opened_at DESC LIMIT 1",
            String.class
        );
        assertEquals("Authenticated Cashier", openedBy);
        assertNotEquals("Forged Owner", openedBy);
    }
}
