package org.keycloak.tests.expiration.policy;

public class AssignedPolicy {

    private String policyId;
    private int policyPriority;
    private String nextAction;

    public AssignedPolicy(ExpirationPolicy policy) {
        this.policyId = policy.getPolicyId();
        this.policyPriority = policy.getPolicyPriority();
        this.nextAction = policy.getFirstActionId();
    }

    public int getPolicyPriority() {
        return this.policyPriority;
    }

    public boolean matches(String policyId, String actionId) {
        return this.policyId.equals(policyId) && this.nextAction.equals(actionId);
    }

    public void setNextAction(String nextActionId) {
        this.nextAction = nextActionId;
    }
}
