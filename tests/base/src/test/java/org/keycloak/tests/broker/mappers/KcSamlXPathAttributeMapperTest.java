package org.keycloak.tests.broker.mappers;

import java.util.List;
import java.util.Map;

import org.keycloak.broker.saml.mappers.XPathAttributeMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.protocol.saml.SamlProtocol;
import org.keycloak.protocol.saml.mappers.AttributeStatementHelper;
import org.keycloak.protocol.saml.mappers.HardcodedAttributeMapper;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the {@link XPathAttributeMapper}: the provider injects an XML-valued SAML
 * attribute (via a {@link HardcodedAttributeMapper}) and the consumer extracts fields from it into
 * user attributes with XPath mappers. Mirrors the legacy {@code KcSamlXPathAttributeMapperTest};
 * that test drove the SAML flow with {@code SamlClientBuilder}, but since the extra attribute and
 * its XPath extraction are applied entirely server-side, the migration completes the same brokered
 * login through the browser and asserts on the resulting consumer user.
 */
@KeycloakIntegrationTest
public class KcSamlXPathAttributeMapperTest extends AbstractSamlMapperTest implements IdentityProviderMapperTest {

    @BeforeEach
    void setupXPathMappers() {
        createUserInProviderRealm(Map.of());

        // The provider SAML client id is the consumer realm entity id (see SamlProviderRealmConfig).
        String providerSamlClientId = "http://localhost:8080/realms/" + CONSUMER_REALM;

        ProtocolMapperRepresentation protocolMapper = new ProtocolMapperRepresentation();
        protocolMapper.setProtocol(SamlProtocol.LOGIN_PROTOCOL);
        protocolMapper.setName("Hardcoded XML");
        protocolMapper.setProtocolMapper(HardcodedAttributeMapper.PROVIDER_ID);
        protocolMapper.getConfig().put(HardcodedAttributeMapper.ATTRIBUTE_VALUE,
                "<firstName>Theo</firstName><lastName>Tester</lastName><email>test@example.org</email><xml-output>Some random text</xml-output>");
        protocolMapper.getConfig().put(AttributeStatementHelper.FRIENDLY_NAME, "xml-friendlyName");
        protocolMapper.getConfig().put(AttributeStatementHelper.SAML_ATTRIBUTE_NAME, "xml-name");
        protocolMapper.getConfig().put(AttributeStatementHelper.SAML_ATTRIBUTE_NAMEFORMAT, AttributeStatementHelper.BASIC);

        ClientRepresentation providerClient = getProviderRealm().admin().clients()
                .findByClientId(providerSamlClientId).get(0);
        getProviderRealm().admin().clients().get(providerClient.getId())
                .getProtocolMappers().createMapper(protocolMapper).close();

        addXpathMapper("firstName");
        addXpathMapper("lastName");
        addXpathMapper("email");
        addXpathMapper("xml-output");
    }

    private void addXpathMapper(String field) {
        IdentityProviderMapperRepresentation xpathMapper = new IdentityProviderMapperRepresentation();
        xpathMapper.setName("xpath-mapper-" + field);
        xpathMapper.setIdentityProviderMapper(XPathAttributeMapper.PROVIDER_ID);
        xpathMapper.setIdentityProviderAlias(getIdpAlias());
        xpathMapper.setConfig(Map.of(
                IdentityProviderMapperModel.SYNC_MODE, "INHERIT",
                XPathAttributeMapper.ATTRIBUTE_FRIENDLY_NAME, "xml-friendlyName",
                XPathAttributeMapper.ATTRIBUTE_XPATH, "//*[local-name()='" + field + "']",
                XPathAttributeMapper.USER_ATTRIBUTE, field));
        getIdpResource().addMapper(xpathMapper).close();
    }

    @Test
    public void testXPathAttributeMapper() {
        loginAsUserFirstTime();

        UserRepresentation user = getConsumerRealm().admin().users().search(getUserLogin()).get(0);
        Assertions.assertEquals("Theo", user.getFirstName());
        Assertions.assertEquals("Tester", user.getLastName());
        Assertions.assertEquals("test@example.org", user.getEmail());
        Assertions.assertEquals(List.of("Some random text"), user.getAttributes().get("xml-output"));
    }
}
