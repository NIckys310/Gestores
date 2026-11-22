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
import org.junit.Test

/** Qué hace cada gesto y cuáles preguntan antes de poner sonido. */
class ActionsTest {
    @Test fun `las acciones con sonido preguntan antes`() {
        for (r in Actions.sound) assertTrue(r.label, ActionRunner.needsConfirm(r))
    }

    @Test fun `camara, linterna y apps se hacen de una vez`() {
        for (r in Actions.direct) assertFalse(r.label, ActionRunner.needsConfirm(r))
        assertFalse(ActionRunner.needsConfirm(Rule("open_app", "com.whatsapp", "📱 Abrir WhatsApp")))
        for (r in Actions.watchApps) assertFalse(r.label, ActionRunner.needsConfirm(r))
    }

    @Test fun `la pregunta de musica es la que pidio la usuaria`() =
        assertEquals("¿Deseas reproducir o pausar la música?", ActionRunner.question(Rule("media", "playpause")))

    @Test fun `cada accion con sonido tiene su propia pregunta`() {
        val questions = Actions.sound.map { ActionRunner.question(it) }
        assertEquals(questions.size, questions.toSet().size)
        for (p in questions) assertTrue(p, p.startsWith("¿") && p.endsWith("?"))
    }

    @Test fun `todas las acciones tienen un nombre con icono`() {
        for (r in Actions.direct + Actions.sound + Actions.watchApps) assertTrue(r.label, r.label.length > 3 && !r.label[0].isLetter())
    }

    @Test fun `abrir una app del reloj no ofrece abrir Gestores dentro de Gestores`() {
        assertFalse(Actions.watchApps.any { it.value == "gestos" })
        assertEquals(WatchApps.all.size - 1, Actions.watchApps.size)
    }

    @Test fun `la accion vacia no hace nada`() {
        assertEquals("none", Rule().type)
        assertEquals("Sin acción", Rule().label)
    }
}
