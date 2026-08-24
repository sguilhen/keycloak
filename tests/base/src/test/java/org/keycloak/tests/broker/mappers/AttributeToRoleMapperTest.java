package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.broker.saml.mappers.AttributeToRoleMapper;
import org.keycloak.broker.saml.mappers.UserAttributeMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.keycloak.models.IdentityProviderMapperSyncMode.FORCE;
import static org.keycloak.models.IdentityProviderMapperSyncMode.LEGACY;

@KeycloakIntegrationTest
public class AttributeToRoleMapperTest extends AbstractSamlMapperTest implements RoleMapperTest {

    protected static final String ROLE_ATTR_NAME = "Role";

    @Test
    public void mapperGrantsRoleOnFirstLogin() {
        createMapperThenLoginAsUserTwiceWithAttributeToRoleMapper();

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserGrantsRoleInLegacyMode() {
        loginAsUserThenCreateMapperAndLoginAgainWithAttributeToRoleMapper(LEGACY);

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserGrantsRoleInForceMode() {
        loginAsUserThenCreateMapperAndLoginAgainWithAttributeToRoleMapper(FORCE);

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    private void createMapperThenLoginAsUserTwiceWithAttributeToRoleMapper() {
        loginAsUserTwiceWithMapper(FORCE, false,
                createUserConfigForRole(CLIENT_ROLE_MAPPER_REPRESENTATION), () -> {
                });
    }

    private void loginAsUserThenCreateMapperAndLoginAgainWithAttributeToRoleMapper(
            IdentityProviderMapperSyncMode syncMode) {
        loginAsUserTwiceWithMapper(syncMode, true,
                createUserConfigForRole(CLIENT_ROLE_MAPPER_REPRESENTATION), () -> {
                });
    }

    @Override
    public void createMapperInIdp(IdentityProviderMapperSyncMode syncMode, String roleValue) {
        IdentityProviderMapperRepresentation samlAttributeToRoleMapper = new IdentityProviderMapperRepresentation();
        samlAttributeToRoleMapper.setName("user-role-mapper");
        samlAttributeToRoleMapper.setIdentityProviderMapper(AttributeToRoleMapper.PROVIDER_ID);
        samlAttributeToRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.ATTRIBUTE_NAME, ROLE_ATTR_NAME,
                AttributeToRoleMapper.ATTRIBUTE_VALUE, ROLE_USER,
                ConfigConstants.ROLE, roleValue));

        persistMapper(samlAttributeToRoleMapper);
    }

    @Override
    public Map<String, List<String>> createUserConfigForRole(String roleValue) {
        return Map.of(ROLE_ATTR_NAME, List.of(roleValue));
    }
}
