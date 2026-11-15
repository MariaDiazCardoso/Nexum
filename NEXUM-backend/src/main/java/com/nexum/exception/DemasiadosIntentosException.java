package com.nexum.exception;
public class DemasiadosIntentosException extends RuntimeException {
    public DemasiadosIntentosException() { super("Demasiados intentos fallidos. Inténtalo de nuevo en 15 minutos."); }
}
