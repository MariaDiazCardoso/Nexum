import { Component, Input, Output, EventEmitter, OnChanges, OnDestroy, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { API_BASE_URL } from '../../../core/api.config';
import { AuthService } from '../../../core/auth.service';
import { getApiErrorMessage } from '../../../core/api-error';
export interface Exam { id: string; leccionId: string; titulo: string; intentosMaximos: number | null; puntajeMinimoAprobacion: number | null; publicado: boolean; }
export interface ExamQuestion { id: string; textoPregunta: string; explicacionRespuesta: string | null; opciones: { id: string; textoOpcion: string; esCorrecta: boolean | null }[]; }
export interface ExamAttempt { id: string; numero: number; puntaje: number | null; aprobado: boolean | null; finalizadoEn: string | null; }
export interface ExamState { examen: Exam | null; preguntas: ExamQuestion[]; intentos: ExamAttempt[]; aprobado: boolean; puedeEditar: boolean; }
@Component({ selector: 'app-lesson-exam', imports: [FormsModule], templateUrl: './lesson-exam.component.html', styleUrl: './lesson-exam.component.scss' })
export class LessonExamComponent implements OnChanges, OnDestroy {
 @Input({ required: true }) lessonId = '';
 @Output() completionAllowed = new EventEmitter<boolean>();
 readonly auth = inject(AuthService);
 private readonly http = inject(HttpClient);
 private requests = new Subscription();
 readonly loading = signal(true);
 readonly busy = signal(false);
 readonly error = signal('');
 readonly notice = signal('');
 readonly state = signal<ExamState | null>(null);
 readonly currentAttempt = signal<ExamAttempt | null>(null);
 readonly answers = signal<Record<string,string>>({});
 readonly feedback = signal<{ preguntaId: string; correcta: boolean; explicacion: string | null }[]>([]);
 readonly editable = computed(() => this.auth.isAdmin() && !!this.state()?.puedeEditar && !this.state()?.examen?.publicado);
 readonly remaining = computed(() => {
  const s = this.state(); const max = s?.examen?.intentosMaximos;
  return max == null ? null : Math.max(0, max - (s?.intentos.filter(i => !!i.finalizadoEn).length ?? 0));
 });
 readonly score = computed(() => this.state()?.intentos.filter(i => i.finalizadoEn).at(-1) ?? null);
 title = ''; attempts: number | null = 3; threshold = 70;
 questionId: string | null = null; questionText = ''; explanation = ''; options = ['', '', '', '']; correct = 0;
 ngOnChanges(): void {
  this.requests.unsubscribe(); this.requests = new Subscription();
  this.busy.set(false); this.state.set(null); this.currentAttempt.set(null);
  this.answers.set({}); this.feedback.set([]); this.notice.set(''); this.clearQuestion();
  this.load();
 }
 ngOnDestroy(): void { this.requests.unsubscribe(); }
 load(): void {
  if (!this.lessonId) return;
  this.loading.set(true); this.completionAllowed.emit(false); this.error.set('');
  this.requests.add(this.http.get<ExamState>(API_BASE_URL + '/evaluaciones/lecciones/' + this.lessonId).subscribe({
   next: s => { this.apply(s); this.loading.set(false); },
   error: e => { this.loading.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos consultar la evaluación.')); },
  }));
 }
 private apply(s: ExamState): void {
  this.state.set(s); this.currentAttempt.set(s.intentos.find(i => !i.finalizadoEn) ?? null); this.answers.set({});
  this.title = s.examen?.titulo ?? 'Evaluación de la lección'; this.attempts = s.examen ? s.examen.intentosMaximos : 3; this.threshold = s.examen?.puntajeMinimoAprobacion ?? 70;
  if (this.auth.isAdmin() && !s.puedeEditar && this.attempts !== null) this.attempts++;
  this.completionAllowed.emit(!s.examen || !s.examen.publicado || s.aprobado);
 }
 private mutate<T>(request: ReturnType<HttpClient['delete']> | import('rxjs').Observable<T>, success: string): void {
  if (this.busy()) return;
  this.busy.set(true); this.error.set(''); this.notice.set('');
  this.requests.add(request.subscribe({
   next: () => { this.busy.set(false); this.notice.set(success); this.clearQuestion(); this.load(); },
   error: (e: unknown) => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos guardar los cambios.')); },
  }));
 }
 saveExam(): void {
  if (!this.auth.isAdmin() || this.busy()) return;
  if (!this.title.trim() || this.title.length > 255 || !Number.isFinite(this.threshold) || this.threshold < 0 || this.threshold > 100 || (this.attempts !== null && (!Number.isInteger(this.attempts) || this.attempts < 1))) {
   this.error.set('Indica el título, un puntaje entre 0 y 100 e intentos válidos.'); return;
  }
  const body = { leccionId: this.lessonId, titulo: this.title.trim(), intentosMaximos: this.attempts, puntajeMinimoAprobacion: this.threshold };
  const exam = this.state()?.examen;
  this.mutate(exam ? this.http.put(API_BASE_URL + '/examenes/' + exam.id, body) : this.http.post(API_BASE_URL + '/examenes', body), 'Configuración guardada.');
 }
 extendAttempts(): void {
  const e = this.state()?.examen;
  if (!e || !this.auth.isAdmin() || this.busy()) return;
  if (this.attempts !== null && (!Number.isInteger(this.attempts) || this.attempts < 1)) { this.error.set('Indica un número válido de intentos.'); return; }
  this.mutate(this.http.patch(API_BASE_URL + '/evaluaciones/' + e.id + '/intentos-maximos', { intentosMaximos: this.attempts }), 'Intentos ampliados.');
 }
 publish(): void {
  const e = this.state()?.examen;
  if (!e || !this.auth.isAdmin()) return;
  this.mutate(this.http.patch(API_BASE_URL + '/evaluaciones/' + e.id + '/publicacion', { publicado: !e.publicado }), e.publicado ? 'Evaluación retirada de publicación.' : 'Evaluación publicada.');
 }
 editQuestion(q: ExamQuestion): void {
  this.questionId = q.id; this.questionText = q.textoPregunta; this.explanation = q.explicacionRespuesta ?? '';
  this.options = q.opciones.map(o => o.textoOpcion); this.correct = q.opciones.findIndex(o => o.esCorrecta); this.error.set('');
 }
 clearQuestion(): void { this.questionId = null; this.questionText = ''; this.explanation = ''; this.options = ['', '', '', '']; this.correct = 0; }
 addOption(): void { if (this.options.length < 10) this.options = [...this.options, '']; }
 removeOption(index: number): void { if (this.options.length <= 2) return; this.options = this.options.filter((_, i) => i !== index); this.correct = 0; }
 saveQuestion(): void {
  const e = this.state()?.examen;
  if (!e || !this.editable() || this.busy()) return;
  if (!this.questionText.trim() || this.options.some(o => !o.trim()) || this.correct < 0 || this.correct >= this.options.length) { this.error.set('Escribe la pregunta, todas sus opciones y marca una única respuesta correcta.'); return; }
  const body = { examenId: e.id, textoPregunta: this.questionText.trim(), explicacionRespuesta: this.explanation.trim() || null, opciones: this.options.map((o,i) => ({ textoOpcion: o.trim(), esCorrecta: i === Number(this.correct) })) };
  this.mutate(this.questionId ? this.http.put(API_BASE_URL + '/preguntas/' + this.questionId, body) : this.http.post(API_BASE_URL + '/preguntas', body), 'Pregunta guardada.');
 }
 removeQuestion(q: ExamQuestion): void {
  if (!this.editable() || !confirm('¿Eliminar esta pregunta del borrador?')) return;
  this.mutate(this.http.delete(API_BASE_URL + '/preguntas/' + q.id), 'Pregunta eliminada del borrador.');
 }
 start(): void {
  const e = this.state()?.examen;
  if (!e || this.auth.isAdmin() || this.busy() || this.state()?.aprobado) return;
  this.busy.set(true); this.error.set(''); this.feedback.set([]);
  this.requests.add(this.http.post<ExamAttempt>(API_BASE_URL + '/evaluaciones/' + e.id + '/intentos', {}).subscribe({
   next: i => { this.busy.set(false); this.currentAttempt.set(i); this.answers.set({}); this.state.update(s => s ? { ...s, intentos: [...s.intentos.filter(a => a.id !== i.id), i] } : s); },
   error: e => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos iniciar el intento.')); },
  }));
 }
 choose(questionId: string, optionId: string): void { if (!this.busy()) this.answers.update(a => ({ ...a, [questionId]: optionId })); }
 submit(): void {
  const attempt = this.currentAttempt(); const s = this.state();
  if (!attempt || !s || this.busy()) return;
  if (s.preguntas.some(q => !this.answers()[q.id])) { this.error.set('Responde todas las preguntas antes de enviar.'); return; }
  this.busy.set(true); this.error.set('');
  const body = { respuestas: s.preguntas.map(q => ({ preguntaId: q.id, opcionId: this.answers()[q.id] })) };
  this.requests.add(this.http.post<{ intento: ExamAttempt; respuestas: { preguntaId: string; correcta: boolean; explicacion: string | null }[] }>(API_BASE_URL + '/evaluaciones/intentos/' + attempt.id + '/respuestas', body).subscribe({
   next: r => {
    this.busy.set(false); this.currentAttempt.set(null); this.feedback.set(r.respuestas);
    this.state.update(s => s ? { ...s, aprobado: !!r.intento.aprobado, intentos: [...s.intentos.filter(i => i.id !== r.intento.id), r.intento] } : s);
    this.completionAllowed.emit(!!r.intento.aprobado);
   },
   error: e => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos enviar la evaluación.')); },
  }));
 }
}
