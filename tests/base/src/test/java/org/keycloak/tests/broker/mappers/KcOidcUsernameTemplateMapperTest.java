package org.keycloak.tests.broker.mappers;

import java.util.Map;

import org.keycloak.broker.oidc.mappers.UsernameTemplateMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

@KeycloakIntegrationTest
public class KcOidcUsernameTemplateMapperTest extends AbstractOidcMapperTest implements UsernameTemplateMapperTest {

    @Override
    public void createMapperInIdp(IdentityProviderMapperSyncMode syncMode) {
        IdentityProviderMapperRepresentation usernameTemplateMapper = new IdentityProviderMapperRepresentation();
        usernameTemplateMapper.setName("oidc-username-template-mapper");
        usernameTemplateMapper.setIdentityProviderMapper(UsernameTemplateMapper.PROVIDER_ID);
        usernameTemplateMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                "template", "${ALIAS}-${CLAIM.user-attribute}"));
        usernameTemplateMapper.setIdentityProviderAlias(getIdpAlias());
        getIdpResource().addMapper(usernameTemplateMapper).close();
    }

    @Override
    public String getMapperTemplate() {
        return "kc-oidc-idp-%s";
    }
}
