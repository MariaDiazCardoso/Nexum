import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { API_BASE_URL } from '../../../core/api.config';
import { AuthService } from '../../../core/auth.service';
interface AiPlan { id: string; nombre: string; precio: number; moneda: string; creditosPorPeriodo: number; periodo: string; }
@Component({
  selector: 'app-ai-plans',
  imports: [CurrencyPipe, DecimalPipe],
  templateUrl: './ai-plans.component.html',
  styleUrl: './ai-plans.component.scss',
})
export class AiPlansComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly plans = signal<AiPlan[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true); this.failed.set(false);
    this.http.get<AiPlan[]>(API_BASE_URL + '/planes-ia').subscribe({
      next: plans => { this.plans.set(plans); this.loading.set(false); },
      error: () => { this.failed.set(true); this.loading.set(false); },
    });
  }
  choose(plan: AiPlan): void {
    void this.router.navigate(['/checkout'], { queryParams: { tipo: 'ia', id: plan.id } });
  }
  period(value: string): string {
    const labels: Record<string, string> = { mensual: 'mes', anual: 'año', semanal: 'semana', diario: 'día' };
    return labels[value.toLowerCase()] ?? value;
  }
}
