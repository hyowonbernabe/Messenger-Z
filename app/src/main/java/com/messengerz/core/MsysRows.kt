package com.messengerz.core

import java.lang.reflect.Field

/**
 * Direct, hook-free reads from MSYS `com.facebook.msys.mci.JQLResultSet` rows.
 *
 * JQLResultSet is a plain-Java columnar table filled by native code, so its `m*` field
 * names are stable (JNI sets them by name). Values live in a storage object (obfuscated
 * class) holding one `Object[]` (strings/blobs/children) and one `long[]`; we locate those
 * by type. Reading the arrays directly avoids re-entering our own getString hook.
 *
 * Column type = `mColumnTypes[c] & 63`: 1 int, 2 long, 3 double, 4 bool, 5 string, 6 blob,
 * 7 child result set. The high bit marks "never null".
 */
object MsysRows {
    const val T_LONG = 2
    const val T_STRING = 5

    private var fTypes: Field? = null
    private var fOffsets: Field? = null
    private var fObjCount: Field? = null
    private var fLongCount: Field? = null
    private var fRowCount: Field? = null
    private var fStorage: Field? = null
    private var fObjects: Field? = null
    private var fLongs: Field? = null

    private fun bind(rs: Any) {
        if (fTypes != null) return
        val c = rs.javaClass
        fun f(n: String) = c.getDeclaredField(n).apply { isAccessible = true }
        fOffsets = f("mColumnValueOffsets"); fObjCount = f("mObjectCount")
        fLongCount = f("mLongCount"); fRowCount = f("mRowCount"); fStorage = f("mStorage")
        val st = fStorage!!.type
        fObjects = st.declaredFields.first { it.type == Array<Any>::class.java }.apply { isAccessible = true }
        fLongs = st.declaredFields.first { it.type == LongArray::class.java }.apply { isAccessible = true }
        fTypes = f("mColumnTypes")
    }

    fun types(rs: Any): ByteArray { bind(rs); return fTypes!!.get(rs) as ByteArray }

    fun rowCount(rs: Any): Int { bind(rs); return fRowCount!!.getInt(rs) }

    fun type(types: ByteArray, col: Int) = types[col].toInt() and 63

    fun str(rs: Any, row: Int, col: Int): String? {
        bind(rs)
        if (type(types(rs), col) != T_STRING) return null
        val offs = fOffsets!!.get(rs) as ShortArray
        val objs = fObjects!!.get(fStorage!!.get(rs)) as Array<*>
        return objs.getOrNull(row * fObjCount!!.getShort(rs) + offs[col]) as? String
    }

    fun long(rs: Any, row: Int, col: Int): Long? {
        bind(rs)
        if (type(types(rs), col) != T_LONG) return null
        val offs = fOffsets!!.get(rs) as ShortArray
        val longs = fLongs!!.get(fStorage!!.get(rs)) as LongArray? ?: return null
        return longs.getOrNull(row * fLongCount!!.getShort(rs) + offs[col])
    }

    /** Stable id for a result-set layout: same query shape → same signature. */
    fun signature(types: ByteArray): String = "${types.size}:${types.contentHashCode()}"
}
