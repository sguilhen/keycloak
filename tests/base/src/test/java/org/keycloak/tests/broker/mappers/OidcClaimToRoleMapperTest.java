package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.broker.oidc.mappers.ClaimToRoleMapper;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.tests.broker.KcOidcBrokerConfigSupport;

import org.junit.jupiter.api.Test;

import static org.keycloak.models.IdentityProviderMapperSyncMode.FORCE;
import static org.keycloak.models.IdentityProviderMapperSyncMode.LEGACY;

@KeycloakIntegrationTest
public class OidcClaimToRoleMapperTest extends AbstractOidcMapperTest implements RoleMapperTest {

    protected static final String CLAIM = KcOidcBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME;
    protected static final String CLAIM_VALUE = "value 1";

    @Test
    public void allClaimValuesMatch() {
        createClaimToRoleMapper(CLAIM_VALUE);
        createUserInProviderRealm(createUserConfig());

        loginAsUserFirstTime();

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    public void claimValuesMismatch() {
        createClaimToRoleMapper("other value");
        createUserInProviderRealm(createUserConfig());

        loginAsUserFirstTime();

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserMismatchDeletesRoleInForceMode() {
        loginWithClaimThenChangeClaimToValue("value mismatch", FORCE, false);

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserMismatchDeletesRoleInLegacyMode() {
        createMapperThenLoginWithStandardClaimThenChangeClaimToValue("value mismatch", LEGACY);

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserNewMatchGrantsRoleAfterFirstLoginInForceMode() {
        loginWithStandardClaimThenAddMapperAndLoginAgain(FORCE);

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserNewMatchDoesNotGrantRoleAfterFirstLoginInLegacyMode() {
        loginWithStandardClaimThenAddMapperAndLoginAgain(LEGACY);

        assertThatRoleHasNotBeenAssignedInConsumerRealm();
    }

    @Test
    public void updateBrokeredUserDoesNotDeleteRoleIfClaimStillMatches() {
        createMapperThenLoginWithStandardClaimThenChangeClaimToValue(CLAIM_VALUE, FORCE);

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

    private void loginWithStandardClaimThenAddMapperAndLoginAgain(IdentityProviderMapperSyncMode syncMode) {
        loginWithClaimThenChangeClaimToValue(CLAIM_VALUE, syncMode, true);
    }

    private void createMapperThenLoginWithStandardClaimThenChangeClaimToValue(String claimOnSecondLogin,
            IdentityProviderMapperSyncMode syncMode) {
        loginWithClaimThenChangeClaimToValue(claimOnSecondLogin, syncMode, false);
    }

    private void loginWithClaimThenChangeClaimToValue(String claimOnSecondLogin,
            IdentityProviderMapperSyncMode syncMode, boolean createAfterFirstLogin) {
        loginAsUserTwiceWithMapper(syncMode, createAfterFirstLogin, createUserConfig(),
                () -> updateClaimUser(claimOnSecondLogin));
    }

    private void createClaimToRoleMapper(String claimValue) {
        createClaimToRoleMapper(claimValue, IdentityProviderMapperSyncMode.IMPORT, CLIENT_ROLE_MAPPER_REPRESENTATION);
    }

    @Override
    public void createMapperInIdp(IdentityProviderMapperSyncMode syncMode, String roleValue) {
        createClaimToRoleMapper(CLAIM_VALUE, syncMode, roleValue);
    }

    private void updateClaimUser(String claimOnSecondLogin) {
        RealmResource providerRealm = getProviderRealm().admin();
        UserRepresentation user = findUser(providerRealm, getUserLogin(), getUserEmail());
        user.setAttributes(Map.of(CLAIM, List.of(claimOnSecondLogin)));
        providerRealm.users().get(user.getId()).update(user);
    }

    protected void createClaimToRoleMapper(String claimValue, IdentityProviderMapperSyncMode syncMode,
            String roleValue) {
        IdentityProviderMapperRepresentation claimToRoleMapper = new IdentityProviderMapperRepresentation();
        claimToRoleMapper.setName("claim-to-role-mapper");
        claimToRoleMapper.setIdentityProviderMapper(ClaimToRoleMapper.PROVIDER_ID);
        claimToRoleMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                ClaimToRoleMapper.CLAIM, CLAIM,
                ClaimToRoleMapper.CLAIM_VALUE, claimValue,
                ConfigConstants.ROLE, roleValue));

        persistMapper(claimToRoleMapper);
    }

    @Override
    public Map<String, List<String>> createUserConfigForRole(String roleValue) {
        return createUserConfig();
    }

    protected static Map<String, List<String>> createUserConfig() {
        return Map.of(CLAIM, List.of(CLAIM_VALUE));
    }
}
