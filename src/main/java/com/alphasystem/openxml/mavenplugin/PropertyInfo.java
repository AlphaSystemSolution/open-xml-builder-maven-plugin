package com.alphasystem.openxml.mavenplugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author sali
 */
public final class PropertyInfo {

    private final Field field;
    private final Method readMethod;
    private final Method writeMethod;

    public PropertyInfo(Field field, Method readMethod, Method writeMethod) {
        this.field = field;
        this.readMethod = readMethod;
        this.writeMethod = writeMethod;
    }

    public String getFieldName() {
        return field.getName();
    }

    public Field getField() {
        return field;
    }

    public Method getReadMethod() {
        return readMethod;
    }

    public Method getWriteMethod() {
        return writeMethod;
    }
}
