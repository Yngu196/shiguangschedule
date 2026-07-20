package com.xingheyuzhuan.shiguangschedule.tool

import android.util.Log
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseImportExport.ImportCourseJsonModel
import org.apache.poi.ss.usermodel.*
import java.io.InputStream

/**
 * 教务系统"大课表"Excel 解析器。
 *
 * 支持格式：教务系统导出的横向周课表 .xlsx 文件。
 * 布局结构（每 11 行为一个数据块）：
 *   Row+0: 周次标题行（含「第N周」合并单元格）
 *   Row+1: 日期行（MM-DD 格式）
 *   Row+2: 星期行（一~日）
 *   Row+3~Row+9: 第一大节~第七大节，共 7 个时间段行
 * 每 7 列为一周（周一~周日），多周横向排列。
 * 单元格内容格式：_x000D_\n课程名[类型]_x000D_\n教师名_x000D_\n节次_x000D_\n教室_x000D_\n
 */
object ExcelCourseParser {

    data class ParsedResult(
        val courses: List<ImportCourseJsonModel>,
        val semesterStartDate: String?,   // yyyy-MM-dd
        val semesterTotalWeeks: Int
    )

    fun parse(inputStream: InputStream): ParsedResult {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)

        val courseMap = mutableMapOf<String, ImportCourseJsonModel>() // key -> merged course
        val earliestDates = mutableListOf<String>()
        var maxWeekNum = 0
        var totalCellsProcessed = 0
        var totalSkippedPartsSize = 0
        var totalWeeksFound = 0

        Log.d("ExcelCourseParser", "=== Excel 解析开始 ===")
        Log.d("ExcelCourseParser", "Sheet 行数: physicalNumberOfRows=${sheet.physicalNumberOfRows}, " +
                "lastRowNum=${sheet.lastRowNum}")

        // ---------- 1. 从 A1 解析学期信息 ----------
        var semesterStartDate: String? = null
        val titleRow = sheet.getRow(0)
        if (titleRow != null) {
            val titleCell = titleRow.getCell(0)
            if (titleCell != null) {
                val title = titleCell.stringCellValue.trim()
                val semesterRegex = Regex("""(\d{4})-(\d{4})-(\d)""")
                val match = semesterRegex.find(title)
                if (match != null) {
                    val startYear = match.groupValues[1].toInt()
                    val semester = match.groupValues[3].toInt()
                    semesterStartDate = if (semester == 1) {
                        "${startYear}-09-01"
                    } else {
                        "${startYear}-02-24"
                    }
                }
            }
        }

        // ---------- 2. 逐行扫描，寻找数据块 ----------
        val rowCount = sheet.physicalNumberOfRows
        var rowIdx = 0

        while (rowIdx < rowCount) {
            val row = sheet.getRow(rowIdx) ?: run { rowIdx++; continue }

            val lastCol = row.lastCellNum.toInt()
            val weekEntries = mutableListOf<Pair<Int, Int>>() // (colIndex, weekNumber)

            // 在当前行中查找所有「第N周」标记
            for (col in 1 until lastCol) {
                val cell = row.getCell(col)
                if (cell != null) {
                    val cellText = cell.stringCellValue.trim()
                    val weekMatch = Regex("""第(\d+)周""").find(cellText)
                    if (weekMatch != null) {
                        val weekNum = weekMatch.groupValues[1].toInt()
                        weekEntries.add(col to weekNum)
                    }
                }
            }

            if (weekEntries.isNotEmpty()) {
                totalWeeksFound += weekEntries.size
                Log.d("ExcelCourseParser", "  发现数据块: rowIdx=$rowIdx, 周次=${weekEntries.map { it.second }}, " +
                        "dateRowExists=${sheet.getRow(rowIdx + 1) != null}, weekdayRowExists=${sheet.getRow(rowIdx + 2) != null}")

                // 数据块结构：rowIdx+0=周次标题, +1=日期, +2=星期, +3~+9=七大节
                val dateRow = sheet.getRow(rowIdx + 1)
                val weekdayRow = sheet.getRow(rowIdx + 2)

                if (dateRow == null || weekdayRow == null) {
                    rowIdx++
                    continue
                }

                // 记录最早日期（用于 fallback 学期起始）
                for ((col, _) in weekEntries) {
                    val dateCell = dateRow.getCell(col)
                    if (dateCell != null) {
                        val dateText = dateCell.stringCellValue.trim()
                        if (dateText.isNotBlank() && dateText.contains("-")) {
                            earliestDates.add(dateText)
                        }
                    }
                }

                // 处理该数据块中的每一周
                for ((weekCol, weekNum) in weekEntries) {
                    if (weekNum > maxWeekNum) maxWeekNum = weekNum

                    // 每周占 7 列 (周一~周日)
                    for (dayOffset in 0 until 7) {
                        val day = dayOffset + 1 // 1=周一 ... 7=周日
                        val col = weekCol + dayOffset

                        // 遍历 7 个时间段行
                        for (timeSlotIdx in 0 until 7) {
                            val timeRowIdx = rowIdx + 3 + timeSlotIdx
                            if (timeRowIdx >= rowCount) break

                            val timeRow = sheet.getRow(timeRowIdx) ?: continue
                            val cell = timeRow.getCell(col) ?: continue

                            val cellText = cell.stringCellValue.trim()
                            if (cellText.isBlank()) continue

                            totalCellsProcessed++
                            val parts = cellText.split("\n")
                            val nonEmptyParts = parts.map { it.trim { c -> c == '\r' } }.filter { it.isNotBlank() }

                            if (nonEmptyParts.size < 3) {
                                totalSkippedPartsSize++
                                if (totalSkippedPartsSize <= 5) {
                                    Log.w("ExcelCourseParser", "  [跳过] cell($timeRowIdx,$col) parts.size=${nonEmptyParts.size}, " +
                                            "cellText(repr)=${cellText.take(60)}")
                                }
                                continue
                            }

                            val courseName = nonEmptyParts[0].trim()
                            val teacher = nonEmptyParts[1].trim()

                            // 单元格格式为"课程名\n教师\n教室\n1-16周"，无标准节次码
                            // 教室名在第3部分
                            val position = if (nonEmptyParts.size >= 3) nonEmptyParts[2].trim() else ""

                            // 从数据块行偏移推算节次
                            // timeSlotIdx: 0=第一大节(1-2节), 1=第二大节(3-4节),
                            //              2=第三大节(5-6节), 3=第四大节(7-8节),
                            //              4=第五大节(9-10节), 5=第六大节(11-12节),
                            //              6=第七大节(13-14节)
                            val startSection = timeSlotIdx * 2 + 1
                            val endSection = timeSlotIdx * 2 + 2

                            // 按课程身份去重合并周次
                            val courseKey = "$courseName|$teacher|$position|$day|$startSection|$endSection"
                            val existing = courseMap[courseKey]
                            if (existing != null) {
                                // 合并周次
                                val mergedWeeks = if (weekNum !in existing.weeks) {
                                    existing.weeks + weekNum
                                } else {
                                    existing.weeks
                                }
                                courseMap[courseKey] = existing.copy(weeks = mergedWeeks)
                            } else {
                                courseMap[courseKey] = ImportCourseJsonModel(
                                    name = courseName,
                                    teacher = teacher,
                                    position = position,
                                    day = day,
                                    startSection = startSection,
                                    endSection = endSection,
                                    weeks = listOf(weekNum),
                                    color = null
                                )
                            }
                        }
                    }
                }
            }

            rowIdx++
        }

        workbook.close()

        // ---------- 3. 补充 semesterStartDate（如果标题解析失败，用最早日期）----------
        if (semesterStartDate == null && earliestDates.isNotEmpty()) {
            // 从标题中获取学年
            val yearFromTitle = try {
                val row0 = sheet.getRow(0)
                val c0 = row0?.getCell(0)
                val t = c0?.stringCellValue ?: ""
                val m = Regex("""(\d{4})-(\d{4})""").find(t)
                m?.groupValues?.get(1)?.toInt()
            } catch (_: Exception) { null }

            if (yearFromTitle != null) {
                val earliestDateStr = earliestDates.minOrNull() ?: ""
                if (earliestDateStr.contains("-")) {
                    semesterStartDate = "$yearFromTitle-${earliestDateStr.trim()}"
                }
            }
        }

        val resultCourses = courseMap.values.toList()
        val coursesWithNullSection = resultCourses.count { it.startSection == null || it.endSection == null }

        Log.d("ExcelCourseParser", "=== Excel 解析总结 ===")
        Log.d("ExcelCourseParser", "  totalCellsProcessed=$totalCellsProcessed")
        Log.d("ExcelCourseParser", "  totalWeeksFound=$totalWeeksFound")
        Log.d("ExcelCourseParser", "  totalSkippedPartsSize=$totalSkippedPartsSize")
        Log.d("ExcelCourseParser", "  earliestDates=$earliestDates")
        Log.d("ExcelCourseParser", "  semesterStartDate=$semesterStartDate")
        Log.d("ExcelCourseParser", "  semesterTotalWeeks=$maxWeekNum")
        Log.d("ExcelCourseParser", "  courseMap.size=${resultCourses.size}")
        Log.d("ExcelCourseParser", "  其中 startSection/endSection 为 null 的课程数=$coursesWithNullSection")

        return ParsedResult(
            courses = resultCourses,
            semesterStartDate = semesterStartDate,
            semesterTotalWeeks = maxWeekNum
        )
    }
}
