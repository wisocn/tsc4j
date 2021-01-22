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

package com.github.tsc4j.testsupport;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Nasty {@link Field} related utilites.
 *
 * @see <a href="https://stackoverflow.com/questions/56039341/get-declared-fields-of-java-lang-reflect-fields-in-jdk12">
 *     Stackoverflow post</a>
 */
@UtilityClass
class FieldHelper {
    private static final VarHandle MODIFIERS = createModifiers();

    @SneakyThrows
    private static VarHandle createModifiers() {
        val lookup = MethodHandles.privateLookupIn(Field.class, MethodHandles.lookup());
        return lookup.findVarHandle(Field.class, "modifiers", int.class);
    }

    /**
     * Makes given field non-final.
     *
     * @param field field to make non-final
     * @return given field
     */
    public static Field makeNonFinal(Field field) {
        int mods = field.getModifiers();
        if (Modifier.isFinal(mods)) {
            MODIFIERS.set(field, mods & ~Modifier.FINAL);
        }
        return field;
    }
}
