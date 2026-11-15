package com.nexum.exception;
public class CorreoNoDisponibleException extends RuntimeException {
    public CorreoNoDisponibleException() { super("El envío de correos no está disponible. Inténtalo más tarde o contacta al administrador."); }
}
