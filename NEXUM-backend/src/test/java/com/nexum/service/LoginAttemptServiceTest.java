package com.nexum.service;
import com.nexum.exception.DemasiadosIntentosException;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class LoginAttemptServiceTest {
    @Test void cincoFallosBloqueanYElBloqueoExpira() {
        Clock clock=mock(Clock.class); AtomicLong time=new AtomicLong(0); when(clock.millis()).thenAnswer(i->time.get());
        LoginAttemptService service=new LoginAttemptService(clock);
        for(int i=0;i<5;i++) { service.verificar("a@example.com","127.0.0.1"); service.fallo("a@example.com","127.0.0.1"); }
        assertThatThrownBy(()->service.verificar("a@example.com","otra-ip")).isInstanceOf(DemasiadosIntentosException.class);
        time.set(900001); assertThatCode(()->service.verificar("a@example.com","127.0.0.1")).doesNotThrowAnyException();
    }
    @Test void exitoReiniciaContadorDeLaCuenta() {
        LoginAttemptService service=new LoginAttemptService();
        for(int i=0;i<4;i++) service.fallo("a@example.com","127.0.0.1");
        service.exito("a@example.com"); service.fallo("a@example.com","127.0.0.1");
        assertThatCode(()->service.verificar("a@example.com","127.0.0.1")).doesNotThrowAnyException();
    }
    @Test void limitaUnaIpAunqueCambieDeCorreo() {
        LoginAttemptService service=new LoginAttemptService();
        for(int i=0;i<30;i++) service.fallo(i+"@example.com","127.0.0.1");
        assertThatThrownBy(()->service.verificar("nuevo@example.com","127.0.0.1")).isInstanceOf(DemasiadosIntentosException.class);
    }
}
