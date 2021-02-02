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

package com.github.tsc4j.core;


import com.github.tsc4j.api.Tsc4jBeanBuilder;
import com.github.tsc4j.core.impl.Deserializers;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.ConfigValueType;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link BeanMapper} implementation that uses reflection to inject values.
 */
@Slf4j
@SuppressWarnings("unchecked")
public final class ReflectiveBeanMapper extends AbstractBeanMapper {
    private final Pattern SETTER_CLEANUP_PATTERN = Pattern.compile("^(?:set|with)");
    private final Map<Class<?>, List<Method>> setterCache = new ConcurrentHashMap<>();

    @Override
    protected <T> T createBean(@NonNull Class<T> clazz, @NonNull ConfigValue value, @NonNull String path) {
        val type = value.valueType();
        if (type == ConfigValueType.OBJECT) {
            return createBean(clazz, (ConfigObject) value, path);
        }

        throw new ConfigException.BadValue(value.origin(), path,
            "Cannot instantiate instance of " + clazz.getName() +
                " from config value type " + value.valueType() + ": " + value);
    }

    @SuppressWarnings("unchecked")
    private <T> T createBean(Class<T> clazz, ConfigObject configObj, String path) {

        val beanBuilder = shouldUseBuilder(clazz) ? createBuilder(clazz) : null;
        val bean = (beanBuilder == null) ? initBean(clazz) : beanBuilder;
        Class<?> beanClass = bean.getClass();

        // find setter methods
        val allBeanSetters = getSetters(beanClass);

        // traverse all configuration paths and try to find suitable setter
        for (val entry : configObj.entrySet()) {
            val propName = entry.getKey();
            val propValue = entry.getValue().unwrapped();

            val setters = findSuitableSetters(allBeanSetters, propName);
            runFirstSuitableSetter(setters, beanClass, bean, propName, propValue, configObj);
        }

        // we're done, check whether we need to build instance from builder or return bean directly
        return (beanBuilder == null) ? (T) bean : createBeanInstance(clazz, beanBuilder, configObj.origin(), path);
    }

    private ParameterizedType toParametrizedType(Type type) {
        if (type instanceof WildcardType) {
            val wType = (WildcardType) type;
            return Stream.concat(Stream.of(wType.getUpperBounds()), Stream.of(wType.getLowerBounds()))
                .peek(e -> log.trace("toParametrizedType(): bound: {}", e))
                .map(e -> createParametrizedType(e))
                .findFirst()
                .orElseThrow(() -> new ConfigException.BadBean("Cannot create parametrized type from: " + wType));
        } else if (type instanceof ParameterizedType) {
            return (ParameterizedType) type;
        } else if (type instanceof Class) {
            return createParametrizedType(type);
        }

        throw new IllegalArgumentException("Can't create parametrized type from: " + type);
    }

    private ParameterizedType createParametrizedType(@NonNull Type rawType, @NonNull Type... args) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return args;
            }

            @Override
            public Type getRawType() {
                return rawType;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    /**
     * Creates bean from given config value.
     *
     * @param type        bean type
     * @param configValue config value to create bean from
     * @param path        config path at which {@code value} is defined.
     * @return bean
     * @throws RuntimeException if bean creation fails
     */
    protected Object createParametrizedBean(@NonNull Type type,
                                            @NonNull ConfigValue configValue,
                                            @NonNull String path) {
        log.trace("creating parametrized type bean of {} from (path: {}): {}", type, path, configValue);

        val ptype = toParametrizedType(type);
        val rawType = ptype.getRawType();
        val typeArgs = ptype.getActualTypeArguments();

        if (log.isTraceEnabled()) {
            log.trace("  ptype: {}\n  raw: {}\n  type args: {}", ptype, rawType, Arrays.asList(typeArgs));
        }

        // raw type can be parametrized as well; in that case we need to recurse again
        if (rawType instanceof ParameterizedType) {
            return createParametrizedBean(rawType, configValue, path);
        }

        if (!(rawType instanceof Class)) {
            throw new IllegalArgumentException("Unsupported parametrized type raw: " + rawType + " (" + type + ")");
        }

        val beanClass = (Class<?>) rawType;

        // enum?
        if (isEnum(type)) {
            return toEnum((Class<Enum>) type, configValue, path);
        }
        // raw object, srsly?
        else if (beanClass == Object.class) {
            return configValue.unwrapped();
        }
        // set?
        else if (Set.class.isAssignableFrom(beanClass)) {
            val typeArg = typeArgs[0];
            log.trace("  creating Set<{}>", typeArg);
            val list = createParametrizedBean(createParametrizedType(List.class, typeArg), configValue, path);
            return Collections.unmodifiableSet(new LinkedHashSet((List) list));
        }
        // list?
        else if (Iterable.class.isAssignableFrom(beanClass)) {
            // list can have only one type arg
            val typeArg = typeArgs[0];
            log.trace("  creating List<{}>", typeArg);
            val configList = (ConfigList) configValue;
            val list = configList.stream()
                .map(cfgVal -> createParametrizedBean(typeArg, cfgVal, path))
                .collect(Collectors.toList());
            log.trace("  created list: {}", list);
            return Collections.unmodifiableList(list);
        }
        // map?
        else if (Map.class.isAssignableFrom(beanClass)) {
            val keyType = typeArgs[0];
            val valType = typeArgs[1];
            log.trace("  creating Map<{}, {}>", keyType, valType);
            val configObj = (ConfigObject) configValue;

            val map = new LinkedHashMap();
            configObj.forEach((key, value) -> {
                val nKey = createParametrizedBean(keyType, ConfigValueFactory.fromAnyRef(key), path);
                val nVal = createParametrizedBean(valType, value, key);
                map.put(nKey, nVal);
            });

            log.trace("  created map: {}", map);
            return Collections.unmodifiableMap(map);
        } else {
            return getConfigValueConverter(beanClass)
                .map(function -> runConverterFunction(function, beanClass, configValue, path))
                .orElseGet(() -> create(beanClass, configValue, path));
        }
    }

    @Override
    protected ByClassRegistry<Function<ConfigValue, ?>> defaultValueConverters() {
        return super.defaultValueConverters()
            .add(Deserializers.convertersJavaPrimitives())
            .add(Deserializers.convertersJava())
            .add(Deserializers.convertersJavaTime())
            .add(Deserializers.convertersJdbc())
            ;
    }

    /**
     * Runs first suitable discovered setter on given bean
     *
     * @param setters   list of all possible setters to choose from.
     * @param beanClass bean class
     * @param bean      bean instance
     * @param propName  config property name
     * @param propValue config property value
     * @param configObj original config object from which {@code propValue} was retrieved by getting key {@code
     *                  propName}.
     */
    @SneakyThrows
    private void runFirstSuitableSetter(List<Method> setters,
                                        Class<?> beanClass,
                                        Object bean,
                                        String propName,
                                        Object propValue,
                                        ConfigObject configObj) {
        if (setters.isEmpty()) {
            log.warn("cannot find setter for property name {} (value: {}) on class: {}",
                propName, propValue, beanClass.getName());
            return;
        }

        val exceptions = new ArrayList<Throwable>();
        for (val setter : setters) {
            val opt = tryRunSetter(setter, beanClass, bean, propName, propValue, configObj);
            if (opt.isPresent()) {
                // setter failed...
                exceptions.add(opt.get());
            } else {
                // setter succeeded, we're done
                return;
            }
        }

        if (!exceptions.isEmpty()) {
            val exception = exceptions.get(0);
            throw exception;
        }
    }

    /**
     * Tries to invoke given setter
     *
     * @param setter    setter method
     * @param beanClass bean class
     * @param bean      bean instance to invoke setter on
     * @param propName  config property name
     * @param propValue config property value
     * @param configObj original config object from which {@code propValue} was retrieved by getting key {@code
     *                  propName}.
     * @return optional of exception that occurred while invoking setter; if empty, setter was invoked successfully.
     */
    private Optional<Throwable> tryRunSetter(Method setter,
                                             Class<?> beanClass,
                                             Object bean,
                                             String propName,
                                             Object propValue,
                                             ConfigObject configObj) {
        if (log.isTraceEnabled()) {
            log.trace("tryRunSetter(): trying setter {} with argument: {} ({})",
                descSetter(setter), propValue, propValue.getClass().getName());
        }

        try {
            // try to fetch config value
            val parameterType = setter.getGenericParameterTypes()[0];
            val parameterClass = setter.getParameterTypes()[0];
            if (log.isTraceEnabled()) {
                log.trace("  tryRunSetter(): propName: generic types: {}, paramTypes: {}",
                    Arrays.asList(setter.getGenericParameterTypes()), Arrays.asList(setter.getParameterTypes()));
            }

            val unwrapped = getValue(beanClass, parameterType, parameterClass, configObj, propName);

            invokeSetter(bean, setter, unwrapped);
            return Optional.empty();
        } catch (Throwable t) {
            return Optional.of(t);
        }
    }

    private void invokeSetter(@NonNull Object bean,
                              @NonNull Method setter,
                              Object setterArgument) {

        val beanClass = bean.getClass();
        try {
            log.trace("  invoking setter with: {} ({})", setterArgument, setterArgument.getClass().getName());
            setter.invoke(bean, setterArgument);
        } catch (IllegalAccessException e) {
            throw new ConfigException.BadBean("Setter " + descSetter(setter) + " is not accessible.", e);
        } catch (InvocationTargetException e) {
            throw new ConfigException.BadBean(
                "Invocation of bean setter " + descSetter(setter) + " with argument " + setterArgument + " on " +
                    beanClass.getName() + " resulted in an exception", e);
        }
    }

    private <T> T initBean(Class<T> clazz) {
        try {
            return clazz.getConstructor().newInstance();
        } catch (Throwable t) {
            throw new ConfigException.BadBean(
                "Cannot instantiate class " + clazz.getName() + " via no-args constructor.", t);
        }
    }

    /**
     * Tells whether builder should be used to construct instance of {@code clazz}.
     *
     * @param clazz class to inspect
     * @return true/false
     * @see Tsc4jBeanBuilder
     */
    protected boolean shouldUseBuilder(Class<?> clazz) {
        return getBuilderAnnotation(clazz) != null;
    }

    protected Tsc4jBeanBuilder getBuilderAnnotation(Class<?> clazz) {
        return clazz.getAnnotation(Tsc4jBeanBuilder.class);
    }

    private Tsc4jBeanBuilder requireBuilderAnnotation(Class<?> clazz) {
        val annotation = getBuilderAnnotation(clazz);
        if (annotation == null) {
            throw new ConfigException.BadBean("Class is not annotated with @TypeSafeBeanBuilder: " + clazz.getName());
        }

        if (annotation.builder() == null || annotation.builder().isEmpty()) {
            throw new ConfigException.BadBean("Class " + clazz.getName() + " is annotated with @" +
                Tsc4jBeanBuilder.class.getSimpleName() + " that contains null or empty builder method.");
        }

        if (annotation.create() == null || annotation.create().isEmpty()) {
            throw new ConfigException.BadBean("Class " + clazz.getName() + " is annotated with @" +
                Tsc4jBeanBuilder.class.getSimpleName() + " that contains null or empty create method.");
        }

        return annotation;
    }

    /**
     * Creates builder for class instance.
     *
     * @param clazz clazz
     * @return {@code clazz} instance builder
     * @see Tsc4jBeanBuilder
     */
    protected Object createBuilder(Class<?> clazz) {
        val annotation = requireBuilderAnnotation(clazz);

        // fetch method
        val builderMethodName = annotation.builder();

        try {
            val method = clazz.getMethod(builderMethodName);
            val builder = method.invoke(null);
            if (builder == null) {
                throw new ConfigException.BadBean("Method " + clazz.getName() + "." + builderMethodName +
                    " returned null builder.");
            }
            return builder;
        } catch (NoSuchMethodException e) {
            throw new ConfigException.BadBean("Class " + clazz.getName() +
                " doesn't contain public static no-args method: " + builderMethodName);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new ConfigException.BadBean("Cannot invoke method " + builderMethodName +
                " on class " + clazz.getName(), e);
        }
    }

    protected <T> T createBeanInstance(Class<T> clazz, Object builder, ConfigOrigin origin, String path) {
        val annotation = requireBuilderAnnotation(clazz);
        val createMethodName = annotation.create();

        try {
            val method = builder.getClass().getMethod(createMethodName);
            val instance = method.invoke(builder);
            return clazz.cast(instance);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ConfigException.BadBean("Cannot create " + clazz.getName() +
                " instance out of builder " + builder.getClass().getName(), e);
        } catch (InvocationTargetException e) {
            val cause = e.getCause() == null ? e : e.getCause();
            throw new ConfigException.BadValue(origin, path, "Cannot create " + clazz.getName() +
                " instance out of builder " + builder.getClass().getName(), cause);
        }
    }

    protected List<Method> getSetters(Class<?> clazz) {
        return setterCache.computeIfAbsent(clazz, this::doGetSetters);
    }

    private List<Method> doGetSetters(Class<?> clazz) {
        val setters = Stream.of(clazz.getMethods())
            .filter(this::isPublicMethodWithSingleArgument)
            .filter(method -> hasAcceptableReturnType(method, clazz))
            .filter(method -> methodNameLooksLikeSetter(method, clazz))
            .collect(Collectors.toList());

        if (log.isTraceEnabled()) {
            val sb = new StringBuilder();
            setters.forEach(e -> sb.append("  " + descSetter(e) + "\n"));
            log.trace("discovered {} setter(s) for class {}.\n{}",
                setters.size(), clazz.getName(), "  " + sb.toString().trim());
        }

        return setters;
    }

    private String descSetter(Method m) {
        return m.getDeclaringClass().getName() + "." + m.getName() +
            "(" + m.getParameters()[0].getType().getName() + ")";
    }

    private boolean isPublicMethodWithSingleArgument(Method method) {
        val modifiers = method.getModifiers();
        return Modifier.isPublic(modifiers) && method.getParameterCount() == 1;
    }

    private boolean hasAcceptableReturnType(Method method, Class<?> clazz) {
        val returnType = method.getReturnType();
        // setters return either void or reference to the same type
        return returnType == void.class || returnType == Void.class || clazz.isAssignableFrom(returnType);
    }

    private boolean methodNameLooksLikeSetter(Method method, Class<?> clazz) {
        val name = method.getName();
        val looksLikeSetter = SETTER_CLEANUP_PATTERN.matcher(name).find();
        if (looksLikeSetter) {
            return true;
        }

        val returnType = method.getReturnType();
        return returnType == clazz;
    }

    /**
     * Tries to find first setter that matches specified property name.
     *
     * @param setters  collection of setters
     * @param propName property name
     * @return setter method if found, otherwise null.
     */
    protected List<Method> findSuitableSetters(@NonNull Collection<Method> setters, @NonNull String propName) {
        val realPropName = Tsc4jImplUtils.toCamelCase(propName);
        if (log.isDebugEnabled()) {
            val settersStr = setters.stream()
                .map(e -> descSetter(e))
                .collect(Collectors.joining(", "));
            log.debug("finding setter for property name '{}', camelcased to '{}' from list: {}",
                propName, realPropName, settersStr);
        }

        return setters.stream()
            .filter(method -> isMatchingSetterMethodName(method, realPropName))
            .collect(Collectors.toList());
    }

    private boolean isMatchingSetterMethodName(Method method, String propName) {
        val setterName = SETTER_CLEANUP_PATTERN.matcher(method.getName()).replaceAll("");
        return setterName.equalsIgnoreCase(propName);
    }

    private Object getValue(Class<?> beanClass,
                            Type parameterType,
                            Class<?> parameterClass,
                            ConfigObject configObj,
                            String configPropName) {
        try {
            val configValue = configObj.get(configPropName);
            return createParametrizedBean(parameterType, configValue, configPropName);
        } catch (ConfigException e) {
            throw e;
        } catch (Throwable t) {
            val msg = String.format("Error creating bean: %s", beanClass.getName());
            throw new ConfigException.BadValue(configObj.origin(), configPropName, msg, t);
        }
    }

    private boolean isEnum(Type t) {
        if (t instanceof ParameterizedType || t instanceof WildcardType) {
            return false;
        }
        return ((Class) t).isEnum();
    }
}
