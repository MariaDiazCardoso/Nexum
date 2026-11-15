import { Component, Input, Output, EventEmitter, OnChanges, OnDestroy, SimpleChanges, inject, signal } from '@angular/core';
import { HttpClient, HttpEventType } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { API_BASE_URL } from '../../../core/api.config';
import { AuthService } from '../../../core/auth.service';
import { getApiErrorMessage } from '../../../core/api-error';
export interface LessonMaterial { id: string; leccionId: string; nombre: string; urlArchivo: string; tipoArchivo: string | null; }
@Component({ selector: 'app-lesson-media', templateUrl: './lesson-media.component.html', styleUrl: './lesson-media.component.scss' })
export class LessonMediaComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) lessonId = '';
  @Input() videoUrl: string | null = null;
  @Output() videoChanged = new EventEmitter<string | null>();
  readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  readonly materials = signal<LessonMaterial[]>([]);
  readonly video = signal<string | null>(null);
  readonly videoSource = signal('');
  readonly loadingMaterials = signal(false);
  readonly loadingVideo = signal(false);
  readonly downloadProgress = signal<number | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  private requests = new Subscription();
  private videoRequest?: Subscription;
  private videoObjectUrl = '';
  private readonly downloadUrls = new Set<string>();
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['lessonId']) { this.requests.unsubscribe(); this.requests = new Subscription(); this.loadMaterials(); }
    if (changes['lessonId'] || changes['videoUrl']) { this.clearVideo(); this.video.set(this.videoUrl); }
  }
  ngOnDestroy(): void { this.requests.unsubscribe(); this.clearVideo(); this.downloadUrls.forEach(url => URL.revokeObjectURL(url)); }
  loadMaterials(): void {
    if (!this.lessonId) return;
    this.loadingMaterials.set(true);
    this.requests.add(this.http.get<LessonMaterial[]>(API_BASE_URL + '/recursos-adjuntos', { params: { leccionId: this.lessonId } }).subscribe({
      next: items => { this.materials.set(items); this.loadingMaterials.set(false); },
      error: e => { this.loadingMaterials.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos cargar los materiales.')); },
    }));
  }
  externalUrl(value: string | null): string | null {
    try { const url = new URL(value ?? ''); return ['http:', 'https:'].includes(url.protocol) ? url.href : null; } catch { return null; }
  }
  isLocalVideo(): boolean {
    return /^\/api\/lecciones\/[0-9a-f-]{36}\/video\/[0-9a-f-]{36}\.mp4$/.test(this.video() ?? '');
  }
  loadVideo(): void {
    if (!this.isLocalVideo() || this.loadingVideo() || this.videoSource()) return;
    this.loadingVideo.set(true); this.error.set(''); this.downloadProgress.set(null);
    this.videoRequest = this.http.get(API_BASE_URL + this.video()!.slice(4), { responseType: 'blob', observe: 'events', reportProgress: true }).subscribe({
      next: event => {
        if (event.type === HttpEventType.DownloadProgress && event.total) this.downloadProgress.set(Math.round(100 * event.loaded / event.total));
        if (event.type === HttpEventType.Response && event.body) {
          this.videoObjectUrl = URL.createObjectURL(event.body); this.videoSource.set(this.videoObjectUrl); this.loadingVideo.set(false);
        }
      },
      error: e => { this.loadingVideo.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos cargar el video. Inténtalo nuevamente.')); },
    });
  }
  playbackError(): void {
    this.clearVideo(); this.error.set('No se pudo reproducir el video en este navegador. Intenta cargarlo nuevamente.');
  }
  private clearVideo(): void {
    this.videoRequest?.unsubscribe();
    if (this.videoObjectUrl) URL.revokeObjectURL(this.videoObjectUrl);
    this.videoObjectUrl = ''; this.videoSource.set(''); this.loadingVideo.set(false); this.downloadProgress.set(null);
  }
  upload(event: Event, kind: 'video' | 'materiales'): void {
    const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = '';
    if (!file || !this.auth.isAdmin() || this.busy()) return;
    const video = kind === 'video';
    if (file.size === 0 || file.size > (video ? 100 : 10) * 1024 * 1024 || !file.name.toLowerCase().endsWith(video ? '.mp4' : '.pdf')) {
      this.error.set(video ? 'Elige un video MP4 de hasta 100 MB.' : 'Elige un documento PDF de hasta 10 MB.'); return;
    }
    const data = new FormData(); data.append('archivo', file);
    this.busy.set(true); this.error.set(''); this.notice.set('');
    this.requests.add(this.http.post<{ videoUrl?: string } & Partial<LessonMaterial>>(API_BASE_URL + '/lecciones/' + this.lessonId + '/' + kind, data).subscribe({
      next: result => {
        this.busy.set(false);
        if (video) { this.clearVideo(); this.video.set(result.videoUrl ?? null); this.videoChanged.emit(result.videoUrl ?? null); }
        else this.loadMaterials();
        this.notice.set(video ? 'Video guardado en la lección.' : 'Material añadido a la lección.');
      },
      error: e => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos guardar el archivo. Inténtalo nuevamente.')); },
    }));
  }
  removeVideo(): void {
    if (!this.auth.isAdmin() || this.busy() || !confirm('¿Quitar el video de esta lección?')) return;
    this.busy.set(true); this.error.set('');
    this.requests.add(this.http.delete(API_BASE_URL + '/lecciones/' + this.lessonId + '/video').subscribe({
      next: () => { this.busy.set(false); this.clearVideo(); this.video.set(null); this.videoChanged.emit(null); this.notice.set('Video retirado de la lección.'); },
      error: e => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos quitar el video.')); },
    }));
  }
  removeMaterial(material: LessonMaterial): void {
    if (!this.auth.isAdmin() || this.busy() || !confirm('¿Quitar este material de la lección?')) return;
    this.busy.set(true); this.error.set('');
    this.requests.add(this.http.delete(API_BASE_URL + '/recursos-adjuntos/' + material.id).subscribe({
      next: () => { this.busy.set(false); this.materials.update(items => items.filter(i => i.id !== material.id)); this.notice.set('Material retirado de la lección.'); },
      error: e => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos quitar el material.')); },
    }));
  }
  download(material: LessonMaterial): void {
    if (!/^\/api\/lecciones\/[0-9a-f-]{36}\/materiales\/[0-9a-f-]{36}\.pdf$/.test(material.urlArchivo) || this.busy()) return;
    this.busy.set(true); this.error.set('');
    this.requests.add(this.http.get(API_BASE_URL + material.urlArchivo.slice(4), { responseType: 'blob' }).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob); this.downloadUrls.add(url);
        const a = document.createElement('a'); a.href = url; a.download = material.nombre; document.body.appendChild(a); a.click(); a.remove();
        setTimeout(() => { URL.revokeObjectURL(url); this.downloadUrls.delete(url); }, 1000);
        this.busy.set(false);
      },
      error: e => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos descargar el material.')); },
    }));
  }
}
