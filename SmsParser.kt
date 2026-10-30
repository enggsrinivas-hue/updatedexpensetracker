package com.expensetracker.sms.parser

import com.expensetracker.sms.data.InstrumentType
import com.expensetracker.sms.data.TransactionType

data class ParsedSms(
    val type: TransactionType,
    val amount: Double,
    val cardOrAccount: String,
    val instrument: InstrumentType,
    val merchant: String
)

/** Regex based parser for Indian / global bank, card and UPI alerts. Fully offline. */
object SmsParser {

    private val amountRx = Regex("""(?i)(?:\b(?:inr|rs\.?|usd)|[₹$])\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")

    // Messages we never want (failed payments, future dues, collect requests...)
    private val rejectRx = Regex(
        """(?i)\b(failed|declined|unsuccessful|will be (?:debited|credited|deducted|charged)|is due|are due|minimum due|total due|due on|due by|due date|collect request|pre-?approved)\b"""
    )

    private val debitRx = Regex("""(?i)\b(debited|spent|withdrawn|withdrawal|purchase|paid|sent|transferred|deducted)\b""")
    private val creditRx = Regex("""(?i)\b(credited|received|deposited|refund(?:ed)?|salary|cashback|reversed)\b""")

    // "A/C **4567", "Card XX1234", "ending with 1234", "a/c no. XXXXXX4567"
    private val acctRx = Regex("""(?i)\b(?:a/?c|acct|account|card|ending(?: with| in)?)\s*(?:no\.?\s*)?[:\-]?\s*(?:x+|\*+|\.{2,})?\s*(\d{4})(?!\d)""")
    private val maskRx = Regex("""(?i)(?:x{2,}|\*{2,})\s*(\d{3,4})(?!\d)""")

    private val vpaRx = Regex("""(?i)\b(?:vpa|upi id)[:\s]+([a-z0-9.\-_]{2,}@[a-z0-9]{2,})""")
    private val payeeRx = Regex(
        """(?i)\b(?:trf to|transfer(?:red)? to|paid to|sent to|payment to|towards|at|to)\s+([A-Za-z0-9][A-Za-z0-9 .&'*\-]{1,40})"""
    )
    private val fromRx = Regex("""(?i)\b(?:received from|credited by|from|by)\s+([A-Za-z][A-Za-z0-9 .&'*\-@]{1,40})""")

    private val cutRx = Regex(
        """(?i)\s+(?:on|via|using|ref|refno|upi|from|avl|bal|available|if|not|is|was|for|dated|txn|transaction|a/c|card|with|at|to|by|and)\b|\.\s|\.$|[,;(]"""
    )
    private val badStartRx = Regex(
        """(?i)^(?:a/?c\b|acct|account|your|card|vpa|the\b|my\b|bank\b|rs\b|inr\b|xx|\*|\d{1,2}[:/-]|\d+$|upi\b|neft|imps|rtgs|cash\b|cheque|atm\b|self\b|you\b)"""
    )

    private val banks = listOf(
        "hdfc" to "HDFC", "icici" to "ICICI", "axis" to "Axis", "kotak" to "Kotak",
        "idfc" to "IDFC", "onecard" to "OneCard", "yesb" to "YES Bank", "indusind" to "IndusInd",
        "federal" to "Federal", "rbl" to "RBL", "amex" to "AmEx", "hsbc" to "HSBC",
        "bandhan" to "Bandhan", "paytm" to "Paytm", "canara" to "Canara", "pnb" to "PNB", "sbi" to "SBI"
    )

    fun parse(sender: String, rawBody: String): ParsedSms? {
        val body = rawBody.replace(Regex("\\s+"), " ").trim()
        if (body.isEmpty() || rejectRx.containsMatchIn(body)) return null

        // 1. Debit or credit? Whichever keyword appears first wins.
        val dm = debitRx.find(body)
        val cm = creditRx.find(body)
        val type = if (dm == null && cm == null) return null
        else if (cm == null) TransactionType.DEBIT
        else if (dm == null) TransactionType.CREDIT
        else if (dm.range.first < cm.range.first) TransactionType.DEBIT
        else TransactionType.CREDIT

        // 2. Amount (skip "Avl Bal Rs ..." / "Avl limit Rs ...")
        val amount = amountRx.findAll(body).firstOrNull { m ->
            val before = body.substring(maxOf(0, m.range.first - 18), m.range.first).lowercase()
            listOf("bal", "limit", "avl", "available").none { before.contains(it) }
        }?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        // 3. Instrument
        val lower = body.lowercase()
        val instrument = when {
            Regex("\\b(?:upi|vpa)\\b").containsMatchIn(lower) -> InstrumentType.UPI
            "credit card" in lower || "onecard" in lower || Regex("\\bcc\\b").containsMatchIn(lower) -> InstrumentType.CREDIT_CARD
            "debit card" in lower || Regex("\\bdc\\b").containsMatchIn(lower) -> InstrumentType.DEBIT_CARD
            Regex("\\bcard\\b").containsMatchIn(lower) ->
                if ("limit" in lower) InstrumentType.CREDIT_CARD else InstrumentType.DEBIT_CARD
            Regex("\\b(?:a/c|ac|acct|account)\\b").containsMatchIn(lower) -> InstrumentType.ACCOUNT
            else -> InstrumentType.OTHER
        }

        // 4. Bank + masked number -> "ICICI CC XX1234"
        val senderLower = sender.lowercase()
        val bank = banks.firstOrNull { senderLower.contains(it.first) }?.second
            ?: banks.firstOrNull { Regex("\\b" + it.first).containsMatchIn(lower) }?.second
        val digits = (acctRx.find(body)?.groupValues?.get(1) ?: maskRx.find(body)?.groupValues?.get(1))?.takeLast(4)
        val short = when (instrument) {
            InstrumentType.CREDIT_CARD -> "CC"
            InstrumentType.DEBIT_CARD -> "DC"
            InstrumentType.UPI -> "UPI"
            InstrumentType.ACCOUNT -> "A/C"
            InstrumentType.OTHER -> ""
        }
        val card = listOfNotNull(bank, short.ifEmpty { null }, digits?.let { "XX$it" })
            .joinToString(" ").ifBlank { senderName(sender) }

        return ParsedSms(type, amount, card, instrument, merchant(type, body, lower, bank, sender))
    }

    private fun merchant(type: TransactionType, body: String, lower: String, bank: String?, sender: String): String {
        val vpa = vpaRx.find(body)?.groupValues?.get(1)
        val fallback = bank?.let { "$it Bank" } ?: senderName(sender)

        if (type == TransactionType.DEBIT) {
            if (vpa != null) return vpa
            firstCandidate(payeeRx, body)?.let { return titleCase(it) }
        } else {
            firstCandidate(fromRx, body)?.let { return titleCase(it) }
            if (vpa != null) return vpa
            if ("salary" in lower) return "Salary"
            if ("refund" in lower) return "Refund"
            if ("interest" in lower) return "Interest"
            if ("cashback" in lower) return "Cashback"
        }
        return fallback
    }

    private fun firstCandidate(rx: Regex, body: String): String? {
        for (m in rx.findAll(body)) {
            var c = m.groupValues[1]
            cutRx.find(c)?.let { c = c.substring(0, it.range.first) }
            c = c.trim(' ', '.', '-', '*')
            if (c.length < 2 || badStartRx.containsMatchIn(c)) continue
            return c
        }
        return null
    }

    private fun senderName(sender: String): String {
        val s = sender.substringAfter('-', sender).trim()
        return if (s.isBlank()) "Unknown" else titleCase(s)
    }

    private fun titleCase(s: String): String =
        if (s.any { it.isLowerCase() }) s
        else s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
