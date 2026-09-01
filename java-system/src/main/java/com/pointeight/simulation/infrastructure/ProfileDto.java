package com.pointeight.simulation.infrastructure;

import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import java.util.List;
import java.util.Set;

/** Layer 1 as the Engine expects it. Kept separate from {@link Profile} on purpose: see DEC-004. */
record ProfileDto(
    int age,
    Gender gender,
    Set<Gender> seekingGenders,
    SeekingType seekingType,
    String city,
    String profession,
    List<String> hobbies) {

  static ProfileDto from(Profile profile) {
    return new ProfileDto(
        profile.age(),
        profile.gender(),
        profile.seekingGenders(),
        profile.seekingType(),
        profile.city(),
        profile.profession(),
        profile.hobbies());
  }
}
