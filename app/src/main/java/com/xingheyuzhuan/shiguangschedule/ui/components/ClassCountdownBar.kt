package com.xingheyuzhuan.shiguangschedule.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.R
import com.xingheyuzhuan.shiguangschedule.ui.theme.LocalIsDarkTheme
import com.xingheyuzhuan.shiguangschedule.ui.today.CourseDisplayModel
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 下课倒计时组件。
 * 实时计算当前课程的剩余时间（精确到分钟），并在无课时显示对应文本。
 */
@Composable
fun ClassCountdownBar(
    courses: List<CourseDisplayModel>,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    val isDark = LocalIsDarkTheme.current

    // 每分钟刷新一次（对齐到下一分钟整秒）
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            val secondsToNextMinute = (60 - now.second).coerceAtLeast(1)
            kotlinx.coroutines.delay(secondsToNextMinute * 1000L)
            currentTime = LocalTime.now()
        }
    }

    // 查找当前正在进行中的课程，并计算剩余分钟数
    data class CountdownInfo(val courseName: String, val minutesLeft: Int)

    val countdownInfo = remember(courses, currentTime) {
        for (model in courses) {
            val start = try { LocalTime.parse(model.startTime ?: "") } catch (_: Exception) { continue }
            val end = try { LocalTime.parse(model.endTime ?: "") } catch (_: Exception) { continue }
            // 当前时间在 [start, end) 区间内
            if (!start.isAfter(currentTime) && currentTime.isBefore(end)) {
                val minutes = ChronoUnit.MINUTES.between(currentTime, end).toInt()
                if (minutes > 0) {
                    return@remember CountdownInfo(model.course.name, minutes)
                }
                break // minutes == 0：仍在课时内但整分钟计数已归零
            }
        }
        null
    }

    val hasCoursesToday = courses.isNotEmpty()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))

            when {
                countdownInfo != null -> {
                    Text(
                        text = "距「${countdownInfo.courseName}」下课还有 ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${countdownInfo.minutesLeft} 分钟",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                hasCoursesToday -> {
                    Text(
                        text = "当前未上课",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "当前无课",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
