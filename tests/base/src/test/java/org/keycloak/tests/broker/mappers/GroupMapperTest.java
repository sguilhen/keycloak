package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.util.AccountHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base support for group-mapper tests. Stateless: the mapped group's id is re-derived from the admin
 * API (searched by name) rather than stored in a field, and per-login mutations of the provider user
 * are supplied as a {@link Runnable} instead of an overridable {@code updateUser()} hook. Mirrors the
 * legacy {@code AbstractGroupMapperTest}.
 */
public interface GroupMapperTest extends IdentityProviderMapperTest {

    String MAPPER_TEST_GROUP_NAME = "mapper-test";
    String MAPPER_TEST_GROUP_PATH = buildGroupPath(MAPPER_TEST_GROUP_NAME);
    String MAPPER_TEST_NOT_EXISTING_GROUP_PATH = buildGroupPath("mapper-test-not-existing");

    String createMapperInIdp(IdentityProviderMapperSyncMode syncMode, String groupPath);

    /**
     * Sets up a scenario with the given group path.
     * @return the ID of the mapper
     */
    String setupScenarioWithGroupPath(String groupPath);

    void setupScenarioWithNonExistingGroup();

    @BeforeEach
    default void addMapperTestGroupToConsumerRealm() {
        GroupRepresentation mapperTestGroup = new GroupRepresentation();
        mapperTestGroup.setName(MAPPER_TEST_GROUP_NAME);
        getConsumerRealm().admin().groups().add(mapperTestGroup).close();
    }

    /**
     * Re-derives the id of the mapped group by walking the consumer realm's group tree. The group is
     * always named {@link #MAPPER_TEST_GROUP_NAME}; the movement/rename tests fetch this id before
     * they rename or relocate the group, so a name search is sufficient.
     */
    default String getMapperGroupId() {
        RealmResource consumer = getConsumerRealm().admin();
        String id = findGroupIdByName(consumer, consumer.groups().groups(null, 0, Integer.MAX_VALUE, false),
                MAPPER_TEST_GROUP_NAME);
        Assertions.assertNotNull(id, "Expected group " + MAPPER_TEST_GROUP_NAME + " to exist");
        return id;
    }

    default String findGroupIdByName(RealmResource consumer, List<GroupRepresentation> groups, String name) {
        for (GroupRepresentation group : groups) {
            if (name.equals(group.getName())) {
                return group.getId();
            }
            List<GroupRepresentation> subGroups =
                    consumer.groups().group(group.getId()).getSubGroups(0, Integer.MAX_VALUE, false);
            String found = findGroupIdByName(consumer, subGroups, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    default String persistGroupMapper(IdentityProviderMapperRepresentation idpMapper) {
        idpMapper.setIdentityProviderAlias(getIdpAlias());
        try (Response response = getIdpResource().addMapper(idpMapper)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    /**
     * Logs the user in twice with the mapper active. The {@code updateAction} performs whatever
     * provider-side mutation should happen between the two logins (replaces the legacy per-subclass
     * {@code updateUser()} override).
     */
    default UserRepresentation loginAsUserTwiceWithMapper(IdentityProviderMapperSyncMode syncMode,
            boolean createAfterFirstLogin, Map<String, List<String>> userConfig, String groupPath,
            Runnable updateAction) {
        if (!createAfterFirstLogin) {
            createMapperInIdp(syncMode, groupPath);
        }
        createUserInProviderRealm(userConfig);

        loginAsUserFirstTimeAndAssertSuccess();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        if (!createAfterFirstLogin) {
            assertThatUserHasBeenAssignedToGroup(user);
        } else {
            assertThatUserHasNotBeenAssignedToGroup(user);
        }

        if (createAfterFirstLogin) {
            createMapperInIdp(syncMode, groupPath);
        }
        AccountHelper.logout(getConsumerRealm().admin(), getUserLogin());

        updateAction.run();

        logInAsUserInIDP();
        Assertions.assertTrue(getOAuthClient().parseLoginResponse().isSuccess());

        return findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
    }

    default void assertMapperHasExpectedPathAndSucceeds(String mapperId, String expectedGroupPath) {
        IdentityProviderMapperRepresentation mapper = getIdpResource().getMapperById(mapperId);
        Map<String, String> config = mapper.getConfig();
        Assertions.assertEquals(expectedGroupPath, config.get(ConfigConstants.GROUP));

        loginAsUserFirstTimeAndAssertSuccess();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        assertThatUserHasBeenAssignedToGroup(user, expectedGroupPath);
    }

    default void assertThatUserHasBeenAssignedToGroup(UserRepresentation user) {
        assertThatUserHasBeenAssignedToGroup(user, MAPPER_TEST_GROUP_PATH);
    }

    default void assertThatUserHasBeenAssignedToGroup(UserRepresentation user, String groupPath) {
        Assertions.assertTrue(getUserGroupPaths(user).contains(groupPath),
                "Expected user to be assigned to group " + groupPath);
    }

    default void assertThatUserHasNotBeenAssignedToGroup(UserRepresentation user) {
        Assertions.assertFalse(getUserGroupPaths(user).contains(MAPPER_TEST_GROUP_PATH),
                "Expected user not to be assigned to group " + MAPPER_TEST_GROUP_PATH);
    }

    default void assertThatUserDoesNotHaveGroups(UserRepresentation user) {
        Assertions.assertTrue(getUserGroupPaths(user).isEmpty(), "Expected user to have no groups");
    }

    static String buildGroupPath(String firstSegment, String... furtherSegments) {
        String separator = KeycloakModelUtils.GROUP_PATH_SEPARATOR;
        StringBuilder sb = new StringBuilder(separator).append(firstSegment);
        for (String furtherSegment : furtherSegments) {
            sb.append(separator).append(furtherSegment);
        }
        return sb.toString();
    }

    default List<String> getUserGroupPaths(UserRepresentation user) {
        return getConsumerRealm().admin().users().get(user.getId()).groups().stream()
                .map(GroupRepresentation::getPath)
                .collect(Collectors.toList());
    }
}
