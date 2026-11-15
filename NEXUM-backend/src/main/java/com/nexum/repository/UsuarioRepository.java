package com.nexum.repository;

import com.nexum.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    @org.springframework.data.jpa.repository.Query("select u from Usuario u where lower(trim(u.email)) = lower(trim(:email))")
    Optional<Usuario> findByEmail(@org.springframework.data.repository.query.Param("email") String email);


    @org.springframework.data.jpa.repository.Query("select count(u)>0 from Usuario u where lower(trim(u.email)) = lower(trim(:email))")
    boolean existsByEmail(@org.springframework.data.repository.query.Param("email") String email);


    @org.springframework.data.jpa.repository.Query("select count(u)>0 from Usuario u where lower(trim(u.email)) = lower(trim(:email)) and u.id<>:id")
    boolean existsByEmailAndIdNot(@org.springframework.data.repository.query.Param("email") String email, @org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.id=:id")
    Optional<Usuario> bloquearPorId(@org.springframework.data.repository.query.Param("id") UUID id);
}
