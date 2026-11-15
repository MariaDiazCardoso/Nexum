package com.nexum;

import com.nexum.repository.UsuarioRepository;
import com.nexum.repository.CodigoVerificacionRepository;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.mail.host=smtp.example", "app.mail.from=noreply@nexum.invalid"})
@Transactional
class RegistroVerificacionApiIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UsuarioRepository users;
    @Autowired CodigoVerificacionRepository codes;
    @Autowired PasswordEncoder encoder;
    @MockitoBean JavaMailSender mail;
    MockMvc mvc;
    String email;

    @BeforeEach void init() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        email=UUID.randomUUID()+"@nexum.invalid";
        when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private String body() {
        return "{\"nombre\":\"Ana\",\"apellido\":\"López\",\"email\":\""+email+"\",\"password\":\"Password123!\",\"genero\":\"FEMENINO\",\"fechaNacimiento\":\"2000-01-01\"}";
    }

    @Test void registroCorreoVerificacionYLoginPersistenSinExponerClaves() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType("application/json").content(body()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerificado").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        var usuario=users.findByEmail(email).orElseThrow();
        assertThat(encoder.matches("Password123!",usuario.getPasswordHash())).isTrue();
        mvc.perform(post("/api/auth/registro").contentType("application/json").content(body()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/verificacion/solicitar").contentType("application/json")
                .content("{\"email\":\""+email+"\"}")).andExpect(status().isAccepted());
        var captor=ArgumentCaptor.forClass(MimeMessage.class); verify(mail).send(captor.capture());
        var mensaje=captor.getValue(); mensaje.saveChanges();
        var parts=(Multipart)((Multipart)mensaje.getContent()).getBodyPart(0).getContent();
        var matcher=Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(parts.getBodyPart(0).getContent().toString());
        assertThat(matcher.find()).isTrue(); String codigo=matcher.group();
        assertThat(codes.findByUsuario_Id(usuario.getId())).anyMatch(c -> encoder.matches(codigo,c.getCodigoHash()));
        String confirmacion="{\"email\":\""+email+"\",\"codigo\":\""+codigo+"\"}";
        mvc.perform(post("/api/auth/verificacion/confirmar").contentType("application/json").content(confirmacion))
                .andExpect(status().isNoContent());
        users.flush(); codes.flush();
        assertThat(users.findByEmail(email).orElseThrow().getEmailVerificado()).isTrue();
        mvc.perform(post("/api/auth/verificacion/confirmar").contentType("application/json").content(confirmacion))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/token").contentType("application/json")
                .content("{\"email\":\""+email+"\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.emailVerificado").value(true));
    }
}
