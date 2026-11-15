import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminPage } from './admin.page';
import { API_BASE_URL } from '../../core/api.config';
describe('Guardar portada desde administración', () => {
  let page: AdminPage; let http: HttpTestingController;
  const photo = new File(['contenido'], 'foto.png', { type: 'image/png' });
  beforeEach(() => {
    vi.stubGlobal('URL', class extends URL { static override createObjectURL = vi.fn(() => 'blob:seleccion'); static override revokeObjectURL = vi.fn(); });
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    page = TestBed.runInInjectionContext(() => new AdminPage()); http = TestBed.inject(HttpTestingController);
    page.openCourseEditor(); page.courseEditor.update(c => ({ ...c, titulo: 'Curso con portada', responsableId: 'admin' }));
  });
  afterEach(() => { page.ngOnDestroy(); http.verify(); vi.unstubAllGlobals(); });
  function select(file: File): void { page.selectCover({ target: { files: [file], value: '' } } as unknown as Event); }
  it('guarda el curso antes de enviar la imagen y permite reintentar sin duplicarlo', () => {
    select(photo); page.saveCourse();
    http.expectOne(API_BASE_URL + '/cursos').flush({ id: 'curso-1' });
    const upload = http.expectOne(API_BASE_URL + '/cursos/curso-1/portada');
    expect(upload.request.body instanceof FormData).toBe(true); expect(upload.request.body.get('foto').name).toBe('foto.png');
    upload.flush({ message: 'No se pudo guardar la portada' }, { status: 500, statusText: 'Error' });
    expect(page.editingId()).toBe('curso-1'); expect(page.coverPreview()).toBe('blob:seleccion');
    page.saveCourse(); const retry = http.expectOne(API_BASE_URL + '/cursos/curso-1'); expect(retry.request.method).toBe('PUT');
    retry.flush({ id: 'curso-1' }); http.expectOne(API_BASE_URL + '/cursos/curso-1/portada').flush({ id: 'curso-1' });
    http.match(() => true).forEach(req => req.flush([])); expect(page.editorType()).toBeNull();
  });
  it('rechaza archivos que no son fotos sin reemplazar la selección anterior', () => {
    select(photo); select(new File(['texto'], 'archivo.txt', { type: 'text/plain' }));
    expect(page.coverPreview()).toBe('blob:seleccion'); expect(page.mutationError()).toContain('JPG o PNG');
  });
});
