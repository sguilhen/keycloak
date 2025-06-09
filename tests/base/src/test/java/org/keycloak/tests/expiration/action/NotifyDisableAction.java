package org.keycloak.tests.expiration.action;

import java.util.Set;

import org.keycloak.models.KeycloakSession;
import org.keycloak.tests.expiration.storage.SimpleUserStorage;
import org.keycloak.tests.expiration.selector.ResourceSelector;

public class NotifyDisableAction implements ResourceAction {

    private final String actionId;

    private SimpleUserStorage storage;

    public NotifyDisableAction(String actionId, SimpleUserStorage storage) {
        this.actionId = actionId;
        this.storage = storage;
    }

    @Override
    public void runAction(KeycloakSession session, Set<String> resourceIds) {
        if (resourceIds.isEmpty()) {
            System.out.println("Action " + actionId + " not selected to run on any resource");
            return;
        }
        resourceIds.stream().map(this.storage::getUser).
                forEach(user -> System.out.println("Action " + actionId +  " notifying user " + user.getUsername() + " will be disabled"));
        System.out.println();

    }

    @Override
    public String getActionId() {
        return this.actionId;
    }
}
