package com.nexum.service;
import com.nexum.dto.*;
import com.nexum.entity.*;
import com.nexum.repository.*;
import com.nexum.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.*;
import java.util.*;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class InscripcionValidacionesTest {
    InscripcionRepository ins=mock(InscripcionRepository.class);
    UsuarioRepository users=mock(UsuarioRepository.class);
    CursoRepository courses=mock(CursoRepository.class);
    PagoRepository pagos=mock(PagoRepository.class);
    AccesoService acceso=mock(AccesoService.class);
    InscripcionServiceImpl service=new InscripcionServiceImpl(ins,users,courses,acceso,pagos);
    Usuario user=Usuario.builder().id(UUID.randomUUID()).emailVerificado(true).build();
    Curso course=Curso.builder().id(UUID.randomUUID()).precio(BigDecimal.TEN).moneda("PEN").estado(EstadoCurso.PUBLICADO).build();
    @BeforeEach void init() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    }
    @Test void cursoPagadoSinPagoSeRechaza() {
        assertThatThrownBy(()->service.crear(new InscripcionRequest(user.getId(),course.getId(),null,null))).isInstanceOf(SolicitudInvalidaException.class);
        verify(ins,never()).save(any());
    }
    @Test void noPermiteDeclararseCompletadoAlInscribirse() {
        course.setPrecio(BigDecimal.ZERO);
        assertThatThrownBy(()->service.crear(new InscripcionRequest(user.getId(),course.getId(),"COMPLETADA",null))).isInstanceOf(SolicitudInvalidaException.class);
    }
    @Test void requiereCorreoVerificado() {
        user.setEmailVerificado(false);
        assertThatThrownBy(()->service.crear(new InscripcionRequest(user.getId(),course.getId(),null,null))).isInstanceOf(SolicitudInvalidaException.class);
    }
    @Test void aceptaPagoAprobadoDelMismoUsuarioYCurso() {
        UUID pid=UUID.randomUUID(); Pago p=Pago.builder().id(pid).usuario(user).curso(course).monto(BigDecimal.TEN).moneda("PEN").estado("COMPLETADO").build();
        when(pagos.findById(pid)).thenReturn(Optional.of(p)); when(ins.save(any())).thenAnswer(i->{ Inscripcion v=i.getArgument(0); v.setId(UUID.randomUUID()); return v; });
        var response=service.crear(new InscripcionRequest(user.getId(),course.getId(),null,pid));
        assertThat(response.estado()).isEqualTo("ACTIVA");
        verify(ins).save(argThat(i->i.getPago()==p && i.getProgresoPorcentaje().signum()==0));
    }
}
