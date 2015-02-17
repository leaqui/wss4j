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

package org.apache.wss4j.dom.action;

import java.util.ArrayList;
import java.util.List;

import javax.security.auth.callback.CallbackHandler;

import org.apache.wss4j.common.SecurityActionToken;
import org.apache.wss4j.common.SignatureActionToken;
import org.apache.wss4j.common.WSEncryptionPart;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.RequestData;
import org.apache.wss4j.dom.handler.WSHandler;
import org.apache.wss4j.dom.message.WSSecSignature;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class SignatureAction implements Action {
    public void execute(WSHandler handler, SecurityActionToken actionToken,
                        Document doc, RequestData reqData)
            throws WSSecurityException {
        CallbackHandler callbackHandler = reqData.getCallbackHandler();
        if (callbackHandler == null) {
            callbackHandler = handler.getPasswordCallbackHandler(reqData);
        }
        
        SignatureActionToken signatureToken = null;
        if (actionToken instanceof SignatureActionToken) {
            signatureToken = (SignatureActionToken)actionToken;
        }
        if (signatureToken == null) {
            signatureToken = reqData.getSignatureToken();
        }
        
        WSPasswordCallback passwordCallback = 
            handler.getPasswordCB(signatureToken.getUser(), WSConstants.SIGN, callbackHandler, reqData);
        WSSecSignature wsSign = new WSSecSignature(reqData.getWssConfig());

        if (signatureToken.getKeyIdentifierId() != 0) {
            wsSign.setKeyIdentifierType(signatureToken.getKeyIdentifierId());
        }
        if (signatureToken.getSignatureAlgorithm() != null) {
            wsSign.setSignatureAlgorithm(signatureToken.getSignatureAlgorithm());
        }
        if (signatureToken.getDigestAlgorithm() != null) {
            wsSign.setDigestAlgo(signatureToken.getDigestAlgorithm());
        }
        if (signatureToken.getC14nAlgorithm() != null) {
            wsSign.setSigCanonicalization(signatureToken.getC14nAlgorithm());
        }
        
        wsSign.setIncludeSignatureToken(signatureToken.isIncludeToken());

        wsSign.setUserInfo(signatureToken.getUser(), passwordCallback.getPassword());
        wsSign.setUseSingleCertificate(signatureToken.isUseSingleCert());
        
        if (passwordCallback.getKey() != null) {
            wsSign.setSecretKey(passwordCallback.getKey());
        } else if (signatureToken.getKey() != null) {
            wsSign.setSecretKey(signatureToken.getKey());
        }
        
        if (signatureToken.getTokenId() != null) {
            wsSign.setCustomTokenId(signatureToken.getTokenId());
        }
        if (signatureToken.getTokenType() != null) {
            wsSign.setCustomTokenValueType(signatureToken.getTokenType());
        }
        if (signatureToken.getSha1Value() != null) {
            wsSign.setEncrKeySha1value(signatureToken.getSha1Value());
        }

        wsSign.setAttachmentCallbackHandler(reqData.getAttachmentCallbackHandler());

        try {
            wsSign.prepare(doc, signatureToken.getCrypto(), reqData.getSecHeader());

            Element siblingElementToPrepend = null;
            boolean signBST = false;
            for (WSEncryptionPart part : signatureToken.getParts()) {
                if ("STRTransform".equals(part.getName()) && part.getId() == null) {
                    part.setId(wsSign.getSecurityTokenReferenceURI());
                } else if (reqData.isAppendSignatureAfterTimestamp()
                        && WSConstants.WSU_NS.equals(part.getNamespace())
                        && "Timestamp".equals(part.getName())) {
                    int originalSignatureActionIndex = 
                        reqData.getOriginalSignatureActionPosition();
                    // Need to figure out where to put the Signature Element in the header
                    if (originalSignatureActionIndex > 0) {
                        Element secHeader = reqData.getSecHeader().getSecurityHeader();
                        Node lastChild = secHeader.getLastChild();
                        int count = 0;
                        while (lastChild != null && count < originalSignatureActionIndex) {
                            while (lastChild != null && lastChild.getNodeType() != Node.ELEMENT_NODE) {
                                lastChild = lastChild.getPreviousSibling();
                            }
                            count++;
                        }
                        if (lastChild instanceof Element) {
                            siblingElementToPrepend = (Element)lastChild;
                        }
                    }
                } else if (WSConstants.WSSE_NS.equals(part.getNamespace())
                    && WSConstants.BINARY_TOKEN_LN.equals(part.getName())) {
                    signBST = true;
                }
            }

            if (signBST) {
                wsSign.prependBSTElementToHeader(reqData.getSecHeader());
            }
            
            List<WSEncryptionPart> parts = signatureToken.getParts();
            if (parts == null || parts.isEmpty()) {
                WSEncryptionPart encP = new WSEncryptionPart(reqData.getSoapConstants()
                        .getBodyQName().getLocalPart(), reqData.getSoapConstants()
                        .getEnvelopeURI(), "Content");
                parts = new ArrayList<>();
                parts.add(encP);
            }
            
            List<javax.xml.crypto.dsig.Reference> referenceList =
                wsSign.addReferencesToSign(parts, reqData.getSecHeader());

            if (signBST || 
                reqData.isAppendSignatureAfterTimestamp() && siblingElementToPrepend == null) {
                wsSign.computeSignature(referenceList, false, null);
            } else {
                wsSign.computeSignature(referenceList, true, siblingElementToPrepend);
            }

            if (!signBST) {
                wsSign.prependBSTElementToHeader(reqData.getSecHeader());
            }
            reqData.getSignatureValues().add(wsSign.getSignatureValue());
        } catch (WSSecurityException e) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE, "empty", e, "Error during Signature: ");
        }
    }

}
