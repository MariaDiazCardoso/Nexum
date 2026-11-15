import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { API_BASE_URL } from '../../core/api.config';
import { getApiErrorMessage } from '../../core/api-error';
interface PublicCertificate { codigo: string; nombreAlumno: string; curso: string; fechaEmision: string; }
@Component({ selector: 'app-verify-certificate', imports: [ReactiveFormsModule, RouterLink, DatePipe], templateUrl: './verify-certificate.page.html', styleUrl: './verify-certificate.page.scss' })
export class VerifyCertificatePage implements OnInit {
 private readonly http = inject(HttpClient);
 private readonly route = inject(ActivatedRoute);
 private readonly fb = inject(FormBuilder);
 readonly busy = signal(false);
 readonly error = signal('');
 readonly certificate = signal<PublicCertificate | null>(null);
 readonly form = this.fb.nonNullable.group({ codigo: ['', [Validators.required, Validators.pattern('[A-Za-z0-9-]{8,100}')]] });
 ngOnInit(): void {
  const code = this.route.snapshot.queryParamMap.get('codigo');
  if (code) { this.form.setValue({ codigo: code.trim() }); this.verify(); }
 }
 verify(): void {
  if (this.busy()) return;
  this.form.markAllAsTouched(); this.error.set(''); this.certificate.set(null);
  const code = this.form.controls.codigo.value.trim().toUpperCase(); this.form.controls.codigo.setValue(code);
  if (this.form.invalid) return;
  this.busy.set(true);
  this.http.get<PublicCertificate>(API_BASE_URL + '/certificados/verificar/' + encodeURIComponent(code)).subscribe({
   next: c => { this.busy.set(false); this.certificate.set(c); },
   error: (e: unknown) => { this.busy.set(false); this.error.set(e instanceof HttpErrorResponse && e.status === 404 ? 'No encontramos un certificado con ese código. Comprueba los caracteres e inténtalo nuevamente.' : getApiErrorMessage(e, 'No pudimos comprobar el certificado. Inténtalo más tarde.')); },
  });
 }
}
