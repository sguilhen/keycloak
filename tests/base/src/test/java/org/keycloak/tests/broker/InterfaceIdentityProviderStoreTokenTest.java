package org.keycloak.tests.broker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.client.registration.Auth;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.representations.idm.ClientInitialAccessCreatePresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.realm.ClientBuilder;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.remote.runonserver.RunOnServerClient;
import org.keycloak.testframework.ui.page.LoginPage;
import org.keycloak.tests.utils.admin.AdminApiUtil;
import org.keycloak.testsuite.util.AccountHelper;
import org.keycloak.testsuite.util.oauth.AbstractHttpResponse;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author rmartinc
 */
public interface InterfaceIdentityProviderStoreTokenTest {

    public static String IDP_ALIAS = "token-idp-alias";

    public static String EXTERNAL_REALM_NAME = "external-realm";

    ManagedRealm getRealm();
    ManagedRealm getExternalRealm();
    OAuthClient getOAuthClient();
    LoginPage getLoginPage();
    RunOnServerClient getRunOnServer();
    AbstractHttpResponse doFetchExternalIdpToken(String token);
    void checkSuccessfulTokenResponse(AbstractHttpResponse response);
    void assertPublicClientTokenResponse(AbstractHttpResponse response);
    boolean isIdentityBrokeringAPIV1();
    boolean isRefreshTokenAllowed();

    default void loginWithIdP() {
        LoginPage loginPage = getLoginPage();
        loginPage.clickSocial(IDP_ALIAS);
        loginPage.fillLogin("testuser", "password");
        loginPage.submit();
    }

    @AfterEach
    default void logout() {
        ManagedRealm realm = getRealm();
        Optional<UserRepresentation> userResult = realm.admin().users().search("testuser", true).stream().findFirst();
        if (userResult.isPresent()) {
            AccountHelper.logout(realm.admin(), "testuser");
            realm.admin().users().delete(userResult.get().getId()).close();
        }

        ManagedRealm externalRealm = getExternalRealm();
        userResult = externalRealm.admin().users().search("testuser", true).stream().findFirst();
        if (userResult.isPresent()) {
            AccountHelper.logout(externalRealm.admin(), "testuser");
        }
    }

    /**
     * A client that registers itself through Dynamic Client Registration must not be able to allow-list an IdP
     * for itself. Otherwise, the per-client external token authorization (V1 and V2) can be bypassed by anyone able
     * to register a client.
     *
     * Test scenario:
     *
     * <ul>
     *     <li>an initial access token is created through the Admin API</li>
     *     <li>a client self-registered-app is registered through DCR as a confidential client, with external.token.enabled=true
     *     and external.token.idp=&lt;IDP_ALIAS&gt; in its attributes</li>
     *     <li>a user logs in through the external IdP using that client</li>
     *     <li>the test calls the broker token endpoint with the user's access token. That is GET in V1, and POST with client
     *     authentication in V2</li>
     *     <li>it expects 403, because the client was never authorized by an administrator</li>
     * </ul>
     */
    @Test
    default void testSelfRegisteredClientCannotAllowIdp() throws Exception {
        ManagedRealm realm = getRealm();
        OAuthClient oauth = getOAuthClient();

        String initialAccessToken = realm.admin().clientInitialAccess()
                .create(new ClientInitialAccessCreatePresentation(0, 1))
                .getToken();

        Map<String, String> attributes = new HashMap<>();
        attributes.put(OIDCConfigAttributes.EXTERNAL_TOKEN_ENABLED, Boolean.TRUE.toString());
        attributes.put(OIDCConfigAttributes.EXTERNAL_TOKEN_IDP, IDP_ALIAS);
        ClientRepresentation toRegister = new ClientRepresentation();
        toRegister.setClientId("self-registered-app");
        toRegister.setPublicClient(false);
        toRegister.setRedirectUris(List.of(oauth.getRedirectUri()));
        toRegister.setAttributes(attributes);

        var reg = oauth.clientRegistration();
        reg.auth(Auth.token(initialAccessToken));
        ClientRepresentation registered = reg.create(toRegister);
        realm.cleanup().add(r -> {
            var found = r.clients().findByClientId("self-registered-app");
            if (!found.isEmpty()) {
                r.clients().get(found.get(0).getId()).remove();
            }
        });
        realm.cleanup().add(r -> oauth.client("test-app", "test-secret"));

        // log in through the IdP using the self-registered client
        oauth.client("self-registered-app", registered.getSecret());
        oauth.openLoginForm();
        loginWithIdP();

        AccessTokenResponse internalTokens = oauth.doAccessTokenRequest(oauth.parseLoginResponse().getCode());
        Assertions.assertTrue(internalTokens.isSuccess());

        AbstractHttpResponse externalTokens = doFetchExternalIdpToken(internalTokens.getAccessToken());
        Assertions.assertEquals(403, externalTokens.getStatusCode(),
                "A self-registered client must not be allowed to retrieve external tokens");
    }

    @Test
    default void testPublicClient() {
        ManagedRealm realm = getRealm();
        OAuthClient oauth = getOAuthClient();
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation originalClient = clientResource.toRepresentation();
        realm.cleanup().add(r -> clientResource.update(originalClient));
        clientResource.update(ClientBuilder.update(clientResource.toRepresentation())
                .publicClient(true)
                .serviceAccountsEnabled(false)
                .attribute(OIDCConfigAttributes.EXTERNAL_TOKEN_ENABLED, Boolean.TRUE.toString())
                .attribute(OIDCConfigAttributes.EXTERNAL_TOKEN_IDP, IDP_ALIAS)
                .build());

        oauth.openLoginForm();
        loginWithIdP();

        AccessTokenResponse internalTokens = oauth.accessTokenRequest(oauth.parseLoginResponse().getCode())
                .client(oauth.getClientId())
                .send();
        Assertions.assertTrue(internalTokens.isSuccess());

        AbstractHttpResponse externalTokens = doFetchExternalIdpToken(internalTokens.getAccessToken());
        assertPublicClientTokenResponse(externalTokens);
    }

    @Test
    default void testDefaultSuccess() {
        OAuthClient oauth = getOAuthClient();
        oauth.openLoginForm();
        loginWithIdP();

        AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(oauth.parseLoginResponse().getCode());
        Assertions.assertTrue(tokenResponse.isSuccess());

        AbstractHttpResponse externalTokens = doFetchExternalIdpToken(tokenResponse.getAccessToken());
        Assertions.assertTrue(externalTokens.isSuccess());
        checkSuccessfulTokenResponse(externalTokens);
    }

    @Test
    default void testIdpDisabled() {
        ManagedRealm realm = getRealm();
        OAuthClient oauth = getOAuthClient();
        oauth.openLoginForm();
        loginWithIdP();

        AccessTokenResponse internalTokens = oauth.doAccessTokenRequest(oauth.parseLoginResponse().getCode());
        Assertions.assertTrue(internalTokens.isSuccess());

        realm.updateIdentityProvider(IDP_ALIAS, idp -> {
            idp.setEnabled(false);
        });

        AbstractHttpResponse externalTokens = doFetchExternalIdpToken(internalTokens.getAccessToken());
        Assertions.assertFalse(externalTokens.isSuccess());
    }

    @Test
    default void testUserDisabled() {
        ManagedRealm realm = getRealm();
        OAuthClient oauth = getOAuthClient();
        oauth.openLoginForm();
        loginWithIdP();

        AccessTokenResponse internalTokens = oauth.doAccessTokenRequest(oauth.parseLoginResponse().getCode());
        Assertions.assertTrue(internalTokens.isSuccess());

        UserRepresentation userRep = AdminApiUtil.findUserByUsername(realm.admin(), "testuser");
        userRep.setEnabled(false);
        realm.admin().users().get(userRep.getId()).update(userRep);

        AbstractHttpResponse externalTokens = doFetchExternalIdpToken(internalTokens.getAccessToken());
        Assertions.assertEquals(400, externalTokens.getStatusCode());
    }
}
