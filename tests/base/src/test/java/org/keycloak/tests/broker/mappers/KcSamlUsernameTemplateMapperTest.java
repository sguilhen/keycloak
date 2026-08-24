package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.keycloak.broker.saml.mappers.UsernameTemplateMapper.PROVIDER_ID;

@KeycloakIntegrationTest
public class KcSamlUsernameTemplateMapperTest extends AbstractSamlMapperTest implements UsernameTemplateMapperTest {

    @Override
    public void createMapperInIdp(IdentityProviderMapperSyncMode syncMode) {
        IdentityProviderMapperRepresentation usernameTemplateMapper = new IdentityProviderMapperRepresentation();
        usernameTemplateMapper.setName("saml-username-template-mapper");
        usernameTemplateMapper.setIdentityProviderMapper(PROVIDER_ID);
        usernameTemplateMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, syncMode.toString(),
                "template", "${ALIAS}-${ATTRIBUTE.user-attribute}"));
        usernameTemplateMapper.setIdentityProviderAlias(getIdpAlias());
        getIdpResource().addMapper(usernameTemplateMapper).close();
    }

    @Override
    public String getMapperTemplate() {
        return "kc-saml-idp-%s";
    }

    @Test
    public void testLoginWithMissingMappedAttributeShouldFail() {
        IdentityProviderMapperRepresentation usernameMapper = new IdentityProviderMapperRepresentation();
        usernameMapper.setName("missing-attribute-mapper");
        usernameMapper.setIdentityProviderMapper(PROVIDER_ID);
        usernameMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, IdentityProviderMapperSyncMode.IMPORT.toString(),
                "template", "${ATTRIBUTE.non-existent-attribute}",
                "target", "LOCAL"));
        usernameMapper.setIdentityProviderAlias(getIdpAlias());
        getIdpResource().addMapper(usernameMapper).close();

        createUserInProviderRealm(Map.of());

        logInAsUserInIDP();

        getUpdateProfilePage().assertCurrent();
        // try to update the account info with only the first and last name (no username provided here)
        getUpdateProfilePage().update("John", "Doe");

        // we should still be on the update profile page, with an error asking to provide the username
        getUpdateProfilePage().assertCurrent();
        Assertions.assertTrue(getWebDriver().driver().getPageSource().contains("Please specify username"),
                "Should show error about missing username");

        // no user should be present in the realm with an empty or null username
        List<UserRepresentation> users = getConsumerRealm().admin().users().list();
        for (UserRepresentation user : users) {
            Assertions.assertNotNull(user.getUsername(), "Username should not be null");
            Assertions.assertFalse(user.getUsername().trim().isEmpty(), "Username should not be empty");
        }
    }

    @Test
    public void testLoginWithPartiallyMissingAttributeInTemplate() {
        IdentityProviderMapperRepresentation usernameMapper = new IdentityProviderMapperRepresentation();
        usernameMapper.setName("partial-attribute-mapper");
        usernameMapper.setIdentityProviderMapper(PROVIDER_ID);
        usernameMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, IdentityProviderMapperSyncMode.IMPORT.toString(),
                "template", "${ALIAS}-${ATTRIBUTE.custom-attr}",
                "target", "LOCAL"));
        usernameMapper.setIdentityProviderAlias(getIdpAlias());
        getIdpResource().addMapper(usernameMapper).close();

        createUserInProviderRealm(Map.of());

        logInAsUserInIDP();

        getUpdateProfilePage().assertCurrent();
        // try to update with only first and last name (no username) - should fail
        getUpdateProfilePage().update("John", "Doe");
        getUpdateProfilePage().assertCurrent();
        Assertions.assertTrue(getWebDriver().driver().getPageSource().contains("Please specify username"),
                "Should show error about missing username");

        // now provide a username and verify the user is created
        getUpdateProfilePage().prepareUpdate()
                .username("valid-username")
                .email("user@example.com")
                .firstName("John")
                .lastName("Doe")
                .submit();
        Assertions.assertTrue(getOAuthClient().parseLoginResponse().isSuccess());

        UserRepresentation user = getConsumerRealm().admin().users().search("valid-username").get(0);
        Assertions.assertNotNull(user);
        Assertions.assertEquals("valid-username", user.getUsername());
    }
}
