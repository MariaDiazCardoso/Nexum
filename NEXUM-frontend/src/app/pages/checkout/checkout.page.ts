import { CurrencyPipe } from '@angular/common';
import { map } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { API_BASE_URL } from '../../core/api.config';
import { AuthService } from '../../core/auth.service';
import { getApiErrorMessage } from '../../core/api-error';
interface Summary { tipo: string; id: string; titulo: string; monto: number; moneda: string; periodo: string | null; }
@Component({ selector: 'app-checkout', imports: [FormsModule, RouterLink, CurrencyPipe], templateUrl: './checkout.page.html', styleUrl: './checkout.page.scss' })
export class CheckoutPage implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);
  readonly product = signal<Summary | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly complete = signal(false);
  readonly step = signal(1);
  readonly attempted = signal(false);
  readonly showCvv = signal(false);
  method = '';
  holder = ''; cardNumber = ''; expiry = ''; cvv = ''; wallet = 'Yape'; phone = ''; bank = '';
  private lastFour = '';
  readonly banks = ['BCP', 'BBVA', 'Interbank', 'Scotiabank', 'Otro banco'];
  readonly methods = [
    { id: 'TARJETA', title: 'Tarjeta de crédito o débito', description: 'Visa, Mastercard o American Express.', icon: '▤' },
    { id: 'BILLETERA', title: 'Billetera digital', description: 'Elige tu billetera y revisa los datos.', icon: '◈' },
    { id: 'TRANSFERENCIA', title: 'Transferencia bancaria', description: 'Continúa con una transferencia desde tu banco.', icon: '⇄' }
  ];
  ngOnInit(): void { this.load(); }
  ngOnDestroy(): void { this.clearCard(); }
  cardBrand(): string {
    const digits = this.cardNumber.replace(/\D/g, '');
    if (/^4/.test(digits)) return 'Visa';
    if (/^(5[1-5]|2[2-7])/.test(digits)) return 'Mastercard';
    if (/^3[47]/.test(digits)) return 'American Express';
    return 'Tarjeta';
  }
  numberInput(value: string): void { this.cardNumber = value.replace(/\D/g, '').slice(0,19).replace(/(.{4})/g, '$1 ').trim(); }
  expiryInput(value: string): void { const v = value.replace(/\D/g, '').slice(0,4); this.expiry = v.length > 2 ? v.slice(0,2) + '/' + v.slice(2) : v; }
  cvvInput(value: string): void { this.cvv = value.replace(/\D/g, '').slice(0,4); }
  cardValid(): boolean {
    const digits = this.cardNumber.replace(/\D/g, '');
    if (!/^\d{13,19}$/.test(digits) || /^(\d)\1+$/.test(digits)) return false;
    if (this.cardBrand() === 'American Express' && digits.length !== 15) return false;
    let sum = 0; let double = false;
    for (let i = digits.length - 1; i >= 0; i--) { let n = Number(digits[i]); if (double) { n *= 2; if (n > 9) n -= 9; } sum += n; double = !double; }
    return sum % 10 === 0;
  }
  expiryValid(): boolean {
    if (!/^\d{2}\/\d{2}$/.test(this.expiry)) return false;
    const [month, year] = this.expiry.split('/').map(Number); const now = new Date();
    return month >= 1 && month <= 12 && (2000 + year > now.getFullYear() || (2000 + year === now.getFullYear() && month >= now.getMonth() + 1));
  }
  cvvValid(): boolean { return (this.cardBrand() === 'American Express' ? /^\d{4}$/ : /^\d{3}$/).test(this.cvv); }
  detailsValid(): boolean {
    if (this.method === 'TARJETA') return this.holder.trim().length >= 3 && this.cardValid() && this.expiryValid() && this.cvvValid();
    if (this.method === 'BILLETERA') return ['Yape', 'Plin'].includes(this.wallet) && /^9\d{8}$/.test(this.phone);
    return this.method === 'TRANSFERENCIA' && !!this.bank;
  }
  next(): void {
    if (this.step() === 1) { if (this.method) { this.attempted.set(false); this.step.set(2); } return; }
    this.attempted.set(true);
    if (this.detailsValid()) { this.error.set(''); this.step.set(3); }
  }
  cardSuffix(): string { return this.lastFour || this.cardNumber.replace(/\D/g, '').slice(-4); }
  private clearCard(): void { this.holder = ''; this.cardNumber = ''; this.expiry = ''; this.cvv = ''; }
  load(): void {
    this.loading.set(true); this.error.set('');
    const tipo = this.route.snapshot.queryParamMap.get('tipo') ?? '';
    const id = this.route.snapshot.queryParamMap.get('id') ?? '';
    if (tipo !== 'curso' && tipo !== 'ia') { this.error.set('Selecciona un curso o un plan para ver el resumen.'); this.loading.set(false); return; }
    const request = tipo === 'curso'
      ? this.http.get<{ id: string; titulo: string; precio: number }>(API_BASE_URL + '/cursos/' + encodeURIComponent(id)).pipe(map(c => ({ tipo, id: c.id, titulo: c.titulo, monto: c.precio ?? 0, moneda: 'PEN', periodo: null } as Summary)))
      : this.http.get<{ id: string; nombre: string; precio: number; moneda: string; periodo: string }[]>(API_BASE_URL + '/planes-ia').pipe(map(plans => {
          const p = plans.find(p => p.id === id); if (!p) throw new Error('Plan no disponible');
          return { tipo, id: p.id, titulo: p.nombre, monto: p.precio, moneda: p.moneda, periodo: p.periodo } as Summary;
        }));
    request.subscribe({ next: p => { this.product.set(p); this.loading.set(false); }, error: e => { this.loading.set(false); this.error.set(getApiErrorMessage(e, 'No pudimos cargar el resumen.')); } });
  }
  pay(): void {
    if (!this.product() || !this.detailsValid() || this.complete() || this.auth.isAdmin()) return;
    this.lastFour = this.cardNumber.replace(/\D/g, '').slice(-4); this.clearCard();
    this.error.set(''); this.complete.set(true);
  }
  methodTitle(): string { return this.methods.find(m => m.id === this.method)?.title ?? ''; }
}
