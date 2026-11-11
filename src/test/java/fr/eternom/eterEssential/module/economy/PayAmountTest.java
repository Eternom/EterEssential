package fr.eternom.eterEssential.module.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Lecture du montant de /pay : ce qui est refusé (0) et ce qui est arrondi, pour ne jamais envoyer plus que tapé. */
class PayAmountTest {

    @Test
    void readsWholeAndDecimalAmounts() {
        assertEquals(150, EconomyCommand.parseAmount("150", 2));
        assertEquals(12.5, EconomyCommand.parseAmount("12,50", 2));
        assertEquals(12.5, EconomyCommand.parseAmount("12.5", 2));
    }

    @Test
    void roundsDownToTheCurrencyDigits() {
        assertEquals(12.56, EconomyCommand.parseAmount("12.569", 2));
        assertEquals(12, EconomyCommand.parseAmount("12.99", 0));
    }

    @Test
    void refusesZeroNegativeAndGarbage() {
        assertEquals(0, EconomyCommand.parseAmount("0", 2));
        assertEquals(0, EconomyCommand.parseAmount("-5", 2));
        assertEquals(0, EconomyCommand.parseAmount("0.001", 2));
        assertEquals(0, EconomyCommand.parseAmount("abc", 2));
        assertEquals(0, EconomyCommand.parseAmount("NaN", 2));
        assertEquals(0, EconomyCommand.parseAmount("1e400", -1));
    }
}
