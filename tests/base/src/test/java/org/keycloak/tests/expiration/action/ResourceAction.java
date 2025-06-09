package org.keycloak.tests.expiration.action;

import java.util.Set;

import org.keycloak.models.KeycloakSession;
import org.keycloak.tests.expiration.selector.ResourceSelector;

public interface ResourceAction {

    String getActionId();

    void runAction(KeycloakSession session, Set<String> resourceIds);
}
