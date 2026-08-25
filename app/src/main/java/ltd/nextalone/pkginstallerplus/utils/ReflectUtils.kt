package ltd.nextalone.pkginstallerplus.utils

import java.lang.reflect.Field
import java.lang.reflect.Method

fun findField(clazz: Class<*>?, type: Class<*>?, name: String?): Field? {
    if (clazz == null || name.isNullOrEmpty()) return null
    var clz: Class<*>? = clazz
    while (clz != null) {
        for (field in clz.declaredFields) {
            if ((type == null || field.type == type) && field.name == name) {
                runCatching { field.isAccessible = true }
                return field
            }
        }
        clz = clz.superclass
    }
    return null
}

fun iGetObjectOrNull(obj: Any, name: String?): Any? = iGetObjectOrNull<Any>(obj, name, null)

@Suppress("UNCHECKED_CAST")
fun <T> iGetObjectOrNull(obj: Any, name: String?, type: Class<T>?): T? {
    return try {
        val field = findField(obj.javaClass, type, name) ?: return null
        field[obj] as? T
    } catch (_: Throwable) {
        null
    }
}

fun iPutObject(obj: Any, name: String?, value: Any?) = iPutObject(obj, name, null, value)

fun iPutObject(obj: Any, name: String?, type: Class<*>?, value: Any?) {
    try {
        val field = findField(obj.javaClass, type, name) ?: return
        field[obj] = value
    } catch (_: Throwable) {
    }
}

internal fun Any.get(objName: String): Any? = this.get(objName, null)
internal fun <T> Any.get(objName: String, clz: Class<T>? = null): T? = iGetObjectOrNull(this, objName, clz)
internal fun Any.getFirst(vararg names: String): Any? {
    names.forEach { name -> get(name)?.let { return it } }
    return null
}

internal fun Any.callNoArg(name: String): Any? {
    var clz: Class<*>? = javaClass
    while (clz != null) {
        val method: Method? = clz.declaredMethods.firstOrNull {
            it.name == name && it.parameterTypes.isEmpty()
        }
        if (method != null) {
            return try {
                method.isAccessible = true
                method.invoke(this)
            } catch (_: Throwable) {
                null
            }
        }
        clz = clz.superclass
    }
    return null
}

internal fun Any.set(name: String, value: Any): Any = apply { iPutObject(this, name, value) }
internal fun Any.set(name: String, clz: Class<*>?, value: Any): Any = apply { iPutObject(this, name, clz, value) }
