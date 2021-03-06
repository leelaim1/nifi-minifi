/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.minifi.commons.schema.common;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class BaseSchema {
    public static final String IT_WAS_NOT_FOUND_AND_IT_IS_REQUIRED = "it was not found and it is required";
    protected final Supplier<Map<String, Object>> mapSupplier;

    public BaseSchema() {
        this(LinkedHashMap::new);
    }

    public BaseSchema(Supplier<Map<String, Object>> mapSupplier) {
        this.mapSupplier = mapSupplier;
    }

    /******* Validation Issue helper methods *******/
    public List<String> validationIssues = new LinkedList<>();

    public boolean isValid() {
        return validationIssues.isEmpty();
    }

    public List<String> getValidationIssues() {
        return validationIssues;
    }

    public String getValidationIssuesAsString() {
        StringBuilder stringBuilder = new StringBuilder();
        boolean first = true;
        for (String validationIssue : validationIssues) {
            if (!first) {
                stringBuilder.append(", ");
            }
            stringBuilder.append("[");
            stringBuilder.append(validationIssue);
            stringBuilder.append("]");
            first = false;
        }
        return stringBuilder.toString();
    }

    public <T> T getAndValidateNotNull(Supplier<T> supplier, String keyName, String wrapperName) {
        return getAndValidate(supplier, t -> t != null, keyName, wrapperName, IT_WAS_NOT_FOUND_AND_IT_IS_REQUIRED);
    }

    public <T> T getAndValidate(Supplier<T> supplier, Predicate<T> predicate, String keyName, String wrapperName, String reason) {
        T result = supplier.get();
        if (!predicate.test(result)) {
            addValidationIssue(keyName, wrapperName, reason);
        }
        return result;
    }

    public void addValidationIssue(String keyName, String wrapperName, String reason) {
        validationIssues.add("'" + keyName + "' in section '" + wrapperName + "' because " + reason);
    }

    public void addIssuesIfNotNull(BaseSchema baseSchema) {
        if (baseSchema != null) {
            validationIssues.addAll(baseSchema.getValidationIssues());
        }
    }

    /******* Value Access/Interpretation helper methods *******/
    public <T> T getOptionalKeyAsType(Map valueMap, String key, Class<T> targetClass, String wrapperName, T defaultValue) {
        return getKeyAsType(valueMap, key, targetClass, wrapperName, false, defaultValue);
    }

    public <T> T getRequiredKeyAsType(Map valueMap, String key, Class<T> targetClass, String wrapperName) {
        return getKeyAsType(valueMap, key, targetClass, wrapperName, true, null);
    }

    <T> T getKeyAsType(Map valueMap, String key, Class<T> targetClass, String wrapperName, boolean required, T defaultValue) {
        Object value = valueMap.get(key);
        if (value == null) {
            if (defaultValue != null) {
                return defaultValue;
            } else if(required) {
                addValidationIssue(key, wrapperName, IT_WAS_NOT_FOUND_AND_IT_IS_REQUIRED);
            }
        } else {
            if (targetClass.isInstance(value)) {
                return (T) value;
            } else {
                addValidationIssue(key, wrapperName, "it is found but could not be parsed as a " + targetClass.getSimpleName());
            }
        }
        return null;
    }


    public <T> T getMapAsType(Map valueMap, String key, Class<T> targetClass, String wrapperName, boolean required) {
        Object obj = valueMap.get(key);
        return interpretValueAsType(obj, key, targetClass, wrapperName, required, true);
    }

    public <T> T getMapAsType(Map valueMap, String key, Class targetClass, String wrapperName, boolean required, boolean instantiateIfNull) {
        Object obj = valueMap.get(key);
        return interpretValueAsType(obj, key, targetClass, wrapperName, required, instantiateIfNull);
    }

    public <T> void transformListToType(List<T> list, String simpleListType, Class<T> targetClass, String wrapperName){
        for (int i = 0; i < list.size(); i++) {
            T obj = interpretValueAsType(list.get(i), simpleListType + " number " + i, targetClass, wrapperName, false, false);
            if (obj != null) {
                list.set(i, obj);
            }
        }
    }

    private <T> T interpretValueAsType(Object obj, String key, Class targetClass, String wrapperName, boolean required, boolean instantiateIfNull) {
        if (obj == null) {
            if (required){
                addValidationIssue(key, wrapperName, "it is a required property but was not found");
            } else {
                if(instantiateIfNull) {
                    try {
                        return (T) targetClass.newInstance();
                    } catch (InstantiationException | IllegalAccessException e) {
                        addValidationIssue(key, wrapperName, "no value was given, and it is supposed to be created with default values as a default, and when attempting to create it the following " +
                                "exception was thrown:" + e.getMessage());
                    }
                }
            }
        } else if (obj instanceof Map) {
            Constructor<?> constructor;
            try {
                constructor = targetClass.getConstructor(Map.class);
                return (T) constructor.newInstance((Map) obj);
            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                addValidationIssue(key, wrapperName, "it is found as a map and when attempting to interpret it the following exception was thrown:" + e.getMessage());
            }
        } else {
            try {
                return (T) obj;
            } catch (ClassCastException e) {
                addValidationIssue(key, wrapperName, "it is found but could not be parsed as a map");
            }
        }
        return null;
    }

    public abstract Map<String, Object> toMap();

    public static void putIfNotNull(Map valueMap, String key, BaseSchema schema) {
        if (schema != null) {
            valueMap.put(key, schema.toMap());
        }
    }

    public static void putListIfNotNull(Map valueMap, String key, List<? extends BaseSchema> list) {
        if (list != null) {
            valueMap.put(key, list.stream().map(BaseSchema::toMap).collect(Collectors.toList()));
        }
    }

    public static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    public static <T> Set<T> nullToEmpty(Set<T> set) {
        return set == null ? Collections.emptySet() : set;
    }

    public static <K, V> Map<K, V> nullToEmpty(Map<K, V> map) {
        return map == null ? Collections.emptyMap() : map;
    }
}
