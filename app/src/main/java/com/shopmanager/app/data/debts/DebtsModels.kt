package com.shopmanager.app.data.debts

import androidx.compose.runtime.Immutable

@Immutable
data class Installment(
    val id: String = "",
    val amount: Double = 0.0,
    val date: Long = 0L,
    val paid: Boolean = false
)

@Immutable
data class Person(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val createdAt: Long = 0L,
    /** رقم هاتف العميل (اختياري) — فارغ إن لم يُدخل. */
    val phone: String = "",
    val dueDate: Long = 0L,
    val installments: List<Installment> = emptyList()
)

data class Debt(
    val id: String = "",
    val personId: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val note: String = "",
    val createdAt: Long = 0L
)
