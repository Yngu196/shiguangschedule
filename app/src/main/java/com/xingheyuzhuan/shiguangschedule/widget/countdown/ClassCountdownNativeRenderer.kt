package com.xingheyuzhuan.shiguangschedule.widget.countdown

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.xingheyuzhuan.shiguangschedule.MainActivity
import com.xingheyuzhuan.shiguangschedule.R
import com.xingheyuzhuan.shiguangschedule.widget.WidgetSnapshot
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

object ClassCountdownNativeRenderer {

    fun render(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_class_countdown_native)
        resetState(rv)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        val currentWeek = snapshot.current_week
        if (currentWeek <= 0) {
            showStatus(rv, context.getString(R.string.title_vacation))
            return rv
        }

        val now = LocalTime.now()
        val todayStr = LocalDate.now().toString()
        val todayCourses = snapshot.courses
            .filter { it.date == todayStr || it.date.isBlank() }
            .filter { !it.is_skipped }
            .sortedBy { it.start_time }

        if (todayCourses.isEmpty()) {
            showStatus(rv, context.getString(R.string.text_no_courses_today))
            return rv
        }

        // 找当前正在上的课
        val currentCourse = todayCourses.firstOrNull { course ->
            try {
                val start = LocalTime.parse(course.start_time)
                val end = LocalTime.parse(course.end_time)
                !now.isBefore(start) && now.isBefore(end)
            } catch (_: Exception) { false }
        }

        if (currentCourse != null) {
            // 课内模式
            val endTime = try { LocalTime.parse(currentCourse.end_time) } catch (_: Exception) { now }
            val remainingMinutes = Duration.between(now, endTime).toMinutes()
            showCountdown(rv, currentCourse.name, remainingMinutes, currentCourse.end_time)
        } else {
            // 找下一节课
            val nextCourse = todayCourses.firstOrNull { course ->
                try {
                    val end = LocalTime.parse(course.end_time)
                    now.isBefore(end)
                } catch (_: Exception) { false }
            }

            if (nextCourse != null) {
                showIdle(rv, context, nextCourse.name, nextCourse.start_time)
            } else {
                showStatus(rv, context.getString(R.string.widget_today_courses_finished))
            }
        }

        return rv
    }

    private fun resetState(rv: RemoteViews) {
        rv.setViewVisibility(R.id.container_countdown, View.GONE)
        rv.setViewVisibility(R.id.container_status, View.GONE)
    }

    private fun showCountdown(rv: RemoteViews, courseName: String, remainingMinutes: Long, endTime: String) {
        rv.setViewVisibility(R.id.container_countdown, View.VISIBLE)
        rv.setTextViewText(R.id.tv_course_name, "「$courseName」")
        rv.setTextViewText(R.id.tv_countdown_minutes, if (remainingMinutes <= 0) "即将下课" else "还有 $remainingMinutes 分钟")
        rv.setTextViewText(R.id.tv_end_time, "下课 $endTime")
    }

    private fun showIdle(rv: RemoteViews, context: Context, nextCourseName: String, nextStartTime: String) {
        rv.setViewVisibility(R.id.container_countdown, View.VISIBLE)
        rv.setTextViewText(R.id.tv_course_name, context.getString(R.string.widget_currently_idle))
        rv.setTextViewText(R.id.tv_countdown_minutes, "「$nextCourseName」$nextStartTime 上课")
        rv.setTextViewText(R.id.tv_end_time, "")
    }

    private fun showStatus(rv: RemoteViews, text: String) {
        rv.setViewVisibility(R.id.container_status, View.VISIBLE)
        rv.setTextViewText(R.id.tv_status, text)
    }
}