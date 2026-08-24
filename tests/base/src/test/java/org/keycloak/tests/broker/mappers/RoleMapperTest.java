package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.models.Constants;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.util.AccountHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Base support for role-mapper tests. Stateless: the consumer client UUID and the set of IdP mappers
 * are always re-derived from the admin API rather than stored in fields, and per-login mutations of
 * the provider user are supplied as a {@link Runnable} instead of an overridable {@code updateUser()}
 * hook. Mirrors the legacy {@code AbstractRoleMapperTest}.
 */
public interface RoleMapperTest extends IdentityProviderMapperTest {

    String MAPPER_TEST_CLIENT_ID = "mapper-test-client";
    String CLIENT_ROLE = "test-role";
    String CLIENT_ROLE_MAPPER_REPRESENTATION = MAPPER_TEST_CLIENT_ID + "." + CLIENT_ROLE;
    String ROLE_USER = "user";
    String REALM_ROLE = "test-realm-role";

    static String createClientRoleString(final String clientId, final String roleName) {
        return clientId + "." + roleName;
    }

    void createMapperInIdp(IdentityProviderMapperSyncMode syncMode, String roleValue);

    Map<String, List<String>> createUserConfigForRole(String roleValue);

    /**
     * Creates the client and roles the mapper tests grant to the brokered user. Runs on a fresh
     * consumer realm per test (METHOD lifecycle), so it never collides with a previous run.
     */
    @BeforeEach
    default void setupRoleMapperClientAndRoles() {
        RealmResource consumer = getConsumerRealm().admin();

        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(MAPPER_TEST_CLIENT_ID);
        String clientUuid;
        try (Response response = consumer.clients().create(client)) {
            clientUuid = CreatedResponseUtil.getCreatedId(response);
        }
        consumer.clients().get(clientUuid).roles().create(roleNamed(CLIENT_ROLE));
        consumer.roles().create(roleNamed(REALM_ROLE));
    }

    static RoleRepresentation roleNamed(String name) {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(name);
        return role;
    }

    default String getConsumerClientUuid() {
        return getConsumerRealm().admin().clients().findByClientId(MAPPER_TEST_CLIENT_ID).get(0).getId();
    }

    default String getProviderUserId() {
        return getProviderRealm().admin().users().search(getUserLogin(), true).get(0).getId();
    }

    @Test
    default void tryToCreateBrokeredUserWithNonExistingClientRoleDoesNotBreakLogin() {
        String clientRoleStringWithMissingRole = createClientRoleString(MAPPER_TEST_CLIENT_ID, "does-not-exist");
        setup(clientRoleStringWithMissingRole);

        loginAsUserFirstTimeAndAssertSuccess();

        assertThatNoRolesHaveBeenAssignedInConsumerRealm();
    }

    /**
     * This test checks that the mapper can also be applied to realm roles (other tests mostly use client roles).
     */
    @Test
    default void mapperCanBeAppliedToRealmRoles() {
        setup(REALM_ROLE);

        loginAsUserFirstTimeAndAssertSuccess();

        assertThatRoleHasBeenAssignedInConsumerRealm(REALM_ROLE);
    }

    @Test
    default void mapperStillWorksWhenClientRoleIsRenamed() {
        setup(CLIENT_ROLE_MAPPER_REPRESENTATION);

        RealmResource consumer = getConsumerRealm().admin();
        String clientUuid = getConsumerClientUuid();
        String newRoleName = "new-name-" + CLIENT_ROLE;
        RoleRepresentation mappedRole = consumer.clients().get(clientUuid).roles().get(CLIENT_ROLE).toRepresentation();
        mappedRole.setName(newRoleName);
        consumer.clients().get(clientUuid).roles().get(CLIENT_ROLE).update(mappedRole);

        String expectedNewClientRoleName = createClientRoleString(MAPPER_TEST_CLIENT_ID, newRoleName);

        // mapper(s) should have been updated to the new client role name
        assertMappersAreConfiguredWithRole(expectedNewClientRoleName);

        loginAsUserFirstTimeAndAssertSuccess();

        assertThatRoleHasBeenAssignedInConsumerRealm(MAPPER_TEST_CLIENT_ID, newRoleName);
    }

    @Test
    default void mapperStillWorksWhenClientIdIsChanged() {
        setup(CLIENT_ROLE_MAPPER_REPRESENTATION);

        RealmResource consumer = getConsumerRealm().admin();
        String clientUuid = getConsumerClientUuid();
        String newClientId = "new-name-" + MAPPER_TEST_CLIENT_ID;
        ClientRepresentation mappedClient = consumer.clients().get(clientUuid).toRepresentation();
        mappedClient.setClientId(newClientId);
        consumer.clients().get(clientUuid).update(mappedClient);

        String expectedNewClientRoleName = createClientRoleString(newClientId, CLIENT_ROLE);

        // mapper(s) should have been updated to the new client role name
        assertMappersAreConfiguredWithRole(expectedNewClientRoleName);

        AccountHelper.logout(getProviderRealm().admin(), getUserLogin());

        loginAsUserFirstTimeAndAssertSuccess();

        assertThatRoleHasBeenAssignedInConsumerRealm(newClientId, CLIENT_ROLE);
    }

    @Test
    default void mapperStillWorksWhenRealmRoleIsRenamed() {
        setup(REALM_ROLE);

        RealmResource consumer = getConsumerRealm().admin();
        String newRoleName = "new-name-" + REALM_ROLE;
        RoleRepresentation mappedRole = consumer.roles().get(REALM_ROLE).toRepresentation();
        mappedRole.setName(newRoleName);
        consumer.roles().get(REALM_ROLE).update(mappedRole);

        // mapper(s) should have been updated to the new realm role name
        assertMappersAreConfiguredWithRole(newRoleName);

        loginAsUserFirstTimeAndAssertSuccess();

        assertThatRoleHasBeenAssignedInConsumerRealm(newRoleName);
    }

    /**
     * A fresh consumer realm (METHOD lifecycle) starts with no IdP mappers, so every mapper present
     * was added by the test. Assert they all point at the expected (possibly renamed) role.
     */
    default void assertMappersAreConfiguredWithRole(String expectedRoleQualifier) {
        List<IdentityProviderMapperRepresentation> mappers = getIdpResource().getMappers();
        Assertions.assertFalse(mappers.isEmpty(), "Expected at least one IdP mapper to be configured");
        for (IdentityProviderMapperRepresentation mapper : mappers) {
            Assertions.assertEquals(expectedRoleQualifier, mapper.getConfig().get(ConfigConstants.ROLE),
                    "Mapper '" + mapper.getName() + "' should be configured with role " + expectedRoleQualifier);
        }
    }

    default void persistMapper(IdentityProviderMapperRepresentation idpMapper) {
        idpMapper.setIdentityProviderAlias(getIdpAlias());
        getIdpResource().addMapper(idpMapper).close();
    }

    /**
     * Logs the user in twice with the mapper active. The {@code updateAction} performs whatever
     * provider-side mutation should happen between the two logins (replaces the legacy per-subclass
     * {@code updateUser()} override, so no mutable instance state is required).
     */
    default void loginAsUserTwiceWithMapper(IdentityProviderMapperSyncMode syncMode, boolean createAfterFirstLogin,
            Map<String, List<String>> userConfig, Runnable updateAction) {
        if (!createAfterFirstLogin) {
            createMapperInIdp(syncMode, CLIENT_ROLE_MAPPER_REPRESENTATION);
        }
        setupUser(userConfig);

        loginAsUserFirstTime();

        if (!createAfterFirstLogin) {
            assertThatRoleHasBeenAssignedInConsumerRealm();
        } else {
            assertThatRoleHasNotBeenAssignedInConsumerRealm();
        }

        if (createAfterFirstLogin) {
            createMapperInIdp(syncMode, CLIENT_ROLE_MAPPER_REPRESENTATION);
        }
        AccountHelper.logout(getConsumerRealm().admin(), getUserLogin());
        AccountHelper.logout(getProviderRealm().admin(), getUserLogin());

        updateAction.run();

        logInAsUserInIDP();
    }

    default void setup(String roleValue) {
        createMapperInIdp(IdentityProviderMapperSyncMode.IMPORT, roleValue);
        setupUser(createUserConfigForRole(roleValue));
    }

    default void setupUser(Map<String, List<String>> userConfig) {
        createUserInProviderRealm(userConfig);
        createUserRoleAndGrantToUserInProviderRealm();
    }

    default void createUserRoleAndGrantToUserInProviderRealm() {
        RealmResource providerRealm = getProviderRealm().admin();
        providerRealm.roles().create(roleNamed(ROLE_USER));
        RoleRepresentation role = providerRealm.roles().get(ROLE_USER).toRepresentation();
        providerRealm.users().get(getProviderUserId()).roles().realmLevel().add(List.of(role));
    }

    default void assertThatRoleHasBeenAssignedInConsumerRealm() {
        assertThatRoleHasBeenAssignedInConsumerRealm(MAPPER_TEST_CLIENT_ID, CLIENT_ROLE);
    }

    default void assertThatRoleHasNotBeenAssignedInConsumerRealm() {
        UserRepresentation user = getConsumerUser();
        List<String> clientRoles = user.getClientRoles().get(MAPPER_TEST_CLIENT_ID);
        Assertions.assertTrue(clientRoles == null || !clientRoles.contains(CLIENT_ROLE),
                "Role " + CLIENT_ROLE + " should not have been assigned");
    }

    default void assertThatRoleHasBeenAssignedInConsumerRealm(String clientId, String roleName) {
        UserRepresentation user = getConsumerUser();
        List<String> clientRoles = user.getClientRoles().get(clientId);
        Assertions.assertNotNull(clientRoles, "Expected client roles for " + clientId);
        Assertions.assertTrue(clientRoles.contains(roleName),
                "Expected client role " + roleName + " to be assigned");
    }

    default void assertThatRoleHasBeenAssignedInConsumerRealm(String roleName) {
        UserRepresentation user = getConsumerUser();
        Assertions.assertTrue(user.getRealmRoles().contains(roleName),
                "Expected realm role " + roleName + " to be assigned");
    }

    /**
     * Check that just initial (default) roles are assigned to the user.
     */
    default void assertThatNoRolesHaveBeenAssignedInConsumerRealm() {
        UserRepresentation user = getConsumerUser();

        Map<String, List<String>> clientRoles = user.getClientRoles();
        boolean onlyDefaultClientRoles = clientRoles.isEmpty()
                || (clientRoles.size() == 1
                        && List.of(Constants.READ_TOKEN_ROLE).equals(clientRoles.get(Constants.BROKER_SERVICE_CLIENT_ID)));
        Assertions.assertTrue(onlyDefaultClientRoles, "Expected no mapped client roles to be assigned");

        List<String> realmRoles = user.getRealmRoles();
        Assertions.assertEquals(1, realmRoles.size(), "Expected only the default realm role");
        Assertions.assertEquals(Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + CONSUMER_REALM, realmRoles.get(0));
    }

    default UserRepresentation getConsumerUser() {
        return findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
    }
}
