package com.gestores.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** La calculadora del reloj funciona como una de bolsillo. */
class CalculadoraTest {
    private fun calcular(teclas: String) = Calculadora().apply { escribir(teclas) }.pantalla

    @Test fun `empieza en cero`() = assertEquals("0", Calculadora().pantalla)
    @Test fun `suma`() = assertEquals("15", calcular("12+3="))
    @Test fun `resta`() = assertEquals("9", calcular("12−3="))
    @Test fun `multiplica`() = assertEquals("36", calcular("12×3="))
    @Test fun `divide con decimales`() = assertEquals("2.5", calcular("5÷2="))
    @Test fun `encadena operaciones de izquierda a derecha`() = assertEquals("20", calcular("2+3×4="))
    @Test fun `porcentaje`() = assertEquals("0.5", calcular("50%"))
    @Test fun `dividir por cero muestra Error`() = assertEquals("Error", calcular("7÷0="))
    @Test fun `C borra todo`() = assertEquals("0", calcular("123+4C"))
    @Test fun `borrar quita el ultimo numero`() = assertEquals("12", calcular("123⌫"))
    @Test fun `borrar todo deja cero`() = assertEquals("0", calcular("1⌫⌫"))
    @Test fun `numeros decimales`() = assertEquals("0.75", calcular(".5+.25="))
    @Test fun `no permite dos puntos en el mismo numero`() = assertEquals("1.25", calcular("1.2.5"))
    @Test fun `no pasa de 12 cifras`() = assertEquals(12, calcular("1234567890123456").length)
    @Test fun `despues de = un numero nuevo empieza de cero`() = assertEquals("7", calcular("2+2=7"))
    @Test fun `cambiar de operacion no calcula dos veces`() = assertEquals("6", calcular("3+×2="))
    @Test fun `un resultado sin decimales no muestra punto`() = assertEquals("4", calcular("2×2="))
    @Test fun `teclas desconocidas se ignoran`() = assertEquals("5", calcular("5x"))
}
