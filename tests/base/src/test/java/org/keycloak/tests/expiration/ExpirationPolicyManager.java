package org.keycloak.tests.expiration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.hibernate.Remove;
import org.keycloak.common.util.Time;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.tests.expiration.action.DisableUserAction;
import org.keycloak.tests.expiration.action.NotifyDisableAction;
import org.keycloak.tests.expiration.action.RemoveUserAction;
import org.keycloak.tests.expiration.policy.AssignedPolicy;
import org.keycloak.tests.expiration.policy.ExpirationPolicy;
import org.keycloak.tests.expiration.selector.UserCreatedDateSelector;
import org.keycloak.tests.expiration.storage.SimplePolicyStorage;
import org.keycloak.tests.expiration.storage.SimpleUserStorage;

public class ExpirationPolicyManager {

    private final SimplePolicyStorage policyStorage;
    private final SimpleUserStorage userStorage;

    // default policy that has lower priority than other policies and has no actions to run
    private final ExpirationPolicy DEFAULT_POLICY = new ExpirationPolicy("DEFAULT", -1, null, null);

    public ExpirationPolicyManager(SimplePolicyStorage storage, SimpleUserStorage userStorage) {
        this.policyStorage = storage;
        this.userStorage = userStorage;
    }

    public void initializePolicies(KeycloakSession session) {
        userStorage.getAllUsers().forEach(user -> userStorage.addAssignedPolicy(user.getId(), new AssignedPolicy(DEFAULT_POLICY)));
    }

    public void assignConfiguredPolicies(KeycloakSession session) {
        policyStorage.getPolicies().forEach(policy -> policy.assignPolicyToUsers(session));
    }

    public void assignPolicyToUser(KeycloakSession session, UserRepresentation user) {
        for(ExpirationPolicy policy : policyStorage.getPolicies()) {
            if (policy.assignPolicyToUser(session, user))
                break;
        }
    }

    public void runConfiguredPolicies(KeycloakSession session) {
        policyStorage.getPolicies().forEach(policy -> policy.runConfiguredActions(session));
    }

    public static void main(String[] args) {

        // setup a simple user storage with 1000 users, some having federated identities.
        SimpleUserStorage userStorage = new SimpleUserStorage();
        long currentTime = System.currentTimeMillis();
        long sixMinCreated = 6 * 60 * 1000;
        IntStream.range(0, 100).forEach(i -> {
            UserRepresentation userRepresentation = new UserRepresentation();
            userRepresentation.setId("user-" + i);
            userRepresentation.setUsername("user-" + i);
            userRepresentation.setEmail("user-" + i + "@keycloak.org");
            userRepresentation.setFirstName("First" + i);
            userRepresentation.setLastName("Last" + i);
            userRepresentation.setCreatedTimestamp(i % 10 != 0 ? currentTime : currentTime - sixMinCreated);
            userStorage.addUser(userRepresentation);
        });

        // make first 20 users have federated identity with broker A, with the last 5 belonging also to broker B.
        IntStream.range(0, 15).forEach(i -> {
            UserRepresentation user = userStorage.getUser("user-" + i);
            user.setFederatedIdentities(createFederatedIdentities(user, "broker-a"));
        });
        IntStream.range(15, 20).forEach(i -> {
            UserRepresentation user = userStorage.getUser("user-" + i);
            user.setFederatedIdentities(createFederatedIdentities(user, "broker-a", "broker-b"));
        });

        // make next 10 users belong to broker B only.
        IntStream.range(20, 30).forEach(i -> {
            UserRepresentation user = userStorage.getUser("user-" + i);
            user.setFederatedIdentities(createFederatedIdentities(user, "broker-b"));
        });


        SimplePolicyStorage policyStorage = new SimplePolicyStorage();

        // create a few expiration policies - one for broker A users, one for broker B users, and one for the rest (realm users).
        ExpirationPolicy brokerAPolicy = new ExpirationPolicy("broker-a-policy", 100, new UserCreatedDateSelector(userStorage, "broker-a"), userStorage);
        brokerAPolicy.addResourceAction(new NotifyDisableAction("broker-a-notify-disable", userStorage), 5 * 60 * 1000); // notify expiration after 5 minutes
        brokerAPolicy.addResourceAction(new DisableUserAction("broker-a-disable", userStorage), 10 * 60 * 1000); // disable after 10 minutes
        policyStorage.addPolicy(brokerAPolicy);

        ExpirationPolicy brokerBPolicy = new ExpirationPolicy("broker-b-policy", 80, new UserCreatedDateSelector(userStorage, "broker-b"), userStorage);
        brokerBPolicy.addResourceAction(new DisableUserAction("broker-b-disable", userStorage), 7 * 60 * 1000); // disable after 7 minutes
        policyStorage.addPolicy(brokerBPolicy);

        ExpirationPolicy realmPolicy = new ExpirationPolicy("realm-policy", 50, new UserCreatedDateSelector(userStorage, null), userStorage);
        realmPolicy.addResourceAction(new NotifyDisableAction("realm-notify-disable", userStorage), 15 * 60 * 1000); // notify disable after 15 minutes
        realmPolicy.addResourceAction(new DisableUserAction("realm-disable", userStorage), 30 * 60 * 1000); // disable after 30 minutes
        realmPolicy.addResourceAction(new RemoveUserAction("realm-remove", userStorage), 60 * 60 * 1000); // remove after 1 hour
        policyStorage.addPolicy(realmPolicy);

        // create an expiration policy manager and setup the initial policies.
        ExpirationPolicyManager policyManager = new ExpirationPolicyManager(policyStorage, userStorage);
        policyManager.initializePolicies(null); // step 1 - assign the default policies to all users to initialize the system.
        policyManager.assignConfiguredPolicies(null); // step 2 - assign the policies that are to be applied.

        // run the actions once - some broker A users should be notified and some broker B users should be disabled.
        policyManager.runConfiguredPolicies(null);
        System.out.println("\n================================================================\n");

        // run policies again after 5 minutes
        Time.setOffset(300);
        policyManager.runConfiguredPolicies(null);
        System.out.println("\n================================================================\n");

        // run policies again after 10 minutes
        Time.setOffset(600);
        policyManager.runConfiguredPolicies(null);
        System.out.println("\n================================================================\n");

        // run policies again after 25 minutes
        Time.setOffset(1500);
        policyManager.runConfiguredPolicies(null);
        System.out.println("\n================================================================\n");

        // run policies again after 55 minutes
        Time.setOffset(3300);
        policyManager.runConfiguredPolicies(null);
        System.out.println("\n================================================================\n");

    }

    public static List<FederatedIdentityRepresentation> createFederatedIdentities(UserRepresentation user, String... brokers) {
        List<FederatedIdentityRepresentation> fedIdentities = new ArrayList<>();
        for (String broker : brokers) {
            FederatedIdentityRepresentation fedIdentity = new FederatedIdentityRepresentation();
            fedIdentity.setIdentityProvider(broker);
            fedIdentity.setUserId(user.getId());
            fedIdentity.setUserName(user.getUsername());
            fedIdentities.add(fedIdentity);
        }
        return fedIdentities;
    }
}
