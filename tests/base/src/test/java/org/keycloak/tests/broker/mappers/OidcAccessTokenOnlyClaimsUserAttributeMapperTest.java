package org.keycloak.tests.broker.mappers;

import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.tests.broker.OidcBrokerConfigSupport;

/**
 * Same as {@link KcOidcAccessTokenOnlyClaimsUserAttributeMapperTest}, but brokers through the generic
 * {@code oidc} identity provider (with the access token treated as a JWT) instead of the Keycloak
 * specific {@code keycloak-oidc} provider. This mirrors the legacy suite, where this was the single
 * mapper test that used the generic provider. The consumer realm field shadows the one declared in
 * {@link AbstractOidcMapperTest} so it is created from the generic-provider config.
 */
@KeycloakIntegrationTest
public class OidcAccessTokenOnlyClaimsUserAttributeMapperTest extends KcOidcAccessTokenOnlyClaimsUserAttributeMapperTest {

    @InjectRealm(ref = "consumer", lifecycle = LifeCycle.METHOD,
            config = OidcBrokerConfigSupport.GenericOidcConsumerRealmConfig.class)
    ManagedRealm consumerRealm;

    @Override
    public ManagedRealm getConsumerRealm() {
        return consumerRealm;
    }
}
