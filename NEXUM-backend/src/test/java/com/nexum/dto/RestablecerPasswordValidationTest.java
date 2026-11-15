package com.nexum.dto;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RestablecerPasswordValidationTest {
    @Test void exigeTokenYPasswordFuerte() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var v=factory.getValidator();
            String token="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a90."+"a".repeat(43);
            assertThat(v.validate(new RestablecerPasswordRequest(token,"ClaveSegura1!"))).isEmpty();
            assertThat(v.validate(new RestablecerPasswordRequest(token,"1234"))).isNotEmpty();
            assertThat(v.validate(new RestablecerPasswordRequest("otro", "ClaveSegura1!"))).isNotEmpty();
            assertThat(v.validate(new RestablecerPasswordRequest(token,"Á".repeat(35)+"a1!"))).isNotEmpty();
        }
    }
}
