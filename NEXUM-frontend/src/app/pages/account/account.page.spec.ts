import { vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { AccountPage } from './account.page';
import { AuthService } from '../../core/auth.service';
import { API_BASE_URL } from '../../core/api.config';
import { UserResponse } from '../../core/api.models';

const user: UserResponse = {
  id: 'user-1',
  nombre: 'Ana',
  apellido: 'Díaz',
  email: 'ana@example.invalid',
  rol: 'USUARIO',
  puntosXp: 125,
  avatarUrl: null,
  fechaRegistro: '2026-10-01T00:00:00',
  ultimaConexion: null,
  emailVerificado: true,
};
describe('AccountPage: datos personales reales', () => {
  let http: HttpTestingController;
  let page: AccountPage;
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('nexum.userId', 'user-1');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { data: { accountPage: 'profile' } } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    page = TestBed.runInInjectionContext(() => new AccountPage());
  });
  afterEach(() => {
    page.ngOnDestroy(); vi.restoreAllMocks(); vi.unstubAllGlobals();
    http.verify();
    sessionStorage.clear();
  });
  it('recupera el perfil y los XP del usuario de la sesión', () => {
    page.load();
    http.expectOne(`${API_BASE_URL}/usuarios/user-1`).flush(user);
    expect(page.nombre).toBe('Ana');
    expect(TestBed.inject(AuthService).user()?.puntosXp).toBe(125);
    expect(page.loading()).toBe(false);
  });
  it('guarda solo datos editables sin enviar XP, rol o correo', () => {
    page.nombre = ' Ana ';
    page.apellido = ' Díaz ';
    page.avatarUrl = 'https://example.invalid/avatar.png';
    page.save();
    const request = http.expectOne(`${API_BASE_URL}/usuarios/user-1`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({
      nombre: 'Ana',
      apellido: 'Díaz',
    });
    request.flush(user);
    expect(page.saving()).toBe(false);
    expect(TestBed.inject(AuthService).user()?.nombre).toBe('Ana');
  });
  it('rechaza archivos que no son fotos antes de subirlos', () => {
    const input = { files: [new File(['texto'], 'archivo.txt', { type: 'text/plain' })], value: 'archivo.txt' };
    page.uploadPhoto({ target: input } as unknown as Event);
    expect(page.photoError()).toContain('JPG o PNG');
    expect(page.uploading()).toBe(false);
  });
  it('usa las iniciales cuando la imagen falla', () => {
    page.avatarUrl = '/api/avatares/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png';
    expect(page.avatarImage()).toBeTruthy();
    page.imageFailed.set(true);
    expect(page.avatarImage()).toBeNull();
  });
  it('rechaza URLs de avatar con protocolos no permitidos', () => {
    page.nombre = 'Ana';
    page.apellido = 'Díaz';
    page.avatarUrl = 'javascript:alert(1)';
    page.save();
    http.expectNone(`${API_BASE_URL}/usuarios/user-1`);
    expect(page.error()).toContain('http o https');
  });

  it('emite los certificados pendientes al consultar Mis certificados', () => {
    TestBed.inject(ActivatedRoute).snapshot.data['accountPage'] = 'certificates';
    page = TestBed.runInInjectionContext(() => new AccountPage()); page.load();
    const req = http.expectOne(API_BASE_URL + '/certificados/emitir-pendientes'); expect(req.request.method).toBe('POST');
    req.flush([{ id: 'c1', tituloCurso: 'Redes' }]); http.expectOne(API_BASE_URL + '/cursos').flush([]);
    expect(page.certificates()).toHaveLength(1); expect(page.loading()).toBe(false);
  });
  it('descarga el PDF por una solicitud autenticada y libera la URL temporal', () => {
    const revoke = vi.fn();
    vi.stubGlobal('URL', class extends URL { static override createObjectURL = vi.fn(() => 'blob:certificado'); static override revokeObjectURL = revoke; });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const certificate = { id: 'c1', codigoVerificacion: 'ABCDEF123456' } as import('../../core/api.models').CertificateResponse;
    page.downloadCertificate(certificate);
    const req = http.expectOne(API_BASE_URL + '/certificados/c1/pdf'); expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF-1.7'], { type: 'application/pdf' })); expect(page.downloadingCertificate()).toBeNull();
    page.ngOnDestroy(); expect(revoke).toHaveBeenCalledWith('blob:certificado');
  });
});
