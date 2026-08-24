package org.keycloak.tests.broker.mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.IdentityProviderResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.tests.broker.BrokerConfigSupport;
import org.keycloak.testsuite.util.userprofile.UserProfileUtil;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base support for identity-provider mapper tests. Stateless: every helper either returns its
 * result or re-derives it from the admin API, so the concrete test classes hold no mutable
 * mapper/user state. Mirrors the legacy {@code AbstractIdentityProviderMapperTest}.
 */
public interface IdentityProviderMapperTest extends BrokerConfigSupport {

    /**
     * Mapper tests stamp arbitrary attributes on the provider user and expect them imported onto the
     * brokered consumer user, so both realms must accept unmanaged attributes. Mirrors the legacy
     * {@code AbstractBaseBrokerTest} which enabled them in its {@code @Before}.
     */
    @BeforeEach
    default void enableUnmanagedAttributes() {
        UserProfileUtil.enableUnmanagedAttributes(getProviderRealm().admin().users().userProfile());
        UserProfileUtil.enableUnmanagedAttributes(getConsumerRealm().admin().users().userProfile());
    }

    /**
     * Full first-time broker login: authenticate at the provider and complete the consumer's
     * review-profile page. The legacy suite called this {@code logInAsUserInIDPForFirstTime()}, but in
     * the new framework that name is already taken by the provider-side credential step (invoked
     * inside {@link #logInAsUserInIDP()}), so it is renamed here to avoid the clash.
     */
    default void loginAsUserFirstTime() {
        logInAsUserInIDP();
        updateAccountInformation();
    }

    /**
     * Like {@link #loginAsUserFirstTime()} but also asserts the resulting login response is a success
     * (mirrors the legacy {@code logInAsUserInIDPForFirstTimeAndAssertSuccess()}).
     */
    default void loginAsUserFirstTimeAndAssertSuccess() {
        loginAsUserFirstTime();
        Assertions.assertTrue(getOAuthClient().parseLoginResponse().isSuccess(),
                "Expected a successful login response after first broker login");
    }

    default IdentityProviderResource getIdpResource() {
        return getConsumerRealm().admin().identityProviders().get(getIdpAlias());
    }

    /**
     * Returns the identity provider already created in the consumer realm by the realm config.
     * Unlike the legacy suite (which created the IdP lazily per test), the new framework creates
     * it as part of realm setup, so this simply fetches the existing representation.
     */
    default IdentityProviderRepresentation setupIdentityProvider() {
        return getIdpResource().toRepresentation();
    }

    default IdentityProviderRepresentation setupIdentityProviderDisableUserInfo() {
        IdentityProviderResource idpResource = getIdpResource();
        IdentityProviderRepresentation idp = idpResource.toRepresentation();
        idp.getConfig().put("disableUserInfo", "true");
        idpResource.update(idp);
        return idp;
    }

    /**
     * Creates the test user in the provider realm with the given attributes, or - if the realm
     * config already pre-created it (the shared provider realm does, so the broker-login tests have
     * a user) - updates that user in place. Either way the provider realm ends up with exactly one
     * matching user, which {@link #findUser} relies on.
     */
    default String createUserInProviderRealm(Map<String, List<String>> attributes) {
        RealmResource providerRealm = getProviderRealm().admin();

        List<UserRepresentation> existing = providerRealm.users().search(getUserLogin(), true);
        UserRepresentation user = existing.isEmpty() ? new UserRepresentation() : existing.get(0);
        user.setUsername(getUserLogin());
        user.setEmail(getUserEmail());
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setAttributes(attributes);

        String userId;
        if (existing.isEmpty()) {
            try (Response response = providerRealm.users().create(user)) {
                userId = CreatedResponseUtil.getCreatedId(response);
            }
        } else {
            userId = user.getId();
            providerRealm.users().get(userId).update(user);
        }

        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(getUserPassword());
        password.setTemporary(false);
        providerRealm.users().get(userId).resetPassword(password);

        return userId;
    }

    default UserRepresentation findUser(RealmResource realm, String userName, String email) {
        UsersResource users = realm.users();

        List<UserRepresentation> found = users.list();
        Assertions.assertEquals(1, found.size(), "There must be exactly one user");
        UserRepresentation user = found.get(0);
        Assertions.assertEquals(userName, user.getUsername(), "Username has to match");
        Assertions.assertEquals(email, user.getEmail(), "Email has to match");

        MappingsRepresentation roles = users.get(user.getId()).roles().getAll();

        List<String> realmRoles = roles.getRealmMappings() == null ? List.of() :
                roles.getRealmMappings().stream()
                        .map(RoleRepresentation::getName)
                        .collect(Collectors.toList());
        user.setRealmRoles(realmRoles);

        Map<String, List<String>> clientRoles = new HashMap<>();
        if (roles.getClientMappings() != null) {
            roles.getClientMappings().forEach((key, value) -> clientRoles.put(key, value.getMappings().stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toList())));
        }
        user.setClientRoles(clientRoles);

        return user;
    }
}
