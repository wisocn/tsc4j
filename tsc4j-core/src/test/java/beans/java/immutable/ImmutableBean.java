/*
 * Copyright 2017 - 2019 tsc4j project
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
 *
 */

package beans.java.immutable;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.github.tsc4j.api.Tsc4jBeanBuilder;
import com.github.tsc4j.api.Tsc4jConfigPath;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tsc4jConfigPath("test.bean")
@Tsc4jBeanBuilder
@Value
@Builder
@JsonDeserialize(builder = ImmutableBean.ImmutableBeanBuilder.class)
public class ImmutableBean {
    boolean aBoolean;
    int anInt;
    long aLong;
    double aDouble;
    String aString;

    @Singular("entryListItem")
    List<ImmutableBeanEntry> entryList;

    @Builder.Default
    Set<ImmutableBeanEntry> entrySet = Collections.emptySet();

    Map<String, String> strMap;

    @Tsc4jBeanBuilder
    @Value
    @Builder
    @JsonDeserialize(builder = ImmutableBean.ImmutableBeanEntry.ImmutableBeanEntryBuilder.class)
    public static class ImmutableBeanEntry {
        @Builder.Default
        String x = "blah";
        @Builder.Default
        int y = 41;

        @JsonPOJOBuilder(withPrefix = "")
        public static class ImmutableBeanEntryBuilder {
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class ImmutableBeanBuilder {
    }
}
