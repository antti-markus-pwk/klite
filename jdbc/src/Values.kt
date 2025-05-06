package klite.jdbc

import klite.AbsentValue
import klite.PropValue
import klite.create
import klite.publicProperties
import java.sql.ResultSet
import kotlin.reflect.KClass

inline fun <reified T: Any> ResultSet.create(vararg provided: PropValue<T, *>) = create(T::class, *provided)

/** Take only prefixed column names, e.g. "alias.id" to get second joined table, see [populatePgColumnNameIndex] for details */
inline fun <reified T: Any> ResultSet.create(columnPrefix: String, vararg provided: PropValue<T, *>) = create(T::class, *provided, columnPrefix = columnPrefix)

fun <T: Any> ResultSet.create(type: KClass<T>, vararg provided: PropValue<T, *>, columnPrefix: String = ""): T {
  val extraArgs = provided.associate { it.first.name to it.second }
  return type.create {
    val column = columnPrefix + (type.publicProperties[it.name]?.colName ?: it.name)
    if (extraArgs.containsKey(it.name)) extraArgs[it.name!!]
    else if (it.isOptional) getOptional<T>(column, it.type).getOrDefault(AbsentValue)
    else get(column, it.type)
  }
}
