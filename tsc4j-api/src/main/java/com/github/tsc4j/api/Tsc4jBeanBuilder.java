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

package com.github.tsc4j.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation instructs tsc4j config bean builder that in order to create a bean it should create builder first.
 * Typically you want to annotate your <a href="https://projectlombok.org/">Lombok</a> {@code @Value/@Builder}
 * generated classes with this annotation.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tsc4jBeanBuilder {
    /**
     * Builder creation method name. Class should have {@code public static} method that creates builder.
     *
     * @return builder method name
     */
    String builder() default "builder";

    /**
     * Builder class method name that constructs actual class instance.
     *
     * @return builder's build/create method name
     */
    String create() default "build";
}
