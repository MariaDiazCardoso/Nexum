import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const nonBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && !control.value.trim() ? { blank: true } : null;

export const bcryptByteLimit: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && new TextEncoder().encode(control.value).length > 72
    ? { bcryptByteLimit: true } : null;

export function localDateToday(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

export const birthDate: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value: string = control.value;
  if (!value) return null;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return { birthDate: true };
  const date = new Date(`${value}T12:00:00`);
  const [year, month, day] = value.split('-').map(Number);
  return Number.isNaN(date.getTime()) || date.getFullYear() !== year ||
    date.getMonth() + 1 !== month || date.getDate() !== day || value > localDateToday()
    ? { birthDate: true } : null;
};

export const passwordComplexity: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value: string = control.value;
  if (!value) return null;
  return /\p{Lu}/u.test(value) && /\p{Ll}/u.test(value) && /\p{Nd}/u.test(value) &&
    /[^\p{L}\p{N}\s]/u.test(value) ? null : { passwordComplexity: true };
};
