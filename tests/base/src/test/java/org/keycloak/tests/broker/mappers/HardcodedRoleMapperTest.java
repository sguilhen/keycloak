package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.broker.provider.HardcodedRoleMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.keycloak.models.IdentityProviderMapperSyncMode.FORCE;
import static org.keycloak.models.IdentityProviderMapperSyncMode.IMPORT;
import static org.keycloak.models.IdentityProviderMapperSyncMode.LEGACY;

@KeycloakIntegrationTest
public class HardcodedRoleMapperTest extends AbstractOidcMapperTest implements RoleMapperTest {

    @Test
    public void mapperGrantsRoleOnFirstLogin() {
        createMapperThenLoginAsUserTwiceWithHardcodedRoleMapper(IMPORT);

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    public void mapperDoesNotGrantRoleInModeImportIfMapperIsAddedLater() {
        loginAsUserThenCreateMapperAndLoginAgainWithHardcodedRoleMapper(IMPORT);

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserDoesNotGrantRoleInLegacyMode() {
        loginAsUserThenCreateMapperAndLoginAgainWithHardcodedRoleMapper(LEGACY);

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserGrantsRoleInForceMode() {
        loginAsUserThenCreateMapperAndLoginAgainWithHardcodedRoleMapper(FORCE);

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserMatchDoesntDeleteRole() {
        createMapperThenLoginAsUserTwiceWithHardcodedRoleMapper(FORCE);

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    private void createMapperThenLoginAsUserTwiceWithHardcodedRoleMapper(IdentityProviderMapperSyncMode syncMode) {
        loginAsUserTwiceWithMapper(syncMode, false, Map.of(), () -> {
        });
    }

    private void loginAsUserThenCreateMapperAndLoginAgainWithHardcodedRoleMapper(
            IdentityProviderMapperSyncMode syncMode) {
        loginAsUserTwiceWithMapper(syncMode, true, Map.of(), () -> {
        });
    }

    @Override
    public void createMapperInIdp(IdentityProviderMapperSyncMode syncMode, String roleValue) {
        IdentityProviderMapperRepresentation hardcodedRoleMapper = new IdentityProviderMapperRepresentation();
        hardcodedRoleMapper.setName("oidc-hardcoded-role-mapper");
        hardcodedRoleMapper.setIdentityProviderMapper(HardcodedRoleMapper.PROVIDER_ID);
        hardcodedRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                ConfigConstants.ROLE, roleValue));

        persistMapper(hardcodedRoleMapper);
    }

    @Override
    public Map<String, List<String>> createUserConfigForRole(String roleValue) {
        return Map.of();
    }
}
