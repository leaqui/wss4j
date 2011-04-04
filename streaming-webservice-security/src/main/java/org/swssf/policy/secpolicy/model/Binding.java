/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.swssf.policy.secpolicy.model;

import org.swssf.ext.Constants;
import org.swssf.policy.OperationPolicy;
import org.swssf.policy.assertionStates.AssertionState;
import org.swssf.policy.assertionStates.IncludeTimeStampAssertionState;
import org.swssf.policy.assertionStates.SignedElementAssertionState;
import org.swssf.policy.secpolicy.SPConstants;
import org.swssf.securityEvent.SecurityEvent;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * class lent from apache rampart
 */
public abstract class Binding extends AbstractSecurityAssertion implements AlgorithmWrapper {

    private AlgorithmSuite algorithmSuite;
    private boolean includeTimestamp;
    private Layout layout;
    private SupportingToken signedSupportingToken;
    private SupportingToken signedEndorsingSupportingTokens;

    public Binding(SPConstants spConstants) {
        setVersion(spConstants);
        layout = new Layout(spConstants);
    }

    /**
     * @return Returns the algorithmSuite.
     */
    public AlgorithmSuite getAlgorithmSuite() {
        return algorithmSuite;
    }

    /**
     * @param algorithmSuite The algorithmSuite to set.
     */
    public void setAlgorithmSuite(AlgorithmSuite algorithmSuite) {
        this.algorithmSuite = algorithmSuite;
    }

    /**
     * @return Returns the includeTimestamp.
     */
    public boolean isIncludeTimestamp() {
        return includeTimestamp;
    }

    /**
     * @param includeTimestamp The includeTimestamp to set.
     */
    public void setIncludeTimestamp(boolean includeTimestamp) {
        this.includeTimestamp = includeTimestamp;
    }

    /**
     * @return Returns the layout.
     */
    public Layout getLayout() {
        return layout;
    }

    /**
     * @param layout The layout to set.
     */
    public void setLayout(Layout layout) {
        this.layout = layout;
    }

    public SupportingToken getSignedEndorsingSupportingTokens() {
        return signedEndorsingSupportingTokens;
    }

    public void setSignedEndorsingSupportingTokens(
            SupportingToken signedEndorsingSupportingTokens) {
        this.signedEndorsingSupportingTokens = signedEndorsingSupportingTokens;
    }

    public SupportingToken getSignedSupportingToken() {
        return signedSupportingToken;
    }

    public void setSignedSupportingToken(SupportingToken signedSupportingToken) {
        this.signedSupportingToken = signedSupportingToken;
    }

    @Override
    public SecurityEvent.Event[] getResponsibleAssertionEvents() {
        return new SecurityEvent.Event[]{
                SecurityEvent.Event.Timestamp,
                SecurityEvent.Event.SignedElement
        };
    }

    @Override
    public void getAssertions(Map<SecurityEvent.Event, Collection<AssertionState>> assertionStateMap, OperationPolicy operationPolicy) {
        if (algorithmSuite != null) {
            algorithmSuite.getAssertions(assertionStateMap, operationPolicy);
        }
        if (layout != null) {
            layout.getAssertions(assertionStateMap, operationPolicy);
        }
        if (signedSupportingToken != null) {
            signedSupportingToken.getAssertions(assertionStateMap, operationPolicy);
        }
        if (signedEndorsingSupportingTokens != null) {
            signedEndorsingSupportingTokens.getAssertions(assertionStateMap, operationPolicy);
        }

        Collection<AssertionState> timestampAssertionStates = assertionStateMap.get(SecurityEvent.Event.Timestamp);
        //ws-securitypolicy-1.3-spec: 6.2 [Timestamp] Property
        if (isIncludeTimestamp()) {
            timestampAssertionStates.add(new IncludeTimeStampAssertionState(this, false));

            Collection<AssertionState> signedElementAssertionStates = assertionStateMap.get(SecurityEvent.Event.SignedElement);
            List<QName> qNames = new ArrayList<QName>();
            qNames.add(Constants.TAG_wsu_Timestamp);
            signedElementAssertionStates.add(new SignedElementAssertionState(this, false, qNames));
        } else {
            timestampAssertionStates.add(new IncludeTimeStampAssertionState(this, true));
        }
    }

    @Override
    public boolean isAsserted(Map<SecurityEvent.Event, Collection<AssertionState>> assertionStateMap) {
        boolean isAsserted = super.isAsserted(assertionStateMap);
        //todo early returns?
        if (algorithmSuite != null) {
            isAsserted &= algorithmSuite.isAsserted(assertionStateMap);
        }
        if (layout != null) {
            isAsserted &= layout.isAsserted(assertionStateMap);
        }
        if (signedSupportingToken != null) {
            isAsserted &= signedSupportingToken.isAsserted(assertionStateMap);
        }
        if (signedEndorsingSupportingTokens != null) {
            isAsserted &= signedEndorsingSupportingTokens.isAsserted(assertionStateMap);
        }
        return isAsserted;
    }
}