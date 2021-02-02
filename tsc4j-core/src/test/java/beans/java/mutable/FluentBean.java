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

package beans.java.mutable;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Accessors(chain = true)
public class FluentBean {
    private boolean aBoolean;
    private int aInt;
    private long aLong;
    private double aDouble;
    private String aString;

    private List<FluentBeanEntry> entryList = new ArrayList<>();
    private Set<FluentBeanEntry> entrySet = new LinkedHashSet<>();

    private Map<String, String> strMap = new HashMap<>();
    private Map<Integer, Boolean> intBoolMap = new HashMap<>();

    @Data
    static class FluentBeanEntry {
        private String x;
        private int y;
    }
}
