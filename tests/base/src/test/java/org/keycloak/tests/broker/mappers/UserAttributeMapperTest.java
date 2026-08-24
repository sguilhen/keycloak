package org.keycloak.tests.broker.mappers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.keycloak.admin.client.resource.IdentityProviderResource;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.tests.broker.KcOidcBrokerConfigSupport;
import org.keycloak.tests.broker.SamlBrokerConfigSupport;
import org.keycloak.testsuite.util.AccountHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Shared test logic for user-attribute mapper tests, ported from the legacy
 * {@code AbstractUserAttributeMapperTest}. Concrete classes supply the identity-provider mappers.
 */
public interface UserAttributeMapperTest extends IdentityProviderMapperTest {

    String MAPPED_ATTRIBUTE_NAME = "mapped-user-attribute";
    String MAPPED_ATTRIBUTE_FRIENDLY_NAME = "mapped-user-attribute-friendly";

    Set<String> PROTECTED_NAMES = Set.of("email", "lastName", "firstName");

    Map<String, String> ATTRIBUTE_NAME_TRANSLATION = Map.of(
            "dotted.email", "dotted.email",
            "nested.email", "nested.email",
            SamlBrokerConfigSupport.ATTRIBUTE_TO_MAP_FRIENDLY_NAME, MAPPED_ATTRIBUTE_FRIENDLY_NAME,
            KcOidcBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME, MAPPED_ATTRIBUTE_NAME);

    String ATTRIBUTE_TO_MAP_NAME = KcOidcBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME;

    Iterable<IdentityProviderMapperRepresentation> createIdentityProviderMappers(IdentityProviderMapperSyncMode syncMode, boolean nullable);

    /**
     * The nullable-attribute tests deliberately clear the user's email - on the provider user before the
     * second login, and (via the mapper) on the brokered consumer user. With the declarative user profile
     * the {@code VERIFY_PROFILE} required action then interrupts the next login with an update-profile page
     * on whichever realm holds the now-invalid profile, which the legacy suite never hit. Disable it on
     * both realms so clearing a protected attribute completes the login instead of stalling on the form.
     */
    @BeforeEach
    default void disableVerifyProfile() {
        disableVerifyProfile(getProviderRealm());
        disableVerifyProfile(getConsumerRealm());
    }

    private void disableVerifyProfile(org.keycloak.testframework.realm.ManagedRealm realm) {
        for (RequiredActionProviderRepresentation action : realm.admin().flows().getRequiredActions()) {
            if ("VERIFY_PROFILE".equals(action.getAlias()) && action.isEnabled()) {
                action.setEnabled(false);
                realm.admin().flows().updateRequiredAction(action.getAlias(), action);
            }
        }
    }

    default void addIdentityProviderToConsumerRealm(IdentityProviderMapperSyncMode syncMode, boolean nullable) {
        IdentityProviderRepresentation idp = setupIdentityProvider();
        IdentityProviderResource idpResource = getIdpResource();
        for (IdentityProviderMapperRepresentation mapper : createIdentityProviderMappers(syncMode, nullable)) {
            mapper.setIdentityProviderAlias(idp.getAlias());
            idpResource.addMapper(mapper).close();
        }
    }

    default void assertUserAttributes(Map<String, List<String>> attrs, UserRepresentation userRep) {
        Set<String> mappedAttrNames = attrs.entrySet().stream()
                .filter(me -> me.getValue() != null && !me.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .filter(a -> !PROTECTED_NAMES.contains(a))
                .map(ATTRIBUTE_NAME_TRANSLATION::get)
                .collect(Collectors.toSet());

        if (mappedAttrNames.isEmpty()) {
            Assertions.assertNull(userRep.getAttributes(), "No attributes are expected to be present");
        } else {
            Assertions.assertNotNull(userRep.getAttributes());
            Assertions.assertEquals(mappedAttrNames, userRep.getAttributes().keySet());
            for (Map.Entry<String, List<String>> me : attrs.entrySet()) {
                String mappedAttrName = ATTRIBUTE_NAME_TRANSLATION.get(me.getKey());
                if (mappedAttrNames.contains(mappedAttrName)) {
                    Assertions.assertEquals(
                            me.getValue().stream().sorted().collect(Collectors.toList()),
                            userRep.getAttributes().get(mappedAttrName).stream().sorted().collect(Collectors.toList()));
                }
            }
        }

        if (attrs.containsKey("email")) {
            Assertions.assertEquals(attrs.get("email").get(0), userRep.getEmail());
        }
        if (attrs.containsKey("firstName")) {
            Assertions.assertEquals(attrs.get("firstName").get(0), userRep.getFirstName());
        }
        if (attrs.containsKey("lastName")) {
            Assertions.assertEquals(attrs.get("lastName").get(0), userRep.getLastName());
        }
    }

    default void testValueMappingForImportSyncMode(Map<String, List<String>> initial, Map<String, List<String>> modified) {
        addIdentityProviderToConsumerRealm(IdentityProviderMapperSyncMode.IMPORT, false);
        testValueMapping(initial, modified, initial);
    }

    default void testValueMappingForForceSyncMode(Map<String, List<String>> initial, Map<String, List<String>> modified) {
        addIdentityProviderToConsumerRealm(IdentityProviderMapperSyncMode.FORCE, false);
        testValueMapping(initial, modified, modified);
    }

    default void testValueMappingForLegacySyncMode(Map<String, List<String>> initial, Map<String, List<String>> modified) {
        addIdentityProviderToConsumerRealm(IdentityProviderMapperSyncMode.LEGACY, false);
        testValueMapping(initial, modified, modified);
    }

    default void testValueMapping(Map<String, List<String>> initialUserAttributes, Map<String, List<String>> modifiedUserAttributes, Map<String, List<String>> assertedModifiedAttributes) {
        String email = getUserEmail();
        createUserInProviderRealm(initialUserAttributes);

        // first broker login imports the user into the consumer realm
        logInAsUserInIDP();
        updateAccountInformation();
        UserRepresentation userRep = findUser(getConsumerRealm().admin(), getUserLogin(), email);
        assertUserAttributes(initialUserAttributes, userRep);

        AccountHelper.logout(getConsumerRealm().admin(), getUserLogin());
        AccountHelper.logout(getProviderRealm().admin(), getUserLogin());

        // update user in provider realm
        UserRepresentation userRepProvider = findUser(getProviderRealm().admin(), getUserLogin(), email);
        Map<String, List<String>> modifiedWithoutSpecialKeys = modifiedUserAttributes.entrySet().stream()
                .filter(a -> !PROTECTED_NAMES.contains(a.getKey()))
                .filter(a -> a.getValue() != null) // remove empty attributes
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        userRepProvider.setAttributes(modifiedWithoutSpecialKeys);
        if (modifiedUserAttributes.containsKey("email")) {
            userRepProvider.setEmail(modifiedUserAttributes.get("email").get(0));
            email = modifiedUserAttributes.get("email").get(0);
        }
        if (modifiedUserAttributes.containsKey("firstName")) {
            userRepProvider.setFirstName(modifiedUserAttributes.get("firstName").get(0));
        }
        if (modifiedUserAttributes.containsKey("lastName")) {
            userRepProvider.setLastName(modifiedUserAttributes.get("lastName").get(0));
        }
        getProviderRealm().admin().users().get(userRepProvider.getId()).update(userRepProvider);

        // second broker login re-imports the user according to the sync mode
        logInAsUserInIDP();
        userRep = findUser(getConsumerRealm().admin(), getUserLogin(), email);
        assertUserAttributes(assertedModifiedAttributes, userRep);
    }

    @Test
    default void testBasicMappingSingleValueForce() {
        testValueMappingForForceSyncMode(
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "value 1").build(),
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "second value").build());
    }

    @Test
    default void testProtectedAttributesAreSetNullInLegacySyncModeWhenNullable() {
        addIdentityProviderToConsumerRealm(IdentityProviderMapperSyncMode.LEGACY, true);
        testValueMapping(
                AttrMap.create().put("email", getUserEmail()).build(),
                AttrMap.create().putNull("email").build(),
                AttrMap.create().putNull("email").build());
    }

    @Test
    default void testProtectedAttributesAreSetNullInForceSyncModeWhenNullable() {
        addIdentityProviderToConsumerRealm(IdentityProviderMapperSyncMode.FORCE, true);
        testValueMapping(
                AttrMap.create().put("email", getUserEmail()).build(),
                AttrMap.create().putNull("email").build(),
                AttrMap.create().putNull("email").build());
    }

    @Test
    default void testBasicMappingSingleValueImport() {
        testValueMappingForImportSyncMode(
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "value 1").build(),
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "second value").build());
    }

    @Test
    default void testBasicMappingEmail() {
        testValueMappingForForceSyncMode(
                AttrMap.create()
                        .put("email", getUserEmail())
                        .put("nested.email", getUserEmail())
                        .put("dotted.email", getUserEmail()).build(),
                AttrMap.create()
                        .put("email", "other_email@redhat.com")
                        .put("nested.email", "other_email@redhat.com")
                        .put("dotted.email", "other_email@redhat.com").build());
    }

    @Test
    default void testBasicMappingAttributeGetsModifiedInSyncModeForce() {
        testValueMappingForForceSyncMode(
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "value 1").build(),
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME).build());
    }

    @Test
    default void testBasicMappingAttributeGetsRemovedInSyncModeForce() {
        testValueMappingForForceSyncMode(
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "value 1").build(),
                AttrMap.create().build());
    }

    @Test
    default void testBasicMappingAttributeWithMultipleValuesIsModifiedInSyncModeForce() {
        testValueMappingForForceSyncMode(
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "value 1", "value 2").build(),
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "second value", "second value 2").build());
    }

    @Test
    default void testBasicMappingAttributeWithMultipleValuesIsModifiedInSyncModeLegacy() {
        testValueMappingForLegacySyncMode(
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "value 1", "value 2").build(),
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "second value", "second value 2").build());
    }

    @Test
    default void testBasicMappingAttributeWithMultipleValuesDoesNotGetModifiedInSyncModeImport() {
        testValueMappingForImportSyncMode(
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "value 1", "value 2").build(),
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "second value", "second value 2").build());
    }

    @Test
    default void testBasicMappingAttributeWithMultipleValuesGetsAddedInSyncModeForce() {
        testValueMappingForForceSyncMode(
                AttrMap.create().build(),
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "second value", "second value 2").build());
    }

    @Test
    default void testBasicMappingAttributeWithMultipleValuesDoesNotGetAddedInSyncModeImport() {
        testValueMappingForImportSyncMode(
                AttrMap.create().build(),
                AttrMap.create().put(ATTRIBUTE_TO_MAP_NAME, "second value", "second value 2").build());
    }

    /**
     * Small builder for the attribute maps used by these tests. Supports empty and explicit-null
     * value lists, which the standard {@code Map.of}/{@code List.of} factories forbid.
     */
    class AttrMap {
        private final Map<String, List<String>> map = new HashMap<>();

        static AttrMap create() {
            return new AttrMap();
        }

        AttrMap put(String key, String... values) {
            map.put(key, Arrays.asList(values));
            return this;
        }

        AttrMap put(String key, List<String> values) {
            map.put(key, values);
            return this;
        }

        AttrMap putNull(String key) {
            map.put(key, Collections.singletonList(null));
            return this;
        }

        Map<String, List<String>> build() {
            return map;
        }
    }
}
