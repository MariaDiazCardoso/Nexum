import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { LessonPage } from './lesson.page';
import { AuthService } from '../../core/auth.service';
import { API_BASE_URL } from '../../core/api.config';
import {
  CourseResponse,
  EnrollmentResponse,
  LessonResponse,
  LessonProgressResponse,
} from '../../core/api.models';

describe('LessonPage: progreso y XP', () => {
  let page: LessonPage;
  let http: HttpTestingController;
  const course = { id: 'course-1' } as CourseResponse;
  const lesson = { id: 'lesson-1', xpOtorgada: 50 } as LessonResponse;
  const enrollment = {
    id: 'enrollment-1',
    usuarioId: 'user-1',
    cursoId: 'course-1',
    estado: 'ACTIVA',
    progresoPorcentaje: 0,
    fechaInscripcion: '2026-10-01',
  } as EnrollmentResponse;
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('nexum.userId', 'user-1');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: {} },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    page = TestBed.runInInjectionContext(() => new LessonPage());
    page.course.set(course);
    page.lesson.set(lesson);
    page.accessAllowed.set(true);
    page.evaluationAllowed.set(true);
  });
  afterEach(() => {
    page.ngOnDestroy();
    http.verify();
    sessionStorage.clear();
  });
  it('no registra progreso si no hay inscripción activa', () => {
    page.complete();
    http.expectNone(`${API_BASE_URL}/progresos-leccion`);
    expect(page.saving()).toBe(false);
  });
  it('no guarda sin acceso o sin aprobar la evaluación aunque se invoque el método', () => {
    page.enrollment.set(enrollment);
    page.evaluationAllowed.set(false);
    page.complete();
    page.evaluationAllowed.set(true);
    page.accessAllowed.set(false);
    page.complete();
    http.expectNone(`${API_BASE_URL}/progresos-leccion`);
    expect(page.saving()).toBe(false);
  });
  it('crea el progreso y consulta los XP y porcentaje que calcula el servidor', () => {
    page.enrollment.set(enrollment);
    page.complete();
    page.complete();
    const request = http.expectOne(`${API_BASE_URL}/progresos-leccion`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      usuarioId: 'user-1',
      leccionId: 'lesson-1',
      completado: true,
    });
    request.flush({
      id: 'progress-1',
      usuarioId: 'user-1',
      leccionId: 'lesson-1',
      completado: true,
    });
    http.expectOne(`${API_BASE_URL}/usuarios/user-1`).flush({ id: 'user-1', puntosXp: 175 });
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/inscripciones` &&
          r.params.get('usuarioId') === 'user-1' &&
          r.params.get('cursoId') === 'course-1',
      )
      .flush([{ ...enrollment, progresoPorcentaje: 50 }]);
    expect(page.completed()).toBe(true);
    expect(page.courseProgress()).toBe(50);
    expect(TestBed.inject(AuthService).user()?.puntosXp).toBe(175);
    page.complete();
    http.expectNone(`${API_BASE_URL}/progresos-leccion`);
  });
  it('actualiza un progreso existente sin crear uno duplicado', () => {
    page.enrollment.set(enrollment);
    page.progress.set([
      {
        id: 'progress-1',
        usuarioId: 'user-1',
        leccionId: 'lesson-1',
        completado: false,
      } as LessonProgressResponse,
    ]);
    page.complete();
    const request = http.expectOne(`${API_BASE_URL}/progresos-leccion/progress-1`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ completado: true });
    request.flush({ message: 'No disponible' }, { status: 503, statusText: 'Service Unavailable' });
    expect(page.saving()).toBe(false);
    expect(page.completed()).toBe(false);
  });
});
