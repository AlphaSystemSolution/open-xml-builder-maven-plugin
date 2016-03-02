package com.alphasystem.openxml.mavenplugin;

import com.sun.codemodel.*;

import java.lang.annotation.Annotation;

import static com.sun.codemodel.JExpr._null;
import static com.sun.codemodel.JExpr.lit;

/**
 * @author sali
 */
public final class CodeModelUtil {

    public static JType parseType(JCodeModel codeModel, String name) {
        JType type = null;
        try {
            type = codeModel.parseType(name);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return type;
    }

    public static JClass parseClass(JCodeModel codeModel, Class<?> _class) {
        return parseClass(codeModel, _class.getName());
    }

    public static JClass parseClass(JCodeModel codeModel, String name) {
        return (JClass) parseType(codeModel, name);
    }

    public static void invokeMethod(JBlock methodBody, String targetMethodName, JExpression... args) {
        final JInvocation invocation = methodBody.invoke(targetMethodName);
        appendArguments(invocation, args);
    }

    public static void invokeMethod(JBlock methodBody, JExpression expr, String targetMethodName, JExpression... args) {
        final JInvocation invocation = methodBody.invoke(expr, targetMethodName);
        appendArguments(invocation, args);
    }

    public static void invokeMethod(JBlock methodBody, JExpression expr, String targetMethodName, JInvocation... args) {
        final JInvocation invocation = methodBody.invoke(expr, targetMethodName);
        appendArguments(invocation, args);

    }

    public static void invokeMethod(JBlock methodBody, JExpression expr, String targetMethodName, String value) {
        methodBody.invoke(expr, targetMethodName).arg(getLiteralValue(value));
    }

    public static void invokeMethod(JBlock methodBody, JExpression expr, String targetMethodName, boolean value) {
        methodBody.invoke(expr, targetMethodName).arg(lit(value));
    }

    public static void invokeMethod(JBlock methodBody, JExpression expr, String targetMethodName, Long value) {
        methodBody.invoke(expr, targetMethodName).arg(lit((value == null) ? 0L : value));
    }

    public static void invokeMethod(JBlock methodBody, JExpression expr, String targetMethodName, Integer value) {
        methodBody.invoke(expr, targetMethodName).arg(lit((value == null) ? 0 : value));
    }

    public static JExpression getLiteralValue(String value) {
        return value == null ? _null() : lit(value);
    }

    @SafeVarargs
    public static JMethod addMethod(int mods, JType type, String methodName, JDefinedClass parentClass,
                                    Class<? extends Annotation>... annotations) {
        JMethod method = parentClass.method(mods, type, methodName);
        if (annotations != null && annotations.length > 0) {
            for (Class<? extends Annotation> c : annotations) {
                method.annotate(c);
            }
        }
        return method;
    }

    private static void appendArguments(JInvocation invocation, JExpression[] args) {
        if (args != null && args.length > 0) {
            for (JExpression arg : args) {
                invocation.arg(arg);
            }
        }
    }
}
