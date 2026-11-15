import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { API_BASE_URL } from './core/api.config';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly darkMode = signal(false);

  constructor() {
    if (this.auth.isAuthenticated()) {
      this.auth.refreshUser().subscribe({ error: () => {} });
    }
    let preference: string | null = null;
    try { preference = localStorage.getItem('nexum-theme'); } catch {}
    const dark = preference === 'dark' || (preference !== 'light' && globalThis.matchMedia?.('(prefers-color-scheme: dark)').matches);
    this.darkMode.set(Boolean(dark));
    document.documentElement.dataset['theme'] = dark ? 'dark' : 'light';
  }

  protected toggleTheme(): void {
    const dark = !this.darkMode();
    this.darkMode.set(dark);
    document.documentElement.dataset['theme'] = dark ? 'dark' : 'light';
    try { localStorage.setItem('nexum-theme', dark ? 'dark' : 'light'); } catch {}
  }

  protected readonly currentYear = new Date().getFullYear();
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly failedAvatar = signal<string | null>(null);
  protected readonly avatarImage = computed(() => {
    const value = this.auth.user()?.avatarUrl;
    if (!value) return null;
    let image: string;
    if (/^\/api\/avatares\/[0-9a-f-]+\.png$/.test(value)) {
      image = API_BASE_URL.replace(/\/api$/, '') + value;
    } else {
      try {
        const url = new URL(value);
        if (!['http:', 'https:'].includes(url.protocol)) return null;
        image = url.href;
      } catch { return null; }
    }
    return this.failedAvatar() === image ? null : image;
  });
  protected readonly initials = computed(() => {
    const user = this.auth.user();
    return ((user?.nombre?.trim()[0] ?? '') + (user?.apellido?.trim()[0] ?? '')).toLocaleUpperCase();
  });

  protected async logout(): Promise<void> {
    this.auth.logout();
    try {
      const navigated = await this.router.navigateByUrl('/login', { replaceUrl: true });
      if (!navigated) {
        globalThis.location.replace('/login');
      }
    } catch (error) {
      console.error('No se pudo navegar al inicio de sesión después de cerrar sesión.', error);
      globalThis.location.replace('/login');
    }
  }
}
