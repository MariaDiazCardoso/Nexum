import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { beforeEach, afterEach, describe, expect, it } from 'vitest';
import { VerifyCertificatePage } from './verify-certificate.page';
import { API_BASE_URL } from '../../core/api.config';
describe('Verificación pública de certificados', () => {
 let page: VerifyCertificatePage; let http: HttpTestingController;
 beforeEach(() => {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: new Map() } } }] });
  page = TestBed.runInInjectionContext(() => new VerifyCertificatePage()); http = TestBed.inject(HttpTestingController);
 });
 afterEach(() => http.verify());
 it('normaliza el código y muestra solo lo que confirma el servidor', () => {
  page.form.setValue({ codigo: ' abcdef123456 ' }); page.verify(); expect(page.certificate()).toBeNull();
  http.expectOne(API_BASE_URL + '/certificados/verificar/ABCDEF123456').flush({ codigo: 'ABCDEF123456', nombreAlumno: 'Ana Díaz', curso: 'Redes', fechaEmision: '2026-10-25' });
  expect(page.certificate()?.nombreAlumno).toBe('Ana Díaz'); expect(page.busy()).toBe(false);
 });
 it('borra un resultado anterior si el nuevo código no existe', () => {
  page.certificate.set({ codigo: 'ANTERIOR1', nombreAlumno: 'Ana', curso: 'Redes', fechaEmision: '2026-10-25' });
  page.form.setValue({ codigo: 'INEXISTENTE1' }); page.verify();
  http.expectOne(API_BASE_URL + '/certificados/verificar/INEXISTENTE1').flush({}, { status: 404, statusText: 'Not Found' });
  expect(page.certificate()).toBeNull(); expect(page.error()).toContain('No encontramos');
 });
 it('no consulta códigos incompletos', () => {
  page.form.setValue({ codigo: '123' }); page.verify(); http.expectNone(() => true); expect(page.form.invalid).toBe(true);
 });
});
