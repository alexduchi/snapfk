package com.neo.dataflow.core
import com.neo.dataflow.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class EngineTest {
 @Test fun decimalUnits(){assertEquals(1_000_000_000L,Units.bytes(1.0,"Go"));assertEquals(5_000_000L,Units.bytes(5.0,"Mo"))}
 @Test fun margin(){val p=Plan(5*Units.GB,LocalDate.now(),LocalDate.now().plusDays(4),20);assertEquals(Units.GB,Engine.reserve(p));assertEquals(4*Units.GB,Engine.usable(p))}
 @Test fun dailyBudget(){val p=Plan(5*Units.GB,LocalDate.now(),LocalDate.now().plusDays(4),20);assertEquals(600_000_000L,Engine.budget(4*Units.GB,p))}
 @Test fun exhausted(){val p=Plan(Units.GB,LocalDate.now(),LocalDate.now().plusDays(2),20);assertEquals(0,Engine.budget(0,p))}
 @Test fun hundredPercentMargin(){val p=Plan(Units.GB,LocalDate.now(),LocalDate.now().plusDays(2),100);assertEquals(0,Engine.budget(Units.GB,p))}
 @Test fun endedPeriod(){val p=Plan(Units.GB,LocalDate.now().minusDays(2),LocalDate.now().minusDays(1),0);assertEquals(0,Engine.daysRemaining(p));assertEquals(0,Engine.budget(Units.GB,p))}
 @Test fun projectionNeverNegative(){val p=Plan(Units.GB,LocalDate.now(),LocalDate.now().plusDays(4),0);assertEquals(0,Engine.projectedRemaining(Units.GB,Units.GB,p))}
 @Test(expected=IllegalArgumentException::class) fun negativeUnitRejected(){Units.bytes(-1.0,"Go")}
}
