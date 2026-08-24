package org.keycloak.tests.broker.mappers;

import java.util.Map;

import org.keycloak.broker.oidc.mappers.UserAttributeMapper;
import org.keycloak.broker.oidc.mappers.UsernameTemplateMapper;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testsuite.util.AccountHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression test for KEYCLOAK-8100: a user attribute mapped from the IDP {@code sub} claim must be
 * available to a username-template mapper, so the brokered username can be derived from the sub.
 * Mirrors the legacy standalone {@code UsernameTemplateMapperTest}.
 */
@KeycloakIntegrationTest
public class KcOidcUsernameTemplateSubMapperTest extends AbstractOidcMapperTest
        implements IdentityProviderMapperTest {

    @BeforeEach
    void addIdentityProviderMappers() {
        IdentityProviderMapperRepresentation userTemplateImporterMapper = new IdentityProviderMapperRepresentation();
        userTemplateImporterMapper.setName("custom-username-import-mapper");
        userTemplateImporterMapper.setIdentityProviderMapper(UsernameTemplateMapper.PROVIDER_ID);
        userTemplateImporterMapper.setConfig(Map.of(UsernameTemplateMapper.TEMPLATE, "${ALIAS}_${CLAIM.sub}"));
        userTemplateImporterMapper.setIdentityProviderAlias(getIdpAlias());
        getIdpResource().addMapper(userTemplateImporterMapper).close();

        IdentityProviderMapperRepresentation jwtClaimsAttrMapper = new IdentityProviderMapperRepresentation();
        jwtClaimsAttrMapper.setName("jwt-claims-mapper");
        jwtClaimsAttrMapper.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        jwtClaimsAttrMapper.setConfig(Map.of(
                UserAttributeMapper.CLAIM, "sub",
                UserAttributeMapper.USER_ATTRIBUTE, "mappedSub",
                UserAttributeMapper.CLAIM_VALUE, "${CLAIM.sub};test"));
        jwtClaimsAttrMapper.setIdentityProviderAlias(getIdpAlias());
        getIdpResource().addMapper(jwtClaimsAttrMapper).close();
    }

    /**
     * See: KEYCLOAK-8100
     */
    @Test
    public void userAttributeShouldBeDerivedFromIdpSubClaim() {
        String idpUserId = createUserInProviderRealm(Map.of());

        loginAsUserFirstTime();
        AccountHelper.logout(getConsumerRealm().admin(), getIdpAlias() + "_" + idpUserId);

        UserRepresentation user = getConsumerRealm().admin().users().search(getUserEmail(), 0, 1).get(0);

        Assertions.assertEquals(idpUserId, user.getAttributes().get("mappedSub").get(0),
                "Should render idpSub as mappedSub attribute");

        String username = user.getUsername();
        Assertions.assertEquals(getIdpAlias() + "_" + idpUserId, username, "Should render alias:sub as Username");
    }
}
