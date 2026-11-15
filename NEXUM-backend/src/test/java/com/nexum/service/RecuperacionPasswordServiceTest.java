package com.nexum.service;
import com.nexum.entity.*;
import com.nexum.repository.*;
import com.nexum.exception.*;
import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class RecuperacionPasswordServiceTest {
    UsuarioRepository usuarios=mock(UsuarioRepository.class);
    CodigoVerificacionRepository codigos=mock(CodigoVerificacionRepository.class);
    JavaMailSender mail=mock(JavaMailSender.class);
    @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
    BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    Usuario usuario=Usuario.builder().id(UUID.randomUUID()).email("ana@nexum.invalid").passwordHash(encoder.encode("Anterior1!")).emailVerificado(false).build();
    RecuperacionPasswordService service;
    List<CodigoVerificacion> registros=new ArrayList<>();
    @BeforeEach void init() {
        service=new RecuperacionPasswordService(usuarios,codigos,encoder,new LoginAttemptService(),provider,"smtp.example","noreply@nexum.invalid","http://127.0.0.1:4306");
        lenient().when(provider.getIfAvailable()).thenReturn(mail);
        lenient().when(provider.getObject()).thenReturn(mail);
        lenient().when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        lenient().when(usuarios.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        lenient().when(usuarios.bloquearPorId(usuario.getId())).thenReturn(Optional.of(usuario));
        lenient().when(codigos.findByUsuario_Id(usuario.getId())).thenReturn(registros);
        lenient().when(codigos.save(any())).thenAnswer(i -> {
            CodigoVerificacion c=i.getArgument(0);
            if(c.getId()==null) { c.setId(UUID.randomUUID()); registros.add(c); }
            return c;
        });
        lenient().when(codigos.propietario(any())).thenAnswer(i -> registros.stream().filter(c -> c.getId().equals(i.getArgument(0))).map(c -> c.getUsuario().getId()).findFirst());
        lenient().when(codigos.bloquearPorId(any())).thenAnswer(i -> registros.stream().filter(c -> c.getId().equals(i.getArgument(0))).findFirst());
    }
    String solicitar() throws Exception {
        service.solicitar(usuario.getEmail(),"127.0.0.1");
        var captor=ArgumentCaptor.forClass(MimeMessage.class); verify(mail,atLeastOnce()).send(captor.capture());
        var mensaje=captor.getValue(); mensaje.saveChanges();
        var alternatives=(Multipart)((Multipart)mensaje.getContent()).getBodyPart(0).getContent();
        String texto=alternatives.getBodyPart(0).getContent().toString();
        String html=alternatives.getBodyPart(1).getContent().toString();
        assertThat(html).contains("Crear nueva contraseña", "15 minutos").doesNotContain("{{ENLACE}}");
        var matcher=java.util.regex.Pattern.compile("#token=([A-Za-z0-9_.-]+)").matcher(texto);
        assertThat(matcher.find()).isTrue(); return matcher.group(1);
    }
    @Test void enlaceCambiaPasswordUnaSolaVezSinVerificarEmail() throws Exception {
        String token=solicitar();
        assertThat(registros.getFirst().getCodigoHash()).startsWith("$2").doesNotContain(token);
        service.restablecer(token,"NuevaClave1!","127.0.0.1");
        assertThat(encoder.matches("NuevaClave1!",usuario.getPasswordHash())).isTrue();
        assertThat(usuario.getEmailVerificado()).isFalse();
        assertThatThrownBy(() -> service.restablecer(token,"OtraClave1!","127.0.0.1")).isInstanceOf(SolicitudInvalidaException.class);
    }
    @Test void rechazaEnlaceVencido() throws Exception {
        String token=solicitar(); registros.getFirst().setExpiraEn(java.time.LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.restablecer(token,"NuevaClave1!","127.0.0.1")).isInstanceOf(SolicitudInvalidaException.class);
        assertThat(encoder.matches("Anterior1!",usuario.getPasswordHash())).isTrue();
    }
    @Test void otroEnvioInvalidaElAnterior() throws Exception {
        String anterior=solicitar(); String nuevo=solicitar();
        assertThatThrownBy(() -> service.restablecer(anterior,"NuevaClave1!","127.0.0.1")).isInstanceOf(SolicitudInvalidaException.class);
        service.restablecer(nuevo,"NuevaClave1!","127.0.0.1");
    }
    @Test void cambiarCorreoInvalidaElEnlace() throws Exception {
        String token=solicitar(); usuario.setEmail("otro@nexum.invalid");
        assertThatThrownBy(() -> service.restablecer(token,"NuevaClave1!","127.0.0.1")).isInstanceOf(SolicitudInvalidaException.class);
    }
    @Test void noEnviaParaCuentasInexistentes() {
        service.solicitar("nadie@nexum.invalid","127.0.0.1");
        verify(mail,never()).send(any(MimeMessage.class)); verify(codigos,never()).save(any());
    }
    @Test void rechazoDeCorreoSeReportaSinExponerCredenciales() {
        doThrow(new MailSendException("detalle privado")).when(mail).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service.solicitar(usuario.getEmail(),"127.0.0.1")).isInstanceOf(CorreoNoDisponibleException.class).hasMessageNotContaining("detalle privado");
    }
    @Test void cuentaEliminadaNoPuedeRecuperar() throws Exception {
        String token=solicitar(); usuario.setDeletedAt(java.time.LocalDateTime.now());
        assertThatThrownBy(() -> service.restablecer(token,"NuevaClave1!","127.0.0.1")).isInstanceOf(SolicitudInvalidaException.class);
    }
    @Test void tokenDeOtroTipoNoPuedeCambiarPassword() throws Exception {
        String token=solicitar(); registros.getFirst().setTipo("EMAIL");
        assertThatThrownBy(() -> service.restablecer(token,"NuevaClave1!","127.0.0.1")).isInstanceOf(SolicitudInvalidaException.class);
    }
}
