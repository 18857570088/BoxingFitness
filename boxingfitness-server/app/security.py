from __future__ import annotations

import string


DIGITS = set(string.digits)


def normalize_serial(value: str) -> str:
    serial = "".join(ch for ch in value.strip() if ch in DIGITS)
    if len(serial) != 11:
        raise ValueError("serial must be 11 digits")
    return serial


def luhn_checksum(number_without_check: str) -> int:
    total = 0
    reverse_digits = list(map(int, reversed(number_without_check)))
    for index, digit in enumerate(reverse_digits):
        if index % 2 == 0:
            doubled = digit * 2
            total += doubled - 9 if doubled > 9 else doubled
        else:
            total += digit
    return (10 - (total % 10)) % 10
