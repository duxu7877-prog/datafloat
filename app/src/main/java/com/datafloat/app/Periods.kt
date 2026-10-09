package com.datafloat.app

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

object Periods {
    fun startOfDayMs(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** First day of the billing cycle containing [today]. Short months clamp to their last day. */
    fun cycleStart(today: LocalDate, resetDay: Int): LocalDate {
        fun clamp(ym: YearMonth): LocalDate = ym.atDay(minOf(resetDay.coerceIn(1, 31), ym.lengthOfMonth()))
        val thisMonth = clamp(YearMonth.from(today))
        return if (!today.isBefore(thisMonth)) thisMonth else clamp(YearMonth.from(today).minusMonths(1))
    }
}
