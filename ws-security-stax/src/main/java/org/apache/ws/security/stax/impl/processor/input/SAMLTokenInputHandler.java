/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ws.security.stax.impl.processor.input;

import org.apache.commons.codec.binary.Base64;
import org.apache.ws.security.binding.wss10.ObjectFactory;
import org.apache.ws.security.binding.wss10.SecurityTokenReferenceType;
import org.apache.ws.security.common.ext.WSSecurityException;
import org.apache.ws.security.common.saml.OpenSAMLUtil;
import org.apache.ws.security.common.saml.SAMLUtil;
import org.apache.ws.security.common.saml.SamlAssertionWrapper;
import org.apache.ws.security.stax.ext.WSSConstants;
import org.apache.ws.security.stax.ext.WSSSecurityProperties;
import org.apache.ws.security.stax.ext.WSSUtils;
import org.apache.ws.security.stax.ext.WSSecurityContext;
import org.apache.ws.security.stax.securityEvent.SamlTokenSecurityEvent;
import org.apache.ws.security.stax.validate.SamlTokenValidator;
import org.apache.ws.security.stax.validate.SamlTokenValidatorImpl;
import org.apache.ws.security.stax.validate.TokenContext;
import org.apache.xml.security.binding.xmldsig.KeyInfoType;
import org.apache.xml.security.binding.xmldsig.KeyValueType;
import org.apache.xml.security.binding.xmldsig.X509DataType;
import org.apache.xml.security.binding.xmlenc.EncryptedKeyType;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.stax.config.JCEAlgorithmMapper;
import org.apache.xml.security.stax.ext.*;
import org.apache.xml.security.stax.ext.stax.XMLSecAttribute;
import org.apache.xml.security.stax.ext.stax.XMLSecEvent;
import org.apache.xml.security.stax.ext.stax.XMLSecNamespace;
import org.apache.xml.security.stax.ext.stax.XMLSecStartElement;
import org.apache.xml.security.stax.impl.XMLSecurityEventReader;
import org.apache.xml.security.stax.impl.securityToken.AbstractInboundSecurityToken;
import org.apache.xml.security.stax.impl.securityToken.SecurityTokenFactory;
import org.apache.xml.security.stax.securityEvent.SecurityEvent;
import org.apache.xml.security.stax.securityEvent.SecurityEventConstants;
import org.apache.xml.security.stax.securityEvent.SecurityEventListener;
import org.apache.xml.security.stax.securityEvent.SignedElementSecurityEvent;
import org.opensaml.security.SAMLSignatureProfileValidator;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.validation.ValidationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Comment;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.ProcessingInstruction;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Processor for the SAML Assertion XML Structure
 *
 * @author $Author$
 * @version $Revision$ $Date$
 */
public class SAMLTokenInputHandler extends AbstractInputSecurityHeaderHandler {

    private static final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

    private static final List<QName> saml1TokenPath = new ArrayList<QName>(WSSConstants.WSSE_SECURITY_HEADER_PATH);
    private static final List<QName> saml2TokenPath = new ArrayList<QName>(WSSConstants.WSSE_SECURITY_HEADER_PATH);

    static {
        documentBuilderFactory.setNamespaceAware(true);
        saml1TokenPath.add(WSSConstants.TAG_saml_Assertion);
        saml2TokenPath.add(WSSConstants.TAG_saml2_Assertion);
    }

    @Override
    public void handle(final InputProcessorChain inputProcessorChain, final XMLSecurityProperties securityProperties,
                       Deque<XMLSecEvent> eventQueue, Integer index) throws XMLSecurityException {

        final Document samlTokenDocument = (Document) parseStructure(eventQueue, index, securityProperties);

        final WSSSecurityProperties wssSecurityProperties = (WSSSecurityProperties) securityProperties;
        final WSSecurityContext wsSecurityContext = (WSSecurityContext) inputProcessorChain.getSecurityContext();
        final Element samlElement = samlTokenDocument.getDocumentElement();
        final SamlAssertionWrapper samlAssertionWrapper = new SamlAssertionWrapper(samlElement);

        SamlTokenValidator samlTokenValidator = wssSecurityProperties.getValidator(new QName(samlElement.getNamespaceURI(), samlElement.getLocalName()));
        if (samlTokenValidator == null) {
            samlTokenValidator = new SamlTokenValidatorImpl();
        }

        //important: check the signature before we do other processing...
        if (samlAssertionWrapper.isSigned()) {
            Signature signature = samlAssertionWrapper.getSignature();
            if (signature == null) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN,
                        "empty", "no signature to validate");
            }
            SAMLSignatureProfileValidator validator = new SAMLSignatureProfileValidator();
            try {
                validator.validate(signature);
            } catch (ValidationException ex) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE,
                        "empty", ex, "SAML signature validation failed");
            }

            int sigKeyInfoIdx = getSignatureKeyInfoIndex(eventQueue);
            if (sigKeyInfoIdx < 0) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN, "noKeyInSAMLToken");
            }
            SecurityToken sigSecurityToken = parseKeyInfo(inputProcessorChain, securityProperties, eventQueue, sigKeyInfoIdx);

            if (sigSecurityToken == null) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN, "noKeyInSAMLToken");
            }

            samlTokenValidator.validate(sigSecurityToken, wssSecurityProperties);

            BasicX509Credential credential = new BasicX509Credential();
            if (sigSecurityToken.getX509Certificates() != null) {
                credential.setEntityCertificate(sigSecurityToken.getX509Certificates()[0]);
            } else if (sigSecurityToken.getPublicKey() != null) {
                credential.setPublicKey(sigSecurityToken.getPublicKey());
            } else {
                throw new WSSecurityException(
                        WSSecurityException.ErrorCode.FAILURE, "invalidSAMLsecurity",
                        "cannot get certificate or key"
                );
            }
            SignatureValidator sigValidator = new SignatureValidator(credential);
            try {
                sigValidator.validate(signature);
            } catch (ValidationException ex) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE,
                        "empty", ex, "SAML signature validation failed");
            }
        }

        String confirmMethod = null;
        List<String> methods = samlAssertionWrapper.getConfirmationMethods();
        if (methods != null && methods.size() > 0) {
            confirmMethod = methods.get(0);
        }

        final SecurityToken subjectSecurityToken;

        if (OpenSAMLUtil.isMethodHolderOfKey(confirmMethod)) {

            // First try to get the credential from a CallbackHandler
            final byte[] subjectSecretKey = SAMLUtil.getSecretKeyFromCallbackHandler(
                    samlAssertionWrapper.getId(), wssSecurityProperties.getCallbackHandler());

            if (subjectSecretKey != null && subjectSecretKey.length > 0) {

                subjectSecurityToken = new AbstractInboundSecurityToken(
                        wsSecurityContext, "",
                        XMLSecurityConstants.XMLKeyIdentifierType.NO_KEY_INFO) {
                    @Override
                    public XMLSecurityConstants.TokenType getTokenType() {
                        return XMLSecurityConstants.DefaultToken;
                    }

                    @Override
                    public boolean isAsymmetric() throws XMLSecurityException {
                        return false;
                    }

                    @Override
                    protected Key getKey(String algorithmURI, XMLSecurityConstants.KeyUsage
                            keyUsage, String correlationID) throws XMLSecurityException {

                        Key key = super.getKey(algorithmURI, keyUsage, correlationID);
                        if (key == null) {
                            String algoFamily = JCEAlgorithmMapper.getJCERequiredKeyFromURI(algorithmURI);
                            key = new SecretKeySpec(subjectSecretKey, algoFamily);
                            setSecretKey(algorithmURI, key);
                        }
                        return key;
                    }
                };
            } else {
                // The assertion must have been signed for HOK
                if (!samlAssertionWrapper.isSigned()) {
                    throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN, "invalidSAMLsecurity");
                }

                int subjectKeyInfoIndex = getSubjectKeyInfoIndex(eventQueue);
                if (subjectKeyInfoIndex < 0) {
                    throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN, "noKeyInSAMLToken");
                }

                subjectSecurityToken = parseKeyInfo(inputProcessorChain, securityProperties, eventQueue, subjectKeyInfoIndex);
                if (subjectSecurityToken == null) {
                    throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN, "noKeyInSAMLToken");
                }
            }
        } else {
            subjectSecurityToken = null;
        }

        final List<XMLSecEvent> xmlSecEvents = getResponsibleXMLSecEvents(eventQueue, index);
        final List<QName> elementPath = getElementPath(eventQueue);
        final TokenContext tokenContext = new TokenContext(wssSecurityProperties, wsSecurityContext, xmlSecEvents, elementPath);

        final SecurityToken securityToken = samlTokenValidator.validate(
                samlAssertionWrapper, subjectSecurityToken, tokenContext);

        SecurityTokenProvider subjectSecurityTokenProvider = new SecurityTokenProvider() {

            @SuppressWarnings("unchecked")
            @Override
            public SecurityToken getSecurityToken() throws XMLSecurityException {
                return securityToken;
            }

            @Override
            public String getId() {
                return samlAssertionWrapper.getId();
            }
        };

        wsSecurityContext.registerSecurityTokenProvider(samlAssertionWrapper.getId(), subjectSecurityTokenProvider);

        //fire a tokenSecurityEvent
        SamlTokenSecurityEvent samlTokenSecurityEvent = new SamlTokenSecurityEvent();
        samlTokenSecurityEvent.setSecurityToken((SecurityToken) subjectSecurityTokenProvider.getSecurityToken());
        samlTokenSecurityEvent.setCorrelationID(samlAssertionWrapper.getId());
        wsSecurityContext.registerSecurityEvent(samlTokenSecurityEvent);

        SAMLTokenVerifierInputProcessor samlTokenVerifierInputProcessor =
                new SAMLTokenVerifierInputProcessor(securityProperties, samlAssertionWrapper, subjectSecurityTokenProvider, subjectSecurityToken);
        wsSecurityContext.addSecurityEventListener(samlTokenVerifierInputProcessor);
        inputProcessorChain.addProcessor(samlTokenVerifierInputProcessor);
    }

    private int getSubjectKeyInfoIndex(Deque<XMLSecEvent> eventQueue) {
        int idx = -1;
        Iterator<XMLSecEvent> xmlSecEventIterator = eventQueue.descendingIterator();
        while (xmlSecEventIterator.hasNext()) {
            XMLSecEvent xmlSecEvent = xmlSecEventIterator.next();
            idx++;
            switch (xmlSecEvent.getEventType()) {
                case XMLStreamConstants.START_ELEMENT: {
                    QName elementName = xmlSecEvent.asStartElement().getName();
                    if (WSSConstants.TAG_dsig_KeyInfo.equals(elementName)) {
                        List<QName> elementPath = xmlSecEvent.asStartElement().getElementPath();
                        if (elementPath.size() >= 4) {
                            int lastIndex = elementPath.size() - 2;
                            if ("SubjectConfirmationData".equals(elementPath.get(lastIndex).getLocalPart()) &&
                                    "SubjectConfirmation".equals(elementPath.get(lastIndex - 1).getLocalPart()) &&
                                    "Subject".equals(elementPath.get(lastIndex - 2).getLocalPart())) {
                                return idx;
                            } else if ("SubjectConfirmation".equals(elementPath.get(lastIndex).getLocalPart()) &&
                                    "Subject".equals(elementPath.get(lastIndex - 1).getLocalPart())) {
                                return idx;
                            }
                        }
                    }
                }
            }
        }
        return idx;
    }

    private int getSignatureKeyInfoIndex(Deque<XMLSecEvent> eventQueue) {
        int idx = -1;
        Iterator<XMLSecEvent> xmlSecEventIterator = eventQueue.descendingIterator();
        while (xmlSecEventIterator.hasNext()) {
            XMLSecEvent xmlSecEvent = xmlSecEventIterator.next();
            idx++;
            switch (xmlSecEvent.getEventType()) {
                case XMLStreamConstants.START_ELEMENT: {
                    QName elementName = xmlSecEvent.asStartElement().getName();
                    if (WSSConstants.TAG_dsig_KeyInfo.equals(elementName)) {
                        List<QName> elementPath = xmlSecEvent.asStartElement().getElementPath();
                        if (elementPath.size() >= 4) {
                            int lastIndex = elementPath.size() - 2;
                            if ("Signature".equals(elementPath.get(lastIndex).getLocalPart()) &&
                                    "Assertion".equals(elementPath.get(lastIndex - 1).getLocalPart())) {
                                return idx;
                            }
                        }
                    }
                }
            }
        }
        return idx;
    }

    private SecurityToken parseKeyInfo(InputProcessorChain inputProcessorChain, XMLSecurityProperties securityProperties,
                                       Deque<XMLSecEvent> eventQueue, int index) throws XMLSecurityException {
        XMLSecEvent xmlSecEvent = null;
        int idx = 0;
        Iterator<XMLSecEvent> xmlSecEventIterator = eventQueue.descendingIterator();
        while (xmlSecEventIterator.hasNext() && idx <= index) {
            xmlSecEvent = xmlSecEventIterator.next();
            idx++;
        }
        //forward to next start element
        while (xmlSecEventIterator.hasNext()) {
            xmlSecEvent = xmlSecEventIterator.next();
            if (xmlSecEvent.isStartElement()) {
                break;
            }
            idx++;
        }
        if (xmlSecEvent == null || !xmlSecEvent.isStartElement()) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN, "noKeyInSAMLToken");
        }

        final XMLSecStartElement xmlSecStartElement = xmlSecEvent.asStartElement();
        final QName elementName = xmlSecStartElement.getName();
        if (WSSConstants.TAG_wst_BinarySecret.equals(elementName) ||
                WSSConstants.TAG_wst0512_BinarySecret.equals(elementName)) {

            final StringBuilder stringBuilder = new StringBuilder();
            loop:
            while (xmlSecEventIterator.hasNext()) {
                xmlSecEvent = xmlSecEventIterator.next();
                switch (xmlSecEvent.getEventType()) {
                    case XMLStreamConstants.END_ELEMENT:
                        if (xmlSecEvent.asEndElement().getName().equals(elementName)) {
                            break loop;
                        }
                        break;
                    case XMLStreamConstants.CHARACTERS:
                        stringBuilder.append(xmlSecEvent.asCharacters().getText());
                        break;
                }
            }

            return new AbstractInboundSecurityToken(
                    inputProcessorChain.getSecurityContext(), "",
                    XMLSecurityConstants.XMLKeyIdentifierType.NO_KEY_INFO) {
                @Override
                public XMLSecurityConstants.TokenType getTokenType() {
                    return XMLSecurityConstants.DefaultToken;
                }

                @Override
                public boolean isAsymmetric() throws XMLSecurityException {
                    return false;
                }

                @Override
                protected Key getKey(String algorithmURI, XMLSecurityConstants.KeyUsage keyUsage, String correlationID)
                        throws XMLSecurityException {
                    Key key = super.getKey(algorithmURI, keyUsage, correlationID);
                    if (key == null) {
                        String algoFamily = JCEAlgorithmMapper.getJCERequiredKeyFromURI(algorithmURI);
                        key = new SecretKeySpec(Base64.decodeBase64(stringBuilder.toString()), algoFamily);
                        setSecretKey(algorithmURI, key);
                    }
                    return key;
                }
            };
        } else {
            Object object = null;
            try {
                Unmarshaller unmarshaller = WSSConstants.getJaxbUnmarshaller(securityProperties.isDisableSchemaValidation());
                object = unmarshaller.unmarshal(new XMLSecurityEventReader(eventQueue, idx));
            } catch (JAXBException e) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.UNSUPPORTED_SECURITY_TOKEN, e);
            }

            if (object instanceof JAXBElement) {
                object = ((JAXBElement) object).getValue();
            }

            KeyInfoType keyInfoType = null;
            if (object instanceof X509DataType) {
                JAXBElement<X509DataType> x509DataTypeJAXBElement =
                        new org.apache.xml.security.binding.xmldsig.ObjectFactory().createX509Data((X509DataType) object);
                keyInfoType = new KeyInfoType();
                SecurityTokenReferenceType securityTokenReferenceType = new SecurityTokenReferenceType();
                securityTokenReferenceType.getAny().add(x509DataTypeJAXBElement);
                JAXBElement<SecurityTokenReferenceType> securityTokenReferenceTypeJAXBElement =
                        new ObjectFactory().createSecurityTokenReference(securityTokenReferenceType);
                keyInfoType.getContent().add(securityTokenReferenceTypeJAXBElement);
            } else if (object instanceof EncryptedKeyType) {
                EncryptedKeyType encryptedKeyType = (EncryptedKeyType) object;
                keyInfoType = encryptedKeyType.getKeyInfo();
            } else if (object instanceof SecurityTokenReferenceType) {
                JAXBElement<SecurityTokenReferenceType> securityTokenReferenceTypeJAXBElement =
                        new ObjectFactory().createSecurityTokenReference((SecurityTokenReferenceType) object);
                keyInfoType = new KeyInfoType();
                keyInfoType.getContent().add(securityTokenReferenceTypeJAXBElement);
            } else if (object instanceof KeyValueType) {
                JAXBElement<KeyValueType> keyValueTypeJAXBElement =
                        new org.apache.xml.security.binding.xmldsig.ObjectFactory().createKeyValue((KeyValueType) object);
                keyInfoType = new KeyInfoType();
                keyInfoType.getContent().add(keyValueTypeJAXBElement);
            } else {
                throw new WSSecurityException(WSSecurityException.ErrorCode.UNSUPPORTED_SECURITY_TOKEN, "unsupportedKeyInfo");
            }

            return SecurityTokenFactory.getInstance().getSecurityToken(
                    keyInfoType, SecurityToken.KeyInfoUsage.SIGNATURE_VERIFICATION,
                    securityProperties, inputProcessorChain.getSecurityContext());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T parseStructure(Deque<XMLSecEvent> eventDeque, int index, XMLSecurityProperties securityProperties)
            throws XMLSecurityException {
        Document document;
        try {
            document = documentBuilderFactory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN, e);
        }

        Iterator<XMLSecEvent> xmlSecEventIterator = eventDeque.descendingIterator();
        int curIdx = 0;
        while (curIdx++ < index) {
            xmlSecEventIterator.next();
        }

        Node currentNode = document;
        while (xmlSecEventIterator.hasNext()) {
            XMLSecEvent next = xmlSecEventIterator.next();
            currentNode = parseXMLEvent(next, currentNode, document);
        }
        return (T) document;
    }

    //todo custom SAML unmarshaller directly to XMLObject?
    public Node parseXMLEvent(XMLSecEvent xmlSecEvent, Node currentNode, Document document) throws WSSecurityException {
        switch (xmlSecEvent.getEventType()) {
            case XMLStreamConstants.START_ELEMENT:
                XMLSecStartElement xmlSecStartElement = xmlSecEvent.asStartElement();
                Element element = document.createElementNS(xmlSecStartElement.getName().getNamespaceURI(),
                        xmlSecStartElement.getName().getLocalPart());
                if (xmlSecStartElement.getName().getPrefix() != null && !xmlSecStartElement.getName().getPrefix().isEmpty()) {
                    element.setPrefix(xmlSecStartElement.getName().getPrefix());
                }
                currentNode = currentNode.appendChild(element);
                @SuppressWarnings("unchecked")
                Iterator<XMLSecNamespace> namespaceIterator = xmlSecStartElement.getNamespaces();
                while (namespaceIterator.hasNext()) {
                    XMLSecNamespace next = namespaceIterator.next();
                    parseXMLEvent(next, currentNode, document);
                }
                @SuppressWarnings("unchecked")
                Iterator<XMLSecAttribute> attributesIterator = xmlSecStartElement.getAttributes();
                while (attributesIterator.hasNext()) {
                    XMLSecAttribute next = attributesIterator.next();
                    parseXMLEvent(next, currentNode, document);
                }
                //add namespace which is not declared on current element but must be on a parent element:
                String elementNs = document.lookupNamespaceURI(xmlSecStartElement.getName().getPrefix());
                if (elementNs == null) {
                    parseXMLEvent(xmlSecStartElement.getElementNamespace(), currentNode, document);
                }
                break;
            case XMLStreamConstants.END_ELEMENT:
                if (currentNode.getParentNode() != null) {
                    currentNode = currentNode.getParentNode();
                }
                break;
            case XMLStreamConstants.PROCESSING_INSTRUCTION:
                Node piNode = document.createProcessingInstruction(
                        ((ProcessingInstruction) xmlSecEvent).getTarget(),
                        ((ProcessingInstruction) xmlSecEvent).getTarget()
                );
                currentNode.appendChild(piNode);
                break;
            case XMLStreamConstants.CHARACTERS:
                Node characterNode = document.createTextNode(xmlSecEvent.asCharacters().getData());
                currentNode.appendChild(characterNode);
                break;
            case XMLStreamConstants.COMMENT:
                Node commentNode = document.createComment(((Comment) xmlSecEvent).getText());
                currentNode.appendChild(commentNode);
                break;
            case XMLStreamConstants.START_DOCUMENT:
                break;
            case XMLStreamConstants.END_DOCUMENT:
                return currentNode;
            case XMLStreamConstants.ATTRIBUTE:
                final XMLSecAttribute xmlSecAttribute = (XMLSecAttribute) xmlSecEvent;
                Attr attributeNode = document.createAttributeNS(
                        xmlSecAttribute.getName().getNamespaceURI(),
                        xmlSecAttribute.getName().getLocalPart());
                attributeNode.setPrefix(xmlSecAttribute.getName().getPrefix());
                attributeNode.setValue(xmlSecAttribute.getValue());
                ((Element) currentNode).setAttributeNodeNS(attributeNode);

                //add namespace which is not declared on current element but must be on a parent element:
                String attrNs = document.lookupNamespaceURI(xmlSecAttribute.getName().getPrefix());
                if (attrNs == null) {
                    parseXMLEvent(xmlSecAttribute.getAttributeNamespace(), currentNode, document);
                }
                break;
            case XMLStreamConstants.DTD:
                //todo?:
                /*
                Node dtdNode = document.getDoctype().getEntities()
                ((DTD)xmlSecEvent).getDocumentTypeDeclaration():
                ((DTD)xmlSecEvent).getEntities()
                */
                break;
            case XMLStreamConstants.NAMESPACE:
                Namespace namespace = (Namespace) xmlSecEvent;
                Attr namespaceNode;
                String prefix = namespace.getPrefix();
                if (prefix == null || prefix.isEmpty()) {
                    namespaceNode = document.createAttributeNS(WSSConstants.NS_XML, "xmlns");
                } else {
                    namespaceNode = document.createAttributeNS(WSSConstants.NS_XML, "xmlns:" + prefix);
                }
                namespaceNode.setValue(namespace.getNamespaceURI());
                ((Element) currentNode).setAttributeNodeNS(namespaceNode);
                break;
            default:
                throw new WSSecurityException(
                        WSSecurityException.ErrorCode.INVALID_SECURITY_TOKEN,
                        "empty",
                        "Illegal XMLEvent received: " + xmlSecEvent.getEventType());
        }
        return currentNode;
    }

    /**
     * Processor to check the holder-of-key or sender-vouches requirements against the received assertion
     * which can not be done until the whole soap-header is processed and we now that the whole soap-body
     * is signed.
     */
    class SAMLTokenVerifierInputProcessor extends AbstractInputProcessor implements SecurityEventListener {

        private SamlAssertionWrapper samlAssertionWrapper;
        private SecurityTokenProvider securityTokenProvider;
        private SecurityToken subjectSecurityToken;
        private List<SignedElementSecurityEvent> samlTokenSignedElementSecurityEvents = new ArrayList<SignedElementSecurityEvent>();
        private SignedElementSecurityEvent bodySignedElementSecurityEvent;

        SAMLTokenVerifierInputProcessor(XMLSecurityProperties securityProperties, SamlAssertionWrapper samlAssertionWrapper,
                                        SecurityTokenProvider securityTokenProvider, SecurityToken subjectSecurityToken) {
            super(securityProperties);
            this.setPhase(XMLSecurityConstants.Phase.POSTPROCESSING);
            this.addAfterProcessor(OperationInputProcessor.class.getName());
            this.samlAssertionWrapper = samlAssertionWrapper;
            this.securityTokenProvider = securityTokenProvider;
            this.subjectSecurityToken = subjectSecurityToken;
        }

        @Override
        public void registerSecurityEvent(SecurityEvent securityEvent) throws XMLSecurityException {
            if (securityEvent.getSecurityEventType() == SecurityEventConstants.SignedElement) {
                SignedElementSecurityEvent signedElementSecurityEvent = (SignedElementSecurityEvent) securityEvent;

                List<QName> elementPath = signedElementSecurityEvent.getElementPath();
                if (elementPath.equals(WSSConstants.SOAP_11_BODY_PATH)) {
                    bodySignedElementSecurityEvent = signedElementSecurityEvent;
                } else if (elementPath.equals(saml2TokenPath) || elementPath.equals(saml1TokenPath)) {
                    samlTokenSignedElementSecurityEvents.add(signedElementSecurityEvent);
                }
            }
        }

        @Override
        public XMLSecEvent processNextHeaderEvent(InputProcessorChain inputProcessorChain)
                throws XMLStreamException, XMLSecurityException {
            return inputProcessorChain.processHeaderEvent();
        }

        @Override
        public XMLSecEvent processNextEvent(InputProcessorChain inputProcessorChain)
                throws XMLStreamException, XMLSecurityException {

            XMLSecEvent xmlSecEvent = inputProcessorChain.processEvent();
            if (xmlSecEvent.getEventType() == XMLStreamConstants.START_ELEMENT) {
                XMLSecStartElement xmlSecStartElement = xmlSecEvent.asStartElement();
                List<QName> elementPath = xmlSecStartElement.getElementPath();
                if (elementPath.size() == 3 && WSSUtils.isInSOAPBody(elementPath)) {
                    inputProcessorChain.removeProcessor(this);
                    checkPossessionOfKey(inputProcessorChain, samlAssertionWrapper, subjectSecurityToken);
                }
            }
            return xmlSecEvent;
        }

        private void checkPossessionOfKey(
                InputProcessorChain inputProcessorChain, SamlAssertionWrapper samlAssertionWrapper,
                SecurityToken subjectSecurityToken) throws WSSecurityException {

            try {
                SecurityToken httpsSecurityToken = getHttpsSecurityToken(inputProcessorChain);

                List<SecurityTokenProvider> securityTokenProviders =
                        inputProcessorChain.getSecurityContext().getRegisteredSecurityTokenProviders();

                List<String> confirmationMethods = samlAssertionWrapper.getConfirmationMethods();
                for (int i = 0; i < confirmationMethods.size(); i++) {
                    String confirmationMethod = confirmationMethods.get(i);
                    if (OpenSAMLUtil.isMethodHolderOfKey(confirmationMethod)) {

                        X509Certificate[] subjectCertificates = subjectSecurityToken.getX509Certificates();
                        PublicKey subjectPublicKey = subjectSecurityToken.getPublicKey();
                        Key subjectSecretKey = null;
                        Map<String, Key> subjectKeyMap = subjectSecurityToken.getSecretKey();
                        if (subjectKeyMap.size() > 0) {
                            subjectSecretKey = subjectKeyMap.values().toArray(new Key[subjectKeyMap.size()])[0];
                        }

                        /**
                         * Check the holder-of-key requirements against the received assertion. The subject
                         * credential of the SAML Assertion must have been used to sign some portion of
                         * the message, thus showing proof-of-possession of the private/secret key. Alternatively,
                         * the subject credential of the SAML Assertion must match a client certificate credential
                         * when 2-way TLS is used.
                         */

                        //compare https token first:
                        if (httpsSecurityToken != null
                                && httpsSecurityToken.getX509Certificates() != null
                                && httpsSecurityToken.getX509Certificates().length > 0) {

                            X509Certificate httpsCertificate = httpsSecurityToken.getX509Certificates()[0];

                            //compare certificates:
                            if (subjectCertificates != null && subjectCertificates.length > 0
                                    && httpsCertificate.equals(subjectCertificates[0])) {
                                return;
                                //compare public keys:
                            } else if (httpsCertificate.getPublicKey().equals(subjectPublicKey)) {
                                return;
                            }
                        } else {
                            for (int j = 0; j < securityTokenProviders.size(); j++) {
                                SecurityTokenProvider securityTokenProvider = securityTokenProviders.get(j);
                                SecurityToken securityToken = securityTokenProvider.getSecurityToken();
                                if (securityToken == httpsSecurityToken) {
                                    continue;
                                }
                                X509Certificate[] x509Certificates = securityToken.getX509Certificates();
                                PublicKey publicKey = securityToken.getPublicKey();
                                Map<String, Key> keyMap = securityToken.getSecretKey();
                                if (x509Certificates != null && x509Certificates.length > 0
                                        && subjectCertificates != null && subjectCertificates.length > 0 &&
                                        subjectCertificates[0].equals(x509Certificates[0])) {
                                    return;
                                }
                                if (publicKey != null && publicKey.equals(subjectPublicKey)) {
                                    return;
                                }
                                Iterator<Map.Entry<String, Key>> iterator = keyMap.entrySet().iterator();
                                while (iterator.hasNext()) {
                                    Map.Entry<String, Key> next = iterator.next();
                                    if (next.getValue().equals(subjectSecretKey)) {
                                        return;
                                    }
                                }
                            }
                        }
                    } else if (OpenSAMLUtil.isMethodSenderVouches(confirmationMethod)) {
                        /**
                         * Check the sender-vouches requirements against the received assertion. The SAML
                         * Assertion and the SOAP Body must be signed by the same signature.
                         */

                        //
                        // If we have a 2-way TLS connection, then we don't have to check that the
                        // assertion + SOAP body are signed
                        if (httpsSecurityToken != null
                                && httpsSecurityToken.getX509Certificates() != null
                                && httpsSecurityToken.getX509Certificates().length > 0) {
                            return;
                        }

                        SignedElementSecurityEvent samlTokenSignedElementSecurityEvent = null;
                        for (int j = 0; j < samlTokenSignedElementSecurityEvents.size(); j++) {
                            SignedElementSecurityEvent signedElementSecurityEvent = samlTokenSignedElementSecurityEvents.get(j);
                            if (((SecurityToken) securityTokenProvider.getSecurityToken()).getXMLSecEvent() ==
                                    signedElementSecurityEvent.getXmlSecEvent()) {

                                samlTokenSignedElementSecurityEvent = signedElementSecurityEvent;
                            }
                        }
                        if (bodySignedElementSecurityEvent != null &&
                                samlTokenSignedElementSecurityEvent != null &&
                                bodySignedElementSecurityEvent.getSecurityToken() ==
                                        samlTokenSignedElementSecurityEvent.getSecurityToken()) {
                            return;
                        }
                    }
                }
            } catch (XMLSecurityException e) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE, e);
            }
            throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION,
                    "empty", "SAML proof-of-possession of the private/secret key failed");
        }

        private SecurityToken getHttpsSecurityToken(InputProcessorChain inputProcessorChain) throws XMLSecurityException {
            List<SecurityTokenProvider> securityTokenProviders =
                    inputProcessorChain.getSecurityContext().getRegisteredSecurityTokenProviders();
            for (int i = 0; i < securityTokenProviders.size(); i++) {
                SecurityTokenProvider securityTokenProvider = securityTokenProviders.get(i);
                SecurityToken securityToken = securityTokenProvider.getSecurityToken();
                if (securityToken.getTokenType() == WSSConstants.HttpsToken) {
                    return securityToken;
                }
            }
            return null;
        }
    }
}
