/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.schemas;

import com.google.auto.value.AutoValue;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.beam.sdk.annotations.Internal;
import org.apache.beam.sdk.schemas.annotations.SchemaCaseFormat;
import org.apache.beam.sdk.schemas.annotations.SchemaFieldDescription;
import org.apache.beam.sdk.schemas.annotations.SchemaFieldName;
import org.apache.beam.sdk.schemas.annotations.SchemaFieldNumber;
import org.apache.beam.sdk.schemas.logicaltypes.OneOfType;
import org.apache.beam.sdk.schemas.utils.ReflectUtils;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.vendor.guava.v32_1_2_jre.com.google.common.base.CaseFormat;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Represents type information for a Java type that will be used to infer a Schema type. */
@AutoValue
@SuppressWarnings({
  "nullness", // TODO(https://github.com/apache/beam/issues/20497)
  "rawtypes"
})
@Internal
public abstract class FieldValueTypeInformation implements Serializable {
  /** Optionally returns the field index. */
  public abstract @Nullable Integer getNumber();

  /** Returns the field name. */
  public abstract String getName();

  /** Returns whether the field is nullable. */
  public abstract boolean isNullable();

  /** Returns the field type. */
  public abstract TypeDescriptor<?> getType();

  /** Returns the raw class type. */
  public abstract Class<?> getRawType();

  public abstract @Nullable Field getField();

  public abstract @Nullable Method getMethod();

  public abstract Map<String, FieldValueTypeInformation> getOneOfTypes();

  /** If the field is a container type, returns the element type. */
  public abstract @Nullable FieldValueTypeInformation getElementType();

  /** If the field is a map type, returns the key type. */
  public abstract @Nullable FieldValueTypeInformation getMapKeyType();

  /** If the field is a map type, returns the key type. */
  public abstract @Nullable FieldValueTypeInformation getMapValueType();

  /** If the field has a description, returns the description for the field. */
  public abstract @Nullable String getDescription();

  abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setNumber(@Nullable Integer number);

    public abstract Builder setName(String name);

    public abstract Builder setNullable(boolean nullable);

    public abstract Builder setType(TypeDescriptor<?> type);

    public abstract Builder setRawType(Class<?> type);

    public abstract Builder setField(@Nullable Field field);

    public abstract Builder setMethod(@Nullable Method method);

    public abstract Builder setOneOfTypes(Map<String, FieldValueTypeInformation> oneOfTypes);

    public abstract Builder setElementType(@Nullable FieldValueTypeInformation elementType);

    public abstract Builder setMapKeyType(@Nullable FieldValueTypeInformation mapKeyType);

    public abstract Builder setMapValueType(@Nullable FieldValueTypeInformation mapValueType);

    public abstract Builder setDescription(@Nullable String fieldDescription);

    abstract FieldValueTypeInformation build();
  }

  public static FieldValueTypeInformation forOneOf(
      String name, boolean nullable, Map<String, FieldValueTypeInformation> oneOfTypes) {
    final TypeDescriptor<OneOfType.Value> typeDescriptor = TypeDescriptor.of(OneOfType.Value.class);
    return new AutoValue_FieldValueTypeInformation.Builder()
        .setName(name)
        .setNullable(nullable)
        .setType(typeDescriptor)
        .setRawType(typeDescriptor.getRawType())
        .setField(null)
        .setElementType(null)
        .setMapKeyType(null)
        .setMapValueType(null)
        .setOneOfTypes(oneOfTypes)
        .build();
  }

  public static FieldValueTypeInformation forField(
      Field field, int index, Map<Type, Type> boundTypes) {
    TypeDescriptor<?> type =
        TypeDescriptor.of(ReflectUtils.resolveType(field.getGenericType(), boundTypes));
    return new AutoValue_FieldValueTypeInformation.Builder()
        .setName(getNameOverride(field.getName(), field))
        .setNumber(getNumberOverride(index, field))
        .setNullable(hasNullableAnnotation(field))
        .setType(type)
        .setRawType(type.getRawType())
        .setField(field)
        .setElementType(getIterableComponentType(field, boundTypes))
        .setMapKeyType(getMapKeyType(field, boundTypes))
        .setMapValueType(getMapValueType(field, boundTypes))
        .setOneOfTypes(Collections.emptyMap())
        .setDescription(getFieldDescription(field))
        .build();
  }

  public static <T extends AnnotatedElement & Member> int getNumberOverride(int index, T member) {
    @Nullable SchemaFieldNumber fieldNumber = member.getAnnotation(SchemaFieldNumber.class);
    if (fieldNumber == null) {
      return index;
    }
    return Integer.parseInt(fieldNumber.value());
  }

  public static <T extends AnnotatedElement & Member> String getNameOverride(
      String original, T member) {
    @Nullable SchemaFieldName fieldName = member.getAnnotation(SchemaFieldName.class);
    @Nullable SchemaCaseFormat caseFormatAnnotation = member.getAnnotation(SchemaCaseFormat.class);
    @Nullable
    SchemaCaseFormat classCaseFormatAnnotation =
        member.getDeclaringClass().getAnnotation(SchemaCaseFormat.class);
    if (fieldName != null) {
      if (caseFormatAnnotation != null) {
        throw new RuntimeException(
            String.format(
                "Cannot define both @SchemaFieldName and @SchemaCaseFormat. From member '%s'.",
                member.getName()));
      }
      return fieldName.value();
    } else if (caseFormatAnnotation != null) {
      return CaseFormat.LOWER_CAMEL.to(caseFormatAnnotation.value(), original);
    } else if (classCaseFormatAnnotation != null) {
      return CaseFormat.LOWER_CAMEL.to(classCaseFormatAnnotation.value(), original);
    } else {
      return original;
    }
  }

  public static <T extends AnnotatedElement & Member> @Nullable String getFieldDescription(
      T member) {
    @Nullable
    SchemaFieldDescription fieldDescription = member.getAnnotation(SchemaFieldDescription.class);
    if (fieldDescription == null) {
      return null;
    }
    return fieldDescription.value();
  }

  public static FieldValueTypeInformation forGetter(
      Method method, int index, Map<Type, Type> boundTypes) {
    String name;
    if (method.getName().startsWith("get")) {
      name = ReflectUtils.stripPrefix(method.getName(), "get");
    } else if (method.getName().startsWith("is")) {
      name = ReflectUtils.stripPrefix(method.getName(), "is");
    } else {
      throw new RuntimeException("Getter has wrong prefix " + method.getName());
    }

    TypeDescriptor<?> type =
        TypeDescriptor.of(ReflectUtils.resolveType(method.getGenericReturnType(), boundTypes));
    boolean nullable = hasNullableReturnType(method);
    return new AutoValue_FieldValueTypeInformation.Builder()
        .setName(getNameOverride(name, method))
        .setNumber(getNumberOverride(index, method))
        .setNullable(nullable)
        .setType(type)
        .setRawType(type.getRawType())
        .setMethod(method)
        .setElementType(getIterableComponentType(type, boundTypes))
        .setMapKeyType(getMapKeyType(type, boundTypes))
        .setMapValueType(getMapValueType(type, boundTypes))
        .setOneOfTypes(Collections.emptyMap())
        .setDescription(getFieldDescription(method))
        .build();
  }

  private static boolean hasNullableAnnotation(Field field) {
    Stream<Annotation> annotations =
        Stream.concat(
            Stream.of(field.getAnnotations()),
            Stream.of(field.getAnnotatedType().getAnnotations()));

    return annotations.anyMatch(FieldValueTypeInformation::isNullableAnnotation);
  }

  /**
   * If the method or its return type are annotated with any variant of Nullable, then the schema
   * field is nullable.
   */
  private static boolean hasNullableReturnType(Method method) {
    Stream<Annotation> annotations =
        Stream.concat(
            Stream.of(method.getAnnotations()),
            Stream.of(method.getAnnotatedReturnType().getAnnotations()));

    return annotations.anyMatch(FieldValueTypeInformation::isNullableAnnotation);
  }

  private static boolean hasSingleNullableParameter(Method method) {
    if (method.getParameterCount() != 1) {
      throw new RuntimeException(
          "Setter methods should take a single argument " + method.getName());
    }

    Stream<Annotation> annotations =
        Stream.concat(
            Arrays.stream(method.getAnnotatedParameterTypes()[0].getAnnotations()),
            Arrays.stream(method.getParameterAnnotations()[0]));

    return annotations.anyMatch(FieldValueTypeInformation::isNullableAnnotation);
  }

  /** Try to accept any Nullable annotation. */
  private static boolean isNullableAnnotation(Annotation annotation) {
    return annotation.annotationType().getSimpleName().equals("Nullable");
  }

  public static FieldValueTypeInformation forSetter(
      Method method, Map<Type, Type> boundParameters) {
    return forSetter(method, "set", boundParameters);
  }

  public static FieldValueTypeInformation forSetter(
      Method method, String setterPrefix, Map<Type, Type> boundTypes) {
    String name;
    if (method.getName().startsWith(setterPrefix)) {
      name = ReflectUtils.stripPrefix(method.getName(), setterPrefix);
    } else {
      throw new RuntimeException("Setter has wrong prefix " + method.getName());
    }

    TypeDescriptor<?> type =
        TypeDescriptor.of(
            ReflectUtils.resolveType(method.getGenericParameterTypes()[0], boundTypes));
    boolean nullable = hasSingleNullableParameter(method);
    return new AutoValue_FieldValueTypeInformation.Builder()
        .setName(name)
        .setNullable(nullable)
        .setType(type)
        .setRawType(type.getRawType())
        .setMethod(method)
        .setElementType(getIterableComponentType(type, boundTypes))
        .setMapKeyType(getMapKeyType(type, boundTypes))
        .setMapValueType(getMapValueType(type, boundTypes))
        .setOneOfTypes(Collections.emptyMap())
        .build();
  }

  public FieldValueTypeInformation withName(String name) {
    return toBuilder().setName(name).build();
  }

  private static FieldValueTypeInformation getIterableComponentType(
      Field field, Map<Type, Type> boundTypes) {
    return getIterableComponentType(TypeDescriptor.of(field.getGenericType()), boundTypes);
  }

  static @Nullable FieldValueTypeInformation getIterableComponentType(
      TypeDescriptor<?> valueType, Map<Type, Type> boundTypes) {
    // TODO: Figure out nullable elements.
    TypeDescriptor<?> componentType = ReflectUtils.getIterableComponentType(valueType, boundTypes);
    if (componentType == null) {
      return null;
    }

    return new AutoValue_FieldValueTypeInformation.Builder()
        .setName("")
        .setNullable(false)
        .setType(componentType)
        .setRawType(componentType.getRawType())
        .setElementType(getIterableComponentType(componentType, boundTypes))
        .setMapKeyType(getMapKeyType(componentType, boundTypes))
        .setMapValueType(getMapValueType(componentType, boundTypes))
        .setOneOfTypes(Collections.emptyMap())
        .build();
  }

  // If the Field is a map type, returns the key type, otherwise returns a null reference.

  private static @Nullable FieldValueTypeInformation getMapKeyType(
      Field field, Map<Type, Type> boundTypes) {
    return getMapKeyType(TypeDescriptor.of(field.getGenericType()), boundTypes);
  }

  private static @Nullable FieldValueTypeInformation getMapKeyType(
      TypeDescriptor<?> typeDescriptor, Map<Type, Type> boundTypes) {
    return getMapType(typeDescriptor, 0, boundTypes);
  }

  // If the Field is a map type, returns the value type, otherwise returns a null reference.

  private static @Nullable FieldValueTypeInformation getMapValueType(
      Field field, Map<Type, Type> boundTypes) {
    return getMapType(TypeDescriptor.of(field.getGenericType()), 1, boundTypes);
  }

  private static @Nullable FieldValueTypeInformation getMapValueType(
      TypeDescriptor<?> typeDescriptor, Map<Type, Type> boundTypes) {
    return getMapType(typeDescriptor, 1, boundTypes);
  }

  // If the Field is a map type, returns the key or value type (0 is key type, 1 is value).
  // Otherwise returns a null reference.
  @SuppressWarnings("unchecked")
  private static @Nullable FieldValueTypeInformation getMapType(
      TypeDescriptor<?> valueType, int index, Map<Type, Type> boundTypes) {
    TypeDescriptor mapType = ReflectUtils.getMapType(valueType, index, boundTypes);
    if (mapType == null) {
      return null;
    }
    return new AutoValue_FieldValueTypeInformation.Builder()
        .setName("")
        .setNullable(false)
        .setType(mapType)
        .setRawType(mapType.getRawType())
        .setElementType(getIterableComponentType(mapType, boundTypes))
        .setMapKeyType(getMapKeyType(mapType, boundTypes))
        .setMapValueType(getMapValueType(mapType, boundTypes))
        .setOneOfTypes(Collections.emptyMap())
        .build();
  }
}
