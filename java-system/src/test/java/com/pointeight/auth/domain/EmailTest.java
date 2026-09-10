package com.pointeight.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

  @Test
  void normalizes_to_lowercase() {
    assertThat(new Email("Marco@Example.COM").value()).isEqualTo("marco@example.com");
  }

  @Test
  void rejects_a_value_with_no_at_sign() {
    assertThatThrownBy(() -> new Email("not-an-email"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_a_value_with_no_domain() {
    assertThatThrownBy(() -> new Email("marco@")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void two_emails_differing_only_in_case_are_equal() {
    assertThat(new Email("Marco@Example.com")).isEqualTo(new Email("marco@example.com"));
  }
}
