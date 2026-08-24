package org.keycloak.tests.broker.mappers;

import java.util.Map;

import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.broker.saml.mappers.AdvancedAttributeToRoleMapper;
import org.keycloak.broker.saml.mappers.AttributeToRoleMapper;
import org.keycloak.broker.saml.mappers.UserAttributeMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

/**
 * Runs the same tests as {@link AttributeToRoleMapperTest} but using multiple SAML mappers that map different IDP
 * attributes to the same {@code Keycloak} role. Regression coverage for {@code KEYCLOAK-8730}: a later mapper must not
 * remove a role a previous mapper has already granted.
 */
@KeycloakIntegrationTest
public class KcSamlMultipleAttributeToRoleMappersTest extends AttributeToRoleMapperTest {

    private static final String ATTRIBUTES_TO_MATCH = "[\n" +
            "  {\n" +
            "    \"key\": \"test attribute\",\n" +
            "    \"value\": \"test value\"\n" +
            "  }\n" +
            "]";

    @Override
    public void createMapperInIdp(IdentityProviderMapperSyncMode syncMode, String roleValue) {
        // first mapper that maps a role the test user has - it should perform the mapping.
        IdentityProviderMapperRepresentation firstSamlAttributeToRoleMapper = new IdentityProviderMapperRepresentation();
        firstSamlAttributeToRoleMapper.setName("first-role-mapper");
        firstSamlAttributeToRoleMapper.setIdentityProviderMapper(AttributeToRoleMapper.PROVIDER_ID);
        firstSamlAttributeToRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.ATTRIBUTE_NAME, ROLE_ATTR_NAME,
                AttributeToRoleMapper.ATTRIBUTE_VALUE, ROLE_USER,
                ConfigConstants.ROLE, roleValue));

        persistMapper(firstSamlAttributeToRoleMapper);

        // second mapper that maps a role the test user doesn't have - it would normally end up removing the mapped
        // role, but it should now check if a previous mapper has already granted the same mapped role.
        IdentityProviderMapperRepresentation secondSamlAttributeToRoleMapper = new IdentityProviderMapperRepresentation();
        secondSamlAttributeToRoleMapper.setName("second-role-mapper");
        secondSamlAttributeToRoleMapper.setIdentityProviderMapper(AttributeToRoleMapper.PROVIDER_ID);
        secondSamlAttributeToRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                UserAttributeMapper.ATTRIBUTE_NAME, ROLE_ATTR_NAME,
                AttributeToRoleMapper.ATTRIBUTE_VALUE, "missing-role",
                ConfigConstants.ROLE, roleValue));

        persistMapper(secondSamlAttributeToRoleMapper);

        // third mapper (advanced) that maps an attribute the test user doesn't have - it would normally end up removing
        // the mapped role, but it should now check if a previous mapper has already granted the same role.
        IdentityProviderMapperRepresentation thirdSamlAttributeToRoleMapper = new IdentityProviderMapperRepresentation();
        thirdSamlAttributeToRoleMapper.setName("advanced-role-mapper");
        thirdSamlAttributeToRoleMapper.setIdentityProviderMapper(AdvancedAttributeToRoleMapper.PROVIDER_ID);
        thirdSamlAttributeToRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                AdvancedAttributeToRoleMapper.ATTRIBUTE_PROPERTY_NAME, ATTRIBUTES_TO_MATCH,
                AdvancedAttributeToRoleMapper.ARE_ATTRIBUTE_VALUES_REGEX_PROPERTY_NAME, Boolean.FALSE.toString(),
                ConfigConstants.ROLE, roleValue));

        persistMapper(thirdSamlAttributeToRoleMapper);
    }
}
