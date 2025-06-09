package org.keycloak.tests.expiration.action;

import java.util.Set;

import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.tests.expiration.selector.ResourceSelector;
import org.keycloak.tests.expiration.storage.SimpleUserStorage;

public class RemoveUserAction implements ResourceAction {

    private final String actionId;

    private final SimpleUserStorage storage;

    public RemoveUserAction(String actionId, SimpleUserStorage storage) {
        this.actionId = actionId;
        this.storage = storage;
    }

    @Override
    public void runAction(KeycloakSession session, Set<String> resourceIds) {
        if (resourceIds.isEmpty()) {
            System.out.println("Action " + actionId + " not selected to run on any resource");
            return;
        }
        resourceIds.forEach(id -> {
            UserRepresentation user = storage.removeUser(id);
            if (user != null) {
                System.out.println("Action " + actionId +  " removing user " + user.getUsername());
            }
        });
        System.out.println();
    }

    @Override
    public String getActionId() {
        return this.actionId;
    }
}
