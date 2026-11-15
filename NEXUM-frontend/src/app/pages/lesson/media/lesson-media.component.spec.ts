import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SimpleChange, signal } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/auth.service';
import { API_BASE_URL } from '../../../core/api.config';
import { LessonMediaComponent } from './lesson-media.component';
describe('Videos y materiales de lección', () => {
  let media: LessonMediaComponent; let http: HttpTestingController;
  const isAdmin = signal(false);
  const lessonId = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a90';
  const url = '/api/lecciones/' + lessonId + '/video/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a91.mp4';
  const revoke = vi.fn();
  beforeEach(() => {
    isAdmin.set(false); revoke.mockClear();
    vi.stubGlobal('URL', class extends URL { static override createObjectURL = vi.fn(() => 'blob:video'); static override revokeObjectURL = revoke; });
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { isAdmin } }] });
    media = TestBed.runInInjectionContext(() => new LessonMediaComponent()); http = TestBed.inject(HttpTestingController); media.lessonId = lessonId;
  });
  afterEach(() => { media.ngOnDestroy(); http.verify(); vi.unstubAllGlobals(); });
  it('consulta materiales sin descargar automáticamente el video', () => {
    media.videoUrl = url; media.ngOnChanges({ lessonId: new SimpleChange('', lessonId, true) });
    http.expectOne(r => r.url === API_BASE_URL + '/recursos-adjuntos' && r.params.get('leccionId') === lessonId).flush([]);
    http.expectNone(url); expect(media.isLocalVideo()).toBe(true);
  });
  it('carga el video protegido una vez y libera su memoria al salir', () => {
    media.video.set(url); media.loadVideo(); media.loadVideo();
    const req = http.expectOne(url); expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['video'], { type: 'video/mp4' })); expect(media.videoSource()).toBe('blob:video');
    media.ngOnDestroy(); expect(revoke).toHaveBeenCalledWith('blob:video');
  });
  it('no permite subir a un alumno', () => {
    const file = new File(['video'], 'video.mp4', { type: 'video/mp4' });
    media.upload({ target: { files: [file], value: '' } } as unknown as Event, 'video');
    http.expectNone(() => true);
  });
  it('envía el video como archivo y actualiza la lección tras guardarlo', () => {
    isAdmin.set(true); const emit = vi.spyOn(media.videoChanged, 'emit');
    const file = new File(['video'], 'video.mp4', { type: 'video/mp4' });
    media.upload({ target: { files: [file], value: '' } } as unknown as Event, 'video');
    const req = http.expectOne(API_BASE_URL + '/lecciones/' + lessonId + '/video');
    expect(req.request.body instanceof FormData).toBe(true); expect(media.busy()).toBe(true);
    req.flush({ videoUrl: url }); expect(media.busy()).toBe(false); expect(emit).toHaveBeenCalledWith(url);
  });
  it('conserva el video anterior si falla una nueva subida', () => {
    isAdmin.set(true); media.video.set(url);
    const file = new File(['video'], 'video.mp4', { type: 'video/mp4' });
    media.upload({ target: { files: [file], value: '' } } as unknown as Event, 'video');
    http.expectOne(API_BASE_URL + '/lecciones/' + lessonId + '/video').flush({ message: 'Archivo no válido' }, { status: 400, statusText: 'Bad Request' });
    expect(media.video()).toBe(url); expect(media.error()).toBe('Archivo no válido'); expect(media.busy()).toBe(false);
  });
  it('solo abre enlaces externos HTTP o HTTPS', () => {
    expect(media.externalUrl('javascript:alert(1)')).toBeNull();
    expect(media.externalUrl('https://example.com/video')).toBe('https://example.com/video');
  });
});
