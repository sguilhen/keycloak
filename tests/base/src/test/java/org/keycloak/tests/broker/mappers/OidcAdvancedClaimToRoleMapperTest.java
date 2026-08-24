package org.keycloak.tests.broker.mappers;

import java.util.HashMap;
import java.util.Map;

import org.keycloak.broker.oidc.mappers.AdvancedClaimToRoleMapper;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

@KeycloakIntegrationTest
public class OidcAdvancedClaimToRoleMapperTest extends AbstractOidcMapperTest implements AdvancedRoleMapperTest {

    @Override
    public void createMapperInIdp(String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes, IdentityProviderMapperSyncMode syncMode, String roleValue) {
        IdentityProviderMapperRepresentation advancedClaimToRoleMapper = new IdentityProviderMapperRepresentation();
        advancedClaimToRoleMapper.setName("advanced-claim-to-role-mapper");
        advancedClaimToRoleMapper.setIdentityProviderMapper(AdvancedClaimToRoleMapper.PROVIDER_ID);

        Map<String, String> config = new HashMap<>();
        config.put(IdentityProviderMapperModel.SYNC_MODE, syncMode.toString());
        config.put(AdvancedClaimToRoleMapper.CLAIM_PROPERTY_NAME, claimsOrAttributeRepresentation);
        config.put(AdvancedClaimToRoleMapper.ARE_CLAIM_VALUES_REGEX_PROPERTY_NAME,
                Boolean.valueOf(areClaimsOrAttributeValuesRegexes).toString());
        config.put(ConfigConstants.ROLE, roleValue);
        advancedClaimToRoleMapper.setConfig(config);

        persistMapper(advancedClaimToRoleMapper);
    }
}
