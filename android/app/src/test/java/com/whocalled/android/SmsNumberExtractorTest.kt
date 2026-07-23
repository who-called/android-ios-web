package com.whocalled.android

import com.whocalled.android.util.SmsNumberExtractor
import com.whocalled.android.util.SmsNumberExtractor.PersonInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsNumberExtractorTest {

    @Test
    fun extractsFromPeopleTelUri() {
        val n = SmsNumberExtractor.extractSenderNumber(
            peopleList = listOf(PersonInfo("tel:+33612345678")),
            messagingPerson = null,
            title = "Jean",
            text = "Coucou",
            bigText = null,
        )
        assertEquals("+33612345678", n)
    }

    @Test
    fun fallsBackToMessagingPerson() {
        val n = SmsNumberExtractor.extractSenderNumber(
            peopleList = emptyList(),
            messagingPerson = PersonInfo("tel:0899123456"),
            title = "Promo",
            text = "Gagnez…",
            bigText = null,
        )
        assertEquals("0899123456", n)
    }

    @Test
    fun usesTitleWhenItLooksLikeANumber() {
        val n = SmsNumberExtractor.extractSenderNumber(
            peopleList = null,
            messagingPerson = null,
            title = "+33 6 12 34 56 78",
            text = "message",
            bigText = null,
        )
        assertEquals("+33612345678", n)
    }

    @Test
    fun ignoresContactNameTitle() {
        // A human name in the title must NOT be treated as a number; we then scan
        // the text — which has no phone-like run here, so null.
        val n = SmsNumberExtractor.extractSenderNumber(
            peopleList = null,
            messagingPerson = null,
            title = "Maman",
            text = "Tu rentres quand ?",
            bigText = null,
        )
        assertNull(n)
    }

    @Test
    fun scansTextAsLastResort() {
        val n = SmsNumberExtractor.extractSenderNumber(
            peopleList = null,
            messagingPerson = null,
            title = "Info",
            text = "Appelez le 0612345678 maintenant",
            bigText = null,
        )
        assertEquals("0612345678", n)
    }
}
