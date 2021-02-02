/*
 * Copyright 2017 - 2021 tsc4j project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.tsc4j.spring;

import lombok.experimental.UtilityClass;

/**
 * tsc4j spring-framework bootstrap configuration and autoconfiguration related constants.
 */
@UtilityClass
class Constants {
    private static final String PREFIX = "tsc4j.spring.";
    /**
     * Spring boolean property defining whether tsc4j is enabled (value: <b>{@value}</b>)
     *
     * @see Tsc4jBootstrapConfiguration#tsc4jPropertySourceLocator(String)
     * @see org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
     */
    static final String PROPERTY_ENABLED = PREFIX + "enabled";

    /**
     * Spring boolean property defining whether tsc4j configuration change should trigger spring-context refresh
     * (value:
     * <b>{@value}</b>)
     *
     * @see Tsc4jContextRefreshConfiguration
     * @see org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
     */
    static final String PROPERTY_REFRESH_CONTEXT = PREFIX + "refresh.enabled";
}
