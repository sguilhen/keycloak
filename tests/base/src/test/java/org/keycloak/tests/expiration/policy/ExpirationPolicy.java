package org.keycloak.tests.expiration.policy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.keycloak.common.util.Time;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.tests.expiration.action.ResourceAction;
import org.keycloak.tests.expiration.selector.ResourceSelector;
import org.keycloak.tests.expiration.storage.SimpleUserStorage;

public class ExpirationPolicy implements Comparable<ExpirationPolicy> {

    private final int priority;
    private final String policyId;
    private final ResourceSelector resourceSelector;

    private final Map<Long, ResourceAction> resourceActions = new TreeMap<>(Comparator.naturalOrder());
    private final SimpleUserStorage userStorage;

    public ExpirationPolicy(String id, int priority, ResourceSelector selector, SimpleUserStorage userStorage) {
        this.policyId = id;
        this.priority = priority;
        this.resourceSelector = selector;
        this.userStorage = userStorage;
    }

    @Override
    public int compareTo(ExpirationPolicy o) {
        return o.priority - this.priority;
    }

    public void runConfiguredActions(KeycloakSession session) {
        List<String> actionsList = resourceActions.values().stream().map(ResourceAction::getActionId).toList();
        AtomicInteger actionIndex = new AtomicInteger(0);
        resourceActions.forEach((key, value) -> {
            Set<String> resourceIds = resourceSelector.selectResources(session, Time.currentTimeMillis() - key)
                            .filter(id -> userStorage.getAssignedPolicy(id).matches(this.policyId, value.getActionId()))
                            .collect(Collectors.toSet());
            value.runAction(session, resourceIds);

            // update the assigned policies to the next action.
            String nextActionId = actionIndex.addAndGet(1) < actionsList.size() ? actionsList.get(actionIndex.get()) : "NO_ACTION";
            resourceIds.forEach(id -> {
                if (userStorage.getAssignedPolicy(id) != null)
                    userStorage.getAssignedPolicy(id).setNextAction(nextActionId);
            });
        });
    }

    public void assignPolicyToUsers(KeycloakSession session) {
        resourceSelector.selectResources(session, -1)
                .filter(id -> userStorage.getAssignedPolicy(id).getPolicyPriority() < this.priority)
                .forEach(id -> userStorage.addAssignedPolicy(id, new AssignedPolicy(this)));
    }

    public boolean assignPolicyToUser(KeycloakSession session, UserRepresentation user) {
        boolean isApplicable = resourceSelector.isApplicableToUser(session, user, -1);
        if (isApplicable) {
            userStorage.addAssignedPolicy(user.getId(), new AssignedPolicy(this));
        }
        return isApplicable;
    }

    public void addResourceAction(ResourceAction action, long expiration) {
        resourceActions.put(expiration, action);
    }

    public String getPolicyId() {
        return policyId;
    }

    public int getPolicyPriority() {
        return priority;
    }

    public String getFirstActionId() {
        return resourceActions.values().stream().map(ResourceAction::getActionId).findFirst().orElse("NO_ACTION");
    }
}
