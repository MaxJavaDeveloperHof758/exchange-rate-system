export interface CurrencyOption {
  readonly code: string;
  readonly name: string;
}

/** Shared by every currency input's `Validators.pattern` — shape-only, not
 * membership: a 3-letter string that isn't one of the options below still
 * passes this and is only rejected by the backend, unchanged from before
 * this list existed. */
export const CURRENCY_CODE_PATTERN = /^[A-Za-z]{3}$/;

/**
 * The 42 codes `CurrencyCode` (backend) actually recognizes — not the full
 * ISO 4217 list. Kept in sync manually; a mismatch here only affects the
 * combobox's suggestions, never validation (the pattern above is what the
 * form actually enforces).
 */
export const CURRENCY_OPTIONS: readonly CurrencyOption[] = [
  { code: 'EUR', name: 'Euro' },
  { code: 'JPY', name: 'Japanese Yen' },
  { code: 'HKD', name: 'Hong Kong Dollar' },
  { code: 'KRW', name: 'South Korean Won' },
  { code: 'MYR', name: 'Malaysian Ringgit' },
  { code: 'INR', name: 'Indian Rupee' },
  { code: 'MXN', name: 'Mexican Peso' },
  { code: 'RUB', name: 'Russian Ruble' },
  { code: 'CNY', name: 'Chinese Yuan' },
  { code: 'ZAR', name: 'South African Rand' },
  { code: 'PLN', name: 'Polish Złoty' },
  { code: 'USD', name: 'US Dollar' },
  { code: 'GBP', name: 'British Pound' },
  { code: 'CHF', name: 'Swiss Franc' },
  { code: 'CAD', name: 'Canadian Dollar' },
  { code: 'AUD', name: 'Australian Dollar' },
  { code: 'NZD', name: 'New Zealand Dollar' },
  { code: 'SGD', name: 'Singapore Dollar' },
  { code: 'BRL', name: 'Brazilian Real' },
  { code: 'SEK', name: 'Swedish Krona' },
  { code: 'NOK', name: 'Norwegian Krone' },
  { code: 'DKK', name: 'Danish Krone' },
  { code: 'CZK', name: 'Czech Koruna' },
  { code: 'HUF', name: 'Hungarian Forint' },
  { code: 'TRY', name: 'Turkish Lira' },
  { code: 'ILS', name: 'Israeli New Shekel' },
  { code: 'AED', name: 'UAE Dirham' },
  { code: 'SAR', name: 'Saudi Riyal' },
  { code: 'THB', name: 'Thai Baht' },
  { code: 'IDR', name: 'Indonesian Rupiah' },
  { code: 'PHP', name: 'Philippine Peso' },
  { code: 'VND', name: 'Vietnamese Dong' },
  { code: 'EGP', name: 'Egyptian Pound' },
  { code: 'NGN', name: 'Nigerian Naira' },
  { code: 'KES', name: 'Kenyan Shilling' },
  { code: 'PKR', name: 'Pakistani Rupee' },
  { code: 'BDT', name: 'Bangladeshi Taka' },
  { code: 'TWD', name: 'Taiwan Dollar' },
  { code: 'CLP', name: 'Chilean Peso' },
  { code: 'COP', name: 'Colombian Peso' },
  { code: 'ARS', name: 'Argentine Peso' },
  { code: 'PEN', name: 'Peruvian Sol' },
];
