package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.tests.broker.KcOidcBrokerConfigSupport;
import org.keycloak.testsuite.util.AccountHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Shared username-template mapper tests for both OIDC and SAML. Stateless: the provider-side
 * attribute mutation applied between the two logins is performed directly against the admin API
 * (replacing the legacy overridable {@code updateUser()} hook). Mirrors the legacy
 * {@code AbstractUsernameTemplateMapperTest}.
 */
public interface UsernameTemplateMapperTest extends IdentityProviderMapperTest {

    String getMapperTemplate();

    void createMapperInIdp(IdentityProviderMapperSyncMode syncMode);

    @Test
    default void testUsernameGetsInsertedFromClaim() {
        loginAsUserTwiceWithMapper(IdentityProviderMapperSyncMode.IMPORT, "customusername", "newname", false);
    }

    @Test
    default void testUsernameGetsUpdatedFromClaimInForceMode() {
        loginAsUserTwiceWithMapper(IdentityProviderMapperSyncMode.FORCE, "customusername", "newname", true);
    }

    @Test
    default void testUsernameDoesNotGetUpdatedInLegacyMode() {
        loginAsUserTwiceWithMapper(IdentityProviderMapperSyncMode.LEGACY, "customusername", "newname", false);
    }

    default void loginAsUserTwiceWithMapper(IdentityProviderMapperSyncMode syncMode, String userName,
            String updatedUserName, boolean updatingUserName) {
        createMapperInIdp(syncMode);
        // The mapped attribute gets emitted as a claim/attribute by the provider config. Its value is
        // always a list, hence the single-element list around the value.
        createUserInProviderRealm(Map.of(KcOidcBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME, List.of(userName)));

        // The mapper sets the username, so first-broker-login must not overwrite it - the review
        // profile page keeps the prefilled username and only fills first/last name.
        loginAsUserFirstTime();

        String mappedUserName = String.format(getMapperTemplate(), userName);
        findUser(getConsumerRealm().admin(), mappedUserName, getUserEmail());

        AccountHelper.logout(getConsumerRealm().admin(), mappedUserName);
        AccountHelper.logout(getProviderRealm().admin(), getUserLogin());

        updateUser(updatedUserName);

        logInAsUserInIDP();
        String updatedMappedUserName = String.format(getMapperTemplate(), updatedUserName);
        UserRepresentation user = findUser(getConsumerRealm().admin(),
                updatingUserName ? updatedMappedUserName : mappedUserName, getUserEmail());
        if (updatingUserName) {
            Assertions.assertEquals(updatedMappedUserName, user.getUsername());
        } else {
            Assertions.assertEquals(mappedUserName, user.getUsername());
        }
    }

    default void updateUser(String updatedUserName) {
        RealmResource providerRealm = getProviderRealm().admin();
        UserRepresentation user = findUser(providerRealm, getUserLogin(), getUserEmail());
        user.setAttributes(Map.of(KcOidcBrokerConfigSupport.ATTRIBUTE_TO_MAP_NAME, List.of(updatedUserName)));
        providerRealm.users().get(user.getId()).update(user);
    }
}
