package com.nexum;
import com.nexum.entity.*;
import com.nexum.repository.*;
import com.nexum.security.UsuarioDetails;
import com.nexum.service.JwtService;
import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.mail.host=smtp.example", "app.mail.from=noreply@nexum.invalid"})
@Transactional
class RecuperacionPasswordApiIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UsuarioRepository users;
    @Autowired CodigoVerificacionRepository codes;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @MockitoBean JavaMailSender mail;
    MockMvc mvc;
    Usuario usuario;
    @BeforeEach void init() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        usuario=users.saveAndFlush(Usuario.builder().nombre("Recuperación").apellido("Temporal")
                .email(UUID.randomUUID()+"@nexum.invalid").passwordHash(encoder.encode("AnteriorClave1!"))
                .rol(RolUsuario.USUARIO).genero(Genero.FEMENINO).fechaNacimiento(java.time.LocalDate.of(2000,1,1)).build());
        when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }
    @Test void recuperaPorHttpPersisteHashYRechazaReutilizacion() throws Exception {
        String anterior=jwt.generarToken(new UsuarioDetails(usuario));
        mvc.perform(post("/api/auth/password/solicitar").contentType("application/json").content("{\"email\":\""+usuario.getEmail()+"\"}"))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        var captor=ArgumentCaptor.forClass(MimeMessage.class); verify(mail).send(captor.capture());
        var msg=captor.getValue(); msg.saveChanges();
        var parts=(Multipart)((Multipart)msg.getContent()).getBodyPart(0).getContent();
        var matcher=java.util.regex.Pattern.compile("#token=([A-Za-z0-9_.-]+)").matcher(parts.getBodyPart(0).getContent().toString());
        assertThat(matcher.find()).isTrue(); String token=matcher.group(1);
        String body="{\"token\":\""+token+"\",\"password\":\"NuevaClave1!\"}";
        mvc.perform(post("/api/auth/password/restablecer").contentType("application/json").content(body)).andExpect(status().isNoContent());
        users.flush(); codes.flush();
        assertThat(encoder.matches("NuevaClave1!",users.findById(usuario.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(jwt.esValido(anterior,new UsuarioDetails(usuario))).isFalse();
        assertThat(codes.findByUsuario_Id(usuario.getId())).allMatch(c -> c.getUsado());
        mvc.perform(post("/api/auth/password/restablecer").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/token").contentType("application/json").content("{\"email\":\""+usuario.getEmail()+"\",\"password\":\"NuevaClave1!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/auth/token").contentType("application/json").content("{\"email\":\""+usuario.getEmail()+"\",\"password\":\"AnteriorClave1!\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void passwordDebilNoConsumeEnlace() throws Exception {
        mvc.perform(post("/api/auth/password/restablecer").contentType("application/json")
                .content("{\"token\":\"a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a90."+"a".repeat(43)+"\",\"password\":\"1234\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mail);
    }
}
