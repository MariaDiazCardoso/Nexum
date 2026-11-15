import { LessonMediaComponent } from './media/lesson-media.component';
import { LessonExamComponent } from './exam/lesson-exam.component';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, computed, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, Subscription } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { API_BASE_URL } from '../../core/api.config';
import { getApiErrorMessage } from '../../core/api-error';
import {
  CourseResponse,
  EnrollmentResponse,
  LessonProgressResponse,
  LessonResponse,
  ModuleResponse,
} from '../../core/api.models';
@Component({
  selector: 'app-lesson-page',
  imports: [RouterLink, LessonExamComponent, LessonMediaComponent],
  templateUrl: './lesson.page.html',
  styleUrl: './lesson.page.scss',
})
export class LessonPage implements OnInit, OnDestroy {
  private readonly routeSubscription = new Subscription();
  private requests = new Subscription();
  readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  readonly course = signal<CourseResponse | null>(null);
  readonly lesson = signal<LessonResponse | null>(null);
  readonly lessons = signal<LessonResponse[]>([]);
  readonly progress = signal<LessonProgressResponse[]>([]);
  readonly enrollment = signal<EnrollmentResponse | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly evaluationAllowed = signal(false);
  readonly accessAllowed = signal(false);
  readonly canRead = computed(() => this.auth.isAdmin() || this.accessAllowed());
  readonly completed = computed(() =>
    this.progress().some((p) => p.leccionId === this.lesson()?.id && p.completado),
  );
  readonly courseProgress = computed(() => this.enrollment()?.progresoPorcentaje ?? 0);
  readonly nextLesson = computed(() => {
    const index = this.lessons().findIndex((l) => l.id === this.lesson()?.id);
    return index >= 0 ? (this.lessons()[index + 1] ?? null) : null;
  });
  ngOnInit(): void {
    this.routeSubscription.add(this.route.paramMap.subscribe(() => this.load()));
  }
  ngOnDestroy(): void {
    this.routeSubscription.unsubscribe();
    this.requests.unsubscribe();
  }
  load(): void {
    this.requests.unsubscribe();
    this.requests = new Subscription();
    this.saving.set(false);
    this.course.set(null);
    this.enrollment.set(null);
    this.progress.set([]);
    this.lessons.set([]);
    const courseId = this.route.snapshot.paramMap.get('id');
    const lessonId = this.route.snapshot.paramMap.get('lessonId');
    const userId = this.auth.userId();
    if (!courseId || !lessonId || !userId) {
      this.error.set('No se pudo identificar la lección o tu sesión.');
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.notice.set('');
    this.lesson.set(null);
    this.accessAllowed.set(false);
    this.evaluationAllowed.set(false);
    const params = new HttpParams().set('cursoId', courseId);
    this.requests.add(forkJoin({
      access: this.http.get<{ puedeAcceder: boolean }>(`${API_BASE_URL}/cursos/${courseId}/acceso`),
      course: this.http.get<CourseResponse>(`${API_BASE_URL}/cursos/${courseId}`),
      modules: this.http.get<ModuleResponse[]>(`${API_BASE_URL}/modulos`, { params }),
      lessons: this.http.get<LessonResponse[]>(`${API_BASE_URL}/lecciones`, { params }),
      enrollments: this.http.get<EnrollmentResponse[]>(`${API_BASE_URL}/inscripciones`, {
        params: new HttpParams().set('usuarioId', userId).set('cursoId', courseId),
      }),
      progress: this.http.get<LessonProgressResponse[]>(`${API_BASE_URL}/progresos-leccion`, {
        params: new HttpParams().set('usuarioId', userId).set('cursoId', courseId),
      }),
    }).subscribe({
      next: (data) => {
        const order = new Map(data.modules.map((m) => [m.id, m.orden]));
        const lessons = [...data.lessons].sort(
          (a, b) =>
            (order.get(a.moduloId) ?? 0) - (order.get(b.moduloId) ?? 0) || a.orden - b.orden,
        );
        this.course.set(data.course);
        this.accessAllowed.set(data.access.puedeAcceder);
        this.lessons.set(lessons);
        this.lesson.set(lessons.find((l) => l.id === lessonId) ?? null);
        this.enrollment.set(
          data.enrollments.find((e) => e.cursoId === courseId && ['ACTIVA', 'COMPLETADA'].includes(e.estado)) ?? null,
        );
        this.progress.set(data.progress);
        this.loading.set(false);
        if (!this.lesson())
          this.error.set('Esta lección no pertenece al curso o ya no está disponible.');
        this.requests.add(this.auth.refreshUser().subscribe({ error: () => {} }));
      },
      error: (e) => {
        this.loading.set(false);
        this.error.set(getApiErrorMessage(e, 'No pudimos cargar la lección.'));
      },
    }));
  }
  complete(): void {
    const userId = this.auth.userId();
    const lesson = this.lesson();
    if (!userId || !lesson || !this.enrollment() || !this.canRead() ||
      !this.evaluationAllowed() || this.auth.isAdmin() || this.saving() || this.completed()) return;
    this.saving.set(true);
    this.notice.set('');
    const existing = this.progress().find((p) => p.leccionId === lesson.id);
    const request = existing
      ? this.http.patch<LessonProgressResponse>(
          `${API_BASE_URL}/progresos-leccion/${existing.id}`,
          { completado: true },
        )
      : this.http.post<LessonProgressResponse>(`${API_BASE_URL}/progresos-leccion`, {
          usuarioId: userId,
          leccionId: lesson.id,
          completado: true,
        });
    this.requests.add(request.subscribe({
      next: (progress) => {
        this.progress.update((items) => [
          ...items.filter((p) => p.leccionId !== progress.leccionId),
          progress,
        ]);
        this.saving.set(false);
        this.notice.set('Lección completada. Actualizando tu progreso y tus XP…');
        this.refreshAfterCompletion(userId);
      },
      error: (e) => {
        this.saving.set(false);
        this.notice.set(getApiErrorMessage(e, 'No pudimos guardar tu progreso.'));
      },
    }));
  }
  private refreshAfterCompletion(userId: string): void {
    this.requests.add(forkJoin({
      user: this.auth.refreshUser(),
      enrollments: this.http.get<EnrollmentResponse[]>(`${API_BASE_URL}/inscripciones`, {
        params: new HttpParams().set('usuarioId', userId).set('cursoId', this.course()!.id),
      }),
    }).subscribe({
      next: (data) => {
        this.enrollment.set(
          data.enrollments.find((e) => e.cursoId === this.course()?.id && ['ACTIVA', 'COMPLETADA'].includes(e.estado)) ??
            null,
        );
        this.notice.set(`Lección completada. Tienes ${data.user.puntosXp} XP acumulados.`);
      },
      error: () =>
        this.notice.set(
          'Lección completada. No pudimos actualizar el resumen; vuelve a cargar para consultar tus XP.',
        ),
    }));
  }
  safeUrl(value: string | null): string | null {
    try {
      const url = new URL(value ?? '');
      return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
    } catch {
      return null;
    }
  }
}
