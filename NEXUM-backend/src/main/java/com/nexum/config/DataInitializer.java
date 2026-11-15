package com.nexum.config;

import com.nexum.entity.RolUsuario;
import com.nexum.entity.Usuario;
import com.nexum.entity.UsuarioXp;
import com.nexum.repository.UsuarioRepository;
import com.nexum.repository.UsuarioXpRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UsuarioRepository usuarioRepository;
    private final UsuarioXpRepository usuarioXpRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String adminEmail = System.getenv("ADMIN_EMAIL");
        String adminPassword = System.getenv("ADMIN_PASSWORD");

        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL o ADMIN_PASSWORD no definidos; se omite el administrador inicial");
            return;
        }

        String emailNormalizado = adminEmail.trim().toLowerCase();
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            log.info("Administrador inicial omitido: ya existe el email {}", emailNormalizado);
            return;
        }

        String genero = System.getenv("ADMIN_GENERO");
        String nacimiento = System.getenv("ADMIN_FECHA_NACIMIENTO");
        if (genero == null || nacimiento == null) {
            log.warn("Define ADMIN_GENERO y ADMIN_FECHA_NACIMIENTO para crear el administrador inicial");
            return;
        }
        com.nexum.entity.Genero generoAdmin = com.nexum.entity.Genero.valueOf(genero);
        java.time.LocalDate fechaAdmin = java.time.LocalDate.parse(nacimiento);
        if (fechaAdmin.isAfter(java.time.LocalDate.now())) throw new IllegalArgumentException("Fecha de nacimiento del administrador inválida");
        if (adminPassword.length()<8 || adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 ||
                !adminPassword.matches("(?s)^(?=.*\\p{Lu})(?=.*\\p{Ll})(?=.*\\p{Nd})(?=.*[^\\p{L}\\p{N}\\s]).*$"))
            throw new IllegalArgumentException("La contraseña inicial no cumple los requisitos");
        Usuario admin = Usuario.builder()
                .nombre("Administrador")
                .apellido("Nexum")
                .email(emailNormalizado)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .rol(RolUsuario.ADMINISTRADOR)
                .genero(generoAdmin)
                .fechaNacimiento(fechaAdmin)
                .emailVerificado(true)
                .build();

        Usuario guardado = usuarioRepository.save(admin);

        UsuarioXp xp = UsuarioXp.builder()
                .usuario(guardado)
                .xpTotal(0)
                .nivel(1)
                .rachaDias(0)
                .build();
        usuarioXpRepository.save(xp);

        log.info("Administrador inicial creado con email {}", emailNormalizado);
    }
}
