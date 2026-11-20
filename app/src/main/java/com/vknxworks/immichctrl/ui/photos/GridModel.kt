package com.vknxworks.immichctrl.ui.photos

import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.settings.GroupBy
import com.vknxworks.immichctrl.data.timeline.Bucket
import java.time.LocalDate

// The rows of a photo grid: what [AssetGrid] hands to its lazy list.

internal sealed interface GridItem {
    val key: String
    val bucket: Int
}

internal data class MonthHeader(override val key: String, override val bucket: Int, val label: String) : GridItem
internal data class DayHeader(override val key: String, override val bucket: Int, val date: LocalDate, val assets: List<Asset>) : GridItem
internal data class AssetRow(override val key: String, override val bucket: Int, val start: Int, val assets: List<Asset>) : GridItem
internal data class PlaceholderRow(override val key: String, override val bucket: Int, val cells: Int) : GridItem

/**
 * The rows of one bucket: [bi] is its position in the list of buckets and [global] the number of assets in
 * the buckets before it. They depend on nothing else, which is what lets [GridBuilder] keep them.
 */
internal fun bucketRows(b: Bucket, bi: Int, global: Int, cols: Int, groupBy: GroupBy, out: MutableList<GridItem>) {
    if (b.label != null) out += MonthHeader("m:${b.key}", bi, b.label)
    val a = b.assets
    if (a == null) {
        val rows = (b.count + cols - 1) / cols
        for (r in 0 until rows) {
            val cells = if (r == rows - 1 && b.count % cols != 0) b.count % cols else cols
            out += PlaceholderRow("p:${b.key}:$r", bi, cells)
        }
    } else if (groupBy == GroupBy.DAY && b.label != null) {
        var i = 0
        while (i < a.size) {
            val day = a[i].localDate
            var j = i
            while (j < a.size && a[j].localDate == day) j++
            val dayAssets = a.subList(i, j)
            out += DayHeader("d:${b.key}:$day:$i", bi, day, dayAssets)
            dayAssets.chunked(cols).forEachIndexed { r, row ->
                out += AssetRow("r:${row.first().id}", bi, global + i + r * cols, row)
            }
            i = j
        }
    } else {
        a.chunked(cols).forEachIndexed { r, row ->
            out += AssetRow("r:${row.first().id}", bi, global + r * cols, row)
        }
    }
}

/** Every row of the grid, worked out from scratch. */
internal fun buildGrid(buckets: List<Bucket>, cols: Int, groupBy: GroupBy): List<GridItem> {
    val out = ArrayList<GridItem>()
    var global = 0
    buckets.forEachIndexed { bi, b ->
        bucketRows(b, bi, global, cols, groupBy, out)
        global += b.size
    }
    return out
}

/**
 * Gives the same list as [buildGrid], but keeps each bucket's rows from the previous call and only works
 * out again the buckets that changed. A timeline replaces its list of buckets every time one month loads;
 * the other months are the same objects at the same place, so their rows are reused as they are.
 *
 * A bucket's rows are reused only when it is the very same [Bucket] object, at the same position, with the
 * same number of assets before it, for the same column count and grouping: everything its rows depend on.
 * Not thread-safe: one grid uses it from composition only.
 */
internal class GridBuilder {
    private class Part(val bucket: Bucket, val index: Int, val start: Int, val rows: List<GridItem>)

    private var parts: Map<String, Part> = emptyMap()
    private var cols = -1
    private var groupBy: GroupBy? = null
    private var lastSize = 0

    fun build(buckets: List<Bucket>, cols: Int, groupBy: GroupBy): List<GridItem> {
        val old = if (cols == this.cols && groupBy == this.groupBy) parts else emptyMap()
        val next = HashMap<String, Part>(buckets.size * 2)
        val out = ArrayList<GridItem>(lastSize)
        var global = 0
        buckets.forEachIndexed { bi, b ->
            val kept = old[b.key]
            val part = if (kept != null && kept.bucket === b && kept.index == bi && kept.start == global) {
                kept
            } else {
                val rows = ArrayList<GridItem>()
                bucketRows(b, bi, global, cols, groupBy, rows)
                Part(b, bi, global, rows)
            }
            next[b.key] = part
            out.addAll(part.rows)
            global += b.size
        }
        parts = next
        this.cols = cols
        this.groupBy = groupBy
        lastSize = out.size
        return out
    }
}
