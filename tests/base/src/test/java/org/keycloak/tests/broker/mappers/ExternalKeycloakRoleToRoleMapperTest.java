package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.broker.oidc.mappers.ExternalKeycloakRoleToRoleMapper;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.keycloak.models.IdentityProviderMapperSyncMode.FORCE;
import static org.keycloak.models.IdentityProviderMapperSyncMode.IMPORT;
import static org.keycloak.models.IdentityProviderMapperSyncMode.LEGACY;

@KeycloakIntegrationTest
public class ExternalKeycloakRoleToRoleMapperTest extends AbstractOidcMapperTest implements RoleMapperTest {

    @Test
    public void mapperGrantsRoleOnFirstLogin() {
        createMapperThenLoginAsUserTwiceWithExternalKeycloakRoleToRoleMapper(IMPORT);

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserDoesNotGrantRoleInLegacyMode() {
        loginAsUserThenCreateMapperAndLoginAgainWithExternalKeycloakRoleToRoleMapper(LEGACY);

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserGrantsRoleInForceMode() {
        loginAsUserThenCreateMapperAndLoginAgainWithExternalKeycloakRoleToRoleMapper(FORCE);

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserMatchDeletesRoleInForceMode() {
        createMapperThenLoginAsUserTwiceWithExternalKeycloakRoleToRoleMapper(FORCE);

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserMatchDoesNotDeleteRoleInLegacyMode() {
        createMapperThenLoginAsUserTwiceWithExternalKeycloakRoleToRoleMapper(LEGACY);

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    private void createMapperThenLoginAsUserTwiceWithExternalKeycloakRoleToRoleMapper(
            IdentityProviderMapperSyncMode syncMode) {
        loginAsUserTwiceWithMapper(syncMode, false, Map.of(), () -> removeUserRole(true));
    }

    private void loginAsUserThenCreateMapperAndLoginAgainWithExternalKeycloakRoleToRoleMapper(
            IdentityProviderMapperSyncMode syncMode) {
        loginAsUserTwiceWithMapper(syncMode, true, Map.of(), () -> removeUserRole(false));
    }

    @Override
    public void createMapperInIdp(IdentityProviderMapperSyncMode syncMode, String roleValue) {
        IdentityProviderMapperRepresentation externalRoleToRoleMapper = new IdentityProviderMapperRepresentation();
        externalRoleToRoleMapper.setName("external-keycloak-role-mapper");
        externalRoleToRoleMapper.setIdentityProviderMapper(ExternalKeycloakRoleToRoleMapper.PROVIDER_ID);
        externalRoleToRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                "external.role", ROLE_USER,
                ConfigConstants.ROLE, roleValue));

        persistMapper(externalRoleToRoleMapper);
    }

    private void removeUserRole(boolean deleteRoleFromUser) {
        if (!deleteRoleFromUser) {
            return;
        }
        RealmResource providerRealm = getProviderRealm().admin();
        RoleRepresentation role = providerRealm.roles().get(ROLE_USER).toRepresentation();
        providerRealm.users().get(getProviderUserId()).roles().realmLevel().remove(List.of(role));
    }

    @Override
    public Map<String, List<String>> createUserConfigForRole(String roleValue) {
        return Map.of();
    }
}
