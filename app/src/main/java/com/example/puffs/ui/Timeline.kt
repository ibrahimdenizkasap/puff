package com.example.puffs.ui
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.puffs.data.Puff
import java.text.SimpleDateFormat
import java.util.*
import com.example.puffs.util.DayRollover

// --- Minute-range compaction helpers (same file) ---
data class MinuteRange(val startTs: Long, val endTs: Long, val count: Int)

/** Collapse consecutive puffs that occur within the same minute into a single row. */
private fun minuteRanges(puffs: List<Puff>): List<MinuteRange> {
    if (puffs.isEmpty()) return emptyList()
    val sorted = puffs.sortedBy { it.timestamp } // ascending
    val out = mutableListOf<MinuteRange>()
    var s = sorted.first().timestamp
    var e = s
    var c = 1
    fun sameMinute(a: Long, b: Long): Boolean = (a / 60_000) == (b / 60_000)
    for (i in 1 until sorted.size) {
        val ts = sorted[i].timestamp
        if (sameMinute(e, ts)) { e = ts; c++ }
        else { out += MinuteRange(s, e, c); s = ts; e = ts; c = 1 }
    }
    out += MinuteRange(s, e, c)
    return out
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupedTimeline(
    allPuffs: List<Puff>,
    daysBack: Int = 7,
    modifier: Modifier = Modifier
) {
    // Group by local date (YYYY-MM-DD), newest date first
    val tz = TimeZone.getDefault()
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    timeFmt.timeZone = tz

    // filter to last N days
    val cal = Calendar.getInstance(tz).apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
    val minMillis = (cal.timeInMillis - (daysBack.toLong()-1) * 24L*60*60*1000)

    val grouped: LinkedHashMap<String, List<Puff>> = remember(allPuffs, minMillis) {
        val within = allPuffs.filter { it.timestamp >= minMillis }
        val byDay = within.groupBy { DayRollover.logicalDate(it.timestamp) }
        // sort days desc (newest first), and items desc (newest first)
        val ordered = byDay.toSortedMap(compareByDescending { it })
        val linked = LinkedHashMap<String, List<Puff>>()
        for ((day, list) in ordered) {
            linked[day] = list.sortedByDescending { it.timestamp }
        }
        linked
    }

    Surface(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            grouped.forEach { (day, items) ->
                stickyHeader {
                    DayHeader(day)
                }
                // cumulative resets per day; since we show newest first, cumulative = size - index
                // Build minute ranges for this day
                val rangesAsc = minuteRanges(items)            // ascending by time
                val rangesDesc = rangesAsc.asReversed()        // newest first for display
                val dayTotal = rangesAsc.sumOf { it.count }    // total puffs that day

                var remaining = dayTotal
                itemsIndexed(rangesDesc) { _, r ->
                    // show "HH:MM – HH:MM" and the cumulative count *at that moment*
                    TimelineRow(
                        time = "${timeFmt.format(Date(r.startTs))} – ${timeFmt.format(Date(r.endTs))}",
                        cumulative = remaining.toString()
                    )
                    remaining -= r.count
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: String) {
    // header bar like your web app
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = day,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
}

@Composable
private fun TimelineRow(time: String, cumulative: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(time, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(cumulative, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
}

@Composable
fun SessionHistoryList(
    allPuffs: List<Puff>,
    daysBack: Int = 7,
    modifier: Modifier = Modifier
) {
    val tz = TimeZone.getDefault()
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = tz }

    val cal = Calendar.getInstance(tz).apply {
        set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0)
        set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)
    }
    val minMillis = cal.timeInMillis - (daysBack.toLong()-1) * 24L*60*60*1000

    val within = allPuffs.filter { it.timestamp >= minMillis }
    val byDay = within.groupBy { DayRollover.logicalDate(it.timestamp) }
    val rows = byDay.toList()
        .sortedByDescending { it.first } // newest day first
        .map { (d, list) -> d to list.size }

    Surface(modifier) {
        LazyColumn {
            // header row
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Puffs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Divider()
            }
            items(rows) { (date, count) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(date, style = MaterialTheme.typography.bodyLarge)
                    Text(count.toString(), style = MaterialTheme.typography.bodyLarge)
                }
                Divider()
            }
        }
    }
}