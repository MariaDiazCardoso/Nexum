package com.nexum;

import com.nexum.entity.*;
import com.nexum.repository.*;
import com.nexum.security.UsuarioDetails;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.lesson-files-directory=./target/archivos-leccion-test")
@Transactional
class ValidacionesApiIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UsuarioRepository users;
    @Autowired CursoRepository courses;
    @Autowired ModuloRepository modules;
    @Autowired LeccionRepository lessons;
    @Autowired InscripcionRepository enrollments;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;
    Usuario student;
    Usuario admin;
    Curso course;
    Leccion lesson;
    @BeforeEach void init() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin=account(RolUsuario.ADMINISTRADOR); student=account(RolUsuario.USUARIO);
        course=courses.saveAndFlush(Curso.builder().responsable(admin).titulo("QA curso").slug(UUID.randomUUID().toString()).estado(EstadoCurso.PUBLICADO).precio(BigDecimal.ZERO).build());
        var module=modules.saveAndFlush(Modulo.builder().curso(course).titulo("QA módulo").orden(1).build());
        lesson=lessons.saveAndFlush(Leccion.builder().modulo(module).titulo("QA lección").orden(1).contenidoTexto("CONTENIDO RESERVADO").videoUrl("https://example.com/video").build());
    }
    Usuario account(RolUsuario role) {
        return users.saveAndFlush(Usuario.builder().nombre("Validación").apellido("Temporal").email(UUID.randomUUID()+"@nexum.invalid")
                .passwordHash(encoder.encode("Password123!")).rol(role).emailVerificado(true).genero(Genero.FEMENINO).fechaNacimiento(LocalDate.of(2000,1,1)).build());
    }
    @Test void catalogoPublicoNoEntregaContenido() throws Exception {
        mvc.perform(get("/api/lecciones").param("cursoId",course.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("QA lección"))
                .andExpect(jsonPath("$[0].contenidoTexto").isEmpty()).andExpect(jsonPath("$[0].videoUrl").isEmpty());
    }
    @Test void alumnoSinInscripcionNoPuedeAbrirLeccion() throws Exception {
        mvc.perform(get("/api/lecciones/"+lesson.getId()).with(user(new UsuarioDetails(student)))).andExpect(status().isForbidden());
    }
    @Test void alumnoInscritoPuedeAbrirSuCurso() throws Exception {
        enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
        mvc.perform(get("/api/lecciones/"+lesson.getId()).with(user(new UsuarioDetails(student)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenidoTexto").value("CONTENIDO RESERVADO"));
    }
    @Test void noPuedeLeerPerfilAjeno() throws Exception {
        mvc.perform(get("/api/usuarios/"+admin.getId()).with(user(new UsuarioDetails(student)))).andExpect(status().isForbidden());
    }
    @Test void noPuedeInscribirseEnCursoPagadoSinPago() throws Exception {
        course.setPrecio(BigDecimal.TEN); courses.saveAndFlush(course);
        mvc.perform(post("/api/inscripciones").with(user(new UsuarioDetails(student))).contentType("application/json")
                .content("{\"usuarioId\":\""+student.getId()+"\",\"cursoId\":\""+course.getId()+"\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void noPuedeAprobarSuPropioPago() throws Exception {
        mvc.perform(post("/api/pagos").with(user(new UsuarioDetails(student))).contentType("application/json")
                .content("{\"usuarioId\":\""+student.getId()+"\",\"cursoId\":\""+course.getId()+"\",\"monto\":10,\"estado\":\"COMPLETADO\",\"metodo\":\"TARJETA\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void rechazaPassword1234EnRegistro() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType("application/json").content("{\"nombre\":\"Ana\",\"apellido\":\"López\",\"email\":\"qa@nexum.invalid\",\"password\":\"1234\",\"genero\":\"FEMENINO\",\"fechaNacimiento\":\"2000-01-01\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void bloqueaDespuesDeCincoIntentosDeLogin() throws Exception {
        String body="{\"email\":\""+student.getEmail()+"\",\"password\":\"Incorrecta123!\"}";
        for(int i=0;i<4;i++) mvc.perform(post("/api/auth/token").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/token").contentType("application/json").content(body)).andExpect(status().isTooManyRequests());
    }

    @Test void accesoPublicoNoHabilitaLecciones() throws Exception {
        mvc.perform(get("/api/cursos/"+course.getId()+"/acceso")).andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeAcceder").value(false));
    }
    @Test void accesoDependeDeInscripcionActivaYCorreoVerificado() throws Exception {
        var inscripcion=enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
        mvc.perform(get("/api/cursos/"+course.getId()+"/acceso").with(user(new UsuarioDetails(student))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.puedeAcceder").value(true));
        inscripcion.setEstado("CANCELADA"); enrollments.saveAndFlush(inscripcion);
        mvc.perform(get("/api/cursos/"+course.getId()+"/acceso").with(user(new UsuarioDetails(student))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.puedeAcceder").value(false));
    }

    @Test void soloAdministradorPuedeCargarPortada() throws Exception {
        var file=new org.springframework.mock.web.MockMultipartFile("foto","falsa.png","image/png","invalido".getBytes());
        mvc.perform(multipart("/api/cursos/"+course.getId()+"/portada").file(file)
                .with(user(new UsuarioDetails(student)))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/cursos/"+course.getId()+"/portada").file(file)
                .with(user(new UsuarioDetails(admin)))).andExpect(status().isBadRequest());
    }
    @Test void portadaSePersisteYSeIncluyeEnCatalogo() throws Exception {
        String url="/api/cursos/"+course.getId()+"/portada/"+UUID.randomUUID()+".jpg";
        course.setPortadaUrl(url); courses.saveAndFlush(course);
        mvc.perform(get("/api/cursos/"+course.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.portadaUrl").value(url));
    }

    @Test void materialesPdfRequierenInscripcionYPermisoDeAdministrador() throws Exception {
        var file=new org.springframework.mock.web.MockMultipartFile("archivo","Guia.pdf","application/pdf","%PDF-1.7\ncontenido".getBytes());
        mvc.perform(multipart("/api/lecciones/"+lesson.getId()+"/materiales").file(file).with(user(new UsuarioDetails(student))))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/lecciones/"+lesson.getId()+"/materiales").file(file).with(user(new UsuarioDetails(admin))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.urlArchivo").isNotEmpty());
        var repo=context.getBean(RecursoAdjuntoRepository.class);
        var recurso=repo.findByLeccion_Id(lesson.getId()).getFirst();
        String url=recurso.getUrlArchivo();
        java.nio.file.Path path=java.nio.file.Path.of("target/archivos-leccion-test",url.substring(url.lastIndexOf('/')+1));
        try {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).with(user(new UsuarioDetails(student)))).andExpect(status().isForbidden());
            enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
            mvc.perform(get(url).with(user(new UsuarioDetails(student)))).andExpect(status().isOk())
                    .andExpect(header().string("Content-Type","application/pdf"));
            mvc.perform(delete("/api/recursos-adjuntos/"+recurso.getId()).with(user(new UsuarioDetails(admin))))
                    .andExpect(status().isNoContent());
            mvc.perform(get(url).with(user(new UsuarioDetails(student)))).andExpect(status().isNotFound());
        } finally { java.nio.file.Files.deleteIfExists(path); }
    }

    Examen prepararEvaluacion(boolean publicar) {
        var repo=context.getBean(ExamenRepository.class);
        var e=repo.saveAndFlush(Examen.builder().leccion(lesson).titulo("Evaluación temporal").intentosMaximos(2).puntajeMinimoAprobacion(new BigDecimal("70")).build());
        var q=Pregunta.builder().examen(e).textoPregunta("¿Cuál es correcta?").explicacionRespuesta("Una explicación reservada").build();
        q.agregarOpcion(OpcionRespuesta.builder().textoOpcion("Correcta").esCorrecta(true).build());
        q.agregarOpcion(OpcionRespuesta.builder().textoOpcion("Incorrecta").esCorrecta(false).build());
        context.getBean(PreguntaRepository.class).saveAndFlush(q);
        if(publicar){e.setPublicado(true);repo.saveAndFlush(e);}
        return e;
    }
    IntentoExamen iniciarEvaluacion(Examen e) throws Exception {
        mvc.perform(post("/api/evaluaciones/"+e.getId()+"/intentos").with(user(new UsuarioDetails(student))))
                .andExpect(status().isOk());
        return context.getBean(IntentoExamenRepository.class).findByExamen_IdAndUsuario_IdOrderByNumeroIntentoAsc(e.getId(),student.getId()).getLast();
    }
    String respuestaEvaluacion(Examen e,boolean correcta) {
        var q=context.getBean(PreguntaRepository.class).findByExamen_Id(e.getId()).getFirst();
        var o=q.getOpciones().stream().filter(a -> a.getEsCorrecta()==correcta).findFirst().orElseThrow();
        return "{\"respuestas\":[{\"preguntaId\":\""+q.getId()+"\",\"opcionId\":\""+o.getId()+"\"}]}";
    }
    @Test void evaluacionSeCalificaYHabilitaProgresoYCertificado() throws Exception {
        var e=prepararEvaluacion(true);
        var inscripcion=enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
        mvc.perform(get("/api/evaluaciones/lecciones/"+lesson.getId()).with(user(new UsuarioDetails(student))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preguntas[0].opciones[0].esCorrecta").isEmpty())
                .andExpect(jsonPath("$.preguntas[0].explicacionRespuesta").isEmpty());
        String progreso="{\"usuarioId\":\""+student.getId()+"\",\"leccionId\":\""+lesson.getId()+"\",\"completado\":true}";
        mvc.perform(post("/api/progresos-leccion").with(user(new UsuarioDetails(student))).contentType("application/json").content(progreso))
                .andExpect(status().isBadRequest());
        var intento=iniciarEvaluacion(e);
        mvc.perform(post("/api/evaluaciones/intentos/"+intento.getId()+"/respuestas").with(user(new UsuarioDetails(student)))
                .contentType("application/json").content(respuestaEvaluacion(e,true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intento.puntaje").value(100)).andExpect(jsonPath("$.intento.aprobado").value(true));
        mvc.perform(post("/api/evaluaciones/intentos/"+intento.getId()+"/respuestas").with(user(new UsuarioDetails(student)))
                .contentType("application/json").content(respuestaEvaluacion(e,true))).andExpect(status().isConflict());
        mvc.perform(post("/api/progresos-leccion").with(user(new UsuarioDetails(student))).contentType("application/json").content(progreso))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/certificados").with(user(new UsuarioDetails(admin))).contentType("application/json")
                .content("{\"inscripcionId\":\""+inscripcion.getId()+"\"}")).andExpect(status().isConflict());
    }
    @Test void maximoDeIntentosYNoSeAceptaPuntajeDelCliente() throws Exception {
        var e=prepararEvaluacion(true); enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
        for(int n=0;n<2;n++) {
            var intento=iniciarEvaluacion(e);
            String falso=respuestaEvaluacion(e,false).replace("{\"respuestas\":","{\"puntaje\":100,\"aprobado\":true,\"respuestas\":");
            mvc.perform(post("/api/evaluaciones/intentos/"+intento.getId()+"/respuestas").with(user(new UsuarioDetails(student)))
                    .contentType("application/json").content(falso)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.intento.puntaje").value(0)).andExpect(jsonPath("$.intento.aprobado").value(false));
        }
        mvc.perform(post("/api/evaluaciones/"+e.getId()+"/intentos").with(user(new UsuarioDetails(student))))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/evaluaciones/"+e.getId()+"/intentos-maximos").with(user(new UsuarioDetails(admin)))
                .contentType("application/json").content("{\"intentosMaximos\":3}")).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(iniciarEvaluacion(e).getNumeroIntento()).isEqualTo(3);
    }
    @Test void retomaIntentoAbiertoYRechazaOpcionesAjenas() throws Exception {
        var e=prepararEvaluacion(true); enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
        var intento=iniciarEvaluacion(e);
        org.assertj.core.api.Assertions.assertThat(iniciarEvaluacion(e).getId()).isEqualTo(intento.getId());
        var q=context.getBean(PreguntaRepository.class).findByExamen_Id(e.getId()).getFirst();
        mvc.perform(post("/api/evaluaciones/intentos/"+intento.getId()+"/respuestas").with(user(new UsuarioDetails(student)))
                .contentType("application/json").content("{\"respuestas\":[{\"preguntaId\":\""+q.getId()+"\",\"opcionId\":\""+UUID.randomUUID()+"\"}]}"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(intento.getFinalizadoEn()).isNull();
    }
    @Test void noPuedeContestarEvaluacionesAjenasNiEditarConIntentos() throws Exception {
        var e=prepararEvaluacion(true); enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
        var intento=iniciarEvaluacion(e);
        mvc.perform(patch("/api/evaluaciones/"+e.getId()+"/publicacion").with(user(new UsuarioDetails(student)))
                .contentType("application/json").content("{\"publicado\":false}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/evaluaciones/"+e.getId()+"/publicacion").with(user(new UsuarioDetails(admin)))
                .contentType("application/json").content("{\"publicado\":false}")).andExpect(status().isConflict());
        var otro=account(RolUsuario.USUARIO);
        mvc.perform(post("/api/evaluaciones/intentos/"+intento.getId()+"/respuestas").with(user(new UsuarioDetails(otro)))
                .contentType("application/json").content(respuestaEvaluacion(e,true))).andExpect(status().isForbidden());
    }
    @Test void borradoresNoSonObligatoriosNiVisiblesParaAlumno() throws Exception {
        prepararEvaluacion(false); enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
        mvc.perform(get("/api/evaluaciones/lecciones/"+lesson.getId()).with(user(new UsuarioDetails(student))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.examen").isEmpty());
        mvc.perform(post("/api/progresos-leccion").with(user(new UsuarioDetails(student))).contentType("application/json")
                .content("{\"usuarioId\":\""+student.getId()+"\",\"leccionId\":\""+lesson.getId()+"\",\"completado\":true}"))
                .andExpect(status().isCreated());
    }

    @Test void bdRechazaCalificacionInventadaYProgresoSinAprobacion() throws Exception {
        var e=prepararEvaluacion(true); enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
        var intento=iniciarEvaluacion(e);
        var ds=context.getBean(javax.sql.DataSource.class);
        var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(ds);
        var savepoint=connection.setSavepoint();
        try(var statement=connection.prepareStatement("UPDATE intento_examen SET puntaje=100, aprobado=true, finalizado_en=now() WHERE id=?")) {
            statement.setObject(1,intento.getId());
            org.assertj.core.api.Assertions.assertThatThrownBy(statement::executeUpdate).isInstanceOfSatisfying(java.sql.SQLException.class, ex -> org.assertj.core.api.Assertions.assertThat(ex.getSQLState()).isEqualTo("23514"));
        } finally { connection.rollback(savepoint); connection.releaseSavepoint(savepoint); }
        savepoint=connection.setSavepoint();
        try(var statement=connection.prepareStatement("INSERT INTO progreso_leccion(id,usuario_id,leccion_id,completado) VALUES(gen_random_uuid(),?,?,true)")) {
            statement.setObject(1,student.getId()); statement.setObject(2,lesson.getId());
            org.assertj.core.api.Assertions.assertThatThrownBy(statement::executeUpdate).isInstanceOfSatisfying(java.sql.SQLException.class, ex -> org.assertj.core.api.Assertions.assertThat(ex.getSQLState()).isEqualTo("23514"));
        } finally { connection.rollback(savepoint); connection.releaseSavepoint(savepoint); }
    }

    @Test void certificadoAutomaticoEsPrivadoPeroCodigoSePuedeVerificar() throws Exception {
        var i=enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
        mvc.perform(post("/api/progresos-leccion").with(user(new UsuarioDetails(student))).contentType("application/json")
                .content("{\"usuarioId\":\""+student.getId()+"\",\"leccionId\":\""+lesson.getId()+"\",\"completado\":true}"))
                .andExpect(status().isCreated());
        var repo=context.getBean(CertificadoRepository.class);var c=repo.findByInscripcion_Id(i.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(c.getNombreAlumno()).isEqualTo(student.getNombre()+" "+student.getApellido());
        mvc.perform(post("/api/certificados/emitir-pendientes").with(user(new UsuarioDetails(student)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/certificados/emitir-pendientes").with(user(new UsuarioDetails(student)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/certificados/"+c.getId()+"/pdf")).andExpect(status().isUnauthorized());
        var otro=account(RolUsuario.USUARIO);
        mvc.perform(get("/api/certificados/"+c.getId()+"/pdf").with(user(new UsuarioDetails(otro)))).andExpect(status().isForbidden());
        var result=mvc.perform(get("/api/certificados/"+c.getId()+"/pdf").with(user(new UsuarioDetails(student))))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type","application/pdf")).andReturn();
        org.assertj.core.api.Assertions.assertThat(new String(result.getResponse().getContentAsByteArray(),java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
        mvc.perform(get("/api/certificados/verificar/"+c.getCodigoVerificacion())).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreAlumno").value(c.getNombreAlumno())).andExpect(jsonPath("$.curso").value(course.getTitulo()))
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.usuarioId").doesNotExist());
        String snapshot=c.getNombreAlumno();student.setNombre("Nombre actualizado");users.saveAndFlush(student);
        mvc.perform(get("/api/certificados/verificar/"+c.getCodigoVerificacion())).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreAlumno").value(snapshot));
    }
    @Test void noEmiteCertificadosSinCompletarYCodigoInexistenteEs404() throws Exception {
        enrollments.saveAndFlush(Inscripcion.builder().usuario(student).curso(course).build());
        mvc.perform(post("/api/certificados/emitir-pendientes").with(user(new UsuarioDetails(student))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/certificados/verificar/"+java.util.UUID.randomUUID().toString().replace("-","")))
                .andExpect(status().isNotFound());
    }
}
