package com.gestores.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Formato de tiempo del cronómetro, temporizador y ejercicio. */
class FormatoTiempoTest {
    @Test fun `cero`() = assertEquals("00:00.00", formatoTiempo(0))
    @Test fun `centesimas de segundo`() = assertEquals("00:01.23", formatoTiempo(1_234))
    @Test fun `minutos y segundos`() = assertEquals("02:05.50", formatoTiempo(125_500))
    @Test fun `sin centesimas para el temporizador`() = assertEquals("02:05", formatoTiempo(125_500, centesimas = false))
    @Test fun `con horas no muestra centesimas`() = assertEquals("1:00:05", formatoTiempo(3_605_000))
    @Test fun `un tiempo negativo se muestra como cero`() = assertEquals("00:00", formatoTiempo(-5_000, centesimas = false))
}
