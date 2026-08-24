package org.keycloak.tests.broker.mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.broker.provider.HardcodedAttributeMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testsuite.util.AccountHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.keycloak.models.IdentityProviderMapperSyncMode.FORCE;
import static org.keycloak.models.IdentityProviderMapperSyncMode.IMPORT;

@KeycloakIntegrationTest
public class HardcodedUserAttributeMapperTest extends AbstractOidcMapperTest implements IdentityProviderMapperTest {

    private static final String USER_ATTRIBUTE = "user-attribute";
    private static final String USER_ATTRIBUTE_VALUE = "user-attribute";

    @Test
    public void addHardcodedAttributeOnFirstLogin() {
        setupIdentityProvider();
        createMapperInIdp(IMPORT);
        createUserInProviderRealm(Map.of());

        loginAsUserFirstTime();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        assertThatAttributeHasBeenAssigned(user);
    }

    @Test
    public void hardcodedAttributeGetsAddedEvenIfMapperIsAddedLaterInSyncModeForce() {
        UserRepresentation user = loginAsUserTwiceWithMapper(FORCE, true);

        assertThatAttributeHasBeenAssigned(user);
    }

    @Test
    public void hardcodedAttributeDoesNotGetAddedIfMapperIsAddedLaterInSyncModeImport() {
        UserRepresentation user = loginAsUserTwiceWithMapper(IMPORT, true);

        assertThatAttributeHasNotBeenAssigned(user);
    }

    @Test
    public void hardcodedAttributeDoesNotGetAddedAgainInSyncModeImport() {
        UserRepresentation user = loginAsUserTwiceWithMapper(IMPORT, false);

        assertThatAttributeHasNotBeenAssigned(user);
    }

    @Test
    public void hardcodedAttributeGetsUpdatedInSyncModeForce() {
        UserRepresentation user = loginAsUserTwiceWithMapper(FORCE, false);

        assertThatAttributeHasBeenAssigned(user);
    }

    protected UserRepresentation loginAsUserTwiceWithMapper(
            IdentityProviderMapperSyncMode syncMode, boolean createAfterFirstLogin) {
        setupIdentityProvider();
        if (!createAfterFirstLogin) {
            createMapperInIdp(syncMode);
        }
        createUserInProviderRealm(Map.of());

        loginAsUserFirstTime();

        UserRepresentation user = findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
        if (!createAfterFirstLogin) {
            assertThatAttributeHasBeenAssigned(user);
        } else {
            assertThatAttributeHasNotBeenAssigned(user);
        }

        if (createAfterFirstLogin) {
            createMapperInIdp(syncMode);
        }
        AccountHelper.logout(getConsumerRealm().admin(), getUserLogin());

        if (user.getAttributes() != null) {
            user.setAttributes(new HashMap<>());
        }
        getConsumerRealm().admin().users().get(user.getId()).update(user);

        logInAsUserInIDP();
        return findUser(getConsumerRealm().admin(), getUserLogin(), getUserEmail());
    }

    protected void createMapperInIdp(IdentityProviderMapperSyncMode syncMode) {
        IdentityProviderMapperRepresentation hardcodedAttributeMapper = new IdentityProviderMapperRepresentation();
        hardcodedAttributeMapper.setName("hardcoded-attribute-mapper");
        hardcodedAttributeMapper.setIdentityProviderMapper(HardcodedAttributeMapper.PROVIDER_ID);
        hardcodedAttributeMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                HardcodedAttributeMapper.ATTRIBUTE, USER_ATTRIBUTE,
                HardcodedAttributeMapper.ATTRIBUTE_VALUE, USER_ATTRIBUTE_VALUE));
        hardcodedAttributeMapper.setIdentityProviderAlias(getIdpAlias());
        getIdpResource().addMapper(hardcodedAttributeMapper).close();
    }

    protected void assertThatAttributeHasBeenAssigned(UserRepresentation user) {
        Assertions.assertEquals(List.of(USER_ATTRIBUTE_VALUE), user.getAttributes().get(USER_ATTRIBUTE));
    }

    protected void assertThatAttributeHasNotBeenAssigned(UserRepresentation user) {
        if (user.getAttributes() != null) {
            Assertions.assertNotEquals(List.of(USER_ATTRIBUTE_VALUE), user.getAttributes().get(USER_ATTRIBUTE));
        }
    }
}
