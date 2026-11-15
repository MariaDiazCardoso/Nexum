import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { getApiErrorMessage } from '../../core/api-error';
import { bcryptByteLimit, birthDate, localDateToday, nonBlank, passwordComplexity } from './registration.validators';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.page.html',
  styleUrl: './register.page.scss',
})
export class RegisterPage {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);
  readonly errorMessage = signal('');
  readonly showPassword = signal(false);
  readonly today = localDateToday();

  readonly form = this.formBuilder.nonNullable.group({
    nombre: ['', [Validators.required, nonBlank, Validators.maxLength(100)]],
    apellido: ['', [Validators.required, nonBlank, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, nonBlank, Validators.minLength(8), Validators.maxLength(72), bcryptByteLimit, passwordComplexity]],
    genero: this.formBuilder.nonNullable.control<'' | 'FEMENINO' | 'MASCULINO'>('', [Validators.required, Validators.pattern(/^(FEMENINO|MASCULINO)$/)]),
    fechaNacimiento: ['', [Validators.required, birthDate]],
  });

  submit(): void {
    this.errorMessage.set('');
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) {
      return;
    }

    const values = this.form.getRawValue();
    if (values.genero !== 'FEMENINO' && values.genero !== 'MASCULINO') return;
    this.submitting.set(true);
    this.authService.register({
      ...values,
      nombre: values.nombre.trim(),
      apellido: values.apellido.trim(),
      email: values.email.trim().toLowerCase(),
      genero: values.genero,
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigate(['/verify-email'], {
          state: { newRegistration: true },
          queryParams: { email: values.email.trim().toLowerCase(), returnUrl: this.route.snapshot.queryParamMap.get('returnUrl') },
        });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(
          error instanceof HttpErrorResponse && error.status === 409
            ? 'Ya existe una cuenta con ese correo. Intenta iniciar sesión.'
            : getApiErrorMessage(error, 'No se pudo crear la cuenta. Revisa tus datos e inténtalo de nuevo.'),
        );
      },
    });
  }
}
