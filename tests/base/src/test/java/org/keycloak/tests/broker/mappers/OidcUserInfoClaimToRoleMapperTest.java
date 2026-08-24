package org.keycloak.tests.broker.mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.broker.oidc.mappers.ClaimToRoleMapper;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.protocol.ProtocolMapperUtils;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.UserAttributeMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.tests.broker.KcOidcBrokerConfigSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies a claim-to-role mapper works when the mapped claim is only exposed by the provider's
 * user-info endpoint (not in the id/access token). Reconfigures the provider client to a single
 * user-info-only mapper, mirroring the legacy inner {@code KcOidcBrokerConfigurationUserInfoOnlyMappers}.
 */
@KeycloakIntegrationTest
public class OidcUserInfoClaimToRoleMapperTest extends AbstractOidcMapperTest implements RoleMapperTest {

    protected static final String ATTRIBUTE_TO_MAP_USER_INFO = "user-attribute-info";
    private static final String USER_INFO_CLAIM = ATTRIBUTE_TO_MAP_USER_INFO;
    private static final String USER_INFO_CLAIM_VALUE = "value 1";

    @BeforeEach
    public void restrictProviderClaimsToUserInfo() {
        RealmResource provider = getProviderRealm().admin();
        ClientRepresentation client = provider.clients().findByClientId(KcOidcBrokerConfigSupport.CLIENT_ID).get(0);
        ProtocolMappersResource mappers = provider.clients().get(client.getId()).getProtocolMappers();

        // drop all pre-configured client mappers so the attribute is not exposed in the id/access token
        for (ProtocolMapperRepresentation mapper : mappers.getMappers()) {
            mappers.delete(mapper.getId());
        }

        // expose the attribute only through the user-info endpoint
        ProtocolMapperRepresentation userAttrMapper = new ProtocolMapperRepresentation();
        userAttrMapper.setName("attribute - name");
        userAttrMapper.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        userAttrMapper.setProtocolMapper(UserAttributeMapper.PROVIDER_ID);
        Map<String, String> config = new HashMap<>();
        config.put(ProtocolMapperUtils.USER_ATTRIBUTE, ATTRIBUTE_TO_MAP_USER_INFO);
        config.put(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, ATTRIBUTE_TO_MAP_USER_INFO);
        config.put(OIDCAttributeMapperHelper.JSON_TYPE, ProviderConfigProperty.STRING_TYPE);
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN, "false");
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN, "false");
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_USERINFO, "true");
        userAttrMapper.setConfig(config);
        mappers.createMapper(userAttrMapper).close();
    }

    @Test
    public void singleClaimValueInUserInfoMatches() {
        createClaimToRoleMapper();
        createUserInProviderRealm(createUserConfig());

        loginAsUserFirstTime();

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    public void noRoleAddedIfUserInfoDisabledAndOnlyClaimIsInUserInfo() {
        createClaimToRoleMapperWithUserInfoDisabledInIdP();
        createUserInProviderRealm(createUserConfig());

        loginAsUserFirstTime();

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    private void createClaimToRoleMapper() {
        createClaimToRoleMapper(USER_INFO_CLAIM_VALUE, IdentityProviderMapperSyncMode.IMPORT,
                CLIENT_ROLE_MAPPER_REPRESENTATION);
    }

    private void createClaimToRoleMapperWithUserInfoDisabledInIdP() {
        setupIdentityProviderDisableUserInfo();
        createClaimToRoleMapper(USER_INFO_CLAIM_VALUE, IdentityProviderMapperSyncMode.IMPORT,
                CLIENT_ROLE_MAPPER_REPRESENTATION);
    }

    @Override
    public void createMapperInIdp(IdentityProviderMapperSyncMode syncMode, String roleValue) {
        createClaimToRoleMapper(USER_INFO_CLAIM_VALUE, syncMode, roleValue);
    }

    private void createClaimToRoleMapper(String claimValue, IdentityProviderMapperSyncMode syncMode, String roleValue) {
        IdentityProviderMapperRepresentation claimToRoleMapper = new IdentityProviderMapperRepresentation();
        claimToRoleMapper.setName("userinfo-claim-to-role-mapper");
        claimToRoleMapper.setIdentityProviderMapper(ClaimToRoleMapper.PROVIDER_ID);
        claimToRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                ClaimToRoleMapper.CLAIM, USER_INFO_CLAIM,
                ClaimToRoleMapper.CLAIM_VALUE, claimValue,
                ConfigConstants.ROLE, roleValue));

        persistMapper(claimToRoleMapper);
    }

    @Override
    public Map<String, List<String>> createUserConfigForRole(String roleValue) {
        return createUserConfig();
    }

    private static Map<String, List<String>> createUserConfig() {
        return Map.of(USER_INFO_CLAIM, List.of(USER_INFO_CLAIM_VALUE));
    }
}
