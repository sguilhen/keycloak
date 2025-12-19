package org.keycloak.models.cache.infinispan.stream;

import java.util.Map;
import java.util.function.Predicate;

import org.keycloak.marshalling.Marshalling;
import org.keycloak.models.cache.infinispan.entities.ClientScopeListQuery;
import org.keycloak.models.cache.infinispan.entities.Revisioned;

import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoTypeId;

@ProtoTypeId(Marshalling.HAS_CLIENT_SCOPE_PREDICATE)
public class HasClientScopePredicate implements Predicate<Map.Entry<String, Revisioned>> {
    private String clientScopeId;

    public static HasClientScopePredicate create() {
        return new HasClientScopePredicate();
    }

    public HasClientScopePredicate clientScope(String id) {
        this.clientScopeId = id;
        return this;
    }

    @ProtoField(1)
    String getClientScope() {
        return clientScopeId == null ? "" : clientScopeId;
    }

    void setClientScope(String id) {
        this.clientScopeId = id;
    }

    @Override
    public boolean test(Map.Entry<String, Revisioned> entry) {
        Object value = entry.getValue();
        if (value instanceof ClientScopeListQuery query) {
            return query.getClientId() != null && query.getClientScopes().contains(clientScopeId);
        }
        return false;
    }
}
