package com.nexum.service;

import com.nexum.entity.CodigoVerificacion;
import com.nexum.repository.*;
import com.nexum.exception.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.net.URI;

@Service
public class RecuperacionPasswordService {
    private static final String TIPO = "PASSWORD_RESET";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UsuarioRepository usuarios;
    private final CodigoVerificacionRepository codigos;
    private final PasswordEncoder encoder;
    private final LoginAttemptService limites;
    private final ObjectProvider<JavaMailSender> sender;
    private final String host, from, frontend;
    public RecuperacionPasswordService(UsuarioRepository usuarios, CodigoVerificacionRepository codigos,
            PasswordEncoder encoder, LoginAttemptService limites, ObjectProvider<JavaMailSender> sender,
            @Value("${spring.mail.host:}") String host, @Value("${app.mail.from:}") String from,
            @Value("${app.frontend-url:http://127.0.0.1:4306}") String frontend) {
        this.usuarios=usuarios; this.codigos=codigos; this.encoder=encoder; this.limites=limites;
        this.sender=sender; this.host=host; this.from=from;
        URI uri=URI.create(frontend);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost()==null
                || uri.getRawQuery()!=null || uri.getRawFragment()!=null || uri.getUserInfo()!=null)
            throw new IllegalArgumentException("app.frontend-url debe ser una URL HTTP o HTTPS sin credenciales, query ni fragmento");
        this.frontend=frontend.replaceAll("/+$", "");
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Transactional
    public void solicitar(String email, String ip) {
        String normalizado=email.trim().toLowerCase(Locale.ROOT);
        limites.verificar("recuperacion:"+normalizado, ip);
        if (host.isBlank() || from.isBlank() || sender.getIfAvailable()==null) throw new CorreoNoDisponibleException();
        limites.fallo("recuperacion:"+normalizado, ip);
        var usuario=usuarios.findByEmail(normalizado).orElse(null);
        if (usuario==null || usuario.getDeletedAt()!=null) return;
        usuario=usuarios.bloquearPorId(usuario.getId()).orElseThrow();
        if (!normalizado.equals(usuario.getEmail()) || usuario.getDeletedAt()!=null) return;
        for (var anterior:codigos.findByUsuario_Id(usuario.getId())) {
            if (TIPO.equals(anterior.getTipo()) && !Boolean.TRUE.equals(anterior.getUsado())) anterior.setUsado(true);
        }
        byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
        String secreto=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var registro=codigos.save(CodigoVerificacion.builder().usuario(usuario).tipo(TIPO)
                .codigoHash(encoder.encode(digest(secreto+"|"+normalizado)))
                .usado(false).expiraEn(LocalDateTime.now().plusMinutes(15)).build());
        String enlace=frontend+"/reset-password#token="+registro.getId()+"."+secreto;
        try {
            var mail=sender.getObject();
            var mensaje=mail.createMimeMessage();
            var helper=new MimeMessageHelper(mensaje, MimeMessageHelper.MULTIPART_MODE_MIXED, StandardCharsets.UTF_8.name());
            helper.setFrom(from,"NEXUM"); helper.setTo(normalizado); helper.setSubject("Restablece tu contraseña | NEXUM");
            String html;
            try (var input=new ClassPathResource("mail/recuperacion.html").getInputStream()) {
                html=new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("{{ENLACE}}",HtmlUtils.htmlEscape(enlace));
            }
            helper.setText("Restablece tu contraseña en NEXUM: "+enlace
                    +" . El enlace vence en 15 minutos y es de un solo uso. Si no lo solicitaste, ignora este mensaje.",html);
            mail.send(mensaje);
        } catch (MailException | MessagingException | IOException e) { throw new CorreoNoDisponibleException(); }
    }
    @Transactional
    public void restablecer(String token, String password, String ip) {
        String key="restablecer:"+digest(token);
        limites.verificar(key,ip);
        limites.fallo(key,ip);
        UUID id;
        String secreto;
        try {
            String[] partes=token.split("\\.",-1);
            if (partes.length!=2 || !partes[1].matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException();
            id=UUID.fromString(partes[0]); secreto=partes[1];
        } catch (IllegalArgumentException e) { throw invalido(); }
        var propietario=codigos.propietario(id).orElseThrow(RecuperacionPasswordService::invalido);
        var usuario=usuarios.bloquearPorId(propietario).orElseThrow(RecuperacionPasswordService::invalido);
        var registro=codigos.bloquearPorId(id).orElseThrow(RecuperacionPasswordService::invalido);
        if (usuario.getDeletedAt()!=null || !TIPO.equals(registro.getTipo()) || Boolean.TRUE.equals(registro.getUsado())
                || !registro.getExpiraEn().isAfter(LocalDateTime.now())
                || !encoder.matches(digest(secreto+"|"+usuario.getEmail()),registro.getCodigoHash())) throw invalido();
        usuario.setPasswordHash(encoder.encode(password));
        for (var pendiente:codigos.findByUsuario_Id(propietario)) {
            if (TIPO.equals(pendiente.getTipo())) pendiente.setUsado(true);
        }
        registro.setUsado(true);
        usuarios.save(usuario); codigos.save(registro);
    }
    private static SolicitudInvalidaException invalido() {
        return new SolicitudInvalidaException("El enlace no es válido o ha vencido. Solicita uno nuevo.");
    }
}
