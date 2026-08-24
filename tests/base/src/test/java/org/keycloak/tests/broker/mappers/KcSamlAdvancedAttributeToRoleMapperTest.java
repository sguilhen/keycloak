package org.keycloak.tests.broker.mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.broker.saml.mappers.AdvancedAttributeToRoleMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.tests.broker.SamlBrokerConfigSupport;

import org.junit.jupiter.api.Test;

@KeycloakIntegrationTest
public class KcSamlAdvancedAttributeToRoleMapperTest extends AbstractSamlMapperTest
        implements AdvancedRoleMapperTest {

    private static final String ATTRIBUTES = "[\n" +
            "  {\n" +
            "    \"key\": \"" + SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_FRIENDLY_NAME + "\",\n" +
            "    \"value\": \"value 1\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"key\": \"" + SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_FRIENDLY_NAME + "\",\n" +
            "    \"value\": \"value 2\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"key\": \"" + SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME_2 + "\",\n" +
            "    \"value\": \"value 2\"\n" +
            "  }\n" +
            "]";

    @Override
    public void createMapperInIdp(String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes, IdentityProviderMapperSyncMode syncMode, String roleValue) {
        IdentityProviderMapperRepresentation advancedAttributeToRoleMapper = new IdentityProviderMapperRepresentation();
        advancedAttributeToRoleMapper.setName("advanced-attribute-to-role-mapper");
        advancedAttributeToRoleMapper.setIdentityProviderMapper(AdvancedAttributeToRoleMapper.PROVIDER_ID);

        Map<String, String> config = new HashMap<>();
        config.put(IdentityProviderMapperModel.SYNC_MODE, syncMode.toString());
        config.put(AdvancedAttributeToRoleMapper.ATTRIBUTE_PROPERTY_NAME, claimsOrAttributeRepresentation);
        config.put(AdvancedAttributeToRoleMapper.ARE_ATTRIBUTE_VALUES_REGEX_PROPERTY_NAME,
                Boolean.valueOf(areClaimsOrAttributeValuesRegexes).toString());
        config.put(ConfigConstants.ROLE, roleValue);
        advancedAttributeToRoleMapper.setConfig(config);

        persistMapper(advancedAttributeToRoleMapper);
    }

    @Test
    public void attributeFriendlyNameGetsConsideredAndMatchedToRole() {
        createAdvancedRoleMapper(ATTRIBUTES, false);
        Map<String, List<String>> userConfig = new HashMap<>();
        userConfig.put(SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_FRIENDLY_NAME, List.of("value 1", "value 2"));
        userConfig.put(SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME_2, List.of("value 2"));
        createUserInProviderRealm(userConfig);

        loginAsUserFirstTime();

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }
}
