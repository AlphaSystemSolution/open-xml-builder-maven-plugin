/**
 *
 */
package com.alphasystem.openxml.mavenplugin;

import com.sun.codemodel.*;
import org.apache.commons.io.FileUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.wml.*;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.sun.codemodel.ClassType.CLASS;
import static com.sun.codemodel.JExpr.*;
import static com.sun.codemodel.JMod.*;
import static java.lang.String.format;
import static java.util.Collections.addAll;
import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * @author sali
 */
public class FluentApiGenerator {

    public static final String GET_OBJECT_METHOD_NAME = "getObject";

    public static final String SET_OBJECT_METHOD_NAME = "setObject";

    public static final String PARAM_NAME = "value";

    public static final String FIELD_NAME = "object";

    public static final JFieldRef FIELD_TYPE_REF = ref(FIELD_NAME);

    private static final String OBJECT_FACTORY_FIELD_NAME = "OBJECT_FACTORY";

    public static final JFieldRef OBJECT_FACTORY_REF = ref(OBJECT_FACTORY_FIELD_NAME);

    private static final String BASE_PACKAGE_NAME = "com.alphasystem.openxml.builder";

    private static final String BUILDER_PACKAGE_NAME = format("%s.wml", BASE_PACKAGE_NAME);

    private static final String SUPER_CALSS_FQN = format("%s.OpenXmlBuilder", BASE_PACKAGE_NAME);

    private static final String BUILDER_FACTORY_CLASS_NAME = "WmlBuilderFactory";

    private static final String BUILDER_FACTORY_CLASS_FQN = format("%s.%s", BUILDER_PACKAGE_NAME, BUILDER_FACTORY_CLASS_NAME);

    private JCodeModel codeModel;
    private Class<?>[] srcClasses;
    private List<String> ignoreMethods = new ArrayList<String>();
    private JDefinedClass openXmlBuilderClass;
    private JDefinedClass builderFactoryClass;

    public FluentApiGenerator(JCodeModel codeModel, Class<?>... srcClasses) {
        this.codeModel = codeModel;
        this.srcClasses = srcClasses;
    }

    public static String getBuilderClassFqn(Class<?> srcClass) {
        return format("%s.%sBuilder", BUILDER_PACKAGE_NAME, getClassName(srcClass));
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

    public static Method getGetterMethod(Field field) {
        Method method = null;
        String fieldName = capitalize(field.getName());
        String methodName = format("get%s", fieldName);
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
            if (method == null) {
                System.err.println(format("%s:%s:%s:%s",
                        e.getClass().getName(), fieldName, methodName,
                        declaringClass.getName()));
            }
        }
        return method;
    }

    public static Method getSetterMethod(Field field) {
        Method method = null;
        String fieldName = capitalize(field.getName());
        String methodName = format("set%s", fieldName);
        Class<?> declaringClass = field.getDeclaringClass();
        try {
            method = declaringClass.getDeclaredMethod(methodName,
                    field.getType());
        } catch (Exception e) {
            System.err.println(format("%s:%s:%s:%s", e.getClass().getName(),
                    fieldName, methodName, declaringClass.getName()));
        }
        return method;
    }

    public static void main(String[] args) {
        JCodeModel codeModel = new JCodeModel();
        FluentApiGenerator apiGenerator = new FluentApiGenerator(codeModel,
                P.class);
        apiGenerator.generate();
        File destDir = new File("test");
        try {
            FileUtils.deleteDirectory(destDir);
            destDir.mkdirs();
        } catch (IOException ex) {
        }
        try {
            codeModel.build(destDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static JClass parseClass(JCodeModel codeModel, String name) {
        return (JClass) parseType(codeModel, name);
    }

    public static JType parseType(JCodeModel codeModel, String name) {
        JType type = null;
        try {
            type = codeModel.parseType(name);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return type;
    }

    private JFieldVar addBuilderFactoryStaticField(Class<?> returnTypeClass,
                                                   String fieldName, String builderMethodName, String valueMethodName,
                                                   JExpression arg) {
        JType returnType = parseType(codeModel, returnTypeClass.getName());
        return builderFactoryClass.field(PUBLIC | STATIC | FINAL, returnType,
                fieldName, invoke(builderMethodName).invoke(valueMethodName)
                        .arg(arg).invoke(GET_OBJECT_METHOD_NAME));
    }

    private void addJcConstants(JcEnumeration enumConstant) {
        JClass jcEnumerationClass = parseClass(codeModel,
                JcEnumeration.class.getName());
        String name = enumConstant.name();
        String fieldName = format("JC_%s", name);
        JFieldVar field = addBuilderFactoryStaticField(Jc.class, fieldName,
                "getJcBuilder", "withVal", jcEnumerationClass.staticRef(name));
        field.javadoc().add(
                format("Constant for %s.%s", jcEnumerationClass.name(), name));
    }

    private JMethod addMethod(int mods, JType type, String methodName,
                              JDefinedClass parentClass, Class<? extends Annotation>[] annotations) {
        JMethod method = parentClass.method(mods, type, methodName);
        if (annotations != null && annotations.length > 0) {
            for (Class<? extends Annotation> c : annotations) {
                method.annotate(c);
            }
        }
        return method;
    }

    public void generate() {
        generateOpenXmlBuilderClass();
        generateOpenXmlBuilderFactoryClass();
        for (Class<?> srcClass : srcClasses) {
            generate(srcClass);
        }
    }

    protected void generate(Class<?> srcClass) {
        ClassGenerator classGenerator = new ClassGenerator(codeModel, srcClass, openXmlBuilderClass.fullName(), builderFactoryClass);
        classGenerator.generate();
    }

    private void generateOpenXmlBuilderClass() {
        try {
            openXmlBuilderClass = codeModel._class(PUBLIC | ABSTRACT,
                    SUPER_CALSS_FQN, CLASS);
            openXmlBuilderClass.generify("T");
            openXmlBuilderClass.field(PUBLIC | STATIC | FINAL, parseType(codeModel, ObjectFactory.class.getName()),
                    OBJECT_FACTORY_FIELD_NAME, parseClass(codeModel, Context.class.getName()).staticInvoke("getWmlObjectFactory"));
            final JType t = parseType(codeModel, "T");
            addMethod(PUBLIC | ABSTRACT, t, GET_OBJECT_METHOD_NAME, openXmlBuilderClass, null);
            final JMethod setMethod = addMethod(PUBLIC | ABSTRACT, parseType(codeModel, "void"), SET_OBJECT_METHOD_NAME,
                    openXmlBuilderClass, null);
            setMethod.param(t, FIELD_NAME);
        } catch (JClassAlreadyExistsException e) {
        }
    }

    private void generateOpenXmlBuilderFactoryClass() {
        try {
            builderFactoryClass = codeModel._class(PUBLIC,
                    BUILDER_FACTORY_CLASS_FQN, CLASS);

            String withValMethod = "withVal";
            addBuilderFactoryStaticField(BooleanDefaultTrue.class,
                    "BOOLEAN_DEFAULT_TRUE_TRUE",
                    "getBooleanDefaultTrueBuilder", withValMethod, TRUE);
            addBuilderFactoryStaticField(BooleanDefaultTrue.class,
                    "BOOLEAN_DEFAULT_TRUE_FALSE",
                    "getBooleanDefaultTrueBuilder", withValMethod, FALSE);
            addBuilderFactoryStaticField(BooleanDefaultFalse.class,
                    "BOOLEAN_DEFAULT_FALSE_TRUE",
                    "getBooleanDefaultFalseBuilder", withValMethod, TRUE);
            addBuilderFactoryStaticField(BooleanDefaultFalse.class,
                    "BOOLEAN_DEFAULT_FALSE_FALSE",
                    "getBooleanDefaultFalseBuilder", withValMethod, FALSE);

            for (JcEnumeration jcEnum : JcEnumeration.values()) {
                addJcConstants(jcEnum);
            }

            // private constructor
            builderFactoryClass.constructor(PRIVATE).javadoc()
                    .add("Do not let anyone instantiate this class.");
        } catch (JClassAlreadyExistsException e) {
        }
    }

    public void setIgnoreMethods(String... ignoreMethods) {
        addAll(this.ignoreMethods, ignoreMethods);
    }
}
