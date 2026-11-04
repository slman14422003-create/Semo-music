package com.shopmanager.app.ui.common

import com.shopmanager.app.data.debts.Debt

object StatementText {

    fun build(name: String, debts: List<Debt>, total: Double, currency: String): String =
        buildString {
            append("كشف حساب: ").append(name).append('\n')
            append("----------------\n")
            debts.sortedBy { it.date }.forEach { debt ->
                append(debt.date.ifBlank { "-" })
                append("  ")
                append(Formatters.number(debt.amount)).append(' ').append(currency)
                if (debt.note.isNotBlank()) append("  (").append(debt.note.trim()).append(')')
                append('\n')
            }
            append("----------------\n")
            append("الإجمالي: ").append(Formatters.number(total)).append(' ').append(currency)
        }
}
