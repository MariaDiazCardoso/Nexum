import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PasswordRecoveryPage } from './password-recovery.page';
import { API_BASE_URL } from '../../core/api.config';
describe('Recuperación de contraseña', () => {
  let http: HttpTestingController;
  let page: PasswordRecoveryPage;
  const token = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a90.' + 'a'.repeat(43);
  function create(resetting = false, link = token): void {
    history.replaceState({}, '', resetting ? '/reset-password#token=' + link : '/forgot-password');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), {
      provide: ActivatedRoute, useValue: { snapshot: { data: { resetting } } },
    }] });
    http = TestBed.inject(HttpTestingController);
    page = TestBed.runInInjectionContext(() => new PasswordRecoveryPage());
  }
  afterEach(() => { page.ngOnDestroy(); http.verify(); vi.useRealTimers(); history.replaceState({}, '', '/'); });
  it('confirma el envío solo después de la respuesta y limita el reenvío', () => {
    vi.useFakeTimers(); create();
    page.emailForm.setValue({ email: 'ANA@nexum.invalid' }); page.send();
    expect(page.done()).toBe(false);
    const req = http.expectOne(API_BASE_URL + '/auth/password/solicitar');
    expect(req.request.body).toEqual({ email: 'ana@nexum.invalid' }); req.flush(null);
    expect(page.done()).toBe(true); expect(page.resendSeconds()).toBe(60);
    page.send(); http.expectNone(API_BASE_URL + '/auth/password/solicitar');
    vi.advanceTimersByTime(60000); expect(page.resendSeconds()).toBe(0);
  });
  it('muestra fallos de envío sin simular éxito', () => {
    create(); page.emailForm.setValue({ email: 'ana@nexum.invalid' }); page.send();
    http.expectOne(API_BASE_URL + '/auth/password/solicitar').flush({ message: 'Correo no disponible' }, { status: 503, statusText: 'Unavailable' });
    expect(page.done()).toBe(false); expect(page.error()).toBe('Correo no disponible');
  });
  it('quita el token de la URL y confirma solo tras aceptar el backend', () => {
    create(true); expect(location.hash).toBe('');
    page.passwordForm.setValue({ password: 'NuevaClave1!', confirm: 'NuevaClave1!' }); page.reset();
    const req = http.expectOne(API_BASE_URL + '/auth/password/restablecer');
    expect(req.request.body).toEqual({ token, password: 'NuevaClave1!' }); expect(page.done()).toBe(false);
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(page.done()).toBe(true); expect(page.passwordForm.controls.password.value).toBe('');
    page.reset(); http.expectNone(API_BASE_URL + '/auth/password/restablecer');
  });
  it('no envía contraseñas débiles o diferentes', () => {
    create(true); page.passwordForm.setValue({ password: '1234', confirm: '1234' }); page.reset();
    http.expectNone(API_BASE_URL + '/auth/password/restablecer');
    page.passwordForm.setValue({ password: 'NuevaClave1!', confirm: 'OtraClave1!' }); page.reset();
    http.expectNone(API_BASE_URL + '/auth/password/restablecer'); expect(page.error()).toContain('no coinciden');
  });
  it('rechaza un enlace incompleto antes de llamar al backend', () => {
    create(true, 'incompleto'); page.reset();
    expect(page.invalidLink).toBe(true); http.expectNone(API_BASE_URL + '/auth/password/restablecer');
  });
  it('conserva el formulario si el enlace ya venció', () => {
    create(true); page.passwordForm.setValue({ password: 'NuevaClave1!', confirm: 'NuevaClave1!' }); page.reset();
    http.expectOne(API_BASE_URL + '/auth/password/restablecer').flush({ message: 'Enlace vencido' }, { status: 400, statusText: 'Bad Request' });
    expect(page.done()).toBe(false); expect(page.error()).toBe('Enlace vencido');
  });
});
