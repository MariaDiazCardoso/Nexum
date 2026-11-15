import { Routes } from '@angular/router';
import { LoginPage } from './pages/login/login.page';
import { RegisterPage } from './pages/register/register.page';
import { CatalogPage } from './pages/catalog/catalog.page';
import { authGuard, adminGuard, studentGuard } from './core/auth.guard';
import { AdminPage } from './pages/admin/admin.page';
import { CourseDetailPage } from './pages/course-detail/course-detail.page';
import { MyLearningPage } from './pages/my-learning/my-learning.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'catalog' },
  { path: 'checkout', loadComponent: () => import('./pages/checkout/checkout.page').then(m => m.CheckoutPage), canActivate: [authGuard], title: 'Método de pago | Nexum' },
  { path: 'verify-email', loadComponent: () => import('./pages/verify-email/verify-email.page').then(m => m.VerifyEmailPage), title: 'Verifica tu correo | Nexum' },
  { path: 'forgot-password', loadComponent: () => import('./pages/password-recovery/password-recovery.page').then(m => m.PasswordRecoveryPage), title: 'Recuperar acceso | Nexum' },
  { path: 'reset-password', loadComponent: () => import('./pages/password-recovery/password-recovery.page').then(m => m.PasswordRecoveryPage), data: { resetting: true }, title: 'Nueva contraseña | Nexum' },
  { path: 'verify-certificate', loadComponent: () => import('./pages/verify-certificate/verify-certificate.page').then(m => m.VerifyCertificatePage), title: 'Verificar certificado | Nexum' },
  { path: 'login', component: LoginPage, title: 'Iniciar sesión | Nexum' },
  { path: 'register', component: RegisterPage, title: 'Crear cuenta | Nexum' },
  {
    path: 'catalog',
    component: CatalogPage,
    title: 'Explorar cursos | Nexum',
  },
  {
    path: 'courses/:id',
    component: CourseDetailPage,
    title: 'Curso | Nexum',
  },
  {
    path: 'my-learning',
    component: MyLearningPage,
    canActivate: [studentGuard],
    title: 'Mis cursos | Nexum',
  },
  {
    path: 'admin',
    component: AdminPage,
    canActivate: [adminGuard],
    title: 'Panel administrador | Nexum',
  },
  {
    path: 'profile',
    loadComponent: () => import('./pages/account/account.page').then((m) => m.AccountPage),
    canActivate: [authGuard],
    data: { accountPage: 'profile' },
    title: 'Mi perfil | Nexum',
  },
  {
    path: 'medals',
    loadComponent: () => import('./pages/account/account.page').then((m) => m.AccountPage),
    canActivate: [studentGuard],
    data: { accountPage: 'medals' },
    title: 'Mis medallas | Nexum',
  },
  {
    path: 'certificates',
    loadComponent: () => import('./pages/account/account.page').then((m) => m.AccountPage),
    canActivate: [studentGuard],
    data: { accountPage: 'certificates' },
    title: 'Mis certificados | Nexum',
  },
  {
    path: 'courses/:id/lessons/:lessonId',
    loadComponent: () => import('./pages/lesson/lesson.page').then((m) => m.LessonPage),
    canActivate: [authGuard],
    title: 'Lección | Nexum',
  },
  { path: '**', redirectTo: 'catalog' },
];
