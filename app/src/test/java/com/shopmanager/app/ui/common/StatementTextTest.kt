package com.shopmanager.app.ui.common

import com.shopmanager.app.data.debts.Debt
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementTextTest {

    @Test
    fun statementContainsNameDebtsAndTotal() {
        val debts = listOf(
            Debt(id = "1", personId = "p", amount = 100.0, date = "2026-01-02", note = "ملاحظة"),
            Debt(id = "2", personId = "p", amount = 50.0, date = "2026-01-01")
        )
        val text = StatementText.build("أحمد", debts, 150.0, "ل.س")
        assertTrue(text.contains("أحمد"))
        assertTrue(text.contains("2026-01-01"))
        assertTrue(text.contains("ملاحظة"))
        assertTrue(text.contains("الإجمالي"))
        assertTrue(text.indexOf("2026-01-01") < text.indexOf("2026-01-02"))
    }

    @Test
    fun phoneNormalizationAddsCountryCode() {
        assertTrue(PhoneUtils.forWhatsApp("0912345678") == "963912345678")
        assertTrue(PhoneUtils.isValidOrBlank(""))
    }
}
