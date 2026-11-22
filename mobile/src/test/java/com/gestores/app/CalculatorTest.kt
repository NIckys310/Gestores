package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import org.junit.Assert.assertEquals
import org.junit.Test

/** La calculadora del reloj funciona como una de bolsillo. */
class CalculatorTest {
    private fun calculate(keys: String) = Calculator().apply { write(keys) }.screen

    @Test fun `empieza en cero`() = assertEquals("0", Calculator().screen)
    @Test fun `suma`() = assertEquals("15", calculate("12+3="))
    @Test fun `resta`() = assertEquals("9", calculate("12−3="))
    @Test fun `multiplica`() = assertEquals("36", calculate("12×3="))
    @Test fun `divide con decimales`() = assertEquals("2.5", calculate("5÷2="))
    @Test fun `encadena operaciones de izquierda a derecha`() = assertEquals("20", calculate("2+3×4="))
    @Test fun `porcentaje`() = assertEquals("0.5", calculate("50%"))
    @Test fun `dividir por cero muestra Error`() = assertEquals("Error", calculate("7÷0="))
    @Test fun `C borra todo`() = assertEquals("0", calculate("123+4C"))
    @Test fun `borrar quita el ultimo numero`() = assertEquals("12", calculate("123⌫"))
    @Test fun `borrar todo deja cero`() = assertEquals("0", calculate("1⌫⌫"))
    @Test fun `numeros decimales`() = assertEquals("0.75", calculate(".5+.25="))
    @Test fun `no permite dos puntos en el mismo numero`() = assertEquals("1.25", calculate("1.2.5"))
    @Test fun `no pasa de 12 cifras`() = assertEquals(12, calculate("1234567890123456").length)
    @Test fun `despues de = un numero nuevo empieza de cero`() = assertEquals("7", calculate("2+2=7"))
    @Test fun `cambiar de operacion no calcula dos veces`() = assertEquals("6", calculate("3+×2="))
    @Test fun `un resultado sin decimales no muestra punto`() = assertEquals("4", calculate("2×2="))
    @Test fun `teclas desconocidas se ignoran`() = assertEquals("5", calculate("5x"))
}
