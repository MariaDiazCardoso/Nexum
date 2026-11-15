import { Component, inject, OnDestroy, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { API_BASE_URL } from '../../core/api.config';
import { getApiErrorMessage } from '../../core/api-error';
import { bcryptByteLimit, passwordComplexity } from '../register/registration.validators';
@Component({
  selector: 'app-password-recovery', imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './password-recovery.page.html', styleUrl: './password-recovery.page.scss',
})
export class PasswordRecoveryPage implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  readonly resetting = inject(ActivatedRoute).snapshot.data['resetting'] === true;
  private token = this.resetting ? new URLSearchParams(location.hash.slice(1)).get('token') ?? '' : '';
  readonly invalidLink = this.resetting && !/^[0-9a-fA-F-]{36}\.[A-Za-z0-9_-]{43}$/.test(this.token);
  readonly busy = signal(false);
  readonly done = signal(false);
  readonly error = signal('');
  readonly showPassword = signal(false);
  readonly resendSeconds = signal(0);
  private timer?: ReturnType<typeof setInterval>;
  readonly emailForm = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]] });
  readonly passwordForm = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), passwordComplexity, bcryptByteLimit]],
    confirm: ['', Validators.required],
  });
  constructor() {
    // El token no permanece en la barra de direcciones ni se envia como Referer.
    if (this.resetting && location.hash) history.replaceState(history.state, '', location.pathname + location.search);
  }
  ngOnDestroy(): void { clearInterval(this.timer); this.token = ''; }
  send(): void {
    if (this.busy() || this.resendSeconds() > 0) return;
    this.emailForm.markAllAsTouched();
    if (this.emailForm.invalid) return;
    this.error.set(''); this.busy.set(true);
    this.http.post<void>(API_BASE_URL + '/auth/password/solicitar', { email: this.emailForm.controls.email.value.trim().toLowerCase() }).subscribe({
      next: () => {
        this.busy.set(false); this.done.set(true); this.resendSeconds.set(60);
        clearInterval(this.timer);
        this.timer = setInterval(() => {
          this.resendSeconds.update(n => Math.max(0, n - 1));
          if (this.resendSeconds() === 0) clearInterval(this.timer);
        }, 1000);
      },
      error: e => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos enviar el enlace. Inténtalo más tarde.')); },
    });
  }
  reset(): void {
    if (this.busy() || this.done() || this.invalidLink) return;
    this.passwordForm.markAllAsTouched(); this.error.set('');
    if (this.passwordForm.invalid) return;
    const { password, confirm } = this.passwordForm.getRawValue();
    if (password !== confirm) { this.error.set('Las contraseñas no coinciden.'); return; }
    this.busy.set(true);
    this.http.post<void>(API_BASE_URL + '/auth/password/restablecer', { token: this.token, password }).subscribe({
      next: () => { this.busy.set(false); this.done.set(true); this.token = ''; this.passwordForm.reset(); },
      error: e => { this.busy.set(false); this.error.set(getApiErrorMessage(e, 'El enlace no es válido o ha vencido. Solicita uno nuevo.')); },
    });
  }
}
