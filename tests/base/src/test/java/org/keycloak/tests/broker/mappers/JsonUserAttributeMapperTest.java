package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.HardcodedClaim;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.social.github.GitHubUserAttributeMapper;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testsuite.util.AccountHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.keycloak.models.IdentityProviderMapperSyncMode.FORCE;
import static org.keycloak.models.IdentityProviderMapperSyncMode.IMPORT;
import static org.keycloak.models.IdentityProviderMapperSyncMode.LEGACY;

/**
 * Mirrors the legacy {@code JsonUserAttributeMapperTest}. The provider client emits a JSON-typed
 * hardcoded claim {@code user-claim = {"test":"value"}} in its ID token, and the consumer's GitHub
 * user-attribute mapper extracts {@code user-claim.test} into a user attribute. The new shared
 * provider realm does not carry this JSON claim mapper, so it is added here per test and its id is
 * captured to disambiguate it from the {@code hardcoded-attribute} claim mapper the shared config
 * already installs.
 */
@KeycloakIntegrationTest
public class JsonUserAttributeMapperTest extends AbstractOidcMapperTest implements IdentityProviderMapperTest {

    public static final String USER_ATTRIBUTE = "user-attribute";

    private static final String USER_INFO_CLAIM = "user-claim";
    private static final String HARDOCDED_CLAIM = "test";
    private static final String HARDOCDED_VALUE = "value";

    private String providerClientUuid;
    private String providerJsonMapperId;

    @BeforeEach
    void addProviderJsonMapper() {
        RealmResource providerRealm = getProviderRealm().admin();
        ClientRepresentation providerClient = providerRealm.clients().findByClientId(CLIENT_ID).get(0);
        providerClientUuid = providerClient.getId();

        ProtocolMapperRepresentation hardcodedJsonClaim = new ProtocolMapperRepresentation();
        hardcodedJsonClaim.setName("json-mapper");
        hardcodedJsonClaim.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        hardcodedJsonClaim.setProtocolMapper(HardcodedClaim.PROVIDER_ID);
        hardcodedJsonClaim.setConfig(Map.of(
                OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, USER_INFO_CLAIM,
                OIDCAttributeMapperHelper.JSON_TYPE, "JSON",
                OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN, "true",
                HardcodedClaim.CLAIM_VALUE, "{\"" + HARDOCDED_CLAIM + "\": \"" + HARDOCDED_VALUE + "\"}"));

        providerJsonMapperId = CreatedResponseUtil.getCreatedId(
                providerRealm.clients().get(providerClientUuid).getProtocolMappers().createMapper(hardcodedJsonClaim));
    }

    @Test
    public void loginWithIdentityProviderMapsJsonAttributeToUserAttributeButDoesNotModify() {
        UserRepresentation user = createMapperThenModifyAttribute(IMPORT, "new-value");

        assertUserAttribute(HARDOCDED_VALUE, user);
    }

    @Test
    public void loginWithIdentityProviderDeletesAttributeInForceMode() {
        UserRepresentation user = createMapperThenDeleteAttribute(FORCE);

        assertAbsentUserAttribute(user);
    }

    @Test
    public void loginWithIdentityProviderDoesNotDeleteAttributeInLegacyMode() {
        UserRepresentation user = createMapperThenDeleteAttribute(LEGACY);

        assertUserAttribute(HARDOCDED_VALUE, user);
    }

    @Test
    public void loginWithIdentityProviderModifiesAttributeInForceMode() {
        UserRepresentation user = createMapperThenModifyAttribute(FORCE, "new-value");

        assertUserAttribute("new-value", user);
    }

    @Test
    public void loginWithIdentityProviderAddsUserAttributeInForceNameWhenMapperIsCreatedLater() {
        UserRepresentation user = loginAndThenCreateMapperThenLoginAgain(FORCE);

        assertUserAttribute(HARDOCDED_VALUE, user);
    }

    @Test
    public void loginWithIdentityProviderDoesNotAddUserAttributeInImportNameWhenMapperIsCreatedLater() {
        UserRepresentation user = loginAndThenCreateMapperThenLoginAgain(IMPORT);

        assertAbsentUserAttribute(user);
    }

    private UserRepresentation loginAndThenCreateMapperThenLoginAgain(IdentityProviderMapperSyncMode syncMode) {
        return loginAsUserTwiceWithMapper(syncMode, true, HARDOCDED_CLAIM, HARDOCDED_VALUE);
    }

    private UserRepresentation createMapperThenDeleteAttribute(IdentityProviderMapperSyncMode syncMode) {
        return loginAsUserTwiceWithMapper(syncMode, false, "deleted", "deleted");
    }

    private UserRepresentation createMapperThenModifyAttribute(IdentityProviderMapperSyncMode syncMode, String updatedValue) {
        return loginAsUserTwiceWithMapper(syncMode, false, HARDOCDED_CLAIM, updatedValue);
    }

    private UserRepresentation loginAsUserTwiceWithMapper(
            IdentityProviderMapperSyncMode syncMode, boolean createAfterFirstLogin, String claim, String updatedValue) {
        setupIdentityProvider();
        if (!createAfterFirstLogin) {
            createGithubProviderMapper(syncMode);
        }
        createUserInProviderRealm(Map.of());

        loginAsUserFirstTime();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        if (!createAfterFirstLogin) {
            assertUserAttribute(HARDOCDED_VALUE, user);
        } else {
            assertAbsentUserAttribute(user);
        }

        if (createAfterFirstLogin) {
            createGithubProviderMapper(syncMode);
        }
        AccountHelper.logout(getConsumerRealm().admin(), getUserLogin());

        if (!createAfterFirstLogin) {
            updateClaimSentToIDP(claim, updatedValue);
        }

        logInAsUserInIDP();
        return findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
    }

    private void updateClaimSentToIDP(String claim, String updatedValue) {
        ProtocolMappersResource protocolMappers =
                getProviderRealm().admin().clients().get(providerClientUuid).getProtocolMappers();
        ProtocolMapperRepresentation claimMapper = protocolMappers.getMapperById(providerJsonMapperId);
        claimMapper.getConfig().put(HardcodedClaim.CLAIM_VALUE, "{\"" + claim + "\": \"" + updatedValue + "\"}");
        protocolMappers.update(claimMapper.getId(), claimMapper);
    }

    private void assertUserAttribute(String value, UserRepresentation userRep) {
        Assertions.assertNotNull(userRep.getAttributes());
        Assertions.assertEquals(List.of(value), userRep.getAttributes().get(USER_ATTRIBUTE));
    }

    private void assertAbsentUserAttribute(UserRepresentation userRep) {
        Assertions.assertNull(userRep.getAttributes());
    }

    private void createGithubProviderMapper(IdentityProviderMapperSyncMode syncMode) {
        IdentityProviderMapperRepresentation githubProvider = new IdentityProviderMapperRepresentation();
        githubProvider.setName("json-attribute-mapper");
        githubProvider.setIdentityProviderMapper(GitHubUserAttributeMapper.PROVIDER_ID);
        githubProvider.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                AbstractJsonUserAttributeMapper.CONF_JSON_FIELD, USER_INFO_CLAIM + "." + HARDOCDED_CLAIM,
                AbstractJsonUserAttributeMapper.CONF_USER_ATTRIBUTE, USER_ATTRIBUTE));
        githubProvider.setIdentityProviderAlias(getIdpAlias());
        getIdpResource().addMapper(githubProvider).close();
    }
}
