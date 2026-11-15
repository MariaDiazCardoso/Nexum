import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { API_BASE_URL } from '../../core/api.config';
import { getApiErrorMessage } from '../../core/api-error';

@Component({ selector: 'app-verify-email', imports: [FormsModule, RouterLink],
  templateUrl: './verify-email.page.html', styleUrl: '../checkout/checkout.page.scss' })
export class VerifyEmailPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  readonly returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
  email = this.route.snapshot.queryParamMap.get('email') ?? '';
  codigo = '';
  readonly busy = signal(false);
  readonly error = signal('');
  readonly done = signal(false);
  readonly sent = signal(false);
  readonly resendSeconds = signal(0);
  private resendTimer?: ReturnType<typeof setInterval>;
  ngOnDestroy(): void { clearInterval(this.resendTimer); }
  private startResendPause(): void {
    clearInterval(this.resendTimer);
    this.resendSeconds.set(60);
    this.resendTimer = setInterval(() => {
      this.resendSeconds.update(seconds => Math.max(0, seconds - 1));
      if (this.resendSeconds() === 0) clearInterval(this.resendTimer);
    }, 1000);
  }
  ngOnInit(): void {
    if (this.email && history.state['newRegistration']) this.send();
  }
  private validEmail(): boolean {
    return this.email.length <= 255 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email.trim());
  }
  send(): void {
    if (this.busy() || this.resendSeconds() > 0) return;
    this.error.set(''); this.sent.set(false);
    if (!this.validEmail()) { this.error.set('Ingresa un correo electrónico válido.'); return; }
    this.busy.set(true);
    this.http.post<void>(`${API_BASE_URL}/auth/verificacion/solicitar`, { email: this.email.trim().toLowerCase() }).subscribe({
      next: () => { this.busy.set(false); this.sent.set(true); this.startResendPause(); },
      error: (e: unknown) => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'No se pudo enviar el código. Inténtalo más tarde.')); },
    });
  }
  confirm(): void {
    if (this.busy()) return;
    this.error.set('');
    if (!this.validEmail() || !/^[0-9]{6}$/.test(this.codigo)) {
      this.error.set('Ingresa tu correo y el código de 6 dígitos.'); return;
    }
    this.busy.set(true);
    this.http.post<void>(`${API_BASE_URL}/auth/verificacion/confirmar`, {
      email: this.email.trim().toLowerCase(), codigo: this.codigo,
    }).subscribe({
      next: () => { this.busy.set(false); this.done.set(true); },
      error: (e: unknown) => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'El código no es válido o ha vencido.')); },
    });
  }
}
