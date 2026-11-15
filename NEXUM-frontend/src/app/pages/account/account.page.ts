import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, Subscription } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { API_BASE_URL } from '../../core/api.config';
import { getApiErrorMessage } from '../../core/api-error';
import {
  CertificateResponse,
  CourseResponse,
  UserMedalResponse,
  UserResponse,
} from '../../core/api.models';
@Component({
  selector: 'app-account-page',
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './account.page.html',
  styleUrl: './account.page.scss',
})
export class AccountPage implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  readonly page = inject(ActivatedRoute).snapshot.data['accountPage'] as string;
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly uploading = signal(false);
  readonly photoError = signal('');
  readonly imageFailed = signal(false);
  readonly medals = signal<UserMedalResponse[]>([]);
  readonly certificates = signal<CertificateResponse[]>([]);
  readonly courses = signal<CourseResponse[]>([]);
  nombre = '';
  apellido = '';
  email = '';
  avatarUrl = '';
  ngOnInit(): void {
    this.load();
  }
  load(): void {
    const id = this.auth.userId();
    if (!id) {
      this.error.set('Vuelve a iniciar sesión para abrir tu espacio.');
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set('');
    if (this.page === 'profile')
      this.auth.refreshUser().subscribe({
        next: (user) => {
          this.nombre = user.nombre;
          this.apellido = user.apellido;
          this.email = user.email;
          this.avatarUrl = user.avatarUrl ?? '';
          this.loading.set(false);
        },
        error: (e) => this.fail(e),
      });
    else if (this.page === 'medals')
      this.http.get<UserMedalResponse[]>(`${API_BASE_URL}/usuarios/${id}/medallas`).subscribe({
        next: (items) => {
          this.medals.set(items);
          this.loading.set(false);
        },
        error: (e) => this.fail(e),
      });
    else
      forkJoin({
        certificates: this.http.post<CertificateResponse[]>(`${API_BASE_URL}/certificados/emitir-pendientes`, {}),
        courses: this.http.get<CourseResponse[]>(`${API_BASE_URL}/cursos`),
      }).subscribe({
        next: (data) => {
          this.certificates.set(data.certificates);
          this.courses.set(data.courses);
          this.loading.set(false);
        },
        error: (e) => this.fail(e),
      });
  }
  readonly downloadingCertificate = signal<string | null>(null);
  private downloadSubscription?: Subscription;
  private downloadUrl = '';
  ngOnDestroy(): void {
    this.downloadSubscription?.unsubscribe();
    if (this.downloadUrl) URL.revokeObjectURL(this.downloadUrl);
  }
  downloadCertificate(certificate: CertificateResponse): void {
    if (this.downloadingCertificate()) return;
    this.downloadingCertificate.set(certificate.id); this.error.set('');
    this.downloadSubscription = this.http.get(`${API_BASE_URL}/certificados/${certificate.id}/pdf`, { responseType: 'blob' }).subscribe({
      next: blob => {
        if (this.downloadUrl) URL.revokeObjectURL(this.downloadUrl);
        this.downloadUrl = URL.createObjectURL(blob);
        const a = document.createElement('a'); a.href = this.downloadUrl;
        a.download = 'NEXUM-certificado-' + certificate.codigoVerificacion.replace(/[^A-Za-z0-9-]/g, '') + '.pdf';
        document.body.appendChild(a); a.click(); a.remove();
        this.downloadingCertificate.set(null);
      },
      error: e => { this.downloadingCertificate.set(null); this.error.set(getApiErrorMessage(e, 'No pudimos descargar tu certificado. Inténtalo nuevamente.')); },
    });
  }
  private fail(e: unknown): void {
    this.loading.set(false);
    this.error.set(getApiErrorMessage(e, 'No pudimos cargar tu información.'));
  }
  save(): void {
    const id = this.auth.userId();
    if (!id || this.saving() || this.uploading()) return;
    if (!this.nombre.trim() || !this.apellido.trim()) {
      this.error.set('Escribe tu nombre y apellido.');
      return;
    }
    if (this.avatarUrl.trim() && !this.avatarImage() && !this.imageFailed()) {
      this.error.set('La imagen debe usar una URL http o https.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.notice.set('');
    this.http
      .patch<UserResponse>(`${API_BASE_URL}/usuarios/${id}`, {
        nombre: this.nombre.trim(),
        apellido: this.apellido.trim(),

      })
      .subscribe({
        next: (user) => {
          this.auth.user.set(user);
          this.nombre = user.nombre;
          this.apellido = user.apellido;
          this.email = user.email;
          this.avatarUrl = user.avatarUrl ?? '';
          this.saving.set(false);
          this.notice.set('Tu perfil se ha actualizado.');
        },
        error: (e) => {
          this.saving.set(false);
          this.error.set(getApiErrorMessage(e, 'No pudimos guardar tu perfil.'));
        },
      });
  }
  avatarImage(): string | null {
    if (this.imageFailed()) return null;
    if (/^\/api\/avatares\/[0-9a-f-]+\.png$/.test(this.avatarUrl)) {
      return API_BASE_URL.replace(/\/api$/, '') + this.avatarUrl;
    }
    return this.safeUrl(this.avatarUrl);
  }
  uploadPhoto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.uploading() || this.saving()) return;
    this.photoError.set('');
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      this.photoError.set('Elige una foto JPG o PNG de hasta 5 MB.');
      return;
    }
    const id = this.auth.userId();
    if (!id) return;
    const data = new FormData(); data.append('foto', file);
    this.uploading.set(true);
    this.notice.set('');
    this.http.post<UserResponse>(API_BASE_URL + '/usuarios/' + id + '/avatar', data).subscribe({
      next: user => this.photoSaved(user, 'Tu foto se ha actualizado.'),
      error: e => { this.uploading.set(false); this.photoError.set(getApiErrorMessage(e, 'No pudimos subir tu foto. Inténtalo de nuevo.')); },
    });
  }
  removePhoto(): void {
    const id = this.auth.userId();
    if (!id || this.uploading() || this.saving()) return;
    this.uploading.set(true); this.photoError.set('');
    this.http.delete<UserResponse>(API_BASE_URL + '/usuarios/' + id + '/avatar').subscribe({
      next: user => this.photoSaved(user, 'Foto eliminada. Ahora se muestran tus iniciales.'),
      error: e => { this.uploading.set(false); this.photoError.set(getApiErrorMessage(e, 'No pudimos quitar tu foto.')); },
    });
  }
  private photoSaved(user: UserResponse, message: string): void {
    this.auth.user.set(user); this.avatarUrl = user.avatarUrl ?? '';
    this.imageFailed.set(false); this.uploading.set(false); this.notice.set(message);
  }
  courseTitle(id: string): string {
    return this.courses().find((c) => c.id === id)?.titulo ?? 'Curso completado';
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
