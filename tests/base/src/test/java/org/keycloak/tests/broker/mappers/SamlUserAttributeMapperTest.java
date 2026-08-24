package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import org.keycloak.broker.saml.mappers.UserAttributeMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.tests.broker.KcOidcBrokerConfigSupport;
import org.keycloak.tests.broker.SamlBrokerConfigSupport;

@KeycloakIntegrationTest
public class SamlUserAttributeMapperTest extends AbstractSamlMapperTest implements UserAttributeMapperTest {

    @Override
    public Iterable<IdentityProviderMapperRepresentation> createIdentityProviderMappers(IdentityProviderMapperSyncMode syncMode, boolean nullable) {
        IdentityProviderMapperRepresentation attrMapperEmail = new IdentityProviderMapperRepresentation();
        attrMapperEmail.setName("attribute-mapper-email");
        attrMapperEmail.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        attrMapperEmail.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.ALLOW_NULLABLE, Boolean.toString(nullable),
                UserAttributeMapper.ATTRIBUTE_FRIENDLY_NAME, "email",
                UserAttributeMapper.USER_ATTRIBUTE, "email"));

        IdentityProviderMapperRepresentation attrMapperNestedEmail = new IdentityProviderMapperRepresentation();
        attrMapperNestedEmail.setName("nested-attribute-mapper-email");
        attrMapperNestedEmail.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        attrMapperNestedEmail.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.ATTRIBUTE_NAME, "nested.email",
                UserAttributeMapper.USER_ATTRIBUTE, "nested.email"));

        IdentityProviderMapperRepresentation attrMapperDottedEmail = new IdentityProviderMapperRepresentation();
        attrMapperDottedEmail.setName("dotted-attribute-mapper-email");
        attrMapperDottedEmail.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        attrMapperDottedEmail.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.ATTRIBUTE_NAME, "dotted.email",
                UserAttributeMapper.USER_ATTRIBUTE, "dotted.email"));

        IdentityProviderMapperRepresentation attrMapper1 = new IdentityProviderMapperRepresentation();
        attrMapper1.setName("attribute-mapper");
        attrMapper1.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        attrMapper1.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.ATTRIBUTE_NAME, KcOidcBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME,
                UserAttributeMapper.USER_ATTRIBUTE, UserAttributeMapperTest.MAPPED_ATTRIBUTE_NAME));

        IdentityProviderMapperRepresentation attrMapper2 = new IdentityProviderMapperRepresentation();
        attrMapper2.setName("attribute-mapper-friendly");
        attrMapper2.setIdentityProviderMapper(UserAttributeMapper.PROVIDER_ID);
        attrMapper2.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.ATTRIBUTE_FRIENDLY_NAME, SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_FRIENDLY_NAME,
                UserAttributeMapper.USER_ATTRIBUTE, UserAttributeMapperTest.MAPPED_ATTRIBUTE_FRIENDLY_NAME));

        return List.of(attrMapperEmail, attrMapper1, attrMapper2, attrMapperDottedEmail, attrMapperNestedEmail);
    }
}
