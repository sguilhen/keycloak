package org.keycloak.tests.broker.mappers;

import java.util.Map;

import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.broker.oidc.mappers.AdvancedClaimToRoleMapper;
import org.keycloak.broker.oidc.mappers.ClaimToRoleMapper;
import org.keycloak.broker.oidc.mappers.ExternalKeycloakRoleToRoleMapper;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.BeforeEach;

/**
 * Runs the same tests as {@link OidcClaimToRoleMapperTest} but using multiple OIDC mappers that map
 * different IDP claims to the same {@code Keycloak} role. Regression coverage for {@code KEYCLOAK-8730}:
 * a later mapper must not remove a role a previous mapper has already granted.
 */
@KeycloakIntegrationTest
public class OidcMultipleClaimToRoleMappersTest extends OidcClaimToRoleMapperTest {

    private static final String CLAIMS_OR_ATTRIBUTES = "[\n" +
            "  {\n" +
            "    \"key\": \"test attribute\",\n" +
            "    \"value\": \"test value*\"\n" +
            "  }\n" +
            "]";

    /**
     * This variant installs an {@link ExternalKeycloakRoleToRoleMapper}, whose {@code applies()} reads
     * the brokered access token straight from {@code VALIDATED_ACCESS_TOKEN} and would NPE if it were
     * absent. The generic {@code oidc} provider only stores that token when it is parsed as a JWT, so
     * enable it here - the legacy suite got this for free from the {@code keycloak-oidc} provider.
     */
    @BeforeEach
    void parseAccessTokenAsJwt() {
        IdentityProviderRepresentation idp = getIdpResource().toRepresentation();
        idp.getConfig().put(OIDCIdentityProviderConfig.IS_ACCESS_TOKEN_JWT, "true");
        getIdpResource().update(idp);
    }

    @Override
    protected void createClaimToRoleMapper(String claimValue, IdentityProviderMapperSyncMode syncMode,
            String roleValue) {
        // first mapper that maps attributes the user has - it should perform the mapping to the expected role.
        IdentityProviderMapperRepresentation firstOidcClaimToRoleMapper = new IdentityProviderMapperRepresentation();
        firstOidcClaimToRoleMapper.setName("claim-to-role-mapper");
        firstOidcClaimToRoleMapper.setIdentityProviderMapper(ClaimToRoleMapper.PROVIDER_ID);
        firstOidcClaimToRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                ClaimToRoleMapper.CLAIM, CLAIM,
                ClaimToRoleMapper.CLAIM_VALUE, claimValue,
                ConfigConstants.ROLE, roleValue));

        persistMapper(firstOidcClaimToRoleMapper);

        // second mapper that maps an external role claim the test user doesn't have - it must not remove
        // the role granted by the first mapper.
        IdentityProviderMapperRepresentation secondOidcClaimToRoleMapper = new IdentityProviderMapperRepresentation();
        secondOidcClaimToRoleMapper.setName("external-keycloak-role-mapper");
        secondOidcClaimToRoleMapper.setIdentityProviderMapper(ExternalKeycloakRoleToRoleMapper.PROVIDER_ID);
        secondOidcClaimToRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                "external.role", "missing-role",
                ConfigConstants.ROLE, roleValue));

        persistMapper(secondOidcClaimToRoleMapper);

        // third mapper (advanced) that maps a claim the test user doesn't have - it must not remove the
        // role granted by the first mapper.
        IdentityProviderMapperRepresentation thirdOidcClaimToRoleMapper = new IdentityProviderMapperRepresentation();
        thirdOidcClaimToRoleMapper.setName("advanced-claim-to-role-mapper");
        thirdOidcClaimToRoleMapper.setIdentityProviderMapper(AdvancedClaimToRoleMapper.PROVIDER_ID);
        thirdOidcClaimToRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                AdvancedClaimToRoleMapper.CLAIM_PROPERTY_NAME, CLAIMS_OR_ATTRIBUTES,
                AdvancedClaimToRoleMapper.ARE_CLAIM_VALUES_REGEX_PROPERTY_NAME, Boolean.TRUE.toString(),
                ConfigConstants.ROLE, roleValue));

        persistMapper(thirdOidcClaimToRoleMapper);
    }
}
