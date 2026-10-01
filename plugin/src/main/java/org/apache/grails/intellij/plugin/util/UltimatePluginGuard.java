/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.intellij.plugin.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a public static field of a class that may not be on this plugin's class path, for the two
 * places where a cosmetic value (an icon, a text-attributes key) comes from an Ultimate-only plugin
 * but the code that uses it has to stay in the main jar. Everything else that needs an Ultimate
 * plugin lives in a content module with its own {@code <dependencies>} and never needs a guard.
 *
 * <p>The lookup goes through this plugin's class loader, so the owning plugin must be reachable
 * from it: either as a hard dependency or through a {@code <depends optional="true">} entry (see
 * {@code grails-el-integration.xml}). A class that is installed but not declared is just as absent
 * as one that is not installed.
 */
public final class UltimatePluginGuard {

  private static final Logger LOG = Logger.getInstance(UltimatePluginGuard.class);

  private static final Map<String, Object> STATIC_FIELD_CACHE = new ConcurrentHashMap<>();

  private UltimatePluginGuard() {
  }

  /**
   * Returns the value of the public static field {@code fieldName} on {@code className}, or
   * {@code fallback} when the class cannot be loaded, has no such field, or the field is not
   * readable. Successful reads are cached; a failed read is logged once at debug level and
   * repeated on the next call, which only happens on an IDE without the owning plugin.
   */
  public static @Nullable <T> T staticFieldValue(@NotNull String className, @NotNull String fieldName, @Nullable T fallback) {
    String key = className + '#' + fieldName;
    Object cached = STATIC_FIELD_CACHE.get(key);
    if (cached != null) {
      @SuppressWarnings("unchecked")
      T value = (T)cached;
      return value;
    }
    try {
      Object value = Class.forName(className).getField(fieldName).get(null);
      if (value == null) return fallback;
      STATIC_FIELD_CACHE.put(key, value);
      @SuppressWarnings("unchecked")
      T typed = (T)value;
      return typed;
    }
    catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException | LinkageError e) {
      LOG.debug("Cannot read static field " + key + ", using the fallback", e);
      return fallback;
    }
  }
}
