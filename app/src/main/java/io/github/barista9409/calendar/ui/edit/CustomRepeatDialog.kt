package io.github.barista9409.calendar.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.barista9409.calendar.util.DateUtils
import io.github.barista9409.calendar.util.RRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 繰り返しの「カスタム」（Google カレンダーと同じ項目） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRepeatDialog(start: LocalDate, initial: RRule?, onDone: (RRule) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var freq by remember { mutableStateOf(initial?.freq ?: RRule.Freq.WEEKLY) }
    var interval by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var days by remember { mutableStateOf(initial?.byDay?.ifEmpty { null } ?: setOf(start.dayOfWeek)) }
    var byWeekday by remember { mutableStateOf(initial?.monthlyByWeekday ?: false) }
    // 終了：0 = なし、1 = 日付、2 = 回数
    var endMode by remember { mutableStateOf(when { initial?.until != null -> 1; initial?.count != null -> 2; else -> 0 }) }
    var until by remember { mutableStateOf(initial?.until ?: start.plusMonths(1)) }
    var count by remember { mutableStateOf((initial?.count ?: 10).toString()) }
    var unitMenu by remember { mutableStateOf(false) }
    var pickUntil by remember { mutableStateOf(false) }

    fun build() = RRule(
        freq = freq, interval = interval.toIntOrNull()?.coerceIn(1, 999) ?: 1,
        byDay = if (freq == RRule.Freq.WEEKLY) days else emptySet(),
        monthlyByWeekday = freq == RRule.Freq.MONTHLY && byWeekday,
        until = if (endMode == 1) until else null,
        count = if (endMode == 2) count.toIntOrNull()?.coerceIn(1, 999) else null,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = scheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("カスタム", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("繰り返す間隔", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(3) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(88.dp))
                    Spacer(Modifier.width(12.dp))
                    Box {
                        TextButton(onClick = { unitMenu = true }) { Text(freq.unit + "ごと", fontSize = 16.sp) }
                        DropdownMenu(unitMenu, { unitMenu = false }) {
                            RRule.Freq.entries.forEach { f -> DropdownMenuItem(text = { Text(f.unit) }, onClick = { freq = f; unitMenu = false }) }
                        }
                    }
                }
                if (freq == RRule.Freq.WEEKLY) {
                    Text("繰り返す曜日", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY).forEach { d ->
                            val on = d in days
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(if (on) scheme.primary else scheme.surfaceVariant)
                                    .clickable { days = if (on && days.size > 1) days - d else days + d },
                                contentAlignment = Alignment.Center,
                            ) { Text(RRule.jp(d), color = if (on) scheme.onPrimary else scheme.onSurface, fontSize = 14.sp) }
                        }
                    }
                }
                if (freq == RRule.Freq.MONTHLY) {
                    Choice("毎月${start.dayOfMonth}日", !byWeekday) { byWeekday = false }
                    Choice("毎月第${RRule.nth(start)}${RRule.jp(start.dayOfWeek)}曜日", byWeekday) { byWeekday = true }
                }
                HorizontalDivider()
                Text("終了", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                Choice("なし", endMode == 0) { endMode = 0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(endMode == 1, { endMode = 1 })
                    Text("終了日", Modifier.width(64.dp))
                    Text(DateUtils.date(until) + " ${until.year}年", fontSize = 15.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, scheme.outlineVariant, RoundedCornerShape(8.dp))
                            .clickable { endMode = 1; pickUntil = true }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(endMode == 2, { endMode = 2 })
                    OutlinedTextField(count, { count = it.filter(Char::isDigit).take(3); endMode = 2 }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(88.dp))
                    Text("  回繰り返す")
                }
                Text(build().label(start), fontSize = 13.sp, color = scheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                    TextButton(onClick = { onDone(build()) }) { Text("完了") }
                }
            }
        }
    }

    if (pickUntil) {
        val st = rememberDatePickerState(initialSelectedDateMillis = until.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickUntil = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { until = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickUntil = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickUntil = false }) { Text("キャンセル") } },
        ) { DatePicker(st) }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(label, fontSize = 15.sp)
    }
}
