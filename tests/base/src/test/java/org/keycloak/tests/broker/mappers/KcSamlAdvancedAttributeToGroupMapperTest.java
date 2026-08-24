package org.keycloak.tests.broker.mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.admin.client.resource.IdentityProviderResource;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.broker.saml.mappers.AdvancedAttributeToGroupMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.tests.broker.SamlBrokerConfigSupport;
import org.keycloak.testsuite.util.AccountHelper;

import org.junit.jupiter.api.Test;

@KeycloakIntegrationTest
public class KcSamlAdvancedAttributeToGroupMapperTest extends AbstractSamlMapperTest
        implements GroupBrokerMapperTest {

    private static final String ATTRIBUTES = "[\n" +
            "  {\n" +
            "    \"key\": \"" + SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_FRIENDLY_NAME + "\",\n" +
            "    \"value\": \"value 1\"\n" +
            "  },\n" + "  {\n" +
            "    \"key\": \"" + SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_FRIENDLY_NAME + "\",\n" +
            "    \"value\": \"value 2\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"key\": \"" + SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME_2 + "\",\n" +
            "    \"value\": \"value 2\"\n" +
            "  }\n" +
            "]";

    @Override
    public String createMapperInIdp(String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes, IdentityProviderMapperSyncMode syncMode, String groupValue) {
        IdentityProviderMapperRepresentation advancedAttributeToGroupMapper =
                new IdentityProviderMapperRepresentation();
        advancedAttributeToGroupMapper.setName("advanced-attribute-to-group-mapper");
        advancedAttributeToGroupMapper.setIdentityProviderMapper(AdvancedAttributeToGroupMapper.PROVIDER_ID);

        Map<String, String> config = new HashMap<>();
        config.put(IdentityProviderMapperModel.SYNC_MODE, syncMode.toString());
        config.put(AdvancedAttributeToGroupMapper.ATTRIBUTE_PROPERTY_NAME, claimsOrAttributeRepresentation);
        config.put(AdvancedAttributeToGroupMapper.ARE_ATTRIBUTE_VALUES_REGEX_PROPERTY_NAME,
                Boolean.valueOf(areClaimsOrAttributeValuesRegexes).toString());
        config.put(ConfigConstants.GROUP, MAPPER_TEST_GROUP_PATH);
        advancedAttributeToGroupMapper.setConfig(config);

        return persistGroupMapper(advancedAttributeToGroupMapper);
    }

    @Test
    public void attributeFriendlyNameGetsConsideredAndMatchedToGroup() {
        createAdvancedGroupMapper(ATTRIBUTES, false, SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME_2);
        Map<String, List<String>> userConfig = new HashMap<>();
        userConfig.put(SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_FRIENDLY_NAME, List.of("value 1", "value 2"));
        userConfig.put(SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME_2, List.of("value 2"));
        createUserInProviderRealm(userConfig);

        loginAsUserFirstTime();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        assertThatUserHasBeenAssignedToGroup(user, MAPPER_TEST_GROUP_PATH);
    }

    @Test
    public void removingAndAddingTheGroupKeepsTheGroup() {
        // Create a mapper that is always executed (force)
        String idpMapperId =
                createAdvancedGroupMapper(ATTRIBUTES, false, SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME_2);
        IdentityProviderResource idp = getIdpResource();
        IdentityProviderMapperRepresentation idpMapper = idp.getMapperById(idpMapperId);
        idpMapper.getConfig().put(IdentityProviderMapperModel.SYNC_MODE,
                IdentityProviderMapperSyncMode.FORCE.toString());
        idp.update(idpMapperId, idpMapper);

        Map<String, List<String>> userConfig = new HashMap<>();
        userConfig.put(SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_FRIENDLY_NAME, List.of("value 1", "value 2"));
        userConfig.put(SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME_2, List.of("value 2"));
        createUserInProviderRealm(userConfig);

        // Login once and logout on both sides
        loginAsUserFirstTimeAndAssertSuccess();
        AccountHelper.logout(getConsumerRealm().admin(), getUserLogin());
        AccountHelper.logout(getProviderRealm().admin(), getUserLogin());

        // Ensure that the expected group exists
        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        assertThatUserHasBeenAssignedToGroup(user, MAPPER_TEST_GROUP_PATH);

        // Add a mapper to remove the group, and ensure that it has a smaller ID than the other one to ensure that it is
        // executed first
        idpMapper.getConfig().put("attributes", "[{\"key\": \"key\", \"value\": \"value\"}]");
        idpMapper.setId("00000000-00000000-00000000-00000000");
        idpMapper.setName(idpMapper.getName() + "-2");
        idp.addMapper(idpMapper).close();

        logInAsUserInIDP();
        user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        assertThatUserHasBeenAssignedToGroup(user, MAPPER_TEST_GROUP_PATH);
    }
}
