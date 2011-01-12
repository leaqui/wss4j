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

package org.apache.ws.security.saml;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.CryptoFactory;

import org.apache.ws.security.saml.ext.AssertionWrapper;
import org.apache.ws.security.saml.ext.OpenSAMLUtil;
import org.apache.ws.security.saml.ext.SAMLParms;
import org.apache.ws.security.util.Loader;

import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.security.x509.X509KeyInfoGeneratorFactory;
import org.opensaml.xml.signature.KeyInfo;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureConstants;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Properties;

import javax.security.auth.callback.CallbackHandler;

/**
 * Builds a WS SAML Assertion and inserts it into the SOAP Envelope. Refer to
 * the WS specification, SAML Token profile
 *
 * @author Davanum Srinivas (dims@yahoo.com).
 */
public class SAMLIssuerImpl implements SAMLIssuer {

    private static final Log log = LogFactory.getLog(SAMLIssuerImpl.class.getName());

    private Properties properties = null;
    
    private CallbackHandler callbackHandler = null;

    private String issuer;
    private Crypto issuerCrypto = null;
    private String issuerKeyPassword = null;
    private String issuerKeyName = null;

    private String samlVersion = "1.1";
    
    /**
     * Flag indicating what format to put the subject's key material in when
     * NOT using Sender Vouches as the confirmation method.  The default is
     * to use ds:X509Data and include the entire certificate.  If this flag
     * is set to true, a ds:KeyValue is used instead with just the key material.
     */
    private boolean sendKeyValue = false;
    
    /**
     * This boolean controls whether the assertion is to be signed or not
     */
    private boolean signAssertion = false;

    /**
     * Constructor.
     */
    public SAMLIssuerImpl() {
    }

    public SAMLIssuerImpl(Properties prop) {
        /*
         * if no properties .. just return an instance, the rest will be done
         * later or this instance is just used to handle certificate
         * conversions in this implementation
         */
        if (prop == null) {
            return;
        }
        properties = prop;

        String cryptoProp =
                properties.getProperty("org.apache.ws.security.saml.issuer.cryptoProp.file");
        if (cryptoProp != null) {
            issuerCrypto = CryptoFactory.getInstance(cryptoProp);
            issuerKeyName =
                    properties.getProperty("org.apache.ws.security.saml.issuer.key.name");
            issuerKeyPassword =
                    properties.getProperty("org.apache.ws.security.saml.issuer.key.password");
        }
        
        String sendKeyValueProp =
            properties.getProperty("org.apache.ws.security.saml.issuer.sendKeyValue");
        if (sendKeyValueProp != null) {
            sendKeyValue = Boolean.valueOf(sendKeyValueProp).booleanValue();
        }
        
        String signAssertionProp =
            properties.getProperty("org.apache.ws.security.saml.issuer.signAssertion");
        if (signAssertionProp != null) {
            signAssertion = Boolean.valueOf(signAssertionProp).booleanValue();
        }
        
        String samlVersionProp = properties.getProperty("org.apache.ws.security.saml.version");
        if (samlVersionProp != null) {
            samlVersion = samlVersionProp;
        }
        
        String issuerProp = properties.getProperty("org.apache.ws.security.saml.issuer");
        if (issuerProp != null) {
            issuer = issuerProp;
        }
    }

    /**
     * Creates a new AssertionWrapper.
     *
     * @return a new AssertionWrapper.
     */
    public AssertionWrapper newAssertion() throws WSSecurityException {
        
        log.debug(
          "Entering AssertionWrapper.newAssertion() ... creating SAML v" 
          + samlVersion + " token"
        );

        if (callbackHandler == null) {
            try {
                String samlCallbackClassname = 
                    properties.getProperty("org.apache.ws.security.saml.callback");
                Class<?> callbackClass = null;
                try {
                    callbackClass = Loader.loadClass(samlCallbackClassname);
                } catch (ClassNotFoundException ex) {
                    throw new WSSecurityException(ex.getMessage(), ex);
                }
                callbackHandler = (CallbackHandler)callbackClass.newInstance();
            } catch (InstantiationException ex) {
                throw new WSSecurityException(ex.getMessage(), ex);
            } catch (IllegalAccessException ex) {
                throw new WSSecurityException(ex.getMessage(), ex);
            }
        }
            
        // Create a new SAMLParms with all of the information from the properties file.
        SAMLParms samlParms = new SAMLParms();
        samlParms.setIssuer(issuer);
        samlParms.setSamlVersion(samlVersion);
        samlParms.setCallbackHandler(callbackHandler);

        AssertionWrapper sa = new AssertionWrapper(samlParms);
        
        if (signAssertion) {
            //
            // Create the signature
            //
            Signature signature = OpenSAMLUtil.buildSignature();
            signature.setCanonicalizationAlgorithm(
                SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
            );
            
            // prepare to sign the SAML token
            X509Certificate[] issuerCerts = issuerCrypto.getCertificates(issuerKeyName);

            String sigAlgo = SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1;
            String pubKeyAlgo = issuerCerts[0].getPublicKey().getAlgorithm();
            log.debug("automatic sig algo detection: " + pubKeyAlgo);
            if (pubKeyAlgo.equalsIgnoreCase("DSA")) {
                sigAlgo = SignatureConstants.ALGO_ID_SIGNATURE_DSA;
            }
            PrivateKey privateKey = null;
            try {
                privateKey = issuerCrypto.getPrivateKey(issuerKeyName, issuerKeyPassword);
            } catch (Exception ex) {
                throw new WSSecurityException(ex.getMessage(), ex);
            }

            signature.setSignatureAlgorithm(sigAlgo);

            BasicX509Credential signingCredential = new BasicX509Credential();
            if (issuerCerts.length == 1) {
                signingCredential.setEntityCertificate(issuerCerts[0]);
            } else {
                signingCredential.setEntityCertificateChain(Arrays.asList(issuerCerts));
            }
            signingCredential.setPrivateKey(privateKey);
            signingCredential.setEntityId(issuer);

            signature.setSigningCredential(signingCredential);

            X509KeyInfoGeneratorFactory kiFactory = new X509KeyInfoGeneratorFactory();
            if (sendKeyValue) {
                kiFactory.setEmitPublicKeyValue(true);
            } else {
                kiFactory.setEmitEntityCertificate(true);
            }
            try {
                KeyInfo keyInfo = kiFactory.newInstance().generate(signingCredential);
                signature.setKeyInfo(keyInfo);
            } catch (org.opensaml.xml.security.SecurityException ex) {
                throw new WSSecurityException(
                    "Error generating KeyInfo from signing credential", ex
                );
            }

            // add the signature to the assertion
            sa.setSignature(signature);
        }

        return sa;
    }
    
    /**
     * Set whether to send the key value or whether to include the entire cert.
     * @param sendKeyValue whether to send the key value.
     */
    public void setSendKeyValue(boolean sendKeyValue) {
        this.sendKeyValue = sendKeyValue;
    }
    
    /**
     * Get whether to send the key value or whether to include the entire cert.
     * @return whether to send the key value
     */
    public boolean isSendKeyValue() {
        return sendKeyValue;
    }
    
    /**
     * Set whether to sign the assertion or not.
     * @param signAssertion whether to sign the assertion or not.
     */
    public void setSignAssertion(boolean signAssertion) {
        this.signAssertion = signAssertion;
    }

    /**
     * Get whether to sign the assertion or not
     * @return whether to sign the assertion or not
     */
    public boolean isSignAssertion() {
        return signAssertion;
    }
    
    /**
     * Set the SAML version
     * @param version the SAML version
     */
    public void setSamlVersion(String version) {
        samlVersion = version;
    }
    
    /**
     * Get the SAML version
     * @return the SAML version
     */
    public String getSamlVersion() {
        return samlVersion;
    }
    
    /**
     * Set the CallbackHandler to use
     * @param callbackHandler the CallbackHandler to use
     */
    public void setCallbackHandler(CallbackHandler callbackHandler) {
        this.callbackHandler = callbackHandler;
    }
    
    /**
     * Get the CallbackHandler in use
     * @return the CallbackHandler in use
     */
    public CallbackHandler getCallbackHandler() {
        return callbackHandler;
    }
    
    /**
     * Set the issuer crypto
     * @param issuerCrypto the issuer crypto
     */
    public void setIssuerCrypto(Crypto issuerCrypto) {
        this.issuerCrypto = issuerCrypto;
    }
    
    /**
     * @return Returns the issuerCrypto.
     */
    public Crypto getIssuerCrypto() {
        return issuerCrypto;
    }
    
    /**
     * Set the issuer name
     * @param issuer the issuer name
     */
    public void setIssuerName(String issuer) {
        this.issuer = issuer;
    }
    
    /**
     * Get the issuer name
     * @return the issuer name
     */
    public String getIssuerName() {
        return issuer;
    }

    /**
     * Set the issuer key name
     * @param issuerKeyName the issuer key name
     */
    public void setIssuerKeyName(String issuerKeyName) {
        this.issuerKeyName = issuerKeyName;
    }
    
    /**
     * @return Returns the issuerKeyName.
     */
    public String getIssuerKeyName() {
        return issuerKeyName;
    }
    
    /**
     * Set the issuer key password
     * @param issuerKeyPassword the issuerKeyPassword.
     */
    public void setIssuerKeyPassword(String issuerKeyPassword) {
        this.issuerKeyPassword = issuerKeyPassword;
    }

    /**
     * @return Returns the issuerKeyPassword.
     */
    public String getIssuerKeyPassword() {
        return issuerKeyPassword;
    }

}
