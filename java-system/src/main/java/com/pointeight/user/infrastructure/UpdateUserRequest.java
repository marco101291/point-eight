package com.pointeight.user.infrastructure;

import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

/** Layer 1 editing only. There's no way to touch Layer 2 from the outside. */
public record UpdateUserRequest(
    @Min(18) @Max(120) int age,
    @NotNull Gender gender,
    @NotEmpty Set<Gender> seekingGenders,
    SeekingType seekingType,
    @NotBlank String city,
    @NotBlank String profession,
    List<String> hobbies,
    @NotBlank String photoUrl) {

  public Profile toProfile() {
    return new Profile(
        age, gender, seekingGenders, seekingType, city, profession, hobbies, photoUrl);
  }
}
