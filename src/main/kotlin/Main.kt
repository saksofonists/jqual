import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import java.io.File
import kotlin.math.max

fun main(args: Array<String>) {
    if (args.size != 3) {
        println("A filter file, an input file, and an output file are required as arguments!")
        return
    }

    val filters = parseFilters(args[0])
    val j1 = getJsonRepresentation(args[1])
    val j2 = getJsonRepresentation(args[2])

    compare(j1, j2, filters, "")
}


fun comparePresence(currentPath: String, j1: Any?, j2: Any?): Boolean {
    if (j1 == null) {
        println("input1 is missing $currentPath")
        return false
    }
    if (j2 == null) {
        println("input2 is missing $currentPath")
        return false
    }
    return true
}


fun compareTypes(currentPath: String, j1: Any?, j2: Any?): Boolean {
    if (j1?.javaClass != j2?.javaClass) {
        println("input2 and input1 have differing types at $currentPath:\n" +
                "\t${j1?.javaClass?.simpleName} and ${j2?.javaClass?.simpleName}")
        return false
    }
    return true
}

fun compareValue(currentPath: String, j1: Any?, j2: Any?) = when (j1) {
    is JsonArray<*> -> false
    is JsonObject -> false
    else -> {
        if (j1 == j2) true
        else println("Values at $currentPath differ: \"$j1\" and \"$j2\"").let { false }
    }
}

fun recurse(
    j1: Any?,
    j2: Any?,
    filters: Map<String, FilterType>,
    currentPath: String
) {
    val j1Keys = (j1 as? JsonObject)?.keys ?: setOf<String>()
    val j2Keys = (j2 as? JsonObject)?.keys ?: setOf<String>()
    val allKeys = mutableSetOf<String>().also {
        it.addAll(j1Keys)
        it.addAll(j2Keys)
    }

    allKeys.forEach {
        compare(
            (j1 as? JsonObject)?.get(it),
            (j2 as? JsonObject)?.get(it),
            filters,
            "$currentPath/$it"
        )
    }

    val j1Len = (j1 as? JsonArray<*>)?.size ?: 0
    val j2Len = (j2 as? JsonArray<*>)?.size ?: 0

    for (i in 0..max(j1Len - 1, j2Len - 1)) {
        compare(
            (j1 as? JsonArray<*>)?.getOrNull(i),
            (j2 as? JsonArray<*>)?.getOrNull(i),
            filters,
            "$currentPath[$i]"
        )
    }
}

fun compare(
    j1: Any?,
    j2: Any?,
    filters: Map<String, FilterType>,
    currentPath: String
) {
    val curFilters = filters.keys
        .filter { it.toRegex().matches(currentPath) }
        .map { filters[it] }

    if (curFilters.contains(FilterType.IgnoreAll)) return
    if (curFilters.contains(FilterType.PresenceOnly)) {
        if (!comparePresence(currentPath, j1, j2)) return
        return recurse(j1, j2, filters, currentPath)
    }
    if (curFilters.contains(FilterType.TypeOnly)) {
        if (!comparePresence(currentPath, j1, j2)) return
        if (!compareTypes(currentPath, j1, j2)) return
        return recurse(j1, j2, filters, currentPath)
    }

    if (!comparePresence(currentPath, j1, j2)) return
    if (!compareTypes(currentPath, j1, j2)) return
    compareValue(currentPath, j1, j2)
    return recurse(j1, j2, filters, currentPath)
}

fun getJsonRepresentation(path: String) = Parser.default().parse(path)

fun parseFilters(filePath: String) = File(filePath)
    .readLines()
    .mapNotNull { """\s*"(.*)"\s*=\s*([a-z]*)\s*""".toRegex().matchEntire(it)?.destructured }
    .map { (key, type) -> key to FilterType.fromString(type) }
    .mapNotNull { (key, type) -> if (type == null) null else key to type }
    .toMap()

enum class FilterType {
    TypeOnly,
    PresenceOnly,
    DifferingDefaults,
    IgnoreAll;

    companion object {
        fun fromString(str: String): FilterType? {
            return when (str) {
                "type" -> TypeOnly
                "presence" -> PresenceOnly
                "ignore" -> IgnoreAll
                else -> null
            }
        }
    }
}

