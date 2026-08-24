package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import org.keycloak.broker.oidc.mappers.UserAttributeMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.tests.broker.KcOidcBrokerConfigSupport;

@KeycloakIntegrationTest
public class OidcUserAttributeMapperTest extends AbstractOidcMapperTest implements UserAttributeMapperTest {

    @Override
    public Iterable<IdentityProviderMapperRepresentation> createIdentityProviderMappers(IdentityProviderMapperSyncMode syncMode, boolean nullable) {
        IdentityProviderMapperRepresentation attrMapper1 = new IdentityProviderMapperRepresentation();
        attrMapper1.setName("attribute-mapper");
        attrMapper1.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        attrMapper1.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.CLAIM, KcOidcBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME,
                UserAttributeMapper.USER_ATTRIBUTE, UserAttributeMapperTest.MAPPED_ATTRIBUTE_NAME));

        IdentityProviderMapperRepresentation emailAttrMapper = new IdentityProviderMapperRepresentation();
        emailAttrMapper.setName("attribute-mapper-email");
        emailAttrMapper.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        emailAttrMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.ALLOW_NULLABLE, Boolean.toString(nullable),
                UserAttributeMapper.CLAIM, "email",
                UserAttributeMapper.USER_ATTRIBUTE, "email"));

        IdentityProviderMapperRepresentation nestedEmailAttrMapper = new IdentityProviderMapperRepresentation();
        nestedEmailAttrMapper.setName("nested-attribute-mapper-email");
        nestedEmailAttrMapper.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        nestedEmailAttrMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.CLAIM, "nested.email",
                UserAttributeMapper.USER_ATTRIBUTE, "nested.email"));

        IdentityProviderMapperRepresentation dottedEmailAttrMapper = new IdentityProviderMapperRepresentation();
        dottedEmailAttrMapper.setName("dotted-attribute-mapper-email");
        dottedEmailAttrMapper.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        dottedEmailAttrMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.CLAIM, "dotted\\.email",
                UserAttributeMapper.USER_ATTRIBUTE, "dotted.email"));

        IdentityProviderMapperRepresentation usernameAttrMapper = new IdentityProviderMapperRepresentation();
        usernameAttrMapper.setName("attribute-mapper-username");
        usernameAttrMapper.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        usernameAttrMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.CLAIM, "preferred_username",
                UserAttributeMapper.USER_ATTRIBUTE, "username"));

        return List.of(attrMapper1, emailAttrMapper, nestedEmailAttrMapper, dottedEmailAttrMapper, usernameAttrMapper);
    }
}
