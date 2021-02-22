package dev.skomlach.biometric.compat.utils

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.lang.reflect.Modifier

@RestrictTo(RestrictTo.Scope.LIBRARY)
object ReflectionUtils {
    fun printClass(name: String?) {
        try {
            // print class name and superclass name (if != Object)
            val cl = Class.forName(name)
            printClass(cl)
        } catch (ignore: ClassNotFoundException) {
        }
    }

    fun printClass(cl: Class<*>) {
        try {
            val supercl = cl.superclass
            val modifiers = Modifier.toString(cl.modifiers)
            if (modifiers.isNotEmpty()) {
                System.err.print("$modifiers ")
            }
            System.err.print("class " + cl.name)
            if (supercl != null && supercl != Any::class.java) {
                System.err.print(
                    " extends "
                            + supercl.name
                )
            }
            System.err.print("\n{\n")
            printConstructors(cl)
            System.err.println()
            printMethods(cl)
            System.err.println()
            printFields(cl)
            System.err.println("}")
        } catch (e: Exception) {
            e(e)
        }
    }

    /**
     * Prints all constructors of a class
     *
     * @param cl a class
     */
    fun printConstructors(cl: Class<*>) {
        val constructors = cl.declaredConstructors
        for (c in constructors) {
            val name = c.name
            System.err.print("   ")
            val modifiers = Modifier.toString(c.modifiers)
            if (modifiers.isNotEmpty()) {
                System.err.print("$modifiers ")
            }
            System.err.print("$name(")

            // print parameter types
            val paramTypes = c.parameterTypes
            for (j in paramTypes.indices) {
                if (j > 0) {
                    System.err.print(", ")
                }
                System.err.print(paramTypes[j].name)
            }
            System.err.println(");")
        }
    }

    /**
     * Prints all methods of a class
     *
     * @param cl a class
     */
    fun printMethods(cl: Class<*>) {
        val methods = cl.declaredMethods
        for (m in methods) {
            val retType = m.returnType
            val name = m.name
            System.err.print("   ")
            // print modifiers, return type and method name
            val modifiers = Modifier.toString(m.modifiers)
            if (modifiers.isNotEmpty()) {
                System.err.print("$modifiers ")
            }
            System.err.print(retType.name + " " + name + "(")

            // print parameter types
            val paramTypes = m.parameterTypes
            for (j in paramTypes.indices) {
                if (j > 0) {
                    System.err.print(", ")
                }
                System.err.print(paramTypes[j].name)
            }
            System.err.println(");")
        }
    }

    /**
     * Prints all fields of a class
     *
     * @param cl a class
     */
    @Throws(IllegalAccessException::class)
    fun printFields(cl: Class<*>) {
        val fields = cl.declaredFields
        for (f in fields) {
            val isAccessible = f.isAccessible
            if (!isAccessible) {
                f.isAccessible = true
            }
            val type = f.type
            val name = f.name
            System.err.print("   ")
            val modifiers = Modifier.toString(f.modifiers)
            if (modifiers.isNotEmpty()) {
                System.err.print("$modifiers ")
            }
            if (Modifier.isStatic(f.modifiers)) {
                if (type == String::class.java) System.err.println(type.name + " " + name + " = \"" + f[null] + "\";") else System.err.println(
                    type.name + " " + name + " = " + f[null] + ";"
                )
            } else System.err.println(type.name + " " + name + ";")
            if (!isAccessible) {
                f.isAccessible = true
            }
        }
    }
}