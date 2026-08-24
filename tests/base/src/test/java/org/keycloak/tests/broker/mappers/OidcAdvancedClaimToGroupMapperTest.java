package org.keycloak.tests.broker.mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.broker.oidc.mappers.AdvancedClaimToGroupMapper;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.keycloak.models.IdentityProviderMapperSyncMode.FORCE;
import static org.keycloak.models.IdentityProviderMapperSyncMode.IMPORT;

@KeycloakIntegrationTest
public class OidcAdvancedClaimToGroupMapperTest extends AbstractOidcMapperTest implements GroupBrokerMapperTest {

    protected boolean isHardcodedGroup() {
        return false;
    }

    @Override
    public String createMapperInIdp(String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes, IdentityProviderMapperSyncMode syncMode, String groupPath) {
        IdentityProviderMapperRepresentation advancedClaimToGroupMapper = new IdentityProviderMapperRepresentation();
        advancedClaimToGroupMapper.setName("advanced-claim-to-group-mapper");
        advancedClaimToGroupMapper.setIdentityProviderMapper(AdvancedClaimToGroupMapper.PROVIDER_ID);

        Map<String, String> config = new HashMap<>();
        config.put(IdentityProviderMapperModel.SYNC_MODE, syncMode.toString());
        config.put(AdvancedClaimToGroupMapper.CLAIM_PROPERTY_NAME, claimsOrAttributeRepresentation);
        config.put(AdvancedClaimToGroupMapper.ARE_CLAIM_VALUES_REGEX_PROPERTY_NAME,
                Boolean.valueOf(areClaimsOrAttributeValuesRegexes).toString());
        config.put(ConfigConstants.GROUP, groupPath);
        advancedClaimToGroupMapper.setConfig(config);

        return persistGroupMapper(advancedClaimToGroupMapper);
    }

    @Test
    public void allValuesMatch() {
        createAdvancedGroupMapper(CLAIMS_OR_ATTRIBUTES, false, MAPPER_TEST_GROUP_PATH);
        createUserInProviderRealm(createMatchingAttributes());

        loginAsUserFirstTimeAndAssertSuccess();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        assertThatUserHasBeenAssignedToGroup(user);
    }

    @Test
    public void valuesMismatch() {
        createAdvancedGroupMapper(CLAIMS_OR_ATTRIBUTES, false, MAPPER_TEST_GROUP_PATH);
        createUserInProviderRealm(Map.of(
                GroupBrokerMapperTest.ATTRIBUTE_TO_MAP_NAME, List.of("value 1"),
                GroupBrokerMapperTest.ATTRIBUTE_TO_MAP_NAME_2, List.of("value mismatch")));

        loginAsUserFirstTimeAndAssertSuccess();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        if (!isHardcodedGroup()) {
            assertThatUserHasNotBeenAssignedToGroup(user);
        } else {
            assertThatUserHasBeenAssignedToGroup(user);
        }
    }

    @Test
    public void valuesMatchIfNoClaimsSpecified() {
        createAdvancedGroupMapper("[]", false, MAPPER_TEST_GROUP_PATH);
        createUserInProviderRealm(Map.of(
                GroupBrokerMapperTest.ATTRIBUTE_TO_MAP_NAME, List.of("some value"),
                GroupBrokerMapperTest.ATTRIBUTE_TO_MAP_NAME_2, List.of("some value")));

        loginAsUserFirstTimeAndAssertSuccess();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        assertThatUserHasBeenAssignedToGroup(user);
    }

    @Test
    public void allValuesMatchRegex() {
        createAdvancedGroupMapper(CLAIMS_OR_ATTRIBUTES_REGEX, true, MAPPER_TEST_GROUP_PATH);
        createUserInProviderRealm(createMatchingAttributes());

        loginAsUserFirstTimeAndAssertSuccess();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        assertThatUserHasBeenAssignedToGroup(user);
    }

    @Test
    public void valuesMismatchRegex() {
        createAdvancedGroupMapper(CLAIMS_OR_ATTRIBUTES_REGEX, true, MAPPER_TEST_GROUP_PATH);
        createUserInProviderRealm(Map.of(
                GroupBrokerMapperTest.ATTRIBUTE_TO_MAP_NAME, List.of("mismatch"),
                GroupBrokerMapperTest.ATTRIBUTE_TO_MAP_NAME_2, List.of("value 2")));

        loginAsUserFirstTimeAndAssertSuccess();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        if (!isHardcodedGroup()) {
            assertThatUserHasNotBeenAssignedToGroup(user);
        } else {
            assertThatUserHasBeenAssignedToGroup(user);
        }
    }

    @Test
    public void updateBrokeredUserMismatchLeavesGroup() {
        UserRepresentation user = createMapperAndLoginAsUserTwiceWithMapper(FORCE, false, MAPPER_TEST_GROUP_PATH,
                "value mismatch");

        if (!isHardcodedGroup()) {
            assertThatUserHasNotBeenAssignedToGroup(user);
        } else {
            assertThatUserHasBeenAssignedToGroup(user);
        }
    }

    @Test
    public void updateBrokeredUserMismatchDoesNotLeaveGroupInImportMode() {
        UserRepresentation user = createMapperAndLoginAsUserTwiceWithMapper(IMPORT, false, MAPPER_TEST_GROUP_PATH,
                "value mismatch");

        assertThatUserHasBeenAssignedToGroup(user);
    }

    @Test
    public void updateBrokeredUserMatchDoesntLeaveGroup() {
        UserRepresentation user = createMapperAndLoginAsUserTwiceWithMapper(FORCE, false, MAPPER_TEST_GROUP_PATH,
                "value 2");

        assertThatUserHasBeenAssignedToGroup(user);
    }

    @Test
    public void tryToUpdateBrokeredUserWithMissingGroupDoesNotBreakLogin() {
        UserRepresentation user = createMapperAndLoginAsUserTwiceWithMapper(FORCE, true,
                MAPPER_TEST_NOT_EXISTING_GROUP_PATH, "value 2");

        assertThatUserDoesNotHaveGroups(user);
    }

    @Test
    public void updateBrokeredUserIsAssignedToGroupInForceModeWhenCreatingTheMapperAfterFirstLogin() {
        UserRepresentation user = createMapperAndLoginAsUserTwiceWithMapper(FORCE, true, MAPPER_TEST_GROUP_PATH,
                "value 2");

        assertThatUserHasBeenAssignedToGroup(user);
    }

    @Test
    public void tryToCreateBrokeredUserWithNonExistingGroupDoesNotBreakLogin() {
        setupScenarioWithNonExistingGroup();

        loginAsUserFirstTimeAndAssertSuccess();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        assertThatUserDoesNotHaveGroups(user);
    }

    @Test
    public void mapperStillWorksWhenTopLevelGroupIsConvertedToSubGroup() {
        String mapperId = setupScenarioWithGroupPath(MAPPER_TEST_GROUP_PATH);
        RealmResource consumer = getConsumerRealm().admin();

        String newParentGroupName = "new-parent";
        GroupRepresentation newParentGroup = new GroupRepresentation();
        newParentGroup.setName(newParentGroupName);
        String newParentGroupId = CreatedResponseUtil.getCreatedId(consumer.groups().add(newParentGroup));

        GroupRepresentation mappedGroup = consumer.groups().group(getMapperGroupId()).toRepresentation();
        consumer.groups().group(newParentGroupId).subGroup(mappedGroup).close();

        String expectedNewGroupPath = GroupMapperTest.buildGroupPath(newParentGroupName, MAPPER_TEST_GROUP_NAME);

        assertMapperHasExpectedPathAndSucceeds(mapperId, expectedNewGroupPath);
    }

    @Test
    public void mapperStillWorksWhenSubGroupChangesParent() {
        RealmResource consumer = getConsumerRealm().admin();

        String parentGroupName = "parent-group";
        GroupRepresentation parentGroup = new GroupRepresentation();
        parentGroup.setName(parentGroupName);
        String parentGroupId = CreatedResponseUtil.getCreatedId(consumer.groups().add(parentGroup));

        GroupRepresentation mappedGroup = consumer.groups().group(getMapperGroupId()).toRepresentation();
        consumer.groups().group(parentGroupId).subGroup(mappedGroup).close();

        String initialGroupPath = GroupMapperTest.buildGroupPath(parentGroupName, MAPPER_TEST_GROUP_NAME);

        String mapperId = setupScenarioWithGroupPath(initialGroupPath);

        String newParentGroupName = "new-parent-group";
        GroupRepresentation newParentGroup = new GroupRepresentation();
        newParentGroup.setName(newParentGroupName);
        String newParentGroupId = CreatedResponseUtil.getCreatedId(consumer.groups().add(newParentGroup));

        consumer.groups().group(newParentGroupId).subGroup(mappedGroup).close();

        String expectedNewGroupPath = GroupMapperTest.buildGroupPath(newParentGroupName, MAPPER_TEST_GROUP_NAME);

        assertMapperHasExpectedPathAndSucceeds(mapperId, expectedNewGroupPath);
    }

    @Test
    public void mapperStillWorksWhenSubGroupIsConvertedToTopLevelGroup() {
        RealmResource consumer = getConsumerRealm().admin();

        String parentGroupName = "parent-group";
        GroupRepresentation parentGroup = new GroupRepresentation();
        parentGroup.setName(parentGroupName);
        String parentGroupId = CreatedResponseUtil.getCreatedId(consumer.groups().add(parentGroup));

        String mapperGroupId = getMapperGroupId();
        GroupRepresentation mappedGroup = consumer.groups().group(mapperGroupId).toRepresentation();
        consumer.groups().group(parentGroupId).subGroup(mappedGroup).close();

        String initialGroupPath = GroupMapperTest.buildGroupPath(parentGroupName, MAPPER_TEST_GROUP_NAME);

        String mapperId = setupScenarioWithGroupPath(initialGroupPath);

        // convert the mapped group to a top-level group
        consumer.groups().add(consumer.groups().group(mapperGroupId).toRepresentation()).close();

        String expectedNewGroupPath = GroupMapperTest.buildGroupPath(MAPPER_TEST_GROUP_NAME);

        assertMapperHasExpectedPathAndSucceeds(mapperId, expectedNewGroupPath);
    }

    @Test
    public void mapperStillWorksWhenGroupIsRenamed() {
        String mapperId = setupScenarioWithGroupPath(MAPPER_TEST_GROUP_PATH);
        RealmResource consumer = getConsumerRealm().admin();

        String mapperGroupId = getMapperGroupId();
        String newGroupName = "new-name-" + MAPPER_TEST_GROUP_NAME;
        GroupRepresentation mappedGroup = consumer.groups().group(mapperGroupId).toRepresentation();
        mappedGroup.setName(newGroupName);
        consumer.groups().group(mapperGroupId).update(mappedGroup);

        String expectedNewGroupPath = GroupMapperTest.buildGroupPath(newGroupName);

        assertMapperHasExpectedPathAndSucceeds(mapperId, expectedNewGroupPath);
    }

    @Test
    public void mapperStillWorksWhenAncestorGroupIsRenamed() {
        RealmResource consumer = getConsumerRealm().admin();

        String topLevelGroupName = "top-level";
        GroupRepresentation topLevelGroup = new GroupRepresentation();
        topLevelGroup.setName(topLevelGroupName);
        String topLevelGroupId = CreatedResponseUtil.getCreatedId(consumer.groups().add(topLevelGroup));

        String midLevelGroupName = "mid-level";
        GroupRepresentation midLevelGroup = new GroupRepresentation();
        midLevelGroup.setName(midLevelGroupName);
        String midLevelGroupId = CreatedResponseUtil.getCreatedId(consumer.groups().add(midLevelGroup));

        midLevelGroup = consumer.groups().group(midLevelGroupId).toRepresentation();
        consumer.groups().group(topLevelGroupId).subGroup(midLevelGroup).close();

        GroupRepresentation mappedGroup = consumer.groups().group(getMapperGroupId()).toRepresentation();
        consumer.groups().group(midLevelGroupId).subGroup(mappedGroup).close();

        String initialGroupPath =
                GroupMapperTest.buildGroupPath(topLevelGroupName, midLevelGroupName, MAPPER_TEST_GROUP_NAME);

        String mapperId = setupScenarioWithGroupPath(initialGroupPath);

        String newTopLevelGroupName = "new-name-" + topLevelGroupName;
        topLevelGroup = consumer.groups().group(topLevelGroupId).toRepresentation();
        topLevelGroup.setName(newTopLevelGroupName);
        consumer.groups().group(topLevelGroupId).update(topLevelGroup);

        String expectedNewGroupPath =
                GroupMapperTest.buildGroupPath(newTopLevelGroupName, midLevelGroupName, MAPPER_TEST_GROUP_NAME);

        assertMapperHasExpectedPathAndSucceeds(mapperId, expectedNewGroupPath);
    }
}
