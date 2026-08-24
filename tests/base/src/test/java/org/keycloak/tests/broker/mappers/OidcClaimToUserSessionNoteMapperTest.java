package org.keycloak.tests.broker.mappers;

import java.util.HashMap;
import java.util.Map;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.IdentityProviderResource;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.broker.oidc.mappers.ClaimToUserSessionNoteMapper;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.HardcodedClaim;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.UserSessionNoteMapper;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testsuite.util.AccountHelper;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;
import org.keycloak.testsuite.util.oauth.AuthorizationEndpointResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors the legacy {@code OidcClaimToUserSessionNoteMapperTest}. The provider client emits a
 * hardcoded access-token claim, the consumer's {@link ClaimToUserSessionNoteMapper} copies it into a
 * user session note on import/force, and a {@link UserSessionNoteMapper} on the token-requesting
 * client surfaces it back into the consumer access token where the assertions can read it.
 *
 * <p>The legacy test requested tokens from the consumer {@code broker-app} client; here the token is
 * requested from the injected OAuth client (the auto-created {@code test-app}), so the session-note
 * protocol mapper is installed on that client instead.
 */
@KeycloakIntegrationTest
public class OidcClaimToUserSessionNoteMapperTest extends AbstractOidcMapperTest implements IdentityProviderMapperTest {

    private static final String CLAIM_NAME = "sessionNoteTest";
    private static final String CLAIM_VALUE = "foo";
    private static final String CONFIG_PROPERTY_CLAIMS = "claims";

    private String providerClientUuid;
    private String providerHardcodedClaimMapperId;

    @BeforeEach
    void setupMappers() {
        // The provider user already carries first/last name in the shared realm config, so the
        // first-broker-login review page is skipped and login() can complete without extra input.
        createUserInProviderRealm(Map.of());

        // The provider only emits the tracked claim in its access token, so the consumer IdP must
        // parse the brokered access token as a JWT for the ClaimToUserSessionNoteMapper to read it
        // (this stores VALIDATED_ACCESS_TOKEN). The legacy suite gets this via the keycloak-oidc
        // provider defaults; the new shared config uses the generic oidc provider, so enable it here.
        var idpRep = getConsumerRealm().admin().identityProviders().get(getIdpAlias()).toRepresentation();
        idpRep.getConfig().put(OIDCIdentityProviderConfig.IS_ACCESS_TOKEN_JWT, "true");
        getConsumerRealm().admin().identityProviders().get(getIdpAlias()).update(idpRep);

        // Surface the imported user session note into the access token issued to the token client.
        ProtocolMapperRepresentation consumerSessionNoteToClaimMapper = new ProtocolMapperRepresentation();
        consumerSessionNoteToClaimMapper.setName("Session Note To Claim");
        consumerSessionNoteToClaimMapper.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        consumerSessionNoteToClaimMapper.setProtocolMapper(UserSessionNoteMapper.PROVIDER_ID);
        consumerSessionNoteToClaimMapper.setConfig(Map.of(
                "user.session.note", CLAIM_NAME,
                OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, CLAIM_NAME,
                OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN, "true"));
        getOAuthClient().clientResource().getProtocolMappers().createMapper(consumerSessionNoteToClaimMapper).close();

        // The provider client hardcodes the claim the consumer IdP mapper looks for.
        RealmResource providerRealm = getProviderRealm().admin();
        ClientRepresentation providerClientRep = providerRealm.clients().findByClientId(CLIENT_ID).get(0);
        providerClientUuid = providerClientRep.getId();

        ProtocolMapperRepresentation providerHardcodedClaimMapper = new ProtocolMapperRepresentation();
        providerHardcodedClaimMapper.setName("Hardcoded Claim");
        providerHardcodedClaimMapper.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        providerHardcodedClaimMapper.setProtocolMapper(HardcodedClaim.PROVIDER_ID);
        providerHardcodedClaimMapper.setConfig(Map.of(
                OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, CLAIM_NAME,
                HardcodedClaim.CLAIM_VALUE, CLAIM_VALUE,
                OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN, "true"));
        providerHardcodedClaimMapperId = CreatedResponseUtil.getCreatedId(
                providerRealm.clients().get(providerClientUuid).getProtocolMappers()
                        .createMapper(providerHardcodedClaimMapper));
    }

    @Test
    public void claimIsPropagatedOnFirstLoginOnlyWhenNameMatchesAndSyncModeIsImport() {
        createUserSessionNoteIdpMapper(IdentityProviderMapperSyncMode.IMPORT, CLAIM_VALUE);

        AccessToken accessToken = login();

        Assertions.assertEquals(CLAIM_VALUE, accessToken.getOtherClaims().get(CLAIM_NAME));

        logout();

        AccessToken accessTokenSecondLogin = login();

        // claim should no longer have a value, because mapping is only applied on import
        Assertions.assertNull(accessTokenSecondLogin.getOtherClaims().get(CLAIM_NAME));
    }

    @Test
    public void claimIsPropagatedOnAllLoginsWhenNameMatchesAndSyncModeIsForce() {
        IdentityProviderMapperRepresentation userSessionNoteIdpMapper =
                createUserSessionNoteIdpMapper(IdentityProviderMapperSyncMode.FORCE, CLAIM_VALUE);

        AccessToken accessTokenFirstLogin = login();

        Assertions.assertEquals(CLAIM_VALUE, accessTokenFirstLogin.getOtherClaims().get(CLAIM_NAME));

        logout();

        String updatedClaimValue = "updated-claim-value";
        updateProviderHardcodedClaimMapper(updatedClaimValue);
        updateUserSessionNoteIdpMapper(userSessionNoteIdpMapper, updatedClaimValue);

        AccessToken accessTokenSecondLogin = login();

        Assertions.assertEquals(updatedClaimValue, accessTokenSecondLogin.getOtherClaims().get(CLAIM_NAME));
    }

    @Test
    public void claimIsNotPropagatedWhenNameDoesNotMatch() {
        createUserSessionNoteIdpMapper(IdentityProviderMapperSyncMode.IMPORT, "something-unexpected-1", "something-unexpected-2");

        AccessToken accessToken = login();

        Assertions.assertNull(accessToken.getOtherClaims().get(CLAIM_NAME));
    }

    private void logout() {
        AccountHelper.logout(getConsumerRealm().admin(), getUserLogin());
        // Also drop the provider-side session so the next login re-authenticates at the provider
        // (the new framework's login flow expects the provider credential form to be shown).
        AccountHelper.logout(getProviderRealm().admin(), getUserLogin());
    }

    private AccessToken login() {
        loginAsUserFirstTime();

        AuthorizationEndpointResponse authzResponse = getOAuthClient().parseLoginResponse();
        Assertions.assertTrue(authzResponse.isSuccess());

        AccessTokenResponse response = getOAuthClient().doAccessTokenRequest(authzResponse.getCode());
        return toAccessToken(response.getAccessToken());
    }

    private AccessToken toAccessToken(String encoded) {
        try {
            return new JWSInput(encoded).readJsonContent(AccessToken.class);
        } catch (JWSInputException cause) {
            throw new RuntimeException("Failed to deserialize token", cause);
        }
    }

    private IdentityProviderMapperRepresentation createUserSessionNoteIdpMapper(IdentityProviderMapperSyncMode syncMode,
            String... matchingValue) {
        IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
        mapper.setName("User Session Note Idp Mapper");
        mapper.setIdentityProviderMapper(ClaimToUserSessionNoteMapper.PROVIDER_ID);
        mapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                CONFIG_PROPERTY_CLAIMS, createClaimsConfig(matchingValue)));

        return persistMapper(mapper);
    }

    private String createClaimsConfig(String... matchingValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        if (matchingValue != null) {
            for (String value : matchingValue) {
                sb.append("{\"key\":\"").append(CLAIM_NAME).append("\",\"value\":\"").append(value).append("\"},");
            }
            sb.setLength(sb.length() - 1);
        }
        sb.append("]");
        return sb.toString();
    }

    private void updateProviderHardcodedClaimMapper(String value) {
        ProtocolMappersResource clientProtocolMappersResource =
                getProviderRealm().admin().clients().get(providerClientUuid).getProtocolMappers();
        ProtocolMapperRepresentation mapper =
                clientProtocolMappersResource.getMapperById(providerHardcodedClaimMapperId);
        Map<String, String> newConfig = mapper.getConfig() == null ? new HashMap<>() : mapper.getConfig();
        newConfig.put(HardcodedClaim.CLAIM_VALUE, value);
        mapper.setConfig(newConfig);

        clientProtocolMappersResource.update(mapper.getId(), mapper);
    }

    private void updateUserSessionNoteIdpMapper(IdentityProviderMapperRepresentation mapper, String matchingValue) {
        Map<String, String> newConfig = mapper.getConfig() == null ? new HashMap<>() : mapper.getConfig();
        newConfig.put(CONFIG_PROPERTY_CLAIMS, createClaimsConfig(matchingValue));
        mapper.setConfig(newConfig);

        getIdpResource().update(mapper.getId(), mapper);
    }

    private IdentityProviderMapperRepresentation persistMapper(IdentityProviderMapperRepresentation idpMapper) {
        IdentityProviderResource idpResource = getIdpResource();
        idpMapper.setIdentityProviderAlias(getIdpAlias());

        String createdId = CreatedResponseUtil.getCreatedId(idpResource.addMapper(idpMapper));
        return idpResource.getMapperById(createdId);
    }
}
