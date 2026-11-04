package com.shopmanager.app.ui.common

private val arabicKeyCache = java.util.concurrent.ConcurrentHashMap<String, String>()

fun arabicSortKey(name: String): String {
    if (arabicKeyCache.size > 4000) arabicKeyCache.clear()
    return arabicKeyCache.getOrPut(name) { ArabicText.normalize(name) }
}

val ArabicNameComparator: Comparator<String> = Comparator { a, b ->
    val c = arabicSortKey(a).compareTo(arabicSortKey(b))
    if (c != 0) c else a.compareTo(b)
}

fun <T> Iterable<T>.sortedArabicBy(selector: (T) -> String): List<T> =
    sortedWith { x, y -> ArabicNameComparator.compare(selector(x), selector(y)) }
