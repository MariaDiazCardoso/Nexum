import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CourseCoverComponent } from './course-cover.component';
describe('Portada del curso', () => {
  let cover: CourseCoverComponent; let http: HttpTestingController;
  const src = '/api/cursos/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a90/portada/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a91.jpg';
  const revoke = vi.fn();
  beforeEach(() => {
    vi.stubGlobal('URL', class extends URL { static override createObjectURL = vi.fn(() => 'blob:portada'); static override revokeObjectURL = revoke; });
    revoke.mockClear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    cover = TestBed.runInInjectionContext(() => new CourseCoverComponent()); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { cover.ngOnDestroy(); http.verify(); vi.unstubAllGlobals(); });
  it('carga por HttpClient y libera la URL al salir', () => {
    cover.src = src; cover.ngOnChanges();
    const req = http.expectOne(src); expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['imagen'], { type: 'image/jpeg' }));
    expect(cover.image()).toBe('blob:portada'); cover.ngOnDestroy(); expect(revoke).toHaveBeenCalledWith('blob:portada');
  });
  it('mantiene la alternativa si la imagen no existe', () => {
    cover.src = src; cover.ngOnChanges(); http.expectOne(src).flush(null, { status: 404, statusText: 'Not Found' });
    expect(cover.image()).toBe('');
  });
  it('no consulta rutas externas y admite la vista previa local', () => {
    cover.src = 'https://example.com/foto.jpg'; cover.ngOnChanges(); expect(cover.image()).toBe('');
    cover.src = 'blob:seleccion'; cover.ngOnChanges(); expect(cover.image()).toBe('blob:seleccion');
    http.expectNone(() => true);
  });
});
