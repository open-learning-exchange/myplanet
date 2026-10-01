package org.ole.planet.myplanet.data.room

import androidx.room.RoomRawQuery
import androidx.sqlite.SQLiteStatement
import io.mockk.every
import io.mockk.mockk

/** Runs the query's binding function against a recording statement and returns the 1-based bound args. */
fun RoomRawQuery.boundArgs(): Map<Int, Any?> {
    val args = mutableMapOf<Int, Any?>()
    val stmt = mockk<SQLiteStatement>(relaxed = true)
    every { stmt.bindNull(any()) } answers { args[firstArg()] = null }
    every { stmt.bindLong(any(), any()) } answers { args[firstArg()] = secondArg<Long>() }
    every { stmt.bindDouble(any(), any()) } answers { args[firstArg()] = secondArg<Double>() }
    every { stmt.bindText(any(), any()) } answers { args[firstArg()] = secondArg<String>() }
    every { stmt.bindBlob(any(), any()) } answers { args[firstArg()] = secondArg<ByteArray>() }
    getBindingFunction().invoke(stmt)
    return args
}
