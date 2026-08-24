package org.keycloak.tests.broker.mappers;

import java.util.Map;

import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.tests.broker.KcOidcBrokerConfigSupport;

import org.junit.jupiter.api.BeforeEach;

/**
 * Verifies attribute mapping still works when the provider only exposes the claims in the access
 * token (not in the id token or user-info endpoint). Reuses the OIDC user-attribute mappers and
 * only reconfigures the provider's protocol mappers.
 */
@KeycloakIntegrationTest
public class KcOidcAccessTokenOnlyClaimsUserAttributeMapperTest extends OidcUserAttributeMapperTest {

    @BeforeEach
    public void restrictProviderClaimsToAccessToken() {
        RealmResource provider = getProviderRealm().admin();
        ClientRepresentation client = provider.clients().findByClientId(KcOidcBrokerConfigSupport.CLIENT_ID).get(0);
        ProtocolMappersResource mappers = provider.clients().get(client.getId()).getProtocolMappers();
        for (ProtocolMapperRepresentation mapper : mappers.getMappers()) {
            Map<String, String> config = mapper.getConfig();
            if (config == null) {
                continue;
            }
            config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN, "true");
            config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN, "false");
            config.put(OIDCAttributeMapperHelper.INCLUDE_IN_USERINFO, "false");
            mappers.update(mapper.getId(), mapper);
        }
    }
}
