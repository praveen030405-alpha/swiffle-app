package com.swiffle.app.conversion.proof

import com.swiffle.app.conversion.proof.core.*
import com.swiffle.app.conversion.proof.verify.VerifyBundle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProofBundleTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun cell(i: Int): Cell {
        val type = listOf(CellType.EMPTY, CellType.TEXT, CellType.DECIMAL, CellType.TEXT)[i % 4]
        val value = listOf(null, "", "0", "0")[i % 4]
        return Cell("s/$i","Sheet 1","A" + (i + 1),"sec","label|\n", "A", value, value,
            value ?: "∅", type,"a".repeat(64))
    }
    @Test fun boundariesAndMutations() {
        val src = tmp.newFile().apply { writeText("source") }
        val target = File(tmp.root,"bundle")
        val cert = ProofBundle.write(src,target,(0..500).asSequence().map { listOf(cell(it)) },500)
        assertEquals(listOf(500L,1L),cert.shards.map { it.cells })
        assertEquals(cert.merkleRoot,VerifyBundle.checkBundle(target,src))
        val file = File(target,cert.shards.first().name)
        val original = file.readLines()
        val cases = listOf<(MutableList<String>) -> Unit>(
            { it[2] = it[2].replace("\"columnKey\":\"A\"", "\"columnKey\":\"B\"") },
            { it.removeAt(2) },
            { java.util.Collections.swap(it,2,3) },
            { it.add(3,it[2]) },
            { it[3] += " " }
        )
        for (mutate in cases) {
            val lines = original.toMutableList().also(mutate)
            file.writeText(lines.joinToString("\n",postfix="\n"))
            assertTrue(runCatching { VerifyBundle.checkBundle(target,src) }.isFailure)
        }
    }
    @Test fun adapterFailurePublishesNothing() {
        val target = File(tmp.root,"incomplete")
        val src = tmp.newFile()
        assertTrue(runCatching { ProofBundle.write(src,target,sequence {
            yield(listOf(cell(0))); error("Sheet 1!A2")
        }) }.isFailure)
        assertFalse(target.exists())
        assertFalse(tmp.root.listFiles()!!.any { it.name.startsWith(".proof-") })
    }
}
