package com.alphasystem.openxml.mavenplugin;

import javax.xml.bind.annotation.XmlTransient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.lang.String.format;
import static java.lang.System.err;
import static org.apache.commons.lang3.StringUtils.capitalize;

/**
 * @author sali
 */
public final class ReflectionUtils {

    public static boolean isAssignableFrom(Class<?> superClass, Class<?> subClass) {
        return superClass.isAssignableFrom(subClass);
    }

    public static String getClassName(Class<?> srcClass) {
        String createMethodNamePrefix = "";
        Class<?> declaringClass = srcClass.getDeclaringClass();
        while (declaringClass != null) {
            createMethodNamePrefix = declaringClass.getSimpleName()
                    + createMethodNamePrefix;
            declaringClass = declaringClass.getDeclaringClass();
        }
        return format("%s%s", createMethodNamePrefix, srcClass.getSimpleName());
    }

    public static Method getReadMethod(Field field) {
        Method method = null;
        String fieldName = field.getName();
        if (fieldName.startsWith("_")) {
            fieldName = fieldName.substring(1);
        }
        fieldName = capitalize(fieldName);
        String methodName = isAssignableFrom(Boolean.class, field.getType()) ? format("is%s", fieldName)
                : format("get%s", fieldName);
        Class<?> declaringClass = field.getDeclaringClass();
        try {
            method = declaringClass.getDeclaredMethod(methodName);
        } catch (Exception e) {
            // due to not following java naming convention go through all getter
            // methods get the required method
            Method[] declaredMethods = declaringClass.getDeclaredMethods();
            for (Method m : declaredMethods) {
                String name = m.getName().toLowerCase();
                if (name.endsWith(fieldName.toLowerCase())) {
                    method = m;
                    break;
                }
            }
        }
        return method;
    }

    public static Method getWriteMethod(Field field) {
        Method method = null;
        String fieldName = field.getName();
        if (fieldName.startsWith("_")) {
            fieldName = fieldName.substring(1);
        }
        fieldName = capitalize(fieldName);
        String methodName = format("set%s", fieldName);
        Class<?> declaringClass = field.getDeclaringClass();
        try {
            method = declaringClass.getDeclaredMethod(methodName, field.getType());
        } catch (Exception e) {
            // ignore
        }
        return method;
    }

    public static Map<String, PropertyInfo> inspectClass(Class<?> srcClass) {
        Map<String, PropertyInfo> propertyInfoMap = new LinkedHashMap<>();
        getProperties(srcClass, propertyInfoMap);
        Class<?> superclass = srcClass.getSuperclass();
        while (superclass != null
                && !superclass.getName().equals(Object.class.getName())) {
            getProperties(superclass, propertyInfoMap);
            superclass = superclass.getSuperclass();
        }
        return propertyInfoMap;
    }

    public static boolean isCollectionType(Field field) {
        return isAssignableFrom(Collection.class, field.getType());
    }

    public static Class<?> getCollectionGenericType(Field field) {
        ParameterizedType genericType = (ParameterizedType) field.getGenericType();
        Class<?> collectionTypeClass;
        try {
            collectionTypeClass = (Class<?>) genericType.getActualTypeArguments()[0];
        } catch (Exception e) {
            err.println(String.format("Collection type not found {%s}", field.getName()));
            return null;
        }
        return collectionTypeClass;
    }

    private static void getProperties(Class<?> srcClass, Map<String, PropertyInfo> propertyInfoMap) {
        Field[] fields = srcClass.getDeclaredFields();
        if (fields != null && fields.length > 0) {
            for (Field field : fields) {
                final PropertyInfo propertyInfo = getPropertyInfo(field);
                if (propertyInfo != null) {
                    propertyInfoMap.put(propertyInfo.getFieldName(), propertyInfo);
                }
            }
        }
    }

    private static PropertyInfo getPropertyInfo(Field field) {
        boolean isTransient = field.getAnnotation(XmlTransient.class) != null;
        int modifiers = field.getModifiers();
        boolean _static = Modifier.isStatic(modifiers);
        boolean _final = Modifier.isFinal(modifiers);
        PropertyInfo propertyInfo = null;
        if (!isTransient && !(_static || _final)) {
            propertyInfo = new PropertyInfo(field, getReadMethod(field), getWriteMethod(field));
        }
        return propertyInfo;
    }

    /**
     * Do not let any one instantiate this class.
     */
    private ReflectionUtils() {
    }

}
