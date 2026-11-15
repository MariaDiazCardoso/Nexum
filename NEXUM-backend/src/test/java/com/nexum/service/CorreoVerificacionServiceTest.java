package com.nexum.service;
import com.nexum.entity.*;
import com.nexum.repository.*;
import com.nexum.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import jakarta.mail.Multipart;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class CorreoVerificacionServiceTest {
    UsuarioRepository users=mock(UsuarioRepository.class);
    CodigoVerificacionRepository codes=mock(CodigoVerificacionRepository.class);
    JavaMailSender mail=mock(JavaMailSender.class);
    @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
    BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    Usuario usuario=Usuario.builder().id(UUID.randomUUID()).email("ana@nexum.invalid").emailVerificado(false).build();
    CorreoVerificacionService servicio;
    @BeforeEach void init() {
        lenient().when(mail.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        servicio=new CorreoVerificacionService(users,codes,encoder,new LoginAttemptService(),provider,"smtp.example","noreply@example.com");
        lenient().when(provider.getIfAvailable()).thenReturn(mail); lenient().when(provider.getObject()).thenReturn(mail);
        lenient().when(users.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        lenient().when(users.bloquearPorId(usuario.getId())).thenReturn(Optional.of(usuario));
    }
    @Test void enviaCodigoSoloPorCorreoYGuardaHash() throws Exception {
        when(codes.findByUsuario_Id(usuario.getId())).thenReturn(List.of());
        servicio.solicitar("ANA@nexum.invalid","127.0.0.1");
        var mensaje=ArgumentCaptor.forClass(MimeMessage.class); verify(mail).send(mensaje.capture()); mensaje.getValue().saveChanges();
        var registro=ArgumentCaptor.forClass(CodigoVerificacion.class); verify(codes).save(registro.capture());
        var matcher=java.util.regex.Pattern.compile("[0-9]{6}").matcher(((Multipart)((Multipart)mensaje.getValue().getContent()).getBodyPart(0).getContent()).getBodyPart(0).getContent().toString()); assertThat(matcher.find()).isTrue();
        assertThat(encoder.matches(matcher.group(),registro.getValue().getCodigoHash())).isTrue();
        var alternatives = (Multipart)((Multipart)mensaje.getValue().getContent()).getBodyPart(0).getContent();
        String html = alternatives.getBodyPart(1).getContent().toString();
        assertThat(html).contains(matcher.group(), "NEXUM", "15 minutos", "#14213a").doesNotContain("{{CODIGO}}");
        assertThat(alternatives.getBodyPart(1).getContentType()).containsIgnoringCase("text/html");
        assertThat(usuario.getEmailVerificado()).isFalse();
    }
    @Test void codigoValidoVerificaUnaSolaVez() {
        var c=CodigoVerificacion.builder().usuario(usuario).tipo("EMAIL").usado(false).codigoHash(encoder.encode("123456")).expiraEn(LocalDateTime.now().plusMinutes(15)).build();
        when(codes.findFirstByUsuario_IdAndTipoAndUsadoFalseOrderByFechaCreacionDesc(usuario.getId(),"EMAIL")).thenReturn(Optional.of(c));
        servicio.confirmar(usuario.getEmail(),"123456"); assertThat(c.getUsado()).isTrue(); assertThat(usuario.getEmailVerificado()).isTrue();
        assertThatThrownBy(()->servicio.confirmar(usuario.getEmail(),"123456")).isInstanceOf(SolicitudInvalidaException.class);
    }
    @Test void codigoVencidoNoVerifica() {
        var c=CodigoVerificacion.builder().usuario(usuario).tipo("EMAIL").usado(false).codigoHash(encoder.encode("123456")).expiraEn(LocalDateTime.now().minusSeconds(1)).build();
        when(codes.findFirstByUsuario_IdAndTipoAndUsadoFalseOrderByFechaCreacionDesc(usuario.getId(),"EMAIL")).thenReturn(Optional.of(c));
        assertThatThrownBy(()->servicio.confirmar(usuario.getEmail(),"123456")).isInstanceOf(SolicitudInvalidaException.class);
        assertThat(usuario.getEmailVerificado()).isFalse(); verify(users,never()).save(any());
    }
    @Test void sinSmtpNoSimulaUnEnvio() {
        servicio=new CorreoVerificacionService(users,codes,encoder,new LoginAttemptService(),provider,"","");
        assertThatThrownBy(()->servicio.solicitar(usuario.getEmail(),"127.0.0.1")).isInstanceOf(CorreoNoDisponibleException.class);
        verify(mail,never()).send(any(MimeMessage.class)); verify(codes,never()).save(any());
    }
}
