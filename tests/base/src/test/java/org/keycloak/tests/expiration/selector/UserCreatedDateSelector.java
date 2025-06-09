package org.keycloak.tests.expiration.selector;

import java.util.Objects;
import java.util.stream.Stream;

import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.tests.expiration.storage.SimpleUserStorage;

public class UserCreatedDateSelector implements ResourceSelector {

    SimpleUserStorage storage;

    String identityProviderId;

    public UserCreatedDateSelector(SimpleUserStorage storage) {
        this(storage, null);
    }

    public UserCreatedDateSelector(SimpleUserStorage storage, String identityProviderId) {
        this.storage = storage;
        this.identityProviderId = identityProviderId;
    }

    @Override
    public Stream<String> selectResources(KeycloakSession session, long expirationTime) {
        // naive impl -> real one would go to the DB and fetch only the user ids.
        return storage.getAllUsers().filter(user -> isApplicableToUser(session, user, expirationTime)).map(UserRepresentation::getId);
    }

    @Override
    public boolean isApplicableToUser(KeycloakSession session, UserRepresentation user, long expirationTime) {
        if (identityProviderId == null) {
            return expirationTime <= 0 || user.getCreatedTimestamp() < expirationTime;
        } else {
            return user.getFederatedIdentities() != null && user.getFederatedIdentities().stream()
                    .map(FederatedIdentityRepresentation::getIdentityProvider)
                    .anyMatch(identityProviderId::equals) && (expirationTime <= 0 || user.getCreatedTimestamp() < expirationTime);
        }
    }
}
