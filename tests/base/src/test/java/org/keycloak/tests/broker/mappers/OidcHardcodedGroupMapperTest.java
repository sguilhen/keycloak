package org.keycloak.tests.broker.mappers;

import java.util.Map;

import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.broker.provider.HardcodedGroupMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

/**
 * For simplicity, extends {@link OidcAdvancedClaimToGroupMapperTest} with a hardcoded group mapper to run all tests
 * from the super class. Since this mapper does not cause leaving the group when the claims do not match, the
 * {@code isHardcodedGroup} flag customizes the expected behavior in the super class.
 */
@KeycloakIntegrationTest
public class OidcHardcodedGroupMapperTest extends OidcAdvancedClaimToGroupMapperTest {

    @Override
    protected boolean isHardcodedGroup() {
        return true;
    }

    @Override
    public String createMapperInIdp(String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes, IdentityProviderMapperSyncMode syncMode, String groupPath) {
        IdentityProviderMapperRepresentation hardcodedGroupMapper = new IdentityProviderMapperRepresentation();
        hardcodedGroupMapper.setName("hardcoded-group-mapper");
        hardcodedGroupMapper.setIdentityProviderMapper(HardcodedGroupMapper.PROVIDER_ID);
        hardcodedGroupMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                ConfigConstants.GROUP, groupPath));

        return persistGroupMapper(hardcodedGroupMapper);
    }
}
