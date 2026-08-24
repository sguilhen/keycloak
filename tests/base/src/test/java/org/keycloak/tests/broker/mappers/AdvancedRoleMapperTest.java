package org.keycloak.tests.broker.mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.UserRepresentation;

import org.junit.jupiter.api.Test;

import static org.keycloak.models.IdentityProviderMapperSyncMode.FORCE;
import static org.keycloak.models.IdentityProviderMapperSyncMode.IMPORT;

/**
 * Shared advanced (multi claim/attribute) role-mapper tests for both OIDC and SAML. Stateless: the
 * value used for the second login is threaded through {@code createMapperAndLoginAsUserTwiceWithMapper}
 * as a parameter and applied via a closure, replacing the legacy mutable {@code newValueForAttribute2}
 * field. Mirrors the legacy {@code AbstractAdvancedRoleMapperTest}.
 */
public interface AdvancedRoleMapperTest extends RoleMapperTest {

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

    @Test
    default void allValuesMatch() {
        createAdvancedRoleMapper(CLAIMS_OR_ATTRIBUTES, false);
        createUserInProviderRealm(createMatchingUserConfig());

        loginAsUserFirstTime();

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    default void valuesMismatch() {
        createAdvancedRoleMapper(CLAIMS_OR_ATTRIBUTES, false);
        createUserInProviderRealm(Map.of(
                ATTRIBUTE_TO_MAP_NAME, List.of("value 1"),
                ATTRIBUTE_TO_MAP_NAME_2, List.of("value mismatch")));

        loginAsUserFirstTime();

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    default void valuesMatchIfNoClaimsSpecified() {
        createAdvancedRoleMapper("[]", false);
        createUserInProviderRealm(Map.of(
                ATTRIBUTE_TO_MAP_NAME, List.of("some value"),
                ATTRIBUTE_TO_MAP_NAME_2, List.of("some value")));

        loginAsUserFirstTime();

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    default void allValuesMatchRegex() {
        createAdvancedRoleMapper(CLAIMS_OR_ATTRIBUTES_REGEX, true);
        createUserInProviderRealm(createMatchingUserConfig());

        loginAsUserFirstTime();

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    default void valuesMismatchRegex() {
        createAdvancedRoleMapper(CLAIMS_OR_ATTRIBUTES_REGEX, true);
        createUserInProviderRealm(Map.of(
                ATTRIBUTE_TO_MAP_NAME, List.of("mismatch"),
                ATTRIBUTE_TO_MAP_NAME_2, List.of("value 2")));

        loginAsUserFirstTime();

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    default void updateBrokeredUserMismatchDeletesRole() {
        createMapperAndLoginAsUserTwiceWithMapper(FORCE, false, "value mismatch");

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    default void updateBrokeredUserMismatchDoesNotDeleteRoleInImportMode() {
        createMapperAndLoginAsUserTwiceWithMapper(IMPORT, false, "value mismatch");

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    default void updateBrokeredUserMatchDoesntDeleteRole() {
        createMapperAndLoginAsUserTwiceWithMapper(FORCE, false, "value 2");

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    default void updateBrokeredUserAssignsRoleInForceModeWhenCreatingTheMapperAfterFirstLogin() {
        createMapperAndLoginAsUserTwiceWithMapper(FORCE, true, "value 2");

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    default void valuesMatchIfNullClaimsSpecified() {
        createAdvancedRoleMapper(null, false);
        createUserInProviderRealm(Map.of(
                ATTRIBUTE_TO_MAP_NAME, List.of("some value"),
                ATTRIBUTE_TO_MAP_NAME_2, List.of("some value")));

        loginAsUserFirstTime();

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    default void createMapperAndLoginAsUserTwiceWithMapper(IdentityProviderMapperSyncMode syncMode,
            boolean createAfterFirstLogin, String newValueForAttribute2) {
        loginAsUserTwiceWithMapper(syncMode, createAfterFirstLogin, createMatchingUserConfig(),
                () -> updateAdvancedUser(newValueForAttribute2));
    }

    default void updateAdvancedUser(String newValueForAttribute2) {
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
    default void createMapperInIdp(IdentityProviderMapperSyncMode syncMode, String roleValue) {
        createMapperInIdp(CLAIMS_OR_ATTRIBUTES, false, syncMode, roleValue);
    }

    @Override
    default Map<String, List<String>> createUserConfigForRole(String roleValue) {
        return createMatchingUserConfig();
    }

    default Map<String, List<String>> createMatchingUserConfig() {
        Map<String, List<String>> config = new HashMap<>();
        config.put(ATTRIBUTE_TO_MAP_NAME, List.of("value 1", "value 2"));
        config.put(ATTRIBUTE_TO_MAP_NAME_2, List.of("value 2"));
        return config;
    }

    default void createAdvancedRoleMapper(String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes) {
        createMapperInIdp(claimsOrAttributeRepresentation, areClaimsOrAttributeValuesRegexes, IMPORT,
                CLIENT_ROLE_MAPPER_REPRESENTATION);
    }

    void createMapperInIdp(String claimsOrAttributeRepresentation, boolean areClaimsOrAttributeValuesRegexes,
            IdentityProviderMapperSyncMode syncMode, String roleValue);
}
