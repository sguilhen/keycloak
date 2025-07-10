package org.keycloak.models.policy;

import java.util.List;
import java.util.function.Function;

import org.keycloak.component.ComponentFactory;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderFactory;

public class ResourcePolicyManager {

    private KeycloakSession session;

    public ResourcePolicyManager(KeycloakSession session) {
        this.session = session;
    }

    public ResourcePolicy addPolicy(String providerId) {
        return addPolicy(new ResourcePolicy(providerId));
    }

    public ResourcePolicy addPolicy(ResourcePolicy policy) {
        RealmModel realm = getRealm();
        ComponentModel model = new ComponentModel();

        model.setParentId(realm.getId());
        model.setProviderId(policy.getProviderId());
        model.setProviderType(ResourcePolicyProvider.class.getName());

        return new ResourcePolicy(realm.addComponentModel(model));
    }

    public ResourceAction addAction(ResourcePolicy policy, ResourceAction action) {
        RealmModel realm = getRealm();
        ComponentModel policyModel = realm.getComponent(policy.getId());
        ComponentModel actionModel = new ComponentModel();

        actionModel.setParentId(policyModel.getId());
        actionModel.setProviderId(action.getProviderId());
        actionModel.setProviderType(ResourceActionProvider.class.getName());
        actionModel.setConfig(action.getConfig());

        return new ResourceAction(realm.addComponentModel(actionModel));
    }

    public List<ResourcePolicy> getPolicies() {
        RealmModel realm = getRealm();
        return realm.getComponentsStream(realm.getId(), ResourcePolicyProvider.class.getName())
                .map(ResourcePolicy::new).toList();
    }

    public List<ResourceAction> getActions(ResourcePolicy policy) {
        RealmModel realm = getRealm();
        return realm.getComponentsStream(policy.getId(), ResourceActionProvider.class.getName())
                .map(ResourceAction::new).toList();
    }

    public void runPolicies() {
        List<ResourcePolicy> policies = getPolicies();

        for (ResourcePolicy policy : policies) {
            runPolicy(policy);
        }
    }

    private void runPolicy(ResourcePolicy policy) {
        ComponentFactory<?, ?> factory = (ComponentFactory<?, ?>) session.getKeycloakSessionFactory()
                .getProviderFactory(ResourcePolicyProvider.class, policy.getProviderId());
        ResourcePolicyProvider policyProvider = (ResourcePolicyProvider) factory.create(session, getRealm().getComponent(policy.getId()));

        for (ResourceAction action : getActions(policy)) {
            ComponentFactory<?, ?> actionFactory = (ComponentFactory<?, ?>) session.getKeycloakSessionFactory()
                    .getProviderFactory(ResourceActionProvider.class, action.getProviderId());
            ResourceActionProvider actionProvider = (ResourceActionProvider) actionFactory.create(session, getRealm().getComponent(action.getId()));

            actionProvider.run(policyProvider::getResources);
        }
    }

    private RealmModel getRealm() {
        return session.getContext().getRealm();
    }
}
