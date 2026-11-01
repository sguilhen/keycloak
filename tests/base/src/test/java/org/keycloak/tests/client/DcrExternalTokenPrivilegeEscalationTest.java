package org.keycloak.tests.client;

import java.util.HashMap;
import java.util.Map;

import org.keycloak.client.registration.Auth;
import org.keycloak.client.registration.ClientRegistrationException;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.representations.idm.ClientInitialAccessCreatePresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The attributes {@code external.token.enabled} and {@code external.token.idp} decide which clients may retrieve
 * stored external IdP tokens (Identity Brokering API V1 and V2). They must only be writable by administrators,
 * never by a client that registers or updates itself through Dynamic Client Registration.
 */
@KeycloakIntegrationTest
public class DcrExternalTokenPrivilegeEscalationTest extends AbstractClientRegistrationTest {

    private String initialAccessToken() {
        return managedRealm.admin()
                .clientInitialAccess()
                .create(new ClientInitialAccessCreatePresentation(0, 1))
                .getToken();
    }

    private void removeOnCleanup(String clientId) {
        managedRealm.cleanup().add(realm -> {
            var found = realm.clients().findByClientId(clientId);
            if (!found.isEmpty()) {
                realm.clients().get(found.get(0).getId()).remove();
            }
        });
    }

    /**
     * Tested scenario:
     *
     * <ul>
     *     <li>a caller with an initial access token registers a new client through /clients-registrations/default</li>
     *     <li>the representation includes external.token.enabled=true, external.token.idp=any-idp and an unrelated safe.attribute</li>
     *     <li>the test reads the stored client back through the Admin API</li>
     *     <li>it expects the two external-token attributes to be absent and safe.attribute to be kept</li>
     * </ul>
     */
    @Test
    public void externalTokenAttributesAreStrippedOnDcrCreate() throws ClientRegistrationException {
        reg.auth(Auth.token(initialAccessToken()));

        ClientRepresentation toCreate = new ClientRepresentation();
        Map<String, String> attrs = new HashMap<>();
        attrs.put(OIDCConfigAttributes.EXTERNAL_TOKEN_ENABLED, "true");
        attrs.put(OIDCConfigAttributes.EXTERNAL_TOKEN_IDP, "any-idp");
        attrs.put("safe.attribute", "allowed");
        toCreate.setAttributes(attrs);

        ClientRepresentation created = reg.create(toCreate);
        removeOnCleanup(created.getClientId());

        Map<String, String> stored = managedRealm.admin().clients().findByClientId(created.getClientId()).get(0).getAttributes();
        assertNull(stored.get(OIDCConfigAttributes.EXTERNAL_TOKEN_ENABLED),
                "external.token.enabled must not be stored via DCR create");
        assertNull(stored.get(OIDCConfigAttributes.EXTERNAL_TOKEN_IDP),
                "external.token.idp must not be stored via DCR create");
        assertEquals("allowed", stored.get("safe.attribute"), "Other attributes must still be stored");
    }

    /**
     * Tested scenario:
     *
     * <ul>
     *     <li>a client is registered normally, then updated with its registration access token (RAT) </li>
     *     <li>the update adds the same two attributes plus an unrelated one</li>
     *     <li>it expects the same outcome as above</li>
     * </ul>
     */
    @Test
    public void externalTokenAttributesAreStrippedOnDcrUpdate() throws ClientRegistrationException {
        reg.auth(Auth.token(initialAccessToken()));
        ClientRepresentation registered = reg.create(new ClientRepresentation());
        String clientId = registered.getClientId();
        removeOnCleanup(clientId);

        reg.auth(Auth.token(registered.getRegistrationAccessToken()));
        ClientRepresentation update = new ClientRepresentation();
        update.setClientId(clientId);
        Map<String, String> attrs = new HashMap<>();
        attrs.put(OIDCConfigAttributes.EXTERNAL_TOKEN_ENABLED, "true");
        attrs.put(OIDCConfigAttributes.EXTERNAL_TOKEN_IDP, "any-idp");
        attrs.put("safe.update.attr", "allowed");
        update.setAttributes(attrs);
        reg.update(update);

        Map<String, String> stored = managedRealm.admin().clients().findByClientId(clientId).get(0).getAttributes();
        assertNull(stored.get(OIDCConfigAttributes.EXTERNAL_TOKEN_ENABLED),
                "external.token.enabled must not be stored via DCR update");
        assertNull(stored.get(OIDCConfigAttributes.EXTERNAL_TOKEN_IDP),
                "external.token.idp must not be stored via DCR update");
        assertEquals("allowed", stored.get("safe.update.attr"), "Other attributes must still be stored");
    }

    /**
     * Tested scenario:
     *
     * <ul>
     *     <li>an admin allow-lists exactly one IdP (admin-allowed-idp) for a registered client through the Admin API</li>
     *     <li>the client then uses its RAT to set external.token.idp to admin-allowed-idp##other-idp</li>
     *     <li>it expects the admin's value to stay unchanged</li>
     * </ul>
     *
     * If the safe attributes are not properly handled the value becomes admin-allowed-idp##other-idp, showing that a client
     * can widen an allow-list the admin set, not only create one for itself.
     */
    @Test
    public void adminConfiguredExternalTokenAttributesCannotBeChangedViaRat() throws ClientRegistrationException {
        reg.auth(Auth.token(initialAccessToken()));
        ClientRepresentation registered = reg.create(new ClientRepresentation());
        String clientId = registered.getClientId();
        removeOnCleanup(clientId);

        // the administrator allows exactly one IdP for the client
        ClientRepresentation adminView = managedRealm.admin().clients().findByClientId(clientId).get(0);
        adminView.getAttributes().put(OIDCConfigAttributes.EXTERNAL_TOKEN_ENABLED, "true");
        adminView.getAttributes().put(OIDCConfigAttributes.EXTERNAL_TOKEN_IDP, "admin-allowed-idp");
        managedRealm.admin().clients().get(adminView.getId()).update(adminView);

        // the client tries to widen the allow-list through its registration access token
        reg.auth(Auth.token(registered.getRegistrationAccessToken()));
        ClientRepresentation update = new ClientRepresentation();
        update.setClientId(clientId);
        Map<String, String> attrs = new HashMap<>();
        attrs.put(OIDCConfigAttributes.EXTERNAL_TOKEN_IDP, "admin-allowed-idp##other-idp");
        update.setAttributes(attrs);
        reg.update(update);

        Map<String, String> stored = managedRealm.admin().clients().findByClientId(clientId).get(0).getAttributes();
        assertEquals("true", stored.get(OIDCConfigAttributes.EXTERNAL_TOKEN_ENABLED));
        assertEquals("admin-allowed-idp", stored.get(OIDCConfigAttributes.EXTERNAL_TOKEN_IDP),
                "A client must not be able to change the IdPs an administrator allowed for it");
    }
}
