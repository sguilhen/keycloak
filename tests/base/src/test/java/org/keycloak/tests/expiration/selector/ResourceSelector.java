package org.keycloak.tests.expiration.selector;

import java.util.stream.Stream;

import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.UserRepresentation;

public interface ResourceSelector {

    Stream<String> selectResources(KeycloakSession session, long expirationTime);

    boolean isApplicableToUser(KeycloakSession session, UserRepresentation user, long expirationTime);
}
