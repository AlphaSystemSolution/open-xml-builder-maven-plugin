/**
 *
 */
package com.alphasystem.openxml.mavenplugin;

import com.sun.codemodel.*;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.CTSdtRow;
import org.docx4j.wml.SdtBlock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.alphasystem.openxml.mavenplugin.CodeModelUtil.*;
import static com.alphasystem.openxml.mavenplugin.FluentApiGenerator.*;
import static com.alphasystem.openxml.mavenplugin.ReflectionUtils.*;
import static com.sun.codemodel.ClassType.CLASS;
import static com.sun.codemodel.JExpr.*;
import static com.sun.codemodel.JMod.*;
import static java.lang.String.format;
import static java.lang.System.err;
import static org.apache.commons.lang3.text.WordUtils.capitalize;
import static org.apache.commons.lang3.text.WordUtils.uncapitalize;

/**
 * @author sali
 */
public class ClassGenerator {

    private static String getCreateMethodName(Class<?> srcClass) {
        String createMethodNamePrefix = "";
        Class<?> declaringClass = srcClass.getDeclaringClass();
        while (declaringClass != null) {
            createMethodNamePrefix = declaringClass.getSimpleName() + createMethodNamePrefix;
            declaringClass = declaringClass.getDeclaringClass();
        }
        return format("create%s%s", createMethodNamePrefix, srcClass.getSimpleName());
    }

    private static String getTargetMethodName(boolean collectionType, String srcMethodName) {
        return collectionType ? srcMethodName.replaceFirst("^get", "add") : srcMethodName.replaceFirst("^set", "with");
    }

    private final JCodeModel codeModel;
    private final Class<?> srcClass;
    private final JDefinedClass enclosingClass;
    private final String superClassName;
    private final String sourcePackageName;
    private final JDefinedClass builderFactoryClass;
    private JBlock constructorBody;
    private JDefinedClass thisClass;
    private Map<String, PropertyInfo> classInfo = new LinkedHashMap<>();

    public ClassGenerator(JCodeModel codeModel, JDefinedClass enclosingClass, Class<?> srcClass, String superClassName, String sourcePackageName,
                          JDefinedClass builderFactoryClass) {
        this.codeModel = codeModel;
        this.enclosingClass = enclosingClass;
        this.srcClass = srcClass;
        this.superClassName = superClassName;
        this.sourcePackageName = sourcePackageName;
        this.builderFactoryClass = builderFactoryClass;
        classInfo.putAll(inspectClass(this.srcClass));
    }

    public JDefinedClass generate() {
        try {
            if (enclosingClass == null) {
                thisClass = codeModel._class(PUBLIC, getBuilderClassFqn(srcClass), CLASS);
            } else {
                thisClass = enclosingClass._class(PUBLIC | STATIC, getInnerBuilderClassName(srcClass), CLASS);
            }
            thisClass.metadata = srcClass;
            thisClass.javadoc().add(format("Fluent API builder for <code>%s</code>.", srcClass.getName()));

            JClass superClass = parseClass(codeModel, superClassName).narrow(srcClass);
            // extends with "OpenXmlBuilder"
            thisClass._extends(superClass);

            // add Constructor
            addConstructor();
            addOverloadedConstructor();

            List<String> classesToIgnore = new ArrayList<>();
            classesToIgnore.add(SdtBlock.class.getName());
            classesToIgnore.add(CTSdtRow.class.getName());
            // TODO: hack to avoid compilation error, need to figure out later
            if (!classesToIgnore.contains(srcClass.getName())) {
                addCopyConstructor();
            }

            // implement "createObject" method
            final JMethod method = thisClass.method(PROTECTED, srcClass, CREATE_OBJECT_METHOD_NAME);
            method.body()._return(invoke(builderFactoryClass.staticRef(OBJECT_FACTORY_FIELD_NAME), getCreateMethodName(srcClass)));

            // add fluent API methods
            classInfo.entrySet().forEach(entry -> processField(entry.getValue()));

            if (enclosingClass == null) {
                // add "get" method in builder class
                addBuilderGetterMethod(PUBLIC | STATIC);
            }
        } catch (JClassAlreadyExistsException e) {
            // ignore
        }
        return thisClass;
    }

    private void addConstructor() {
        JMethod constructor = thisClass.constructor(PUBLIC);
        constructor.javadoc().add("Initialize the underlying object.");
        JBlock constructorBody = constructor.body();
        constructorBody.invoke("this").arg(_null());
    }

    private void addOverloadedConstructor() {
        JMethod constructor = thisClass.constructor(PUBLIC);
        final JDocComment javadoc = constructor.javadoc();
        javadoc.addParam(FIELD_NAME).add("the given object");
        javadoc.add("Initialize the builder with given object.");
        constructor.param(parseType(codeModel, srcClass.getName()), FIELD_NAME);
        constructorBody = constructor.body();
        constructorBody.invoke("super").arg(FIELD_TYPE_REF);
    }

    private void addCopyConstructor() {
        JMethod constructor = thisClass.constructor(PUBLIC);

        final JVar srcParam = constructor.param(srcClass, "src");
        final JVar targetParam = constructor.param(srcClass, "target");

        final JDocComment javadoc = constructor.javadoc();
        javadoc.add("Copies values fom <code>src</code> into <code>target</code>. Values of <code>target</code> will be overridden by the values from <code>src</code>.");
        javadoc.addParam(srcParam).add("source object");
        javadoc.addParam(targetParam).add("target object");

        final JBlock body = constructor.body();
        body.invoke("this").arg(targetParam);
        final JBlock ifBlock = body._if(srcParam.ne(_null()))._then();

        JInvocation invocation = null;
        for (Map.Entry<String, PropertyInfo> entry : classInfo.entrySet()) {
            final PropertyInfo propertyInfo = entry.getValue();

            final Field field = propertyInfo.getField();
            final boolean collectionType = isCollectionType(field);
            Class<?> paramType = getParamType(field, collectionType);
            if (collectionType) {
                final Type genericType = field.getGenericType();
                final String typeName = genericType.getTypeName();
                if (typeName.contains("?")) {
                    continue;
                }
                final String methodName = propertyInfo.getReadMethod().getName();
                final String paramTypeName = (paramType == null) ? null : paramType.getName();
                final JClass thisType = parseClass(codeModel, typeName);
                final JVar jVar = ifBlock.decl(thisType, propertyInfo.getFieldName(), srcParam.invoke(methodName));
                final JForEach forEach = ifBlock.forEach(parseClass(codeModel, paramType), "o", jVar);
                final JVar var = forEach.var();
                JBlock forBody = forEach.body();
                final String targetMethodName = getTargetMethodName(true, propertyInfo.getReadMethod().getName());
                if (Object.class.getName().equals(paramTypeName)) {
                    forBody.invoke(targetMethodName).arg(builderFactoryClass.staticInvoke(CLONE_OBJECT_METHOD_NAME).arg(var));
                } else {
                    String builderClassFqn = getBuilderClassFqn(paramType);
                    final JInvocation builderArg = _new(parseClass(codeModel, builderClassFqn)).arg(var).arg(_null()).invoke(GET_OBJECT_METHOD_NAME);
                    forBody.invoke(targetMethodName).arg(builderArg);
                }
            } else {
                invocation = copyValue(propertyInfo, ifBlock, srcParam, paramType, invocation);
            }
        }

        if (invocation != null) {
            ifBlock.add(invocation);
        }
    }

    private JInvocation copyValue(PropertyInfo propertyInfo, JBlock ifBlock, JVar srcParam, Class<?> paramType, JInvocation invocation) {
        final JClass thisType = parseClass(codeModel, paramType);
        final boolean sourcePackage = paramType.getPackage().getName().equals(sourcePackageName);
        final String paramTypeName = paramType.getName();
        final boolean innerType = paramTypeName.contains("$");
        final String fieldName = propertyInfo.getFieldName();

        final String readMethodName = propertyInfo.getReadMethod().getName();
        JInvocation methodToInvoke = srcParam.invoke(readMethodName);
        JExpression var;

        if (paramTypeName.equals(BooleanDefaultTrue.class.getName())) {
            var = builderFactoryClass.staticInvoke(CLONE_BOOLEAN_DEFAULT_TRUE_METHOD_NAME).arg(methodToInvoke);
        } else if (paramTypeName.equals(Boolean.class.getName())) {
            var = builderFactoryClass.staticInvoke(CLONE_BOOLEAN_METHOD_NAME).arg(srcParam).arg(lit(fieldName));
        } else if (paramTypeName.equals(BigInteger.class.getName())) {
            var = builderFactoryClass.staticInvoke(CLONE_BIG_INTEGER_METHOD_NAME).arg(methodToInvoke);
        } else if (!paramType.isEnum() && sourcePackage) {
            String builderClassFqn = getBuilderClassFqn(paramType, thisClass.name(), innerType);
            final JClass builderClass = parseClass(codeModel, builderClassFqn);
            final JVar localVar = ifBlock.decl(thisType, fieldName, methodToInvoke);
            final JBlock localIf = ifBlock._if(localVar.ne(_null()))._then();
            localIf.assign(localVar, _new(builderClass).arg(localVar).arg(FIELD_TYPE_REF.invoke(readMethodName)).invoke(GET_OBJECT_METHOD_NAME));
            var = localVar;
        } else {
            var = methodToInvoke;
        }

        invocation = setValue(propertyInfo, var, invocation);
        return invocation;
    }

    private JInvocation setValue(PropertyInfo propertyInfo, JExpression var, JInvocation invocation) {
        Method srcMethod = propertyInfo.getWriteMethod();
        String targetMethodName = getTargetMethodName(false, srcMethod.getName());
        if (invocation == null) {
            invocation = invoke(targetMethodName).arg(var);
        } else {
            invocation = invocation.invoke(targetMethodName).arg(var);
        }
        return invocation;
    }

    @SuppressWarnings("unused")
    private JInvocation copyValues(PropertyInfo propertyInfo, JInvocation invocation, JVar srcParam) {
        final Field field = propertyInfo.getField();
        final boolean collectionType = isCollectionType(field);
        Class<?> paramType = getParamType(field, collectionType);
        if (collectionType || paramType == null) {
            return invocation;
        }
        Method srcMethod = collectionType ? propertyInfo.getReadMethod() : propertyInfo.getWriteMethod();
        if (srcMethod == null) {
            throw new RuntimeException(format("Unable to find source method for field {%s} in class {%s}",
                    propertyInfo.getFieldName(), thisClass.name()));
        }
        String targetMethodName = getTargetMethodName(collectionType, srcMethod.getName());
        JInvocation methodToInvoke = srcParam.invoke(propertyInfo.getReadMethod().getName());
        if (collectionType) {
            methodToInvoke = methodToInvoke.invoke("toArray");
            if (!paramType.getSimpleName().equals("Object")) {
                methodToInvoke = methodToInvoke.arg(_new(parseClass(codeModel, paramType).array()));
            }
        }
        if (invocation == null) {
            invocation = invoke(targetMethodName).arg(methodToInvoke);
        } else {
            invocation = invocation.invoke(targetMethodName).arg(methodToInvoke);
        }
        return invocation;
    }

    private void processField(PropertyInfo propertyInfo) {
        final Field field = propertyInfo.getField();
        final boolean collectionType = isCollectionType(field);
        Method srcMethod = collectionType ? propertyInfo.getReadMethod() : propertyInfo.getWriteMethod();
        if (srcMethod == null) {
            throw new RuntimeException(format("Unable to find source method for field {%s} in class {%s}",
                    propertyInfo.getFieldName(), thisClass.name()));
        }
        // name of builder method
        // if it is a collection type and src method name is "getContent" then the target method name will be "addContent"
        // if it is object type and src method name is "getContent" then the target method name will be "withContent"
        String targetMethodName = getTargetMethodName(collectionType, srcMethod.getName());

        Class<?> paramType = getParamType(field, collectionType);
        if (paramType == null) {
            return;
        }

        JMethod method = addMethod(PUBLIC, thisClass, targetMethodName, thisClass);

        JVar param;
        if (collectionType) {
            param = method.varParam(paramType, PARAM_NAME);
        } else {
            param = method.param(paramType, PARAM_NAME);
        }
        final JBlock body = method.body();
        JBlock block = body;
        if (!paramType.isPrimitive() && !collectionType) {
            block = body._if(param.ne(_null()))._then();
        }
        final boolean sourcePackage = paramType.getPackage().getName().equals(sourcePackageName);
        final boolean innerType = paramType.getName().contains("$");
        if (collectionType) {
            invokeMethod(body, ADD_CONTENT_METHOD_NAME, FIELD_TYPE_REF.invoke(propertyInfo.getReadMethod().getName()), param);
        } else {
            invokeMethod(block, FIELD_TYPE_REF, propertyInfo.getWriteMethod().getName(), param);
        }
        body._return(_this());

        // overloaded method for fields with param type BigInteger
        if (isAssignableFrom(BigInteger.class, paramType)) {
            addBigIntegerOverloadedMethod(method.name(), propertyInfo.getWriteMethod());
        }

        // generates builder for any class which is part of our source package
        // if param type is ENUM then we do not do any thing else
        if (!paramType.isEnum() && sourcePackage) {
            // we are about to generate builder for any field with data type of our source package
            // but if the field is non collection type and an inner class then we will generate inner builder
            if (!collectionType && innerType) {
                addInnerBuilder(paramType, targetMethodName, propertyInfo);
            } else {
                addOverloadMethod(propertyInfo, collectionType, targetMethodName, paramType);
                generateChildBuilder(paramType);
            }
        }
    }

    /**
     * Add overload method for any collection or non-collection OpenXML property which has one and only one field.
     *
     * @param propertyInfo     parent propertyInfo
     * @param collectionType   flag to indicate whether current property is collection based
     * @param targetMethodName name of target method to invoke
     * @param paramType        type of current property
     */
    private void addOverloadMethod(PropertyInfo propertyInfo, boolean collectionType, String targetMethodName,
                                   Class<?> paramType) {
        final Map<String, PropertyInfo> map = ReflectionUtils.inspectClass(paramType);
        if (map.size() == 1) {
            PropertyInfo childPropertyInfo = null;
            for (Map.Entry<String, PropertyInfo> entry : map.entrySet()) {
                childPropertyInfo = entry.getValue();
                break;
            }
            final Field childField = childPropertyInfo.getField();
            final String childBuilderClassFqn = getBuilderClassFqn(paramType);
            final boolean childCollectionType = isCollectionType(childField);
            Class<?> childType = getParamType(childField, childCollectionType);
            String childPackageName = (childType == null) ? null : childType.getPackage().getName();
            final boolean generateCollectionOverload = childPackageName != null && !childPackageName.equals(sourcePackageName)
                    && !childType.getName().equals(Object.class.getName());
            if (collectionType) {
                if (generateCollectionOverload) {
                    addOverloadedCollectionMethod(targetMethodName, childPropertyInfo, childType, childBuilderClassFqn);
                }
            } else {
                if (childCollectionType) {
                    if (generateCollectionOverload) {
                        final String targetMethodName1 = getTargetMethodName(false, childPropertyInfo.getWriteMethod().getName());
                        System.out.println(format("<<<<< %s: %s : %s : %s : %s : %s : %s : %s >>>>>", thisClass.fullName(),
                                propertyInfo.getFieldName(), paramType.getName(), childPropertyInfo.getFieldName(),
                                childType.getName(), childBuilderClassFqn, targetMethodName, targetMethodName1));
                    }
                } else {
                    addOverloadedMethod(propertyInfo, targetMethodName, childPropertyInfo, childType, childBuilderClassFqn);
                }
            }
        }
    }

    /**
     * Add overload method for any collection OpenXML property which has one and only one field.
     *
     * @param targetMethodName     name of target method to invoke
     * @param childPropertyInfo    propertyInfo of current property
     * @param childType            type of current property
     * @param childBuilderClassFqn builder fully qualified name of current property
     */
    private void addOverloadedCollectionMethod(String targetMethodName, PropertyInfo childPropertyInfo,
                                               Class<?> childType, String childBuilderClassFqn) {
        final String targetMethodName1 = getTargetMethodName(false, childPropertyInfo.getWriteMethod().getName());
        final JMethod method1 = addMethod(PUBLIC, thisClass, targetMethodName, thisClass);
        JVar param1;
        JClass jClass = parseClass(codeModel, childType);
        if (BigInteger.class.getName().equals(childType.getName())) {
            jClass = parseClass(codeModel, Long.class);
            param1 = method1.varParam(jClass, childPropertyInfo.getFieldName());
        } else {
            param1 = method1.varParam(jClass, childPropertyInfo.getFieldName());
        }
        final JBlock body1 = method1.body();
        final JBlock ifBlock1 = body1._if(invoke(HAS_CONTENT_MEHOD_NAME).arg(param1))._then();
        final JForEach jForEach = ifBlock1.forEach(jClass, "o", param1);
        final JBlock forBody = jForEach.body();
        forBody.add(invoke(targetMethodName).arg(_new(parseClass(codeModel, childBuilderClassFqn))
                .invoke(targetMethodName1).arg(jForEach.var()).invoke(GET_OBJECT_METHOD_NAME)));
        body1._return(_this());
    }

    /**
     * Add overload method for any non-collection OpenXML property which has one and only one field.
     *
     * @param propertyInfo         parent propertyInfo
     * @param targetMethodName     name of target method to invoke
     * @param childPropertyInfo    propertyInfo of current property
     * @param childType            type of current property
     * @param childBuilderClassFqn builder fully qualified name of current property
     */
    private void addOverloadedMethod(PropertyInfo propertyInfo, String targetMethodName, PropertyInfo childPropertyInfo,
                                     Class<?> childType, String childBuilderClassFqn) {
        final String targetMethodName1 = getTargetMethodName(false, childPropertyInfo.getWriteMethod().getName());
        // add method in current builder
        final JMethod method = addMethod(PUBLIC, thisClass, targetMethodName, thisClass);
        JVar param;
        if (BigInteger.class.getName().equals(childType.getName())) {
            param = method.param(parseClass(codeModel, Long.class), childPropertyInfo.getFieldName());
        } else {
            param = method.param(parseClass(codeModel, childType), childPropertyInfo.getFieldName());
        }
        final JBlock body = method.body();
        final JBlock ifBlock = body._if(param.ne(_null()))._then();
        final JInvocation invocation = _new(parseClass(codeModel, childBuilderClassFqn))
                .invoke(targetMethodName1).arg(param).invoke(GET_OBJECT_METHOD_NAME);
        ifBlock.add(FIELD_TYPE_REF.invoke(propertyInfo.getWriteMethod().getName()).arg(invocation));
        body._return(_this());
    }

    private void generateChildBuilder(Class<?> paramType) {
        ClassGenerator generator = new ClassGenerator(codeModel, null, paramType, superClassName, sourcePackageName,
                builderFactoryClass);
        generator.generate();
    }

    private void addInnerBuilder(Class<?> paramType, String targetMethodName, PropertyInfo pi) {
        ClassGenerator generator = new ClassGenerator(codeModel, thisClass, paramType, superClassName,
                sourcePackageName, builderFactoryClass);
        final JDefinedClass innerClass = generator.generate();

        // add this inner builder construction method
        final JFieldVar field = thisClass.field(PRIVATE, innerClass, uncapitalize(innerClass.name()));
        constructorBody.assign(field, _new(innerClass).arg(refthis(FIELD_NAME).invoke(pi.getReadMethod().getName())));

        String builderMethodName = format("get%s", innerClass.name());

        // generate overloaded method, if the number of fields in the type is less then or equal to 4
        addOverloadMethod(paramType, field, targetMethodName);

        JMethod method = addMethod(PUBLIC, innerClass, builderMethodName, thisClass);
        method.body()._return(field);
    }

    private void addOverloadMethod(Class<?> paramType, JFieldVar fieldVar, String targetMethodName) {
        final Map<String, PropertyInfo> propertyInfoMap = inspectClass(paramType);
        if (propertyInfoMap.size() <= 4) {
            final JMethod method = addMethod(PUBLIC, thisClass, targetMethodName, thisClass);

            List<JVar> methodParams = new ArrayList<>();
            List<PropertyInfo> collectionFields = new ArrayList<>();
            for (Map.Entry<String, PropertyInfo> entry : propertyInfoMap.entrySet()) {
                final PropertyInfo pi = entry.getValue();
                final Field field = pi.getField();
                final Class<?> fieldType = field.getType();
                if (isCollectionType(field)) {
                    // collection type will be added as the last parameter as var args
                    collectionFields.add(pi);
                } else {
                    if (isAssignableFrom(BigInteger.class, fieldType)) {
                        methodParams.add(method.param(Long.class, pi.getFieldName()));
                    } else {
                        methodParams.add(method.param(fieldType, pi.getFieldName()));
                    }
                }
            }

            if (collectionFields.size() > 1) {
                throw new RuntimeException(format("More then one collection type fields in class {%s} of type {%s} of method {%s}",
                        thisClass.name(), paramType.getName(), targetMethodName));
            }

            if (!collectionFields.isEmpty()) {
                final PropertyInfo pi = collectionFields.get(0);
                final Field field = pi.getField();
                final Class<?> collectionGenericType = getCollectionGenericType(field);
                if (collectionGenericType == null) {
                    System.err.println(format("Unable to find collection generic type, field Name {%s}, filed type {%s} in class {%s}",
                            field.getName(), field.getType(), thisClass.name()));
                    collectionFields.clear();
                } else {
                    methodParams.add(method.varParam(collectionGenericType, field.getName()));
                }
            }

            final JBlock body = method.body();

            // initialize boolean variable
            JExpression init;
            JVar param = methodParams.get(0);
            boolean array = param.type().isArray();
            if (array) {
                init = invoke(HAS_CONTENT_MEHOD_NAME).arg(param);
            } else {
                init = param.ne(_null());
            }

            for (int i = 1; i < methodParams.size(); i++) {
                param = methodParams.get(i);
                array = param.type().isArray();
                if (array) {
                    init = init.cor(invoke(HAS_CONTENT_MEHOD_NAME).arg(param));
                } else {
                    init = init.cor(param.ne(_null()));
                }
            }

            body.decl(codeModel.BOOLEAN, INITIALIZED_VARIABLE_NAME).init(init);

            final JBlock ifParamBlock = body._if(INITIALIZED_TYPE_REF)._then();
            JInvocation invocation = null;
            for (JVar var : methodParams) {
                String varName = var.name();
                if (var.type().isArray()) {
                    // collection type
                    final PropertyInfo pi = collectionFields.get(0);
                    final Method readMethod = pi.getReadMethod();
                    String methodName = readMethod.getName().replaceFirst("^get", "add");
                    if (invocation == null) {
                        invocation = fieldVar.invoke(methodName).arg(var);
                    } else {
                        invocation = invocation.invoke(methodName).arg(var);
                    }
                } else {
                    varName = varName.startsWith("_") ? varName.substring(1) : varName;
                    String mn = format("with%s", capitalize(varName));
                    if (invocation == null) {
                        invocation = fieldVar.invoke(mn).arg(var);
                    } else {
                        invocation = invocation.invoke(mn).arg(var);
                    }
                }
            }
            if (invocation == null) {
                throw new RuntimeException(format("invocation is still null, Param Type: %s, Field: %s, Target Method Name: %s",
                        paramType.getName(), fieldVar.name(), targetMethodName));
            }

            ifParamBlock.invoke(targetMethodName).arg(invocation.invoke(GET_OBJECT_METHOD_NAME));
            body._return(_this());
        }
    }

    private void addBigIntegerOverloadedMethod(String withMethodName, Method setterMethod) {
        addOverloadedMethod(withMethodName, setterMethod, BigInteger.class, String.class);
        addOverloadedMethod(withMethodName, setterMethod, BigInteger.class, Long.class);
    }

    private void addOverloadedMethod(String withMethodName, Method setterMethod, Class<?> valueClass, Class<?> paramType) {
        // we can have over loaded by two way,
        // either we have a constructor with given "paramType"
        // or we have static "valueOf" method

        // look for constructor first
        boolean hasConstructor = false;
        try {
            hasConstructor = valueClass.getConstructor(paramType) != null;
        } catch (NoSuchMethodException e) {
            // not found
        }

        JMethod method = addMethod(PUBLIC, thisClass, withMethodName, thisClass);
        final JVar param = method.param(paramType, PARAM_NAME);
        String setterMethodName = addJavaDocComments(method, setterMethod);

        JBlock block = method.body();
        JBlock ifBodyBlock = block._if(param.ne(_null()))._then();
        JInvocation arg;
        final JClass type = parseClass(codeModel, valueClass);
        if (hasConstructor) {
            arg = _new(type).arg(param);
        } else {
            arg = type.staticInvoke("valueOf").arg(param);
        }
        invokeMethod(ifBodyBlock, FIELD_TYPE_REF, setterMethodName, arg);
        block._return(_this());
    }

    private void addBuilderGetterMethod(int mods) {
        String methodName = format("get%s", thisClass.name());
        JType returnType = parseType(codeModel, thisClass.name());

        JMethod method = addMethod(mods, returnType, methodName, builderFactoryClass);
        method.body()._return(_new(returnType));

        method = addMethod(mods, returnType, methodName, builderFactoryClass);
        method.param(srcClass, FIELD_NAME);
        JBlock body = method.body();
        body._return(_new(returnType).arg(FIELD_TYPE_REF));
    }

    private String addJavaDocComments(JMethod method, Method setterMethod) {
        JDocComment javadoc = method.javadoc();
        String setterMethodName = setterMethod.getName();
        javadoc.add(format("Calls <code>%s</code> method.", setterMethodName));
        javadoc.addParam("value").add("Value to set");
        javadoc.addReturn().add("reference to this");
        return setterMethodName;
    }

    private Class<?> getParamType(Field field, boolean collectionType) {
        Class<?> paramType = field.getType();
        if (collectionType) {
            paramType = getCollectionGenericType(field);
            if (paramType == null) {
                err.println(format("No collection type for field {%s} for class {%s}", field.getName(), thisClass.name()));
                return null;
            }
        }
        return paramType;
    }
}
