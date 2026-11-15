package com.nexum.service;

import com.nexum.entity.CodigoVerificacion;
import com.nexum.repository.*;
import com.nexum.exception.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class CorreoVerificacionService {
    private final UsuarioRepository usuarios;
    private final CodigoVerificacionRepository codigos;
    private final PasswordEncoder encoder;
    private final LoginAttemptService limites;
    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;
    private static final SecureRandom RANDOM=new SecureRandom();
    public CorreoVerificacionService(UsuarioRepository usuarios, CodigoVerificacionRepository codigos,
            PasswordEncoder encoder, LoginAttemptService limites, ObjectProvider<JavaMailSender> sender,
            @Value("${spring.mail.host:}") String host, @Value("${app.mail.from:}") String from) {
        this.usuarios=usuarios; this.codigos=codigos; this.encoder=encoder; this.limites=limites;
        this.sender=sender; this.host=host; this.from=from;
    }
    @Transactional
    public void solicitar(String email, String ip) {
        String normalizado=email.trim().toLowerCase(Locale.ROOT);
        limites.verificar("envio:"+normalizado,ip);
        if(host.isBlank() || from.isBlank() || sender.getIfAvailable()==null) throw new CorreoNoDisponibleException();
        limites.fallo("envio:"+normalizado,ip);
        var usuario=usuarios.findByEmail(normalizado).orElse(null);
        // La respuesta HTTP no revela si existe la cuenta.
        if(usuario==null || usuario.getDeletedAt()!=null || Boolean.TRUE.equals(usuario.getEmailVerificado())) return;
        usuario=usuarios.bloquearPorId(usuario.getId()).orElseThrow();
        if (!normalizado.equalsIgnoreCase(usuario.getEmail())) return;
        for(var anterior:codigos.findByUsuario_Id(usuario.getId())) {
            if("EMAIL".equals(anterior.getTipo()) && !Boolean.TRUE.equals(anterior.getUsado())) anterior.setUsado(true);
        }
        String claro=String.format("%06d",RANDOM.nextInt(1_000_000));
        codigos.save(CodigoVerificacion.builder().usuario(usuario).tipo("EMAIL").codigoHash(encoder.encode(claro))
                .usado(false).expiraEn(LocalDateTime.now().plusMinutes(15)).build());
        try {
            var mailSender = sender.getObject();
            var mensaje = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mensaje, MimeMessageHelper.MULTIPART_MODE_MIXED, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "NEXUM");
            helper.setTo(normalizado);
            helper.setSubject("Verifica tu correo en NEXUM");
            String html;
            try (var input = new ClassPathResource("mail/verificacion.html").getInputStream()) {
                html = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("{{CODIGO}}", claro);
            }
            helper.setText("Tu código de verificación de NEXUM es: " + claro
                    + ". Caduca en 15 minutos y es de un solo uso. Si solicitas otro código, utiliza el más reciente."
                    + " Si no solicitaste este código, ignora este correo.", html);
            mailSender.send(mensaje);
        } catch (MailException | MessagingException | IOException ex) {
            throw new CorreoNoDisponibleException();
        }
    }
    @Transactional
    public void confirmar(String email, String codigo) {
        String normalizado=email.trim().toLowerCase(Locale.ROOT);
        String key="verificacion:"+normalizado;
        limites.verificar(key,key);
        var usuario=usuarios.findByEmail(normalizado).orElse(null);
        if (usuario != null) usuario=usuarios.bloquearPorId(usuario.getId()).orElseThrow();
        var registro=usuario==null ? null : codigos.findFirstByUsuario_IdAndTipoAndUsadoFalseOrderByFechaCreacionDesc(usuario.getId(),"EMAIL").orElse(null);
        if(usuario==null || usuario.getDeletedAt()!=null || registro==null || Boolean.TRUE.equals(registro.getUsado()) || !registro.getExpiraEn().isAfter(LocalDateTime.now()) ||
                !encoder.matches(codigo,registro.getCodigoHash())) {
            limites.fallo(key,key); limites.verificar(key,key);
            throw new SolicitudInvalidaException("El código no es válido o ha vencido");
        }
        registro.setUsado(true); usuario.setEmailVerificado(true);
        codigos.save(registro); usuarios.save(usuario); limites.exito(key);
    }
}
