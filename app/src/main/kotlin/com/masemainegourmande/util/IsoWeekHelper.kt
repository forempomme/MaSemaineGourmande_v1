package com.masemainegourmande.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

object IsoWeekHelper {

    data class YearWeek(val year: Int, val week: Int) {
        val key: String get() = "$year-W${week.toString().padStart(2, '0')}"
    }

    fun today(): YearWeek {
        val d = LocalDate.now()
        return YearWeek(
            year = d.get(IsoFields.WEEK_BASED_YEAR),
            week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        )
    }

    fun weeksInYear(year: Int): Int {
        // A year has 53 weeks if Dec 28 falls on week 53
        val dec28 = LocalDate.of(year, 12, 28)
        return dec28.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    }

    /** Monday and Sunday dates for (year, week). */
    fun weekBounds(year: Int, week: Int): Pair<LocalDate, LocalDate> {
        val monday = LocalDate.ofYearDay(year, 1)
            .with(IsoFields.WEEK_BASED_YEAR, year.toLong())
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
            .with(java.time.DayOfWeek.MONDAY)
        return monday to monday.plusDays(6)
    }

    private val FMT = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
    fun fmt(date: LocalDate): String = date.format(FMT)

    /** "2024-W03" → YearWeek(2024, 3) */
    fun parseKey(key: String): YearWeek? {
        val m = Regex("""(\d{4})-W(\d+)""").find(key) ?: return null
        return YearWeek(m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    /** How many weeks ago was (year, week) relative to current week. Negative = future. */
    fun weeksAgo(year: Int, week: Int): Int {
        val cur = today()
        return (cur.year - year) * 52 + (cur.week - week)
    }

    fun lastCookedLabel(weeksAgo: Int): String = when {
        weeksAgo == 0 -> "cette semaine"
        weeksAgo == 1 -> "la semaine dernière"
        weeksAgo < 5  -> "il y a $weeksAgo semaines"
        else -> {
            val months = (weeksAgo / 4.3).toInt()
            if (months < 12) "il y a $months mois" else "il y a plus d'un an"
        }
    }
}
