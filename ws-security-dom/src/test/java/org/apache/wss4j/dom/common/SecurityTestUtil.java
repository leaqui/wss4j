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
package org.apache.wss4j.dom.common;

import java.io.File;
import java.util.Base64;

import org.apache.wss4j.common.cache.ReplayCache;
import org.apache.wss4j.common.cache.ReplayCacheFactory;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.util.WSSecurityUtil;

/**
 * A utility class for security tests
 */
public final class SecurityTestUtil {

    private SecurityTestUtil() {
        // complete
    }

    public static void cleanup() {
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir != null) {
            File[] tmpFiles = new File(tmpDir).listFiles();
            if (tmpFiles != null) {
                for (File tmpFile : tmpFiles) {
                    if (tmpFile.exists() && tmpFile.getName().endsWith(".data")) {
                        tmpFile.delete();
                    }
                }
            }
        }
    }

    public static ReplayCache createCache(String key) throws WSSecurityException {
        ReplayCacheFactory replayCacheFactory = ReplayCacheFactory.newInstance();
        String cacheKey = key + Base64.getEncoder().encodeToString(WSSecurityUtil.generateNonce(10));
        return replayCacheFactory.newReplayCache(cacheKey, null);
    }
}
