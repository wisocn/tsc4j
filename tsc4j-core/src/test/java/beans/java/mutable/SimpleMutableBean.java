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

import beans.java.immutable.MyEnum;
import com.typesafe.config.ConfigMemorySize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimpleMutableBean {
    @NonNull Duration duration;
    @NonNull Period period;

    @NonNull ConfigMemorySize minUsedBytes;
    @NonNull ConfigMemorySize maxUsedBytes;
    @NonNull ConfigMemorySize totalUsedBytes;

    @NonNull ZonedDateTime zonedDateTime;
    @NonNull LocalDateTime localDateTime;
    @NonNull LocalDate localDate;
    @NonNull Date date;
    @NonNull UUID uuid;
    @NonNull Pattern regex;

    boolean aBoolean;
    @NonNull String aString;
    short aShort;
    int anInt;
    long aLong;
    float aFloat;
    double aDouble;

    @NonNull MyEnum anEnum;
}
