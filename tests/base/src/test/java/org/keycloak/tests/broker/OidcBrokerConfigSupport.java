package org.keycloak.tests.broker;

import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.broker.oidc.OIDCIdentityProviderFactory;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.testframework.realm.IdentityProviderBuilder;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;

/**
 * Broker configuration that brokers to the provider through the <em>generic</em> {@code oidc} identity
 * provider (as opposed to the Keycloak-specific {@code keycloak-oidc} provider used by the default
 * {@link KcOidcBrokerConfigSupport}). Because the generic provider does not natively treat the access
 * token as a JWT, {@link OIDCIdentityProviderConfig#IS_ACCESS_TOKEN_JWT} is enabled so claims can be
 * read from it. Mirrors the anonymous {@code KcOidcBrokerConfiguration} override in the legacy
 * {@code OidcAccessTokenOnlyClaimsUserAttributeMapperTest}, which is the only case that needed it.
 */
public interface OidcBrokerConfigSupport extends KcOidcBrokerConfigSupport {

    String IDP_GENERIC_OIDC_PROVIDER_ID = OIDCIdentityProviderFactory.PROVIDER_ID;

    static IdentityProviderBuilder createGenericOidcIdentityProvider() {
        return IdentityProviderBuilder.create()
                .providerId(IDP_GENERIC_OIDC_PROVIDER_ID)
                .alias(IDP_OIDC_ALIAS)
                .displayName("kc-oidc-idp")
                .attribute(IdentityProviderModel.SYNC_MODE, "IMPORT")
                .attribute("clientId", CLIENT_ID)
                .attribute("clientSecret", CLIENT_SECRET)
                .attribute("backchannelSupported", "true")
                .attribute("defaultScope", "email profile")
                .attribute(OIDCIdentityProviderConfig.IS_ACCESS_TOKEN_JWT, "true");
    }

    class GenericOidcConsumerRealmConfig implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            return KcOidcBrokerConfigSupport.configureConsumerRealm(realm, createGenericOidcIdentityProvider());
        }
    }
}
