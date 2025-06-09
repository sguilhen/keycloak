package org.keycloak.tests.expiration.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.tests.expiration.policy.AssignedPolicy;

public class SimpleUserStorage {

    private final Map<String, UserRepresentation> users = new HashMap<>();

    private final Map<String, AssignedPolicy> assignedPolicies = new HashMap<>();

    public void addUser(UserRepresentation user) {
        this.users.put(user.getId(), user);
    }

    public UserRepresentation removeUser(String id) {
        UserRepresentation rep = this.users.remove(id);
        this.assignedPolicies.remove(id);
        return rep;
    }

    public UserRepresentation getUser(String id) {
        return users.get(id);
    }

    public Stream<UserRepresentation> getAllUsers() {
        return users.values().stream();
    }

    public void addAssignedPolicy(String userId, AssignedPolicy policy) {
        this.assignedPolicies.put(userId, policy);
    }

    public AssignedPolicy getAssignedPolicy(String userId) {
        return assignedPolicies.get(userId);
    }
}
