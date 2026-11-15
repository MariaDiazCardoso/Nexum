import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { VerifyEmailPage } from './verify-email.page';
import { API_BASE_URL } from '../../core/api.config';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
describe('Verificación real de correo', () => {
  let page: VerifyEmailPage; let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), {
      provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: new Map([['email', 'ana@nexum.invalid']]) } },
    }] });
    http = TestBed.inject(HttpTestingController);
    page = TestBed.runInInjectionContext(() => new VerifyEmailPage());
  });
  afterEach(() => { page.ngOnDestroy(); vi.useRealTimers(); http.verify(); });
  it('solicita el envío y solo muestra confirmación tras la respuesta', () => {
    page.send(); expect(page.sent()).toBe(false);
    const req = http.expectOne(API_BASE_URL + '/auth/verificacion/solicitar');
    expect(req.request.body).toEqual({ email: 'ana@nexum.invalid' });
    req.flush(null, { status: 202, statusText: 'Accepted' }); expect(page.sent()).toBe(true);
  });
  it('no muestra éxito cuando falta SMTP', () => {
    page.send(); http.expectOne(API_BASE_URL + '/auth/verificacion/solicitar').flush({ message: 'Correo no disponible' }, { status: 503, statusText: 'Unavailable' });
    expect(page.sent()).toBe(false); expect(page.error()).toBe('Correo no disponible');
  });
  it('confirma únicamente cuando el backend acepta el código', () => {
    page.codigo = '123456'; page.confirm(); expect(page.done()).toBe(false);
    http.expectOne(API_BASE_URL + '/auth/verificacion/confirmar').flush(null, { status: 204, statusText: 'No Content' });
    expect(page.done()).toBe(true);
  });
  it('rechaza un código vencido y conserva la pantalla de confirmación', () => {
    page.codigo = '123456'; page.confirm();
    http.expectOne(API_BASE_URL + '/auth/verificacion/confirmar').flush({ message: 'Código vencido' }, { status: 400, statusText: 'Bad Request' });
    expect(page.done()).toBe(false); expect(page.error()).toBe('Código vencido');
  });
  it('no envía un código incompleto', () => {
    page.codigo = '1234'; page.confirm();
    http.expectNone(API_BASE_URL + '/auth/verificacion/confirmar'); expect(page.done()).toBe(false);
  });
  it('espera 60 segundos para reenviar y libera el temporizador al salir', () => {
    vi.useFakeTimers();
    page.send();
    http.expectOne(API_BASE_URL + '/auth/verificacion/solicitar').flush(null);
    expect(page.resendSeconds()).toBe(60);
    page.send();
    http.expectNone(API_BASE_URL + '/auth/verificacion/solicitar');
    vi.advanceTimersByTime(60000);
    expect(page.resendSeconds()).toBe(0);
    page.send();
    http.expectOne(API_BASE_URL + '/auth/verificacion/solicitar').flush(null);
    page.ngOnDestroy();
    expect(vi.getTimerCount()).toBe(0);
  });

});
