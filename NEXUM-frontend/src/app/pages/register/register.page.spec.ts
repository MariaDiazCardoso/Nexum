import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { RegisterPage } from './register.page';

describe('RegisterPage required registration fields', () => {
  let page: RegisterPage;
  const register = vi.fn();
  beforeEach(async () => {
    register.mockReset().mockReturnValue(of(undefined));
    await TestBed.configureTestingModule({
      imports: [RegisterPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: { register } }],
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(RegisterPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    page.form.patchValue({
      nombre: ' Ana ', apellido: ' López ', email: 'ANA@nexum.com',
      password: 'Password123!', genero: 'FEMENINO', fechaNacimiento: '2000-01-01',
    });
  });

  it('does not send a registration with the password 1234', () => {
    page.form.controls.password.setValue('1234');
    page.submit();
    expect(register).not.toHaveBeenCalled();
    expect(page.form.controls.password.hasError('minlength')).toBe(true);
  });

  it('requires gender and date of birth', () => {
    page.form.patchValue({ genero: '', fechaNacimiento: '' });
    page.submit();
    expect(register).not.toHaveBeenCalled();
    expect(page.form.controls.genero.hasError('required')).toBe(true);
    expect(page.form.controls.fechaNacimiento.hasError('required')).toBe(true);
  });

  it('rejects future dates before sending the request', () => {
    page.form.controls.fechaNacimiento.setValue('9999-01-01');
    page.submit();
    expect(register).not.toHaveBeenCalled();
  });

  it('sends both required fields and normalizes identity fields', () => {
    page.submit();
    expect(register).toHaveBeenCalledWith({
      nombre: 'Ana', apellido: 'López', email: 'ana@nexum.com',
      password: 'Password123!', genero: 'FEMENINO', fechaNacimiento: '2000-01-01',
    });
  });
  it('opens verification with the normalized email after registration succeeds', () => {
    page.submit();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/verify-email'], {
      state: { newRegistration: true }, queryParams: { email: 'ana@nexum.com', returnUrl: null },
    });
  });
  it('prevents duplicate submissions while registration is pending', () => {
    register.mockReturnValue(new Subject());
    page.submit(); page.submit();
    expect(register).toHaveBeenCalledTimes(1);
    expect(page.submitting()).toBe(true);
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
  });
  it('keeps the form and explains a duplicate email without navigating', () => {
    register.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    page.submit();
    expect(page.errorMessage()).toContain('Ya existe una cuenta');
    expect(page.submitting()).toBe(false);
    expect(page.form.controls.nombre.value).toBe(' Ana ');
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
  });

  it('does not send a password missing any required character group', () => {
    for (const password of ['password1!', 'PASSWORD1!', 'Password!!', 'Password12', 'Password1 ']) {
      page.form.controls.password.setValue(password);
      page.submit();
      expect(register).not.toHaveBeenCalled();
      expect(page.form.controls.password.hasError('passwordComplexity')).toBe(true);
    }
  });
});
