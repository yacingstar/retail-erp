package com.retailerp.accounting;

import java.util.List;

/**
 * The accounts of the Algerian SCF (Système Comptable Financier) that the app uses, with their official French names.
 * Class 1 equity/long-term debt, 2 fixed assets, 3 stocks, 4 third parties, 5 cash/bank, 6 expenses, 7 income.
 */
public final class ChartOfAccounts {

    public static final String CAPITAL = "101";
    public static final String OWNER = "108";
    public static final String LOANS = "164";
    public static final String STOCK = "300";
    public static final String SUPPLIERS = "401";
    public static final String SUPPLIERS_UNBILLED = "408";
    public static final String CUSTOMERS = "411";
    public static final String CUSTOMER_ADVANCES = "4191";
    public static final String VAT_DEDUCTIBLE = "4456";
    public static final String VAT_COLLECTED = "4457";
    public static final String BANK = "512";
    public static final String CASH = "530";
    public static final String COST_OF_GOODS_SOLD = "600";
    public static final String DEFAULT_EXPENSE = "607";
    public static final String OTHER_EXPENSES = "658";
    public static final String SALES = "700";
    public static final String OTHER_INCOME = "758";

    public record Seed(String code, String name, AccountType type) {
    }

    public static final List<Seed> SEED = List.of(
            new Seed("101", "Capital émis", AccountType.EQUITY),
            new Seed("108", "Compte de l'exploitant", AccountType.EQUITY),
            new Seed("164", "Emprunts auprès des établissements de crédit", AccountType.LIABILITY),
            new Seed("215", "Installations techniques, matériel et outillage", AccountType.ASSET),
            new Seed("218", "Autres immobilisations corporelles", AccountType.ASSET),
            new Seed("300", "Stocks de marchandises", AccountType.ASSET),
            new Seed("401", "Fournisseurs de stocks et services", AccountType.LIABILITY),
            new Seed("408", "Fournisseurs - factures non parvenues", AccountType.LIABILITY),
            new Seed("411", "Clients", AccountType.ASSET),
            new Seed("4191", "Clients - avances et acomptes reçus", AccountType.LIABILITY),
            new Seed("421", "Personnel - rémunérations dues", AccountType.LIABILITY),
            new Seed("4456", "TVA déductible", AccountType.ASSET),
            new Seed("4457", "TVA collectée", AccountType.LIABILITY),
            new Seed("512", "Banques - comptes courants", AccountType.ASSET),
            new Seed("530", "Caisse", AccountType.ASSET),
            new Seed("600", "Achats de marchandises vendues", AccountType.EXPENSE),
            new Seed("607", "Achats non stockés de matières et fournitures", AccountType.EXPENSE),
            new Seed("613", "Locations", AccountType.EXPENSE),
            new Seed("615", "Entretien, réparations et maintenance", AccountType.EXPENSE),
            new Seed("616", "Primes d'assurances", AccountType.EXPENSE),
            new Seed("622", "Rémunérations d'intermédiaires et honoraires", AccountType.EXPENSE),
            new Seed("624", "Transports de biens et transports collectifs du personnel", AccountType.EXPENSE),
            new Seed("626", "Frais postaux et de télécommunications", AccountType.EXPENSE),
            new Seed("627", "Services bancaires et assimilés", AccountType.EXPENSE),
            new Seed("631", "Rémunérations du personnel", AccountType.EXPENSE),
            new Seed("641", "Impôts, taxes et versements assimilés sur rémunérations", AccountType.EXPENSE),
            new Seed("658", "Autres charges opérationnelles", AccountType.EXPENSE),
            new Seed("700", "Ventes de marchandises", AccountType.INCOME),
            new Seed("758", "Autres produits opérationnels", AccountType.INCOME));

    private ChartOfAccounts() {
    }
}
