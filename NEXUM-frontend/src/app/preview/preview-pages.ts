import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
 selector: 'app-preview-pages', imports: [FormsModule, RouterLink],
 templateUrl: './preview-pages.html', styleUrl: './preview-pages.scss'
})
export class PreviewPages {
 readonly page = inject(ActivatedRoute).snapshot.data['page'] as string;
 readonly notice = signal('');
 readonly name = signal('Alex');
 readonly avatar = signal('AD');
 readonly lesson = signal(0);
 readonly finished = signal(false);
 readonly submitted = signal(false);
 readonly answers = signal<number[]>([-1,-1,-1]);
 readonly questions = [
  {text:'¿Qué hace un componente en Angular?',options:['Organiza una parte de la interfaz','Almacena toda la base de datos','Reemplaza el navegador'],correct:0},
  {text:'¿Para qué sirve el enrutamiento?',options:['Cambiar los colores del logo','Navegar entre vistas de la aplicación','Instalar PostgreSQL'],correct:1},
  {text:'¿Qué facilita un formulario?',options:['Crear un servidor','Modificar el sistema operativo','Recoger y validar información'],correct:2}
 ];
 readonly lessons = ['Tu primer componente','Navegación entre páginas','Formularios que ayudan'];
 email = ''; code = ''; password = ''; confirmation = ''; biography = 'Aprendiendo a crear mis propias ideas.';
 get score(): number { return this.answers().filter((a,i)=>a===this.questions[i].correct).length; }
 get complete(): boolean { return this.answers().every(a=>a>=0); }
 selectAnswer(index:number, answer:number):void { if(this.submitted())return; const values=[...this.answers()];values[index]=answer;this.answers.set(values); }
 saveProfile():void { this.notice.set('Cambios aplicados en esta vista. No se guardan en una cuenta real.'); }
 changeAvatar(event:Event):void { const input=event.target as HTMLInputElement; const file=input.files?.[0]; if(!file)return; if(!['image/png','image/jpeg','image/webp'].includes(file.type)||file.size>2*1024*1024){this.notice.set('Elige una imagen PNG, JPG o WebP de hasta 2 MB.');return;} const reader=new FileReader();reader.onload=()=>this.avatar.set(String(reader.result));reader.readAsDataURL(file); }
 requestReset():void { this.notice.set('Solicitud de ejemplo preparada. Esta vista previa no envía correos.'); }
 resetPassword():void { this.notice.set(this.password!==this.confirmation?'Las contraseñas deben coincidir.':'Demostración completada. No se ha cambiado ninguna contraseña real.'); }
 verify():void { this.notice.set(/^\d{6}$/.test(this.code)?'Código aceptado en la demostración. No verifica una cuenta real.':'Introduce un código de seis números.'); }
 nextLesson():void { if(this.lesson()<2)this.lesson.update(n=>n+1);else this.finished.set(true); }
}
