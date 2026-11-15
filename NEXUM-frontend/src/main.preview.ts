import { bootstrapApplication } from '@angular/platform-browser';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import {
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { App } from './app/app';
import { routes } from './app/app.routes';
import { PreviewPages } from './app/preview/preview-pages';
import { AuthService } from './app/core/auth.service';
import { CourseResponse, UserResponse } from './app/core/api.models';

// Solo se incluye al ejecutar la configuracion preview. Nunca llama al backend.
const user: UserResponse = {
  id: 'demo-user',
  nombre: 'Alex',
  apellido: 'Demo',
  email: 'alex@example.invalid',
  rol: 'ADMINISTRADOR',
  puntosXp: 420,
  avatarUrl: null,
  fechaRegistro: '2026-10-01T12:00:00',
  ultimaConexion: null,
  emailVerificado: true,
};
const categories = ['Programación', 'Diseño', 'Productividad'].map((nombre, i) => ({
  id: `cat-${i}`,
  nombre,
  iconoUrl: null,
}));
const courses: CourseResponse[] = [
  [
    'Angular desde cero',
    'Construye interfaces modernas paso a paso con componentes, rutas y formularios.',
    'Principiante',
    0,
  ],
  [
    'Diseño de interfaces digitales',
    'Transforma tus ideas en experiencias claras, accesibles y atractivas.',
    'Principiante',
    1,
  ],
  [
    'Java y Spring Boot',
    'Aprende a construir APIs y conectar tus aplicaciones a una base de datos.',
    'Intermedio',
    0,
  ],
  [
    'Organiza tus proyectos',
    'Crea hábitos y un sistema de trabajo para convertir tus metas en resultados.',
    'Principiante',
    2,
  ],
  [
    'Diseño de sistemas visuales',
    'Crea una identidad consistente con colores, tipografía y componentes.',
    'Intermedio',
    1,
  ],
  [
    'PostgreSQL práctico',
    'Domina consultas y relaciones con ejercicios aplicados.',
    'Intermedio',
    0,
  ],
].map(([titulo, descripcion, nivel, cat], i) => ({
  id: `curso-${i + 1}`,
  titulo: String(titulo),
  descripcion: String(descripcion),
  nivel: String(nivel),
  slug: `curso-${i + 1}`,
  responsableId: user.id,
  precio: 0,
  estado: 'PUBLICADO',
  promedioValoracion: 4.8,
  fechaCreacion: '2026-10-01T12:00:00',
  recomendadoPara: 'Personas con curiosidad y ganas de aprender',
  duracionEstimada: '4 horas',
  ruta: 'Aprende a tu ritmo',
  categoriaAcademica: categories[Number(cat)].nombre,
  categoriaIds: [categories[Number(cat)].id],
}));
const enrollments = courses
  .slice(0, 3)
  .map((c, i) => ({
    id: `ins-${i}`,
    usuarioId: user.id,
    cursoId: c.id,
    estado: 'ACTIVA',
    progresoPorcentaje: [35, 65, 10][i],
    fechaInscripcion: '2026-10-04T12:00:00',
  }));
const previewHttp: HttpInterceptorFn = (req) => {
  if (req.method !== 'GET')
    return throwError(
      () =>
        new HttpErrorResponse({
          status: 403,
          error: { message: 'Esta es una vista previa: los cambios no se guardan.' },
        }),
    );
  const path = new URL(req.url, location.origin).pathname;
  const courseId = req.params.get('cursoId') || 'curso-1';
  let body: unknown = [];
  if (/\/cursos\/[^/]+$/.test(path))
    body = courses.find((c) => c.id === path.split('/').pop()) || courses[0];
  else if (path.endsWith('/cursos')) body = courses;
  else if (path.endsWith('/medallas'))
    body = [
      {
        usuarioId: user.id,
        medallaId: 'medal-1',
        nombre: 'Primer paso',
        descripcion: 'Tu primera lección completada.',
        imagenUrl: null,
        fechaObtencion: '2026-10-12T12:00:00',
      },
    ];
  else if (path.endsWith('/certificados'))
    body = [
      {
        id: 'certificate-1',
        inscripcionId: 'ins-0',
        usuarioId: user.id,
        cursoId: 'curso-1',
        codigoVerificacion: 'MUESTRA-SIN-VALIDEZ',
        urlPdf: null,
        fechaEmision: '2026-10-12T12:00:00',
      },
    ];
  else if (path.endsWith('/categorias')) body = categories;
  else if (/\/usuarios\/[^/]+$/.test(path)) body = user;
  else if (path.endsWith('/usuarios'))
    body = [
      user,
      {
        ...user,
        id: 'demo-2',
        nombre: 'Lucía',
        apellido: 'Ejemplo',
        email: 'lucia@example.invalid',
        rol: 'USUARIO',
      },
      {
        ...user,
        id: 'demo-3',
        nombre: 'Diego',
        apellido: 'Ejemplo',
        email: 'diego@example.invalid',
        rol: 'USUARIO',
      },
    ];
  else if (path.endsWith('/inscripciones')) body = enrollments;
  else if (path.endsWith('/modulos'))
    body = [1, 2].map((n) => ({
      id: `${courseId}-mod-${n}`,
      cursoId: courseId,
      titulo: n === 1 ? 'Primeros pasos' : 'Ponlo en práctica',
      orden: n,
      descripcion: 'Un recorrido guiado con ejercicios.',
      slug: `modulo-${n}`,
    }));
  else if (path.endsWith('/lecciones'))
    body = [1, 2, 3, 4].map((n) => ({
      id: `leccion-${n}`,
      moduloId: `${courseId}-mod-${n < 3 ? 1 : 2}`,
      titulo: [
        'Bienvenido a tu curso',
        'Los conceptos esenciales',
        'Tu primer proyecto',
        'Un reto para seguir creciendo',
      ][n - 1],
      tipo: 'TEXTO',
      orden: n,
      contenidoTexto:
        'Cada gran proyecto empieza con un primer paso. En esta lección de ejemplo descubrirás los conceptos básicos y podrás aplicar lo aprendido a tu propio proyecto.',
      puntosOtorgados: 50,
      videoUrl: null,
      xpOtorgada: 50,
      slug: `leccion-${n}`,
      retoPractico: 'Escribe una idea de proyecto que te gustaría construir.',
      dificultad: 'Principiante',
      duracionEstimada: '15 min',
      duracionMinima: 0,
      imagenUrl: null,
    }));
  return of(new HttpResponse({ status: 200, body }));
};
const previewAuth = {
  user: signal(user),
  userId: signal(user.id),
  role: signal('ADMINISTRADOR'),
  isAuthenticated: signal(true),
  isAdmin: signal(true),
  refreshUser: () => of(user),
  logout: () => {},
  login: () => throwError(() => new Error('Vista previa')),
  register: () => throwError(() => new Error('Vista previa')),
};
const banner = document.createElement('div');
banner.textContent = 'VISTA PREVIA · Datos de ejemplo · Los cambios no se guardan';
banner.style.cssText =
  'padding:12px 20px;text-align:center;background:#172554;color:#f8fafc;font:600 13px Segoe UI,sans-serif';
document.body.prepend(banner);
const previewLink = document.createElement('a');
previewLink.href = '/profile';
previewLink.textContent = ' · Explorar nuevas páginas →';
previewLink.style.cssText = 'color:#93c5fd;margin-left:12px';
banner.append(previewLink);
const connectedLink = document.createElement('a');
connectedLink.href = '/account/profile';
connectedLink.textContent = ' · Ver páginas conectadas';
connectedLink.style.cssText = 'color:#93c5fd;margin-left:12px';
banner.append(connectedLink);
bootstrapApplication(App, {
  providers: [
    provideRouter([
      {
        path: 'account/profile',
        loadComponent: () => import('./app/pages/account/account.page').then((m) => m.AccountPage),
        data: { accountPage: 'profile' },
        title: 'Perfil conectado (muestra) | Nexum',
      },
      ...[
        'profile',
        'recover-password',
        'reset-password',
        'verify-email',
        'lesson',
        'assessment',
        'achievements',
        'not-found',
      ]
        .map((path, i) => ({
          path,
          component: PreviewPages,
          data: {
            page: [
              'profile',
              'recover',
              'reset',
              'verify',
              'lesson',
              'assessment',
              'achievements',
              'not-found',
            ][i],
          },
          title: 'Tu espacio | Nexum',
        }))
        .filter((route) => route.path !== 'profile'),
      ...routes.slice(0, -1),
      { path: '**', component: PreviewPages, data: { page: 'not-found' } },
    ]),
    provideHttpClient(withInterceptors([previewHttp])),
    { provide: AuthService, useValue: previewAuth },
  ],
}).catch(console.error);
