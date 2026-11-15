package com.nexum.service;
import com.nexum.entity.*;
import com.nexum.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AccesoCursoServiceTest {
    InscripcionRepository ins=mock(InscripcionRepository.class);
    UsuarioRepository users=mock(UsuarioRepository.class);
    AccesoService acceso=mock(AccesoService.class);
    AccesoCursoService service=new AccesoCursoService(ins,users,mock(LeccionRepository.class),acceso);
    UUID id=UUID.randomUUID();
    Usuario user=Usuario.builder().id(id).emailVerificado(true).build();
    Curso course=Curso.builder().id(UUID.randomUUID()).precio(BigDecimal.ZERO).estado(EstadoCurso.PUBLICADO).build();
    Inscripcion enrollment;
    @BeforeEach void init() {
        enrollment=Inscripcion.builder().usuario(user).curso(course).estado("ACTIVA").build();
        lenient().when(acceso.obtenerIdUsuarioAutenticado()).thenReturn(id);
        lenient().when(users.findById(id)).thenReturn(Optional.of(user));
        lenient().when(ins.findByUsuario_IdAndCurso_Id(id,course.getId())).thenReturn(List.of(enrollment));
    }
    @Test void inscritoEnCursoGratuitoPuedeLeer() { assertThat(service.puedeLeer(course)).isTrue(); }
    @Test void sinInscripcionNoPuedeLeer() { when(ins.findByUsuario_IdAndCurso_Id(id,course.getId())).thenReturn(List.of()); assertThat(service.puedeLeer(course)).isFalse(); }
    @Test void correoSinVerificarNoPuedeLeer() { user.setEmailVerificado(false); assertThat(service.puedeLeer(course)).isFalse(); }
    @Test void inscripcionCanceladaNoPuedeLeer() { enrollment.setEstado("CANCELADA"); assertThat(service.puedeLeer(course)).isFalse(); }
    @Test void cursoPagadoSinPagoNoPuedeLeer() { course.setPrecio(BigDecimal.TEN); assertThat(service.puedeLeer(course)).isFalse(); }
    @Test void pagoDebeEstarAprobadoYPertenecerAlCursoYUsuario() {
        course.setPrecio(BigDecimal.TEN);
        Pago p=Pago.builder().usuario(user).curso(course).estado("PENDIENTE").build(); enrollment.setPago(p);
        assertThat(service.puedeLeer(course)).isFalse(); p.setEstado("COMPLETADO"); assertThat(service.puedeLeer(course)).isTrue();
        p.setUsuario(Usuario.builder().id(UUID.randomUUID()).build()); assertThat(service.puedeLeer(course)).isFalse();
        p.setUsuario(user); p.setCurso(Curso.builder().id(UUID.randomUUID()).build()); assertThat(service.puedeLeer(course)).isFalse();
    }
    @Test void administradorPuedeGestionarContenido() { when(acceso.esAdministrador()).thenReturn(true); assertThat(service.puedeLeer(course)).isTrue(); }
    @Test void cursoRetiradoNoPuedeLeerseComoAlumno() { course.setDeletedAt(java.time.LocalDateTime.now()); assertThat(service.puedeLeer(course)).isFalse(); }
}
