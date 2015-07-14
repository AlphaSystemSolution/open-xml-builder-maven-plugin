/**
 *
 */
package com.alphasystem.openxml.mavenplugin;

import com.sun.codemodel.*;

import javax.xml.bind.annotation.XmlTransient;
import javax.xml.datatype.XMLGregorianCalendar;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigInteger;
import java.util.*;

import static com.alphasystem.openxml.mavenplugin.FluentApiGenerator.*;
import static com.sun.codemodel.ClassType.CLASS;
import static com.sun.codemodel.JExpr.*;
import static com.sun.codemodel.JMod.*;
import static java.lang.String.format;
import static java.lang.System.err;

/**
 * @author sali
 */
public class ClassGenerator {

    private final JCodeModel codeModel;
    private final Class<?> srcClass;
    private final String superClassName;
    private final JDefinedClass builderFactoryClass;
    private JMethod buildObjectMethod;
    private JExpression buildObjectMethodReturnStatatement;
    private List<Field> javaObjectFields;
    private List<Field> openXmlObjectFields;
    private List<Field> collectionTypeObjectFields;

    public ClassGenerator(JCodeModel codeModel, Class<?> srcClass,
                          String superClassName, JDefinedClass builderFactoryClass) {
        this.codeModel = codeModel;
        this.srcClass = srcClass;
        this.superClassName = superClassName;
        this.builderFactoryClass = builderFactoryClass;
        javaObjectFields = new ArrayList<Field>();
        openXmlObjectFields = new ArrayList<Field>();
        collectionTypeObjectFields = new ArrayList<Field>();
    }

    private static void addConstructor(Class<?> srcClass, JDefinedClass _class) {
        JMethod constructor = _class.constructor(PUBLIC);
        constructor.javadoc().add("Initialize the underlying object.");
        JBlock constructorBody = constructor.body();
        String createMethodName = getCreateMethodName(srcClass);
        constructorBody.assign(FIELD_TYPE_REF,
                invoke(OBJECT_FACTORY_REF, createMethodName));
    }

    private static String getCreateMethodName(Class<?> srcClass) {
        String createMethodNamePrefix = "";
        Class<?> declaringClass = srcClass.getDeclaringClass();
        while (declaringClass != null) {
            createMethodNamePrefix = declaringClass.getSimpleName()
                    + createMethodNamePrefix;
            declaringClass = declaringClass.getDeclaringClass();
        }
        return format("create%s", getClassName(srcClass));
    }

    private static List<Field> getFields(Class<?> srcClass) {
        List<Field> fields = new ArrayList<Field>();
        getFields(srcClass, fields);
        Class<?> superclass = srcClass.getSuperclass();
        while (superclass != null
                && !superclass.getName().equals(Object.class.getName())) {
            getFields(superclass, fields);
            superclass = superclass.getSuperclass();
        }
        return fields;
    }

    private static void getFields(Class<?> srcClass, List<Field> fieldList) {
        Field[] fields = srcClass.getDeclaredFields();
        if (fields != null && fields.length > 0) {
            for (Field field : fields) {
                boolean isTransient = field.getAnnotation(XmlTransient.class) != null;
                int modifiers = field.getModifiers();
                boolean _static = Modifier.isStatic(modifiers);
                boolean _final = Modifier.isFinal(modifiers);
                if (!isTransient && !(_static || _final)) {
                    fieldList.add(field);
                }
            }
        }
    }

    private static boolean isAssignableFrom(Class<?> superClass,
                                            Class<?> subClass) {
        return superClass.isAssignableFrom(subClass);
    }

    private void addBigIntegerConvienceMethod(String withMethodName,
                                              Method setterMthod, JDefinedClass _class) {
        addConvienceMethod(withMethodName, setterMthod, String.class,
                BigInteger.class, _class);
    }

    private void addBuilderGetMethod(JClass _class, String builderGetMethodName) {
        JType returnType = parseType(codeModel, _class.fullName());
        JMethod method = addMethod(PUBLIC | STATIC, returnType,
                builderGetMethodName, builderFactoryClass, null);
        JBlock body = method.body();
        body._return(_new(returnType));
    }

    private void addCollectionDelegateMethod(Class<?> srcClass,
                                             JDefinedClass _class, String srcMethodName, String methodName,
                                             String paramName, String localVariable, Class<?> collectionTypeClass) {
        try {
            JType thisType = parseType(codeModel, _class.name());
            Method getContentMethod = srcClass.getMethod(srcMethodName);
            JMethod method = addMethod(PUBLIC, thisType, methodName, _class,
                    null);
            method.varParam(collectionTypeClass, paramName);
            JBlock body = method.body();
            JFieldRef paramRef = ref(paramName);
            JConditional _if = body._if(paramRef.ne(_null()));
            JBlock _then = _if._then();
            JClass narrow = parseType(codeModel, List.class.getName()).boxify()
                    .narrow(collectionTypeClass);
            _then.decl(narrow, localVariable).init(
                    invoke(FIELD_TYPE_REF, getContentMethod.getName()));
            _then.staticInvoke(
                    parseClass(codeModel, Collections.class.getName()),
                    "addAll").arg(ref(localVariable)).arg(paramRef);
            body._return(_this());
        } catch (Exception e) {
        }
    }

    private void addConvienceMethod(String withMethodName, Method setterMthod,
                                    Class<?> setterMethodParamType, Class<?> valueClass,
                                    JDefinedClass _class) {
        // method.param(setterMethodParamType, PARAM_NAME);
        JType thisType = parseType(codeModel, _class.name());
        JMethod method = addMethod(PUBLIC, thisType, withMethodName, _class,
                null);
        method.param(setterMethodParamType, PARAM_NAME);

        String setterMethodName = addJavaDocComments(method, setterMthod);

        JBlock block = method.body();
        JFieldRef paramRef = JExpr.ref(PARAM_NAME);
        JConditional ifBlock = block._if(paramRef.ne(JExpr._null()));
        JBlock ifBodyBlock = ifBlock._then();
        JInvocation initValue = JExpr._new(
                parseType(codeModel, valueClass.getName())).arg(paramRef);
        ifBodyBlock.invoke(FIELD_TYPE_REF, setterMethodName).arg(initValue);
        block._return(_this());
    }

    @SuppressWarnings("unchecked")
    private void addGetObjectMethod(JDefinedClass _class, Class<?> srcClass) {
        JMethod method = addMethod(PUBLIC,
                parseType(codeModel, srcClass.getName()),
                GET_OBJECT_METHOD_NAME, _class, new Class[]{Override.class});
        JBlock block = method.body();
        block._return(FIELD_TYPE_REF);
    }

    private String addJavaDocComments(JMethod method, Method setterMthod) {
        JDocComment javadoc = method.javadoc();
        String setterMethodName = setterMthod.getName();
        javadoc.add(format("Calls <code>%s</code> method.", setterMethodName));
        javadoc.addParam("value");
        javadoc.addReturn().add("reference to this");
        return setterMethodName;
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

    private void addMethods(JDefinedClass _class) {
        if (!javaObjectFields.isEmpty()) {
            for (Field field : javaObjectFields) {
                processField(field, srcClass, _class);
            }
        }
        if (!openXmlObjectFields.isEmpty()) {
            for (Field field : openXmlObjectFields) {
                processField(field, srcClass, _class);
            }
        }
        if (!collectionTypeObjectFields.isEmpty()) {
            for (Field field : collectionTypeObjectFields) {
                processCollectionField(field, srcClass, _class);
            }
        }
    }

    private void addToBuildObjectMethod(Field field, String methodName) {
        String fieldName = field.getName();
        JFieldRef paramRef = ref(fieldName);
        if (buildObjectMethodReturnStatatement == null) {
            buildObjectMethodReturnStatatement = invoke(methodName).arg(
                    paramRef);
        } else {
            buildObjectMethodReturnStatatement = buildObjectMethodReturnStatatement
                    .invoke(methodName).arg(paramRef);
        }
    }

    private void finalizeBuildObjectMethod() {
        if (buildObjectMethod == null) {
            return;
        }
        JBlock block = buildObjectMethod.body();

        if (buildObjectMethodReturnStatatement == null) {
            buildObjectMethodReturnStatatement = _this();
        }
        block._return(buildObjectMethodReturnStatatement);
    }

    public void generate() {
        JDefinedClass _class = null;
        String builderClassFqn = getBuilderClassFqn(srcClass);
        try {
            _class = codeModel._class(PUBLIC, builderClassFqn, CLASS);
            _class.javadoc().add(
                    format("Fluent API builder for <code>%s</code>.",
                            srcClass.getName()));

            JClass superClass = parseClass(codeModel, superClassName).narrow(
                    srcClass);
            // extends with "OpenXmlBuilder"
            _class._extends(superClass);

            // add the field
            _class.field(PRIVATE, srcClass, FIELD_NAME);

            // add Constructor
            addConstructor(srcClass, _class);

            // override abstract method
            addGetObjectMethod(_class, srcClass);

            // initBuildObjectMethod(_class);

            populateFields(_class);

            // add fluent API methods
            addMethods(_class);

            finalizeBuildObjectMethod();

            String builderGetMethodName = format("get%s", _class.name());
            // add "get" method in builder class
            addBuilderGetMethod(_class, builderGetMethodName);
        } catch (JClassAlreadyExistsException e) {
        }
    }

    void initBuildObjectMethod(JDefinedClass _class) {
        JType thisType = parseType(codeModel, _class.name());
        buildObjectMethod = addMethod(PUBLIC, thisType, "buildObject", _class,
                null);
    }

    private void initMethod(JMethod method, Method setterMthod,
                            JDefinedClass _class) {
        Class<?> setterMethodParamType = setterMthod.getParameterTypes()[0];
        if (setterMethodParamType.isEnum()) {
            System.out.println(format(
                    "<<<<<<<<<<<<<<<<<<<<<<< %s:%s:%s >>>>>>>>>>>>>>>>>>>>>>>",
                    _class.name(), setterMthod.getName(),
                    setterMethodParamType.getName()));
        } else if (setterMethodParamType.getPackage().getName()
                .equals("org.docx4j.wml")) {
            ClassGenerator generator = new ClassGenerator(codeModel,
                    setterMethodParamType, superClassName, builderFactoryClass);
            generator.generate();
        }
        String name = setterMethodParamType.getName();
        if (name.equals(BigInteger.class.getName())) {
            addBigIntegerConvienceMethod(method.name(), setterMthod, _class);
        }
        method.param(setterMethodParamType, PARAM_NAME);

        String setterMethodName = addJavaDocComments(method, setterMthod);

        JBlock block = method.body();
        JFieldRef paramRef = ref(PARAM_NAME);
        JConditional ifBlock = block._if(paramRef.ne(_null()));
        JBlock ifBodyBlock = ifBlock._then();
        ifBodyBlock.invoke(FIELD_TYPE_REF, setterMethodName).arg(paramRef);
        block._return(JExpr._this());
    }

    private void populateFields(JDefinedClass _class) {
        List<Field> fields = getFields(srcClass);
        if (fields != null && !fields.isEmpty()) {
            for (Field field : fields) {
                Class<?> type = field.getType();
                if (isAssignableFrom(Collection.class, type)) {
                    collectionTypeObjectFields.add(field);
                } else if (isAssignableFrom(String.class, type)
                        || type.isPrimitive()
                        || isAssignableFrom(Number.class, type)
                        || isAssignableFrom(Boolean.class, type)
                        || isAssignableFrom(Date.class, type)
                        || isAssignableFrom(XMLGregorianCalendar.class, type)) {
                    javaObjectFields.add(field);
                } else {
                    openXmlObjectFields.add(field);
                }
            }
        }
    }

    private void processCollectionField(Field field, Class<?> srcClass,
                                        JDefinedClass _class) {
        Method srcMethod = getGetterMethod(field);
        if (srcMethod == null) {
            return;
        }
        String srcMethodName = srcMethod.getName();
        String fieldName = field.getName();
        String methodName = srcMethodName.replaceFirst("^get", "add");
        String paramName = format("%ss", fieldName);
        ParameterizedType genericType = (ParameterizedType) field
                .getGenericType();
        Class<?> collectionTypeClass = null;
        try {
            collectionTypeClass = (Class<?>) genericType
                    .getActualTypeArguments()[0];
        } catch (Exception e) {
            err.println(srcClass.getName() + " : " + fieldName);
            return;
        }
        addCollectionDelegateMethod(field.getDeclaringClass(), _class,
                srcMethodName, methodName, paramName, fieldName,
                collectionTypeClass);
    }

    private void processField(Field field, Class<?> srcClass,
                              JDefinedClass _class) {
        JType thisType = parseType(codeModel, _class.name());
        Method srcMethod = getSetterMethod(field);
        if (srcMethod == null) {
            return;
        }
        String srcMethodName = srcMethod.getName();
        String withMethodName = srcMethodName.replaceFirst("^set", "with");
        JMethod method = addMethod(PUBLIC, thisType, withMethodName, _class,
                null);
        initMethod(method, srcMethod, _class);

        if (buildObjectMethod != null) {
            buildObjectMethod.param(field.getType(), field.getName());
            addToBuildObjectMethod(field, withMethodName);
        }
    }

}
