import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LessonExamComponent, ExamState, ExamAttempt } from './lesson-exam.component';
import { AuthService } from '../../../core/auth.service';
import { API_BASE_URL } from '../../../core/api.config';
describe('Evaluaciones de lección', () => {
 let page: LessonExamComponent; let http: HttpTestingController; const admin = signal(false);
 const fixture = (): ExamState => ({ examen: { id: 'exam-1', leccionId: 'lesson-1', titulo: 'Evaluación', intentosMaximos: 2, puntajeMinimoAprobacion: 70, publicado: true }, preguntas: [{ id: 'q1', textoPregunta: 'Pregunta', explicacionRespuesta: null, opciones: [{ id: 'o1', textoOpcion: 'Uno', esCorrecta: null }, { id: 'o2', textoOpcion: 'Dos', esCorrecta: null }] }], intentos: [], aprobado: false, puedeEditar: false });
 const attempt: ExamAttempt = { id: 'attempt-1', numero: 1, puntaje: null, aprobado: null, finalizadoEn: null };
 beforeEach(() => {
  admin.set(false);
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { isAdmin: admin } }] });
  page = TestBed.runInInjectionContext(() => new LessonExamComponent()); http = TestBed.inject(HttpTestingController); page.lessonId = 'lesson-1';
 });
 afterEach(() => { page.ngOnDestroy(); http.verify(); });
 it('no habilita completar mientras no se consulte la evaluación', () => {
  const emit = vi.spyOn(page.completionAllowed, 'emit'); page.load(); expect(emit).toHaveBeenLastCalledWith(false);
  http.expectOne(API_BASE_URL + '/evaluaciones/lecciones/lesson-1').flush({ examen: null, preguntas: [], intentos: [], aprobado: false, puedeEditar: false });
  expect(emit).toHaveBeenLastCalledWith(true);
 });
 it('conserva intentos ilimitados y no los convierte en tres', () => {
  const state = fixture(); state.examen!.intentosMaximos = null; page.load();
  http.expectOne(API_BASE_URL + '/evaluaciones/lecciones/lesson-1').flush(state); expect(page.attempts).toBeNull(); expect(page.remaining()).toBeNull();
 });
 it('envía únicamente las opciones y usa la nota calculada por el backend', () => {
  page.state.set(fixture()); const emit = vi.spyOn(page.completionAllowed, 'emit');
  page.start(); http.expectOne(API_BASE_URL + '/evaluaciones/exam-1/intentos').flush(attempt);
  page.submit(); http.expectNone(API_BASE_URL + '/evaluaciones/intentos/attempt-1/respuestas');
  expect(page.error()).toContain('Responde todas'); page.choose('q1', 'o1'); page.submit();
  const req = http.expectOne(API_BASE_URL + '/evaluaciones/intentos/attempt-1/respuestas');
  expect(req.request.body).toEqual({ respuestas: [{ preguntaId: 'q1', opcionId: 'o1' }] });
  req.flush({ intento: { ...attempt, puntaje: 100, aprobado: true, finalizadoEn: '2026-10-25' }, respuestas: [] });
  expect(page.state()?.aprobado).toBe(true); expect(emit).toHaveBeenLastCalledWith(true);
 });
 it('no habilita completar después de reprobar y conserva el historial', () => {
  page.state.set(fixture()); page.currentAttempt.set(attempt); page.choose('q1', 'o2'); const emit = vi.spyOn(page.completionAllowed, 'emit'); page.submit();
  http.expectOne(API_BASE_URL + '/evaluaciones/intentos/attempt-1/respuestas').flush({ intento: { ...attempt, puntaje: 0, aprobado: false, finalizadoEn: '2026-10-25' }, respuestas: [] });
  expect(page.remaining()).toBe(1); expect(page.score()?.puntaje).toBe(0); expect(emit).toHaveBeenLastCalledWith(false);
 });
 it('conserva el intento y las selecciones si falla el envío', () => {
  page.state.set(fixture()); page.currentAttempt.set(attempt); page.choose('q1', 'o1'); page.submit();
  http.expectOne(API_BASE_URL + '/evaluaciones/intentos/attempt-1/respuestas').flush({ message: 'Error temporal' }, { status: 503, statusText: 'Unavailable' });
  expect(page.currentAttempt()?.id).toBe('attempt-1'); expect(page.answers()['q1']).toBe('o1'); expect(page.busy()).toBe(false);
 });
 it('guarda las preguntas con una única respuesta correcta como administrador', () => {
  admin.set(true); const state = fixture(); state.puedeEditar = true; state.examen!.publicado = false; page.state.set(state);
  page.questionText = '¿Qué opción?'; page.options = ['A', 'B']; page.correct = 1; page.saveQuestion();
  const req = http.expectOne(API_BASE_URL + '/preguntas'); expect(req.request.body.opciones).toEqual([{ textoOpcion: 'A', esCorrecta: false }, { textoOpcion: 'B', esCorrecta: true }]);
  req.flush({}); http.expectOne(API_BASE_URL + '/evaluaciones/lecciones/lesson-1').flush(state);
 });
 it('el administrador no puede abrir intentos de alumno', () => {
  admin.set(true); page.state.set(fixture()); page.start(); http.expectNone(() => true);
 });
 it('cancela el envío anterior y limpia el estado al cambiar de lección', () => {
  page.state.set(fixture()); page.currentAttempt.set(attempt); page.choose('q1', 'o1');
  page.feedback.set([{ preguntaId: 'q1', correcta: false, explicacion: 'Anterior' }]);
  page.submit();
  const previous = http.expectOne(API_BASE_URL + '/evaluaciones/intentos/attempt-1/respuestas');
  page.lessonId = 'lesson-2'; page.ngOnChanges();
  expect(previous.cancelled).toBe(true);
  expect(page.busy()).toBe(false); expect(page.currentAttempt()).toBeNull();
  expect(page.state()).toBeNull(); expect(page.answers()).toEqual({}); expect(page.feedback()).toEqual([]);
  http.expectOne(API_BASE_URL + '/evaluaciones/lecciones/lesson-2').flush({ examen: null, preguntas: [], intentos: [], aprobado: false, puedeEditar: false });
  expect(page.loading()).toBe(false);
 });

 it('permite ampliar el límite como administrador conservando los resultados', () => {
  admin.set(true); const state = fixture(); state.intentos = [{ ...attempt, puntaje: 0, aprobado: false, finalizadoEn: '2026-10-25' }];
  page.load(); http.expectOne(API_BASE_URL + '/evaluaciones/lecciones/lesson-1').flush(state);
  expect(page.attempts).toBe(3); page.extendAttempts();
  const req = http.expectOne(API_BASE_URL + '/evaluaciones/exam-1/intentos-maximos'); expect(req.request.body).toEqual({ intentosMaximos: 3 });
  req.flush({}); state.examen!.intentosMaximos = 3; http.expectOne(API_BASE_URL + '/evaluaciones/lecciones/lesson-1').flush(state);
  expect(page.state()?.intentos).toHaveLength(1);
 });
});
