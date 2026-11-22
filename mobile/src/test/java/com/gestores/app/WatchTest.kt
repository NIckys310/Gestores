package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/** Cronómetro y alarmas del reloj. */
class WatchTest {
    @Before fun limpiar() = WatchState.resetChrono()

    @Test fun `el cronometro empieza detenido en cero`() {
        assertFalse(WatchState.chronoRunning.value)
        assertEquals(0L, WatchState.chronoNow())
    }

    @Test fun `al iniciar, el cronometro avanza`() {
        WatchState.toggleChrono()
        Thread.sleep(30)
        assertTrue(WatchState.chronoRunning.value)
        assertTrue(WatchState.chronoNow() >= 25)
    }

    @Test fun `en pausa el cronometro no avanza`() {
        WatchState.toggleChrono(); Thread.sleep(20); WatchState.toggleChrono()
        val paused = WatchState.chronoNow()
        Thread.sleep(30)
        assertEquals(paused, WatchState.chronoNow())
    }

    @Test fun `reiniciar borra el tiempo y las vueltas`() {
        WatchState.toggleChrono(); WatchState.laps.value = listOf(1000L)
        WatchState.resetChrono()
        assertEquals(0L, WatchState.chronoNow())
        assertTrue(WatchState.laps.value.isEmpty())
    }

    private fun alarmIn(minutes: Int): Alarm {
        val c = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
        return Alarm(1, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    @Test fun `una alarma para mas tarde suena hoy`() {
        val en = Alarms.nextTime(alarmIn(90)) - System.currentTimeMillis()
        assertTrue("faltan $en ms", en in 60 * 60_000L..91 * 60_000L)
    }

    @Test fun `una alarma de una hora que ya paso suena manana`() {
        val en = Alarms.nextTime(alarmIn(-30)) - System.currentTimeMillis()
        assertTrue("faltan $en ms", en in 22 * 3600_000L..24 * 3600_000L)
    }

    @Test fun `la alarma suena en el minuto exacto (segundos en cero)`() {
        val c = Calendar.getInstance().apply { timeInMillis = Alarms.nextTime(Alarm(1, 6, 30)) }
        assertEquals(6, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, c.get(Calendar.MINUTE))
        assertEquals(0, c.get(Calendar.SECOND))
    }

    @Test fun `una alarma nueva viene encendida`() = assertTrue(Alarm(1, 7, 0).on)
}
