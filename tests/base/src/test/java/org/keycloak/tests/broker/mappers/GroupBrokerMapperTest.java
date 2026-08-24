package org.keycloak.tests.broker.mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.UserRepresentation;

import org.junit.jupiter.api.Test;

import static org.keycloak.models.IdentityProviderMapperSyncMode.IMPORT;

/**
 * Shared advanced (multi claim/attribute) group-mapper tests for both OIDC and SAML. Stateless: the
 * value used for the second login is threaded through {@code createMapperAndLoginAsUserTwiceWithMapper}
 * as a parameter and applied via a closure, replacing the legacy mutable {@code newValueForAttribute2}
 * field. Mirrors the legacy {@code AbstractGroupBrokerMapperTest}.
 */
public interface GroupBrokerMapperTest extends GroupMapperTest {

    String ATTRIBUTE_TO_MAP_NAME = "user-attribute";
    String ATTRIBUTE_TO_MAP_NAME_2 = "user-attribute-2";

    String CLAIMS_OR_ATTRIBUTES = "[\n" +
            "  {\n" +
            "    \"key\": \"" + ATTRIBUTE_TO_MAP_NAME + "\",\n" +
            "    \"value\": \"value 1\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"key\": \"" + ATTRIBUTE_TO_MAP_NAME + "\",\n" +
            "    \"value\": \"value 2\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"key\": \"" + ATTRIBUTE_TO_MAP_NAME_2 + "\",\n" +
            "    \"value\": \"value 2\"\n" +
            "  }\n" +
            "]";

    String CLAIMS_OR_ATTRIBUTES_REGEX = "[\n" +
            "  {\n" +
            "    \"key\": \"" + ATTRIBUTE_TO_MAP_NAME + "\",\n" +
            "    \"value\": \"va.*\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"key\": \"" + ATTRIBUTE_TO_MAP_NAME_2 + "\",\n" +
            "    \"value\": \"value 2\"\n" +
            "  }\n" +
            "]";

    default UserRepresentation createMapperAndLoginAsUserTwiceWithMapper(IdentityProviderMapperSyncMode syncMode,
            boolean createAfterFirstLogin, String groupPath, String newValueForAttribute2) {
        return loginAsUserTwiceWithMapper(syncMode, createAfterFirstLogin, createMatchingAttributes(), groupPath,
                () -> updateGroupUser(newValueForAttribute2));
    }

    @Test
    default void valuesMatchIfNullClaimsSpecified() {
        createAdvancedGroupMapper(null, false, MAPPER_TEST_GROUP_PATH);
        createUserInProviderRealm(Map.of(
                ATTRIBUTE_TO_MAP_NAME, List.of("some value"),
                ATTRIBUTE_TO_MAP_NAME_2, List.of("some value")));

        loginAsUserFirstTimeAndAssertSuccess();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        assertThatUserHasBeenAssignedToGroup(user);
    }

    default void updateGroupUser(String newValueForAttribute2) {
        RealmResource providerRealm = getProviderRealm().admin();
        UserRepresentation user = findUser(providerRealm, getUserLogin(), getUserEmail());
        Map<String, List<String>> matchingAttributes = new HashMap<>();
        matchingAttributes.put(ATTRIBUTE_TO_MAP_NAME, List.of("value 1", "value 2"));
        matchingAttributes.put(ATTRIBUTE_TO_MAP_NAME_2, List.of(newValueForAttribute2));
        matchingAttributes.put("some.other.attribute", List.of("some value"));
        user.setAttributes(matchingAttributes);
        providerRealm.users().get(user.getId()).update(user);
    }

    @Override
    default String createMapperInIdp(IdentityProviderMapperSyncMode syncMode, String groupPath) {
        return createMapperInIdp(CLAIMS_OR_ATTRIBUTES, false, syncMode, groupPath);
    }

    @Override
    default String setupScenarioWithGroupPath(String groupPath) {
        String mapperId = createAdvancedGroupMapper(CLAIMS_OR_ATTRIBUTES, false, groupPath);
        createUserInProviderRealm(createMatchingAttributes());
        return mapperId;
    }

    @Override
    default void setupScenarioWithNonExistingGroup() {
        createAdvancedGroupMapper(CLAIMS_OR_ATTRIBUTES, false, MAPPER_TEST_NOT_EXISTING_GROUP_PATH);
        createUserInProviderRealm(createMatchingAttributes());
    }

    default String createAdvancedGroupMapper(String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes, String groupPath) {
        return createMapperInIdp(claimsOrAttributeRepresentation, areClaimsOrAttributeValuesRegexes, IMPORT, groupPath);
    }

    String createMapperInIdp(String claimsOrAttributeRepresentation, boolean areClaimsOrAttributeValuesRegexes,
            IdentityProviderMapperSyncMode syncMode, String groupPath);

    default Map<String, List<String>> createMatchingAttributes() {
        Map<String, List<String>> config = new HashMap<>();
        config.put(ATTRIBUTE_TO_MAP_NAME, List.of("value 1", "value 2"));
        config.put(ATTRIBUTE_TO_MAP_NAME_2, List.of("value 2"));
        return config;
    }
}
