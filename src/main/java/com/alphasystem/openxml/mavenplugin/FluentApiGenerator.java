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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static com.alphasystem.openxml.mavenplugin.CodeModelUtil.*;
import static com.alphasystem.openxml.mavenplugin.ReflectionUtils.getClassName;
import static com.sun.codemodel.ClassType.CLASS;
import static com.sun.codemodel.JExpr.*;
import static com.sun.codemodel.JMod.*;
import static java.lang.String.format;

/**
 * @author sali
 */
public class FluentApiGenerator {

    static final String GET_OBJECT_METHOD_NAME = "getObject";
    private static final String SET_OBJECT_METHOD_NAME = "setObject";
    static final String PARAM_NAME = "value";
    static final String FIELD_NAME = "object";
    static final JFieldRef FIELD_TYPE_REF = ref(FIELD_NAME);
    static final String INITIALIZED_VARIABLE_NAME = "initialized";
    static final JFieldRef INITIALIZED_TYPE_REF = ref(INITIALIZED_VARIABLE_NAME);
    private static final String CONTENT_PARA_NAME = "content";
    private static final JFieldRef CONTENT_TYPE_REF = ref(CONTENT_PARA_NAME);
    private static final String SRC_PARA_NAME = "src";
    private static final JFieldRef SRC_TYPE_REF = ref(SRC_PARA_NAME);
    static final String CREATE_OBJECT_METHOD_NAME = "createObject";
    static final String HAS_CONTENT_MEHOD_NAME = "hasContent";
    static final String ADD_CONTENT_METHOD_NAME = "addContent";
    static final String OBJECT_FACTORY_FIELD_NAME = "OBJECT_FACTORY";
    static final String CLONE_BOOLEAN_DEFAULT_TRUE_METHOD_NAME = "cloneBooleanDefaultTrue";
    static final String CLONE_BIG_INTEGER_METHOD_NAME = "cloneBigInteger";
    static final String CLONE_BOOLEAN_METHOD_NAME = "cloneBoolean";
    static final String CLONE_OBJECT_METHOD_NAME = "cloneObject";

    public static void main(String[] args) {
        JCodeModel codeModel = new JCodeModel();

        FluentApiGenerator apiGenerator = new FluentApiGenerator(codeModel,
                "com.alphasystem.openxml.builder", "wml", "WmlBuilderFactory",
                P.class, P.Hyperlink.class, Tbl.class, Tr.class, Tc.class, R.class, Text.class, CTTabStop.class,
                Br.class, FldChar.class, SectPr.class, TblGridCol.class, CTBookmarkRange.class, CTBookmark.class,
                BooleanDefaultFalse.class, Styles.class, Style.class, Numbering.class, SdtBlock.class, CTSdtRow.class);
        apiGenerator.generate("org.docx4j.wml");

        File destDir = new File("test");
        try {
            FileUtils.deleteDirectory(destDir);
            destDir.mkdirs();
        } catch (IOException ex) {
            // ignore
        }

        try {
            codeModel.build(destDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    static String getBuilderClassFqn(Class<?> srcClass, String builderPackageName) {
        return format("%s.%sBuilder", builderPackageName, getClassName(srcClass));
    }

    static String getInnerBuilderClassName(Class<?> srcClass) {
        return format("%sBuilder", srcClass.getSimpleName());
    }

    static String getBuilderClassFqn(Class<?> srcClass,
                                     String enclosingClassName,
                                     String builderPackageName,
                                     boolean innerType) {
        return innerType ? format("%s.%s", enclosingClassName, getInnerBuilderClassName(srcClass)) :
                getBuilderClassFqn(srcClass, builderPackageName);
    }

    private JCodeModel codeModel;
    private Class<?>[] srcClasses;
    private JDefinedClass openXmlBuilderClass;
    private JDefinedClass builderFactoryClass;
    private String builderPackageName;
    private String superClassFqn;
    private String builderFactoryClassFqn;

    public FluentApiGenerator(JCodeModel codeModel,
                              String basePackageName,
                              String builderType,
                              String builderFactoryClassName,
                              Class<?>... srcClasses) {
        this.codeModel = codeModel;
        this.srcClasses = srcClasses;
        this.builderPackageName = format("%s.%s", basePackageName, builderType);
        this.superClassFqn = format("%s.OpenXmlBuilder", basePackageName);
        this.builderFactoryClassFqn = format("%s.%s", builderPackageName, builderFactoryClassName);
    }

    private JFieldVar addBuilderFactoryStaticField(Class<?> returnTypeClass,
                                                   String fieldName,
                                                   String builderMethodName,
                                                   String valueMethodName,
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

    public void generate(String sourcePackageName) {
        generateOpenXmlBuilderClass();
        generateOpenXmlBuilderFactoryClass();
        for (Class<?> srcClass : srcClasses) {
            generate(srcClass, sourcePackageName);
        }
    }

    private JDefinedClass generate(Class<?> srcClass, String sourcePackageName) {
        ClassGenerator classGenerator = new ClassGenerator(codeModel, null, srcClass, openXmlBuilderClass.fullName(),
                sourcePackageName, builderFactoryClass);
        return classGenerator.generate(builderPackageName);
    }

    private void generateOpenXmlBuilderClass() {
        try {
            openXmlBuilderClass = codeModel._class(PUBLIC | ABSTRACT, superClassFqn, CLASS);
            openXmlBuilderClass.generify("T");
            final JType t = parseType(codeModel, "T");

            openXmlBuilderClass.field(PROTECTED, t, FIELD_NAME);
            JMethod constructor = openXmlBuilderClass.constructor(PROTECTED);
            constructor.body().invoke("this").arg(_null());
            constructor = openXmlBuilderClass.constructor(PROTECTED);
            constructor.param(t, FIELD_NAME);
            constructor.body().invoke(SET_OBJECT_METHOD_NAME).arg(FIELD_TYPE_REF);
            openXmlBuilderClass.method(PROTECTED | ABSTRACT, t, CREATE_OBJECT_METHOD_NAME);
            addGetObjectMethod(t);
            addSetObjectMethod(t);
            addHasContentMethod();
            addAddContentMethod();
        } catch (JClassAlreadyExistsException e) {
            // ignore
        }
    }

    private void addGetObjectMethod(JType t) {
        JMethod method = addMethod(PUBLIC, t, GET_OBJECT_METHOD_NAME, openXmlBuilderClass);
        JBlock block = method.body();
        block._return(FIELD_TYPE_REF);
    }

    private void addSetObjectMethod(JType t) {
        JMethod method = addMethod(PUBLIC, codeModel.VOID, SET_OBJECT_METHOD_NAME, openXmlBuilderClass);
        method.param(t, FIELD_NAME);
        final JBlock body = method.body();
        final JBlock ifBlock = body._if(FIELD_TYPE_REF.eq(_null()))._then();
        ifBlock.assign(FIELD_TYPE_REF, invoke(CREATE_OBJECT_METHOD_NAME));
        body.assign(refthis(FIELD_NAME), FIELD_TYPE_REF);
    }

    private void addAddContentMethod() {
        final JMethod method = addMethod(PROTECTED | STATIC, codeModel.VOID, ADD_CONTENT_METHOD_NAME, openXmlBuilderClass);
        method.generify("C");
        final JType c = parseType(codeModel, "C");
        method.param(parseClass(codeModel, List.class).narrow(c), SRC_PARA_NAME);
        method.varParam(c, CONTENT_PARA_NAME);
        final JBlock methodBody = method.body();
        final JBlock ifBlock = methodBody._if(openXmlBuilderClass.staticInvoke(HAS_CONTENT_MEHOD_NAME)
                .arg(CONTENT_TYPE_REF))._then();
        final JClass collectionsClass = parseClass(codeModel, Collections.class);
        ifBlock.staticInvoke(collectionsClass, "addAll").arg(SRC_TYPE_REF).arg(CONTENT_TYPE_REF);
    }

    private void addHasContentMethod() {
        final JMethod method = addMethod(PROTECTED | STATIC, parseClass(codeModel, Boolean.class), HAS_CONTENT_MEHOD_NAME,
                openXmlBuilderClass);
        method.generify("C");
        method.varParam(parseType(codeModel, "C"), CONTENT_PARA_NAME);
        final JBlock methodBody = method.body();
        methodBody._return(CONTENT_TYPE_REF.ne(_null()).cand(CONTENT_TYPE_REF.ref("length").gt(JExpr.lit(0))));
    }

    private void generateOpenXmlBuilderFactoryClass() {
        try {
            builderFactoryClass = codeModel._class(PUBLIC, builderFactoryClassFqn, CLASS);

            builderFactoryClass.field(PUBLIC | STATIC | FINAL, parseType(codeModel, ObjectFactory.class.getName()),
                    OBJECT_FACTORY_FIELD_NAME, parseClass(codeModel, Context.class.getName()).staticInvoke("getWmlObjectFactory"));

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
            builderFactoryClass.constructor(PRIVATE).javadoc().add("Do not let anyone instantiate this class.");

            addCloneBooleanDefaultTrueMethod();
            addCloneBigIntegerMethod();
            addCloneBooleanMethod();
            addCloneObjectMethod();
        } catch (JClassAlreadyExistsException e) {
            // ignore
        }
    }

    private void addCloneBooleanDefaultTrueMethod() {
        final JClass type = parseClass(codeModel, BooleanDefaultTrue.class);
        final JMethod method = addMethod(PUBLIC | STATIC, type, CLONE_BOOLEAN_DEFAULT_TRUE_METHOD_NAME, builderFactoryClass);
        final JVar source = method.param(type, "source");
        final JBlock body = method.body();
        final JVar target = body.decl(type, "target", _null());
        final JBlock ifBlock = body._if(source.ne(_null()))._then();
        final JInvocation left = _new(parseClass(codeModel, "BooleanDefaultTrueBuilder")).arg(source).arg(target);
        ifBlock.assign(target, left.invoke(GET_OBJECT_METHOD_NAME));
        body._return(target);
    }

    private void addCloneBooleanMethod() {
        final JClass type = parseClass(codeModel, Boolean.class);
        final JMethod method = addMethod(PUBLIC | STATIC, type, CLONE_BOOLEAN_METHOD_NAME, builderFactoryClass);
        final JVar source = method.param(parseClass(codeModel, Object.class), "source");
        final JVar fieldName = method.param(parseClass(codeModel, String.class), "fieldName");
        final JBlock body = method.body();
        final JVar target = body.decl(type, "target", _null());
        final JBlock ifBlock = body._if(source.ne(_null()))._then();

        final JTryBlock tryBlock = ifBlock._try();
        final JBlock tryBody = tryBlock.body();
        final JVar field = tryBody.decl(FINAL, parseClass(codeModel, Field.class), "field", source.invoke("getClass")
                .invoke("getDeclaredField").arg(fieldName));
        tryBody.add(field.invoke("setAccessible").arg(lit(true)));
        tryBody.assign(target, cast(type, field.invoke("get").arg(source)));

        final JCatchBlock catchBlock = tryBlock._catch(parseClass(codeModel, Exception.class));
        final JVar ex = catchBlock.param("ex");
        catchBlock.body().add(ex.invoke("printStackTrace"));


        body._return(target);
    }

    private void addCloneBigIntegerMethod() {
        final JClass type = parseClass(codeModel, BigInteger.class);
        final JMethod method = addMethod(PUBLIC | STATIC, type, CLONE_BIG_INTEGER_METHOD_NAME, builderFactoryClass);
        final JVar source = method.param(type, "source");
        final JBlock body = method.body();
        final JVar target = body.decl(type, "target", _null());
        final JBlock ifBlock = body._if(source.ne(_null()))._then();
        final JInvocation invocation = type.staticInvoke("valueOf").arg(source.invoke("longValue"));
        ifBlock.assign(target, invocation);
        body._return(target);
    }

    private void addCloneObjectMethod() {
        final JClass type = parseClass(codeModel, Object.class);
        final JMethod method = addMethod(PUBLIC | STATIC, type, CLONE_OBJECT_METHOD_NAME, builderFactoryClass);
        final JVar source = method.param(type, "source");
        final JBlock body = method.body();
        body._if(source.eq(_null()))._then()._return(source);
        final JClass classType = parseClass(codeModel, Class.class);
        final JVar objectClass = body.decl(FINAL, classType, "objectClass", source.invoke("getClass"));
        final JBlock ifBlock = body._if(objectClass.invoke("getPackage").invoke("getName").invoke("equals").arg(lit("org.docx4j.wml")))._then();
        final JVar builderClass = ifBlock.decl(classType, "builderClass", _null());
        final JClass stringType = parseClass(codeModel, String.class);
        final JVar builderPackageName = ifBlock.decl(FINAL, stringType, "builderPackageName", lit("com.alphasystem.openxml.builder.wml"));
        final JVar builderFqn = ifBlock.decl(stringType, "builderFqn", stringType.staticInvoke("format")
                .arg(lit("%s.%sBuilder")).arg(builderPackageName).arg(objectClass.invoke("getSimpleName")));

        JTryBlock tryBlock = ifBlock._try();
        tryBlock.body().assign(builderClass, classType.staticInvoke("forName").arg(builderFqn));
        JCatchBlock catchBlock = tryBlock._catch(parseClass(codeModel, ClassNotFoundException.class));
        catchBlock.param("ex");
        catchBlock.body().directStatement("// ignore");

        final JBlock block = ifBlock._if(builderClass.eq(_null()))._then();
        final JClass systemClass = parseClass(codeModel, System.class);
        block.add(systemClass.staticRef("err").invoke("printf").arg("Error creating builder: %s\n").arg(objectClass.invoke("getName")));
        block._return(source);

        tryBlock = ifBlock._try();
        final JBlock tryBody = tryBlock.body();
        final JVar constructor = tryBody.decl(FINAL, parseClass(codeModel, Constructor.class), "constructor",
                builderClass.invoke("getConstructor").arg(objectClass).arg(objectClass));
        final JVar builder = tryBody.decl(FINAL, openXmlBuilderClass, "builder",
                cast(openXmlBuilderClass, constructor.invoke("newInstance").arg(source).arg(_null())));
        tryBody._return(builder.invoke(GET_OBJECT_METHOD_NAME));
        catchBlock = tryBlock._catch(parseClass(codeModel, Exception.class));
        final JVar ex = catchBlock.param("ex");
        final JBlock catchBody = catchBlock.body();
        catchBody.add(systemClass.staticRef("err").invoke("printf").arg("Error creating builder: %s\n").arg(ex.invoke("getMessage")));
        catchBody._return(source);

        body._return(source);
    }
}
