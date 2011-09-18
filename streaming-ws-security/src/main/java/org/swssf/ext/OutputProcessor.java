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
package org.swssf.ext;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.util.Set;

/**
 * This is the Interface which every OutputProcessor must implement.
 *
 * @author $Author$
 * @version $Revision$ $Date$
 */
public interface OutputProcessor {

    /**
     * This OutputProcessor will be added before the processors in this set
     *
     * @return The set with the named OutputProcessor
     */
    Set<Object> getBeforeProcessors();

    /**
     * This OutputProcessor will be added after the processors in this set
     *
     * @return The set with the named OutputProcessor
     */
    Set<Object> getAfterProcessors();

    /**
     * The Phase in which this OutputProcessor should be applied
     *
     * @return The Phase
     */
    Constants.Phase getPhase();

    /**
     * Will be called from the framework for every XMLEvent
     *
     * @param xmlEvent             The next XMLEvent to process
     * @param outputProcessorChain
     * @throws XMLStreamException  thrown when a streaming error occurs
     * @throws WSSecurityException thrown when a Security failure occurs
     */
    void processNextEvent(XMLEvent xmlEvent, OutputProcessorChain outputProcessorChain) throws XMLStreamException, WSSecurityException;

    /**
     * Will be called when the whole document is processed.
     *
     * @param outputProcessorChain
     * @throws XMLStreamException  thrown when a streaming error occurs
     * @throws WSSecurityException thrown when a Security failure occurs
     */
    void doFinal(OutputProcessorChain outputProcessorChain) throws XMLStreamException, WSSecurityException;
}