import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { bcryptByteLimit, birthDate, nonBlank, localDateToday, passwordComplexity } from './registration.validators';

describe('Registration validation', () => {
  it('rejects blank names and passwords', () => {
    expect(nonBlank(new FormControl('   '))).toEqual({ blank: true });
    expect(nonBlank(new FormControl(' Ana '))).toBeNull();
  });
  it('uses the UTF-8 byte limit of bcrypt', () => {
    expect(bcryptByteLimit(new FormControl('a'.repeat(72)))).toBeNull();
    expect(bcryptByteLimit(new FormControl('á'.repeat(36)))).toBeNull();
    expect(bcryptByteLimit(new FormControl('á'.repeat(37)))).toEqual({ bcryptByteLimit: true });
  });
  it('accepts valid birth dates including leap days and today', () => {
    expect(birthDate(new FormControl('2000-02-29'))).toBeNull();
    expect(birthDate(new FormControl(localDateToday()))).toBeNull();
  });
  it('rejects future and impossible birth dates', () => {
    expect(birthDate(new FormControl('9999-01-01'))).not.toBeNull();
    expect(birthDate(new FormControl('2001-02-29'))).not.toBeNull();
    expect(birthDate(new FormControl('2000-13-01'))).not.toBeNull();
  });

  it('accepts a password with all required groups including accented letters', () => {
    expect(passwordComplexity(new FormControl('Aprender1!'))).toBeNull();
    expect(passwordComplexity(new FormControl('Árbol123!'))).toBeNull();
  });
  it('rejects passwords missing one group and does not count spaces as special characters', () => {
    for (const password of ['aprender1!', 'APRENDER1!', 'Aprender!!', 'Aprender12', 'Aprender1 ']) {
      expect(passwordComplexity(new FormControl(password))).toEqual({ passwordComplexity: true });
    }
  });
});
