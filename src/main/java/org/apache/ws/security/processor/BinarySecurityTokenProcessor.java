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

package org.apache.ws.security.processor;

import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSDocInfo;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.WSSecurityEngineResult;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.message.token.BinarySecurity;
import org.apache.ws.security.message.token.PKIPathSecurity;
import org.apache.ws.security.message.token.X509Security;
import org.w3c.dom.Element;

import java.security.cert.X509Certificate;
import java.util.List;
import javax.security.auth.callback.CallbackHandler;

/**
 * Processor implementation to handle wsse:BinarySecurityToken elements
 */
public class BinarySecurityTokenProcessor implements Processor {

    /**
     * {@inheritDoc}
     */
    public List<WSSecurityEngineResult> handleToken(
        Element elem, 
        Crypto crypto, 
        Crypto decCrypto,
        CallbackHandler cb, 
        WSDocInfo wsDocInfo,
        WSSConfig config
    ) throws WSSecurityException {
        
        BinarySecurity token = createSecurityToken(elem);
        X509Certificate[] certs = null;
        if (crypto == null) {
            certs = getCertificatesTokenReference(token, decCrypto);
        } else {
            certs = getCertificatesTokenReference(token, crypto);
        }
        WSSecurityEngineResult result = 
            new WSSecurityEngineResult(WSConstants.BST, token, certs);
        wsDocInfo.addTokenElement(elem);
        String id = elem.getAttributeNS(WSConstants.WSU_NS, "Id");
        result.put(WSSecurityEngineResult.TAG_ID, id);
        wsDocInfo.addResult(result);
        return java.util.Collections.singletonList(result);
    }
    
    /**
     * Extracts the certificate(s) from the Binary Security token reference.
     *
     * @param token The BinarySecurity instance corrresponding to either X509Security or 
     *              PKIPathSecurity
     * @return The X509Certificates associated with this reference
     * @throws WSSecurityException
     */
    private X509Certificate[] getCertificatesTokenReference(BinarySecurity token, Crypto crypto)
        throws WSSecurityException {
        if (token instanceof PKIPathSecurity) {
            return ((PKIPathSecurity) token).getX509Certificates(crypto);
        } else if (token instanceof X509Security) {
            X509Certificate cert = ((X509Security) token).getX509Certificate(crypto);
            return new X509Certificate[]{cert};
        }
        return null;
    }

    /**
     * Checks the <code>element</code> and creates appropriate binary security object.
     *
     * @param element The XML element that contains either a <code>BinarySecurityToken
     *                </code> or a <code>PKIPath</code> element.
     * @return a BinarySecurity token element
     * @throws WSSecurityException
     */
    private BinarySecurity createSecurityToken(Element element) throws WSSecurityException {
        String type = element.getAttribute("ValueType");
        BinarySecurity token = null;
        if (X509Security.X509_V3_TYPE.equals(type)) {
            token = new X509Security(element);
        } else if (PKIPathSecurity.getType().equals(type)) {
            token = new PKIPathSecurity(element);
        } else {
            token = new BinarySecurity(element);
        }
        return token;
    }

}