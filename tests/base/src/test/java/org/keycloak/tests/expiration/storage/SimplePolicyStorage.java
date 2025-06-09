package org.keycloak.tests.expiration.storage;

import java.util.Set;
import java.util.TreeSet;

import org.keycloak.tests.expiration.policy.ExpirationPolicy;

public class SimplePolicyStorage {

    private final Set<ExpirationPolicy> policies = new TreeSet<>();

    public void addPolicy(ExpirationPolicy policy) {
        this.policies.add(policy);
    }

    public Set<ExpirationPolicy> getPolicies() {
        return policies;
    }
}
