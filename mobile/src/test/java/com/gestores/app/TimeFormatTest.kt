package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import org.junit.Assert.assertEquals
import org.junit.Test

/** Formato de tiempo del cronómetro, temporizador y ejercicio. */
class TimeFormatTest {
    @Test fun `cero`() = assertEquals("00:00.00", formatTime(0))
    @Test fun `centesimas de segundo`() = assertEquals("00:01.23", formatTime(1_234))
    @Test fun `minutos y segundos`() = assertEquals("02:05.50", formatTime(125_500))
    @Test fun `sin centesimas para el temporizador`() = assertEquals("02:05", formatTime(125_500, hundredths = false))
    @Test fun `con horas no muestra centesimas`() = assertEquals("1:00:05", formatTime(3_605_000))
    @Test fun `un tiempo negativo se muestra como cero`() = assertEquals("00:00", formatTime(-5_000, hundredths = false))
}
