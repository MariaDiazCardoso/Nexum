import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { CheckoutPage } from './checkout.page';
import { API_BASE_URL } from '../../core/api.config';
describe('Checkout de prueba',()=>{
 let page:CheckoutPage;let http:HttpTestingController;
 beforeEach(()=>{TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting(),{provide:ActivatedRoute,useValue:{snapshot:{queryParamMap:new Map([['tipo','ia'],['id','plan-1']])}}}]});http=TestBed.inject(HttpTestingController);page=TestBed.runInInjectionContext(()=>new CheckoutPage());});
 afterEach(()=>http.verify());
 it('consulta planes existentes y completa la demostración sin solicitar pagos',()=>{
  page.load();const req=http.expectOne(API_BASE_URL+'/planes-ia');
  req.flush([{id:'plan-1',nombre:'Plan Pro',precio:59.9,moneda:'PEN',periodo:'mensual'}]);
  page.method='TARJETA'; page.holder='Ana Diaz'; page.cardNumber='4242 4242 4242 4242'; page.expiry='12/'+String(new Date().getFullYear()+2).slice(-2); page.cvv='123'; page.pay();http.expectNone(API_BASE_URL+'/checkout/prueba');expect(page.complete()).toBe(true);

 });
 it('no paga sin un producto o método',()=>{page.pay();http.expectNone(API_BASE_URL+'/checkout/prueba');expect(page.complete()).toBe(false);});
 it('bloquea tarjetas inválidas y fechas vencidas',()=>{
   page.method='TARJETA';page.holder='Ana Diaz';page.cardNumber='4242424242424241';page.expiry='01/20';page.cvv='123';page.step.set(2);page.next();
   expect(page.cardValid()).toBe(false);expect(page.expiryValid()).toBe(false);expect(page.step()).toBe(2);
 });
 it('oculta los datos completos al terminar y no los conserva al salir',()=>{
   page.holder='Ana Diaz';page.cardNumber='4242424242424242';page.cvv='123';page.ngOnDestroy();expect(page.cardNumber).toBe('');expect(page.cvv).toBe('');expect(page.holder).toBe('');
 });
 it('pide cuatro dígitos de seguridad para American Express',()=>{
   page.cardNumber='378282246310005';page.cvv='123';expect(page.cardValid()).toBe(true);expect(page.cvvValid()).toBe(false);page.cvv='1234';expect(page.cvvValid()).toBe(true);
 });
});
