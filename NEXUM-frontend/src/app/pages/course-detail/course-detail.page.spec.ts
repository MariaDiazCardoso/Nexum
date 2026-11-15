import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { CourseDetailPage } from './course-detail.page';
import { AuthService } from '../../core/auth.service';
import { API_BASE_URL } from '../../core/api.config';
import { CourseResponse } from '../../core/api.models';
describe('Inscripción y acceso al curso', () => {
  let page: CourseDetailPage;
  let http: HttpTestingController;
  const router = { url: '/courses/curso-1', navigate: vi.fn() };
  const auth = { isAdmin: signal(false), isAuthenticated: signal(true), userId: signal('usuario-1'), refreshUser: vi.fn() };
  beforeEach(() => {
    vi.clearAllMocks(); auth.isAdmin.set(false); auth.isAuthenticated.set(true);
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: Router, useValue: router }, { provide: AuthService, useValue: auth },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', 'curso-1']]) } } },
    ] });
    page = TestBed.runInInjectionContext(() => new CourseDetailPage()); http = TestBed.inject(HttpTestingController);
    page.course.set({ id: 'curso-1', precio: 25, estado: 'PUBLICADO' } as CourseResponse);
  });
  afterEach(() => http.verify());
  it('no habilita contenido por tener una inscripción sin acceso aprobado', () => {
    page.loadCourse();
    http.expectOne(API_BASE_URL + '/cursos/curso-1/acceso').flush({ puedeAcceder: false });
    http.expectOne(API_BASE_URL + '/cursos/curso-1').flush({ id: 'curso-1', precio: 25, estado: 'PUBLICADO' });
    http.expectOne(r => r.url === API_BASE_URL + '/modulos').flush([]);
    http.expectOne(r => r.url === API_BASE_URL + '/lecciones').flush([]);
    http.expectOne(r => r.url === API_BASE_URL + '/inscripciones').flush([{ id: 'i', estado: 'ACTIVA' }]);
    http.expectOne(r => r.url === API_BASE_URL + '/progresos-leccion').flush([]);
    expect(page.enrollment()).not.toBeNull(); expect(page.canReadLessons()).toBe(false);
  });
  it('solicita verificar el correo antes de enviar al pago', () => {
    auth.refreshUser.mockReturnValue(of({ email: 'ana@nexum.invalid', emailVerificado: false }));
    page.enroll();
    expect(router.navigate).toHaveBeenCalledWith(['/verify-email'], { queryParams: { email: 'ana@nexum.invalid', returnUrl: '/courses/curso-1' } });
    http.expectNone(API_BASE_URL + '/inscripciones');
  });
  it('envía un curso pagado al checkout sin crear la inscripción', () => {
    auth.refreshUser.mockReturnValue(of({ emailVerificado: true })); page.enroll();
    expect(router.navigate).toHaveBeenCalledWith(['/checkout'], { queryParams: { tipo: 'curso', id: 'curso-1' } });
    http.expectNone(API_BASE_URL + '/inscripciones');
  });
});
