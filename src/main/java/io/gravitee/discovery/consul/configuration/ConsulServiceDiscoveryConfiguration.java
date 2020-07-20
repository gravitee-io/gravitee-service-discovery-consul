/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.discovery.consul.configuration;

import io.gravitee.discovery.api.ServiceDiscoveryConfiguration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConsulServiceDiscoveryConfiguration implements ServiceDiscoveryConfiguration {

    private String url;

    private String acl;

    private String dc;

    private String service;

    private TrustStoreType trustStoreType = TrustStoreType.NONE;

    private String trustStorePassword;

    private String trustStorePath;

    private String trustStoreContent;

    private KeyStoreType keyStoreType = KeyStoreType.NONE;

    private String keyStorePassword;

    private String keyStorePath;

    private String keyStoreContent;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAcl() {
        return acl;
    }

    public void setAcl(String acl) {
        this.acl = acl;
    }

    public String getDc() {
        return dc;
    }

    public void setDc(String dc) {
        this.dc = dc;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public TrustStoreType getTrustStoreType() {
        return trustStoreType;
    }

    public void setTrustStoreType(TrustStoreType trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    public KeyStoreType getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(KeyStoreType keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public String getTrustStoreContent() {
        return trustStoreContent;
    }

    public void setTrustStoreContent(String trustStoreContent) {
        this.trustStoreContent = trustStoreContent;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public String getKeyStoreContent() {
        return keyStoreContent;
    }

    public void setKeyStoreContent(String keyStoreContent) {
        this.keyStoreContent = keyStoreContent;
    }
}
