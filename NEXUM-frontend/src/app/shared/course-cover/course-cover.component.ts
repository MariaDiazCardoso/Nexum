import { Component, Input, OnChanges, OnDestroy, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { API_BASE_URL } from '../../core/api.config';
@Component({
  selector: 'app-course-cover',
  template: `@if (image()) { <img [src]="image()" [alt]="alt" (error)="image.set('')" /> } @else { <ng-content /> }`,
  styles: `:host{display:flex;align-items:center;justify-content:center;width:100%;height:100%;overflow:hidden;position:relative}img{display:block;width:100%;height:100%;object-fit:cover}`,
})
export class CourseCoverComponent implements OnChanges, OnDestroy {
  @Input() src: string | null | undefined;
  @Input() alt = '';
  private readonly http = inject(HttpClient);
  readonly image = signal('');
  private subscription?: Subscription;
  private objectUrl = '';
  ngOnChanges(): void {
    this.release(); this.image.set('');
    if (!this.src) return;
    if (this.src.startsWith('blob:')) { this.image.set(this.src); return; }
    if (!/^\/api\/cursos\/[0-9a-f-]{36}\/portada\/[0-9a-f-]{36}\.jpg$/.test(this.src)) return;
    this.subscription = this.http.get(API_BASE_URL + this.src.slice(4), { responseType: 'blob' }).subscribe({
      next: blob => { this.objectUrl = URL.createObjectURL(blob); this.image.set(this.objectUrl); },
      error: () => this.image.set(''),
    });
  }
  private release(): void { this.subscription?.unsubscribe(); if (this.objectUrl) URL.revokeObjectURL(this.objectUrl); this.objectUrl = ''; }
  ngOnDestroy(): void { this.release(); }
}
